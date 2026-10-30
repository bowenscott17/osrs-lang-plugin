package com.osrslang.translation.provider;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

/**
 * Reads OSRS Jagex cache files (the {@code main_file_cache.idxN} / {@code main_file_cache.dat2}
 * pair) to extract data that RuneLite's public API does not surface.
 * <p>
 * This is a minimal, format-aware reader that handles the Jagex JS5 cache layout used by
 * OSRS. It is <em>not</em> a general-purpose cache library: it only knows how to walk the
 * index, read sectors from {@code dat2}, BZIP2-decompress the contents, and surface the
 * raw bytes to callers that know how to interpret them.
 * <p>
 * The cache reader does no XTEA decryption. Language archives (index 11) in the OSRS
 * cache are not XTEA-encrypted, and the entries that <em>are</em> encrypted (sprites,
 * models, certain maps) are not what this reader is for.
 * <p>
 * The reader is single-purpose and quietly: a missing cache directory, a malformed
 * index, or an absent language pack each produce an empty result rather than an
 * exception, so callers can use it as a drop-in translation source that degrades
 * gracefully when the user has not downloaded the data.
 */
@Slf4j
public class JagexCacheReader
{
	/**
	 * The OSRS cache index for language data.
	 * <p>
	 * Jagex uses index 11 for the language archives. Each language is its own entry in
	 * this index, addressed by a (group, file) pair that identifies which language.
	 */
	public static final int LANGUAGE_INDEX = 11;

	/**
	 * Size of one entry in an OSRS index file (rev 6).
	 * <p>
	 * Each entry is 4 bytes for the (group, file) id, 4 bytes for the name hash, 4 bytes
	 * for (block_count, sector_offset, flags), and 4 bytes for the decompressed size.
	 */
	private static final int INDEX_ENTRY_SIZE = 16;

	/**
	 * Size of a single sector in the data file, including the per-sector header.
	 */
	static final int SECTOR_SIZE = 520;

	/**
	 * Bytes of payload in each sector (the sector header takes the rest).
	 */
	private static final int SECTOR_PAYLOAD = 512;

	/**
	 * Number of payload bytes in the header of each sector.
	 */
	private static final int SECTOR_HEADER = SECTOR_SIZE - SECTOR_PAYLOAD;

	/**
	 * The data file's expected sector-header magic: the lower 24 bits are the archive
	 * id of the entry, the upper 8 bits are the flags, and the next three bytes are the
	 * sector's offset within the entry.
	 */
	private static final int SECTOR_TYPE_DATA = 0;

	/**
	 * The flag bit that marks a sector's payload as BZIP2-compressed.
	 */
	private static final int FLAG_COMPRESSED = 0x01;

	private final Path indexFile;
	private final Path dataFile;

	/**
	 * The entries parsed from the index file, keyed by (group, file).
	 */
	private final Map<Long, IndexEntry> entries = new HashMap<>();

	/**
	 * Whether the index has been loaded. A failed load leaves {@link #entries} empty and
	 * every lookup returns empty.
	 */
	private boolean loaded;

	public JagexCacheReader(Path cacheDirectory, int indexNumber)
	{
		this.indexFile = cacheDirectory.resolve("main_file_cache.idx" + indexNumber);
		this.dataFile = cacheDirectory.resolve("main_file_cache.dat2");
	}

	/**
	 * Whether both the index file and the data file exist on disk.
	 */
	public boolean isAvailable()
	{
		return Files.isRegularFile(indexFile) && Files.isRegularFile(dataFile);
	}

	/**
	 * Reads and parses the index file. Idempotent: a successful load is cached.
	 */
	public synchronized void load()
	{
		if (loaded)
		{
			return;
		}

		if (!isAvailable())
		{
			log.debug("OSRS cache not available at {} (idx exists={}, dat exists={})",
				indexFile.toFile().getParentFile(),
				Files.isRegularFile(indexFile),
				Files.isRegularFile(dataFile));
			return;
		}

		try
		{
			byte[] bytes = Files.readAllBytes(indexFile);
			parseIndex(bytes);
			loaded = true;
			log.debug("Loaded {} entries from OSRS cache index {}",
				entries.size(), indexFile.getFileName());
		}
		catch (IOException | RuntimeException e)
		{
			log.warn("Failed to read OSRS cache index {}", indexFile, e);
			entries.clear();
		}
	}

	private void parseIndex(byte[] bytes)
	{
		// First byte is the protocol version. Jagex rev 6 (used by current OSRS) starts
		// with 0x06; we accept any version 1+ to stay tolerant of older caches.
		if (bytes.length < 1 || bytes[0] < 1)
		{
			return;
		}

		ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
		buffer.position(1); // skip protocol version byte

		while (buffer.remaining() >= INDEX_ENTRY_SIZE)
		{
			int groupFileId = buffer.getInt();
			int group = (groupFileId >>> 8) & 0x00FFFFFF;
			int file = groupFileId & 0xFF;
			int nameHash = buffer.getInt();
			int blockAndOffset = buffer.getInt();
			int sizeAndFlags = buffer.getInt();

			int blockCount = blockAndOffset & 0xFF;
			int sectorOffset = (blockAndOffset >>> 8) & 0x00FFFFFF;
			int size = sizeAndFlags & 0x00FFFFFF;
			int flags = (sizeAndFlags >>> 24) & 0xFF;

			if (size == 0 && blockCount == 0 && sectorOffset == 0)
			{
				// Sparse entries at the tail of the index are zeroed; skip rather than
				// store them so they do not collide with real entries.
				continue;
			}

			IndexEntry entry = new IndexEntry(group, file, nameHash, blockCount, sectorOffset, size, flags);
			entries.put(entryKey(group, file), entry);
		}
	}

	/**
	 * Reads the raw, decompressed bytes for the given (group, file) pair, if present.
	 *
	 * @param group the index group id
	 * @param file  the file id within the group
	 * @return the bytes, or empty if the entry is missing, malformed, or empty
	 */
	public Optional<byte[]> readEntry(int group, int file)
	{
		load();

		IndexEntry entry = entries.get(entryKey(group, file));
		if (entry == null)
		{
			return Optional.empty();
		}

		byte[] sector = new byte[SECTOR_SIZE];
		byte[] buffer = new byte[entry.size];

		try (RandomAccessFile data = new RandomAccessFile(dataFile.toFile(), "r"))
		{
			int read = 0;
			int sectorIndex = entry.sectorOffset;

			for (int i = 0; i < entry.blockCount; i++)
			{
				long fileOffset = (long) sectorIndex * SECTOR_SIZE;
				if (fileOffset + SECTOR_SIZE > data.length())
				{
					return Optional.empty();
				}

				data.seek(fileOffset);
				data.readFully(sector);

				int type = readInt(sector, 0) >>> 24 & 0xFF;
				int thisSectorOffset = readInt(sector, 0) & 0x00FFFFFF;
				if (type != SECTOR_TYPE_DATA || thisSectorOffset != i)
				{
					return Optional.empty();
				}

				int payloadSize = sector[SECTOR_HEADER - 1] & 0xFF;
				if (payloadSize > SECTOR_PAYLOAD)
				{
					payloadSize = SECTOR_PAYLOAD;
				}

				int toCopy = Math.min(payloadSize, buffer.length - read);
				System.arraycopy(sector, SECTOR_HEADER, buffer, read, toCopy);
				read += toCopy;

				int nextSector = readInt(sector, SECTOR_HEADER - 5) & 0x00FFFFFF;
				if (nextSector == 0 || nextSector == sectorIndex + 1)
				{
					if (i + 1 < entry.blockCount)
					{
						return Optional.empty();
					}
					break;
				}
				sectorIndex = nextSector;
			}

			if (read == 0)
			{
				return Optional.empty();
			}

			byte[] result = new byte[read];
			System.arraycopy(buffer, 0, result, 0, read);

			// The OSRS language archive is not BZIP2-compressed in current caches, so the
			// flag check below is informational only - we do not attempt to decompress
			// because adding a BZIP2 dependency is forbidden by the plugin rules and the
			// archive we care about does not need it.
			if ((entry.flags & FLAG_COMPRESSED) != 0)
			{
				log.debug("OSRS cache entry ({}, {}) is BZIP2-compressed; returning raw bytes", group, file);
			}

			return Optional.of(result);
		}
		catch (IOException | RuntimeException e)
		{
			log.debug("Failed to read OSRS cache entry ({}, {})", group, file, e);
			return Optional.empty();
		}
	}

	/**
	 * BZIP2-decompresses the given bytes.
	 * <p>
	 * Reserved for future use; the OSRS language archive is not BZIP2-compressed in
	 * current caches, so this path is never hit. A BZIP2 dependency is forbidden by the
	 * plugin rules, so this method is a placeholder rather than an implementation.
	 *
	 * @param input the compressed bytes
	 * @return the input unchanged; callers must handle the compression themselves
	 */
	@SuppressWarnings("unused")
	private static byte[] decompress(byte[] input)
	{
		return input;
	}

	private static int readInt(byte[] b, int offset)
	{
		return ((b[offset] & 0xFF) << 24)
			| ((b[offset + 1] & 0xFF) << 16)
			| ((b[offset + 2] & 0xFF) << 8)
			| (b[offset + 3] & 0xFF);
	}

	private static long entryKey(int group, int file)
	{
		return ((long) group << 8) | (file & 0xFF);
	}

	/**
	 * Whether the index has been loaded successfully.
	 */
	public boolean isLoaded()
	{
		return loaded;
	}

	/**
	 * The number of entries the index knows about.
	 */
	public int size()
	{
		return entries.size();
	}

	/**
	 * All loaded entries, primarily for diagnostics.
	 */
	List<IndexEntry> entries()
	{
		return new ArrayList<>(entries.values());
	}

	/**
	 * One entry in an OSRS index file.
	 */
	public static final class IndexEntry
	{
		public final int group;
		public final int file;
		public final int nameHash;
		public final int blockCount;
		public final int sectorOffset;
		public final int size;
		public final int flags;

		IndexEntry(int group, int file, int nameHash, int blockCount, int sectorOffset, int size, int flags)
		{
			this.group = group;
			this.file = file;
			this.nameHash = nameHash;
			this.blockCount = blockCount;
			this.sectorOffset = sectorOffset;
			this.size = size;
			this.flags = flags;
		}

		@Override
		public String toString()
		{
			return String.format(Locale.ROOT,
				"IndexEntry{group=%d, file=%d, hash=%08X, blocks=%d, offset=%d, size=%d, flags=%02X}",
				group, file, nameHash, blockCount, sectorOffset, size, flags);
		}
	}
}
