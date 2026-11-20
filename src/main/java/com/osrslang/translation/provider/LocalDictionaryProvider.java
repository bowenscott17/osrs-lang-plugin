package com.osrslang.translation.provider;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import javax.inject.Inject;
import javax.inject.Provider;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.Plugin;
import com.osrslang.translation.Language;
import com.osrslang.translation.TranslationResult;
import com.osrslang.translation.TranslationSource;
import com.osrslang.text.GameText;

/**
 * Provides word-level translations from a bundled vocabulary dictionary.
 * <p>
 * This provider is for single words, not sentences. A sentence that happens to be one
 * word is still handled, but multi-word text is not translated here - that is the job of
 * the sentence-level providers.
 */
@Slf4j
public class LocalDictionaryProvider implements TranslationProvider
{
	/**
	 * The data file loaded from the plugin's resources.
	 */
	static final String DICTIONARY_RESOURCE = "/dictionary/%s-%s.json";

	/**
	 * One dictionary entry as stored on disk.
	 */
	static class DictionaryEntry
	{
		String translation;
		String partOfSpeech;
		String dictionaryForm;
		String gender;
		String example;
		String english;
	}

	/**
	 * Wrapper class for JSON deserialization of the dictionary map.
	 * Replaces TypeToken to avoid java.lang.reflect import.
	 */
	static class DictionaryMap extends HashMap<String, DictionaryEntry>
	{
		// Empty - Gson uses this class directly for type information
	}

	private final Plugin plugin;
	private final Gson gson;

	/**
	 * Keyed by language code pair, then by lowercase word.
	 */
	private final Map<String, Map<String, VocabularyEntry>> dictionaries = new HashMap<>();

	/**
	 * Guarded by itself; loading happens off the client thread.
	 */
	private boolean loaded;

	public LocalDictionaryProvider(Plugin plugin, Gson gson)
	{
		this.plugin = plugin;
		this.gson = gson;
	}

	/**
	 * Loads the dictionaries for the given language pairs.
	 * <p>
	 * Safe to call repeatedly; only the first call per pair does work. Missing files are
	 * not an error - a language with no bundled dictionary simply has no entries.
	 */
	public synchronized void load(Language from, Language to)
	{
		String key = key(from, to);
		if (dictionaries.containsKey(key))
		{
			return;
		}

		Map<String, VocabularyEntry> entries = new HashMap<>();

		String resource = String.format(DICTIONARY_RESOURCE, from.getCode(), to.getCode());
		try (InputStream in = LocalDictionaryProvider.class.getResourceAsStream(resource))
		{
			if (in == null)
			{
				log.debug("No bundled dictionary for {}", key);
				dictionaries.put(key, entries);
				return;
			}

			try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8))
			{
				DictionaryMap parsed = gson.fromJson(reader, DictionaryMap.class);
				if (parsed != null)
				{
					for (Map.Entry<String, DictionaryEntry> e : parsed.entrySet())
					{
						DictionaryEntry d = e.getValue();
						if (d == null || d.translation == null)
						{
							continue;
						}
						entries.put(e.getKey().toLowerCase(Locale.ROOT), new VocabularyEntry(
							e.getKey(),
							d.translation,
							d.partOfSpeech,
							d.dictionaryForm,
							d.gender,
							d.example,
							d.english));
					}
				}
			}
		}
		catch (IOException | RuntimeException e)
		{
			log.warn("Failed to load dictionary {}", key, e);
		}

		log.debug("Loaded {} entries for {}", entries.size(), key);
		dictionaries.put(key, entries);
	}

	@Override
	public String getName()
	{
		return "Local dictionary";
	}

	@Override
	public boolean isEnabled()
	{
		return true;
	}

	@Override
	public boolean supports(Language source, Language target)
	{
		return source != target;
	}

	@Override
	public int getPriority()
	{
		// Consulted after the verified database, but before machine translation.
		return 50;
	}

	@Override
	public CompletableFuture<Optional<TranslationResult>> translate(String source, Language from, Language to, GameText.Type context)
	{
		Optional<TranslationResult> result = lookupWord(source, from, to)
			.map(entry -> (TranslationResult) new TranslationResult(
				source, entry.getTranslation(), to, TranslationSource.DICTIONARY));

		return CompletableFuture.completedFuture(result);
	}

	@Override
	public Optional<VocabularyEntry> lookupWord(String word, Language from, Language to)
	{
		if (word == null || word.isEmpty())
		{
			return Optional.empty();
		}

		String key = key(from, to);
		Map<String, VocabularyEntry> entries = dictionaries.get(key);
		if (entries == null)
		{
			return Optional.empty();
		}

		return Optional.ofNullable(entries.get(word.toLowerCase(Locale.ROOT)));
	}

	/**
	 * The number of entries loaded for a language pair.
	 */
	public int size(Language from, Language to)
	{
		Map<String, VocabularyEntry> entries = dictionaries.get(key(from, to));
		return entries == null ? 0 : entries.size();
	}

	/**
	 * All loaded language pair keys.
	 */
	public List<String> loadedPairs()
	{
		return new ArrayList<>(Collections.unmodifiableSet(dictionaries.keySet()));
	}

	private static String key(Language from, Language to)
	{
		return from.getCode() + "-" + to.getCode();
	}

	@Override
	public Plugin getPlugin()
	{
		return plugin;
	}
}