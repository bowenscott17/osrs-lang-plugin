package com.osrslang.text;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import com.osrslang.translation.Language;

/**
 * Holds the translations of the game's own interface text.
 * <p>
 * These are the fixed strings the client draws from its scripts - skill names, attack
 * styles, prayer names, button captions. They are ordinary words rather than sentences,
 * so they are stored as a plain map from the English text to its translation.
 * <p>
 * The table is deliberately limited to strings the game actually writes. Anything else is
 * left alone, because a translation applied to text the game never produced is a guess
 * about what the client meant.
 */
@Slf4j
public class InterfaceStringDatabase
{
	/**
	 * Where the table for a language pair is bundled.
	 */
	static final String RESOURCE = "/interface-strings/%s-%s.json";

	/**
	 * Keyed by language pair, then by the exact English text.
	 */
	private final Map<String, Map<String, String>> tables = new HashMap<>();

	private final Gson gson;

	public InterfaceStringDatabase(Gson gson)
	{
		this.gson = gson;
	}

	/**
	 * Loads the table for a language pair.
	 * <p>
	 * Safe to call repeatedly; only the first call per pair reads anything. A pair with no
	 * bundled table is not an error - it simply has no entries, and nothing is translated.
	 */
	public synchronized void load(Language from, Language to)
	{
		String pair = key(from, to);
		if (tables.containsKey(pair))
		{
			return;
		}

		Map<String, String> entries = new HashMap<>();

		String resource = String.format(RESOURCE, from.getCode(), to.getCode());
		try (InputStream in = InterfaceStringDatabase.class.getResourceAsStream(resource))
		{
			if (in == null)
			{
				log.debug("No bundled interface strings for {}", pair);
				tables.put(pair, entries);
				return;
			}

			Type type = new TypeToken<Map<String, String>>()
			{
			}.getType();

			try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8))
			{
				Map<String, String> parsed = gson.fromJson(reader, type);
				if (parsed != null)
				{
					for (Map.Entry<String, String> entry : parsed.entrySet())
					{
						if (entry.getKey() != null && entry.getValue() != null && !entry.getKey().isEmpty())
						{
							entries.put(entry.getKey(), entry.getValue());
						}
					}
				}
			}
		}
		catch (IOException | RuntimeException e)
		{
			log.warn("Failed to load interface strings {}", pair, e);
		}

		log.debug("Loaded {} interface strings for {}", entries.size(), pair);
		tables.put(pair, Collections.unmodifiableMap(entries));
	}

	/**
	 * The translation of an exact piece of interface text.
	 *
	 * @param text the text the game drew, matched exactly and case-sensitively
	 * @return the translation, or empty when this string is not in the table
	 */
	public Optional<String> lookup(String text, Language from, Language to)
	{
		if (text == null || text.isEmpty())
		{
			return Optional.empty();
		}

		Map<String, String> entries = tables.get(key(from, to));
		return entries == null ? Optional.empty() : Optional.ofNullable(entries.get(text));
	}

	/**
	 * Every translation in the table.
	 * <p>
	 * These are the strings this plugin has written onto the screen, and they must never be
	 * looked up again - see {@link InterfaceTextTranslator}.
	 */
	public Set<String> translationsFor(Language from, Language to)
	{
		Map<String, String> entries = tables.get(key(from, to));
		return entries == null ? Collections.emptySet() : new HashSet<>(entries.values());
	}

	/**
	 * The number of entries loaded for a language pair.
	 */
	public int size(Language from, Language to)
	{
		Map<String, String> entries = tables.get(key(from, to));
		return entries == null ? 0 : entries.size();
	}

	private static String key(Language from, Language to)
	{
		return from.getCode() + "-" + to.getCode();
	}
}