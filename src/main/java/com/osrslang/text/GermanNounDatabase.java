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
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import com.osrslang.translation.Language;
import com.osrslang.translation.TranslationResult;

/**
 * Looks up the German grammatical article of a noun.
 * <p>
 * The hover translator sees nouns that come from three sources, only one of which
 * already carries an article:
 * <ul>
 * <li>the dictionary, where the entry's gender field is authoritative;</li>
 * <li>the local OSRS German language pack, which stores bare nouns ("Ahornbaum")
 * without an article;</li>
 * <li>machine translation, which produces a bare noun with no article metadata.</li>
 * </ul>
 * A noun from the second or third source gets no article unless we know its gender from
 * somewhere else. This class loads a static table of German nouns and their articles,
 * so a noun like "Ahornbaum" can be shown as "Der Ahornbaum" rather than just
 * "Ahornbaum". The table is only consulted when neither the dictionary nor the
 * translation's own leading article can answer the question.
 * <p>
 * Heuristics from word endings are deliberately not used here. A rule like "ends in -e
 * is feminine" gets the easy cases right and the common ones wrong ("Mädchen" is neuter
 * despite ending in a feminine-looking suffix), and an article shown as a fact is more
 * harmful than no article at all. A noun the table does not know gets no article.
 */
@Slf4j
public class GermanNounDatabase
{
	/**
	 * Where the noun table is bundled in the plugin's resources.
	 */
	static final String RESOURCE = "/german-nouns.json";

	private final Gson gson;

	/**
	 * The article of each known noun, keyed by the noun's lowercase form.
	 * <p>
	 * Loaded lazily on first lookup so the plugin pays no startup cost when the
	 * translator is never asked for an article.
	 */
	private volatile Map<String, String> articles = null;

	public GermanNounDatabase(Gson gson)
	{
		this.gson = gson;
	}

	/**
	 * The article of a German noun, when the table knows it.
	 *
	 * @param noun the noun, may already include an article ("der Schwert") or be bare
	 *             ("Schwert")
	 * @return {@code der}, {@code die} or {@code das} when the noun is known and is not
	 *         a verb or adjective; empty otherwise
	 */
	public Optional<String> articleOf(String noun)
	{
		if (noun == null)
		{
			return Optional.empty();
		}

		String trimmed = noun.trim();
		if (trimmed.isEmpty())
		{
			return Optional.empty();
		}

		Map<String, String> table = table();
		if (table.isEmpty())
		{
			return Optional.empty();
		}

		// A translation that already starts with an article is returned as-is; the
		// article for "Der Schwert" is whatever the user wrote, not what the table says.
		String leading = leadingArticle(trimmed);
		if (leading != null)
		{
			String remainder = trimmed.substring(leading.length()).trim();
			if (!remainder.isEmpty())
			{
				String fromTable = table.get(norm(remainder));
				if (fromTable != null)
				{
					return Optional.of(fromTable);
				}
			}

			return Optional.of(leading);
		}

		// Multi-word translations ("Riesenratte") - try the whole phrase first, then the
		// trailing word, so "Ahornbaum" wins over a missing "Ahorn" entry.
		String lookup = norm(trimmed);
		String direct = table.get(lookup);
		if (direct != null)
		{
			return Optional.of(direct);
		}

		int lastSpace = trimmed.lastIndexOf(' ');
		if (lastSpace > 0 && lastSpace < trimmed.length() - 1)
		{
			String lastWord = norm(trimmed.substring(lastSpace + 1));
			String fromLast = table.get(lastWord);
			if (fromLast != null)
			{
				return Optional.of(fromLast);
			}
		}

		return Optional.empty();
	}

	/**
	 * The article that already starts a translation, if it does.
	 * <p>
	 * Recognised only when followed by whitespace or the end of the string, so a word
	 * that begins with "der" but is not an article ("Dermatologe") does not match. The
	 * returned article is lowercased, since the noun table only stores lowercase forms.
	 */
	private static String leadingArticle(String text)
	{
		if (text == null || text.length() < 3)
		{
			return null;
		}

		String head = text.substring(0, 3).toLowerCase(Locale.ROOT);
		if (!TranslationResult.MASCULINE_ARTICLE.equals(head)
			&& !TranslationResult.FEMININE_ARTICLE.equals(head)
			&& !TranslationResult.NEUTER_ARTICLE.equals(head))
		{
			return null;
		}

		if (text.length() > 3)
		{
			char next = text.charAt(3);
			if (Character.isLetterOrDigit(next))
			{
				return null;
			}
		}

		return head;
	}

	/**
	 * Normalises a noun for table lookup - lowercase and trimmed.
	 */
	private static String norm(String noun)
	{
		return noun.toLowerCase(Locale.ROOT);
	}

	/**
	 * Loads the table on first use, returning an empty map on any failure so the rest
	 * of the plugin still works without the file.
	 */
	private Map<String, String> table()
	{
		Map<String, String> existing = articles;
		if (existing != null)
		{
			return existing;
		}

		synchronized (this)
		{
			if (articles != null)
			{
				return articles;
			}

			Map<String, String> loaded = load();
			articles = loaded;
			return loaded;
		}
	}

	/**
	 * Reads the bundled noun table from the plugin's resources.
	 */
	private Map<String, String> load()
	{
		try (InputStream in = GermanNounDatabase.class.getResourceAsStream(RESOURCE))
		{
			if (in == null)
			{
				log.debug("No bundled German noun table at {}", RESOURCE);
				return Collections.emptyMap();
			}

			Type type = new TypeToken<Map<String, String>>()
			{
			}.getType();

			try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8))
			{
				Map<String, String> parsed = gson.fromJson(reader, type);
				if (parsed == null)
				{
					return Collections.emptyMap();
				}

				Map<String, String> byNoun = new HashMap<>();
				for (Map.Entry<String, String> entry : parsed.entrySet())
				{
					if (entry.getKey() == null || entry.getValue() == null)
					{
						continue;
					}

					String article = entry.getValue().trim().toLowerCase(Locale.ROOT);
					if (TranslationResult.MASCULINE_ARTICLE.equals(article)
						|| TranslationResult.FEMININE_ARTICLE.equals(article)
						|| TranslationResult.NEUTER_ARTICLE.equals(article))
					{
						byNoun.put(norm(entry.getKey()), article);
					}
				}

				log.debug("Loaded {} German nouns for article lookup", byNoun.size());
				return Collections.unmodifiableMap(byNoun);
			}
		}
		catch (IOException | RuntimeException e)
		{
			log.warn("Failed to load German noun table", e);
			return Collections.emptyMap();
		}
	}

	/**
	 * The number of nouns currently loaded.
	 * <p>
	 * Used by tests to confirm the table was read.
	 */
	public int size()
	{
		return table().size();
	}

	/**
	 * No-op kept for symmetry with the other databases; the noun table is not paired to a
	 * source language.
	 */
	public void load(Language from, Language to)
	{
		table();
	}
}
