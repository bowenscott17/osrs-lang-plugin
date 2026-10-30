package com.osrslang.translation.provider;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * Parses an OSRS language archive read from {@code main_file_cache.idx11}.
 * <p>
 * OSRS stores localised strings as pairs of {@code (english, translated)} inside a
 * versioned, length-prefixed archive. When the German pack is downloaded, an entry like
 * {@code "Logs"} (the source text the game uses internally) is paired with its
 * translation {@code "Stämme"}. The provider looks up translations by matching the
 * English source text.
 * <p>
 * Two archive layouts are understood:
 * <ul>
 * <li><b>v1</b> - a stream of {@code (value, key)} pairs, each length-prefixed. A
 * length byte of {@code 0xFF} means the next four bytes hold the length instead.</li>
 * <li><b>v2</b> - a count followed by the same {@code (value, key)} pair format.</li>
 * </ul>
 * The parser is deliberately permissive: a malformed entry stops the parse without
 * failing the whole provider, and what was parsed so far is returned. A missing
 * terminator, an unknown version, or a buffer overrun all degrade to "we got
 * whatever was readable".
 */
@Slf4j
public class OsrsLanguageArchive
{
	/**
	 * Parses the bytes of a single language archive.
	 *
	 * @param bytes the bytes of the archive, already BZIP2-decompressed
	 * @return an English-to-translation map (case-preserving on insert); the provider
	 *         lowercases the keys at lookup time
	 */
	public Map<String, String> parse(byte[] bytes)
	{
		if (bytes == null || bytes.length == 0)
		{
			return Collections.emptyMap();
		}

		ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
		int version = buffer.get(0) & 0xFF;

		Map<String, String> result;
		try
		{
			buffer.position(1);
			if (version == 0 || version == 1)
			{
				result = parseV1(buffer);
			}
			else if (version == 2 || version == 3)
			{
				result = parseV2(buffer);
			}
			else
			{
				log.debug("OSRS language archive version {} is not understood; returning empty", version);
				return Collections.emptyMap();
			}
		}
		catch (RuntimeException e)
		{
			log.debug("OSRS language archive parse failed at version {}", version, e);
			return Collections.emptyMap();
		}

		log.debug("Parsed {} entries from OSRS language archive version {}", result.size(), version);
		return result;
	}

	/**
	 * Parses v1 archives: a stream of {@code (value, key)} pairs where each string is
	 * length-prefixed. A {@code 0xFF} length byte means the next four bytes hold the
	 * length instead.
	 * <p>
	 * The {@code value} is the English source text and the {@code key} is the
	 * translation - so the map is built as {@code key -> value} for caller clarity,
	 * which makes "give me the German for X" read as "give me the value for the key X".
	 */
	private Map<String, String> parseV1(ByteBuffer buffer)
	{
		Map<String, String> result = new LinkedHashMap<>();

		while (buffer.hasRemaining())
		{
			String translated = readLengthPrefixedString(buffer);
			if (translated == null || !buffer.hasRemaining())
			{
				break;
			}

			String english = readLengthPrefixedString(buffer);
			if (english == null || english.isEmpty())
			{
				break;
			}

			result.put(english, translated);
		}

		return result;
	}

	/**
	 * Parses v2 archives: a 4-byte count followed by the same {@code (value, key)} pair
	 * format as v1.
	 */
	private Map<String, String> parseV2(ByteBuffer buffer)
	{
		Map<String, String> result = new LinkedHashMap<>();
		int count = buffer.getInt();

		for (int i = 0; i < count; i++)
		{
			if (!buffer.hasRemaining())
			{
				break;
			}

			String translated = readLengthPrefixedString(buffer);
			if (translated == null)
			{
				break;
			}

			String english = readLengthPrefixedString(buffer);
			if (english == null || english.isEmpty())
			{
				break;
			}

			result.put(english, translated);
		}

		return result;
	}

	/**
	 * Reads a length-prefixed string.
	 * <p>
	 * Lengths are encoded as one byte when under 255, and as the four-byte value
	 * following a {@code 0xFF} sentinel otherwise.
	 *
	 * @return the string, or {@code null} when the buffer is exhausted
	 */
	private static String readLengthPrefixedString(ByteBuffer buffer)
	{
		if (!buffer.hasRemaining())
		{
			return null;
		}

		int length = buffer.get() & 0xFF;
		if (length == 0xFF)
		{
			if (buffer.remaining() < 4)
			{
				return null;
			}
			length = buffer.getInt();
		}

		if (length == 0)
		{
			return "";
		}

		if (length > buffer.remaining())
		{
			return null;
		}

		byte[] bytes = new byte[length];
		buffer.get(bytes);
		return new String(bytes, StandardCharsets.UTF_8);
	}

	/**
	 * Builds a lowercased-key view of the parsed map.
	 * <p>
	 * The provider does case-insensitive lookups so that "Cow" and "cow" both resolve
	 * to the same translation.
	 */
	public Map<String, String> lowercased(Map<String, String> parsed)
	{
		Map<String, String> result = new HashMap<>(parsed.size());
		for (Map.Entry<String, String> entry : parsed.entrySet())
		{
			String key = entry.getKey();
			if (key != null && !key.isEmpty())
			{
				result.put(key.toLowerCase(java.util.Locale.ROOT), entry.getValue());
			}
		}
		return result;
	}
}
