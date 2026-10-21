package com.osrslang.vocabulary;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The user's saved vocabulary.
 * <p>
 * Words are stored in insertion order so the list reads in the order they were learned.
 */
public class VocabularyList
{
	private final Map<String, VocabularyEntry> entries = new LinkedHashMap<>();

	/**
	 * Adds a word, or updates the existing entry for it.
	 */
	public void add(VocabularyEntry entry)
	{
		String key = key(entry.getWord(), entry.getLanguage());
		VocabularyEntry existing = entries.get(key);

		if (existing == null)
		{
			entry.incrementTimesSeen();
			entries.put(key, entry);
			return;
		}

		// Keep the newest context, but remember that the word came up again.
		existing.incrementTimesSeen();

		if (entry.getTranslation() != null && !entry.getTranslation().isEmpty())
		{
			existing.setTranslation(entry.getTranslation());
		}
		if (entry.getContextSentence() != null && !entry.getContextSentence().isEmpty())
		{
			existing.setContextSentence(entry.getContextSentence());
		}
		if (entry.getContextTranslation() != null && !entry.getContextTranslation().isEmpty())
		{
			existing.setContextTranslation(entry.getContextTranslation());
		}
	}

	/**
	 * Looks up a saved word.
	 */
	public Optional<VocabularyEntry> get(String word, String language)
	{
		return Optional.ofNullable(entries.get(key(word, language)));
	}

	/**
	 * Removes a word.
	 *
	 * @return whether the word was present
	 */
	public boolean remove(String word, String language)
	{
		return entries.remove(key(word, language)) != null;
	}

	/**
	 * Every saved word, in the order they were added.
	 */
	public Collection<VocabularyEntry> getAll()
	{
		return new ArrayList<>(entries.values());
	}

	/**
	 * The words not yet marked as known.
	 */
	public List<VocabularyEntry> getUnknown()
	{
		List<VocabularyEntry> unknown = new ArrayList<>();
		for (VocabularyEntry entry : entries.values())
		{
			if (!entry.isKnown())
			{
				unknown.add(entry);
			}
		}
		return unknown;
	}

	/**
	 * The words marked as known.
	 */
	public List<VocabularyEntry> getKnown()
	{
		List<VocabularyEntry> known = new ArrayList<>();
		for (VocabularyEntry entry : entries.values())
		{
			if (entry.isKnown())
			{
				known.add(entry);
			}
		}
		return known;
	}

	/**
	 * The number of saved words.
	 */
	public int size()
	{
		return entries.size();
	}

	/**
	 * Removes every word.
	 */
	public void clear()
	{
		entries.clear();
	}

	/**
	 * Exports the words as CSV, with a header row.
	 * <p>
	 * Values are quoted and internal quotes are doubled, so a sentence containing a
	 * comma or a quote does not break the file.
	 */
	public String toCsv()
	{
		StringBuilder sb = new StringBuilder();
		sb.append("word,translation,language,known,times_seen,context,context_translation\n");

		for (VocabularyEntry entry : entries.values())
		{
			sb.append(escape(entry.getWord())).append(',')
				.append(escape(entry.getTranslation())).append(',')
				.append(escape(entry.getLanguage())).append(',')
				.append(entry.isKnown()).append(',')
				.append(entry.getTimesSeen()).append(',')
				.append(escape(entry.getContextSentence())).append(',')
				.append(escape(entry.getContextTranslation())).append('\n');
		}

		return sb.toString();
	}

	/**
	 * Quotes a value for CSV.
	 */
	private static String escape(String value)
	{
		if (value == null)
		{
			return "";
		}

		return '"' + value.replace("\"", "\"\"") + '"';
	}

	private static String key(String word, String language)
	{
		return (word == null ? "" : word.toLowerCase(Locale.ROOT)) + "|" + (language == null ? "" : language);
	}
}
