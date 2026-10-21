package com.osrslang.translation;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.util.Filepath;
import com.osrslang.translation.provider.TranslationProvider;

/**
 * A translation cache backed by a JSON file in the plugin's data directory.
 * <p>
 * The cache is bounded: when it exceeds the configured maximum, the oldest entries are
 * dropped. Entries are written to disk lazily rather than on every single put, because
 * a dialogue can produce many entries in quick succession.
 */
@Slf4j
public class JsonTranslationCache implements TranslationCache
{
	private static final String FILE_NAME = "translation-cache.json";

	/**
	 * One cached entry as stored on disk.
	 */
	static class CacheEntry
	{
		String text;
		String translation;
		String from;
		String to;
		String source;
	}

	private final Gson gson;
	private final Filepath file;
	private final int maxEntries;

	/**
	 * Keyed by language pair then by lowercase source text. A linked map so that the
	 * oldest entries are the first to be evicted.
	 */
	private final Map<String, Map<String, CacheEntry>> entries = new LinkedHashMap<>();

	/**
	 * Set when the cache has unsaved changes.
	 */
	private boolean dirty;

	/**
	 * Guards the in-memory map. Disk access happens on a background thread, but reads
	 * can come from the client thread.
	 */
	private final Object lock = new Object();

	public JsonTranslationCache(Gson gson, Filepath file, int maxEntries)
	{
		this.gson = gson;
		this.file = file;
		this.maxEntries = Math.max(1, maxEntries);
	}

	/**
	 * Loads the cache from disk. Missing or corrupt files are treated as an empty cache.
	 */
	public void load()
	{
		synchronized (lock)
		{
			entries.clear();
			dirty = false;

			if (!file.exists())
			{
				return;
			}

			try (Reader reader = file.openReader())
			{
				Type type = new TypeToken<Map<String, Map<String, CacheEntry>>>()
				{
				}.getType();

				Map<String, Map<String, CacheEntry>> loaded = gson.fromJson(reader, type);
				if (loaded != null)
				{
					entries.putAll(loaded);
				}
			}
			catch (IOException | RuntimeException e)
			{
				log.warn("Failed to load translation cache, starting empty", e);
				entries.clear();
			}

			log.debug("Loaded {} cached translations", size());
		}
	}

	/**
	 * Writes the cache to disk if it has changed.
	 */
	public void save()
	{
		synchronized (lock)
		{
			if (!dirty)
			{
				return;
			}

			try
			{
				file.createDirectories();
				try (Writer writer = file.openWriter(StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))
				{
					gson.toJson(entries, writer);
				}
				dirty = false;
			}
			catch (IOException | RuntimeException e)
			{
				log.warn("Failed to save translation cache", e);
			}
		}
	}

	@Override
	public Optional<TranslationResult> get(String text, Language from, Language to)
	{
		if (text == null)
		{
			return Optional.empty();
		}

		synchronized (lock)
		{
			Map<String, CacheEntry> pair = entries.get(key(from, to));
			if (pair == null)
			{
				return Optional.empty();
			}

			CacheEntry entry = pair.get(text.toLowerCase(Locale.ROOT));
			if (entry == null)
			{
				return Optional.empty();
			}

			return Optional.of(new TranslationResult(
				entry.text, entry.translation, to, parseSource(entry.source)));
		}
	}

	@Override
	public void put(String text, Language from, Language to, TranslationResult result)
	{
		if (text == null || result == null)
		{
			return;
		}

		synchronized (lock)
		{
			Map<String, CacheEntry> pair = entries.computeIfAbsent(key(from, to), k -> new LinkedHashMap<>());

			CacheEntry entry = new CacheEntry();
			entry.text = text;
			entry.translation = result.getTranslatedText();
			entry.from = from.getCode();
			entry.to = to.getCode();
			entry.source = result.getSource().name();

			pair.put(text.toLowerCase(Locale.ROOT), entry);
			dirty = true;

			evictIfNeeded();
		}
	}

	@Override
	public int size()
	{
		synchronized (lock)
		{
			int total = 0;
			for (Map<String, CacheEntry> pair : entries.values())
			{
				total += pair.size();
			}
			return total;
		}
	}

	@Override
	public void clear()
	{
		synchronized (lock)
		{
			entries.clear();
			dirty = true;
		}
	}

	/**
	 * Removes the oldest entries until the cache is within its limit.
	 */
	private void evictIfNeeded()
	{
		while (size() > maxEntries)
		{
			boolean removed = false;

			for (Map.Entry<String, Map<String, CacheEntry>> pair : entries.entrySet())
			{
				if (!pair.getValue().isEmpty())
				{
					// LinkedHashMap iterates in insertion order, so the first key is the oldest.
					pair.getValue().remove(pair.getValue().keySet().iterator().next());
					removed = true;
					break;
				}
			}

			if (!removed)
			{
				break;
			}
		}
	}

	private static String key(Language from, Language to)
	{
		return from.getCode() + "-" + to.getCode();
	}

	private static TranslationSource parseSource(String name)
	{
		if (name == null)
		{
			return TranslationSource.CACHE;
		}

		try
		{
			return TranslationSource.valueOf(name);
		}
		catch (IllegalArgumentException e)
		{
			return TranslationSource.CACHE;
		}
	}
}
