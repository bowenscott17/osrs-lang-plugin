package com.osrslang.vocabulary;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.runelite.client.util.Filepath;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

/**
 * The user's saved vocabulary.
 * <p>
 * Words are stored in insertion order so the list reads in the order they were learned.
 * <p>
 * Persisted to {@code vocabulary.json} in the plugin directory using RuneLite's Filepath API.
 */
@Slf4j
public class VocabularyList
{
	private static final String VOCABULARY_FILE = "vocabulary.json";

	private final Map<String, VocabularyEntry> entries = new LinkedHashMap<>();
	private final Gson gson;
	private final Filepath vocabularyFile;
	private final String defaultTargetLanguage;

	/**
	 * Type token for the vocabulary map structure.
	 */
	private static final Type VOCABULARY_TYPE = new TypeToken<Map<String, VocabularyEntry>>(){}.getType();

	/**
	 * Creates a new vocabulary list backed by a JSON file.
	 *
	 * @param vocabularyFile the Filepath to the vocabulary JSON file (can be null for testing)
	 * @param gson           the Gson instance for JSON serialization
	 */
	public VocabularyList(Filepath vocabularyFile, Gson gson)
	{
		this(vocabularyFile, gson, null);
	}

	public VocabularyList(Filepath vocabularyFile, Gson gson, String defaultTargetLanguage)
	{
		this.gson = gson;
		this.vocabularyFile = vocabularyFile;
		this.defaultTargetLanguage = defaultTargetLanguage;
		if (vocabularyFile != null)
		{
			load();
		}
	}

	/**
	 * Returns the entry as a translation.provider.VocabularyEntry for integration with
	 * the translation system.
	 */
	public Optional<com.osrslang.translation.provider.VocabularyEntry> lookupWord(String word, String language)
	{
		VocabularyEntry entry = entries.get(key(word, language));
		if (entry == null)
		{
			return Optional.empty();
		}
		// Convert to translation.provider.VocabularyEntry
		com.osrslang.translation.provider.VocabularyEntry providerEntry =
			new com.osrslang.translation.provider.VocabularyEntry(
				entry.getWord(),
				entry.getTranslation(),
				null, // partOfSpeech
				null, // dictionaryForm
				null, // gender
				entry.getContextSentence(), // example
				entry.getContextTranslation() // english gloss
			);
		return Optional.of(providerEntry);
	}

	/** Looks up a saved word only when its recorded target language matches. */
	public Optional<com.osrslang.translation.provider.VocabularyEntry> lookupWord(
		String word, String sourceLanguage, String targetLanguage)
	{
		VocabularyEntry entry = entries.get(key(word, sourceLanguage));
		if (entry == null || entry.getTargetLanguage() == null || targetLanguage == null
			|| !entry.getTargetLanguage().equalsIgnoreCase(targetLanguage))
		{
			return Optional.empty();
		}
		return lookupWord(word, sourceLanguage);
	}

	/**
	 * Loads the vocabulary from disk. Missing, empty, or malformed files are treated
	 * as an empty vocabulary without overwriting any existing in-memory entries.
	 */
	public void load()
	{
		if (vocabularyFile == null)
		{
			return;
		}

		if (!vocabularyFile.exists())
		{
			log.debug("No vocabulary file found at {}, starting empty", vocabularyFile);
			return;
		}

		try (Reader reader = vocabularyFile.openReader())
		{
			Map<String, VocabularyEntry> loaded = gson.fromJson(reader, VOCABULARY_TYPE);
			if (loaded != null && !loaded.isEmpty())
			{
				// Preserve insertion order by putting into LinkedHashMap
				entries.clear();
				for (VocabularyEntry entry : loaded.values())
				{
					// Older JSON files did not record the target language. Associate them
					// with the configured target rather than serving them for every target.
					if (entry != null && entry.getTargetLanguage() == null && defaultTargetLanguage != null)
					{
						entry.setTargetLanguage(defaultTargetLanguage);
					}
				}
				entries.putAll(loaded);
				log.debug("Loaded {} vocabulary entries from {}", entries.size(), vocabularyFile);
			}
			else
			{
				log.debug("Vocabulary file exists but is empty");
			}
		}
		catch (IOException | RuntimeException e)
		{
			log.warn("Failed to load vocabulary from {}, keeping existing entries", vocabularyFile, e);
			// Do NOT clear entries - preserve what we have in memory
		}
	}

	/**
	 * Saves to a temporary file and replaces the destination without deleting the last
	 * good copy first. If replacement fails, the existing vocabulary remains intact.
	 */
	public void save()
	{
		if (vocabularyFile == null)
		{
			return;
		}

		try
		{
			// Create parent directories, not the file path itself
			vocabularyFile.getParent().createDirectories();
			// Write to a temporary file first
			Filepath tempFile = vocabularyFile.getParent().createTempFile("vocab-", ".tmp");
			try
			{
				try (Writer writer = tempFile.openWriter(StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))
				{
					gson.toJson(entries, writer);
				}
				tempFile.moveTo(vocabularyFile, StandardCopyOption.REPLACE_EXISTING);
			}
			finally
			{
				if (tempFile.exists())
				{
					tempFile.delete();
				}
			}
			log.debug("Saved {} vocabulary entries to {}", entries.size(), vocabularyFile);
		}
		catch (IOException e)
		{
			log.warn("Failed to save vocabulary to {}", vocabularyFile, e);
		}
		catch (RuntimeException e)
		{
			log.warn("Failed to save vocabulary to {}", vocabularyFile, e);
		}
	}

	/**
	 * Adds a word, or updates the existing entry for it.
	 */
	public void add(VocabularyEntry entry)
	{
		if (entry.getTargetLanguage() == null && defaultTargetLanguage != null)
		{
			entry.setTargetLanguage(defaultTargetLanguage);
		}
		String key = key(entry.getWord(), entry.getLanguage());
		VocabularyEntry existing = entries.get(key);

		if (existing == null)
		{
			entry.incrementTimesSeen();
			entries.put(key, entry);
			save();
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
		save();
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
		boolean removed = entries.remove(key(word, language)) != null;
		if (removed)
		{
			save();
		}
		return removed;
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
		save();
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
		sb.append("word,translation,language,known,times_seen,context,context_translation,target_language\n");

		for (VocabularyEntry entry : entries.values())
		{
			sb.append(escape(entry.getWord())).append(',')
				.append(escape(entry.getTranslation())).append(',')
				.append(escape(entry.getLanguage())).append(',')
				.append(entry.isKnown()).append(',')
				.append(entry.getTimesSeen()).append(',')
				.append(escape(entry.getContextSentence())).append(',')
				.append(escape(entry.getContextTranslation())).append(',')
				.append(escape(entry.getTargetLanguage())).append('\n');
		}

		return sb.toString();
	}

	/**
	 * Imports vocabulary entries from CSV format.
	 * <p>
	 * Validates each entry before adding. Malformed entries are skipped with a warning.
	 * Existing entries are not overwritten - new entries are only added if the word
	 * doesn't already exist in the vocabulary.
	 *
	 * @param csv      the CSV content to import
	 * @param language the language code for imported entries (if not specified in CSV)
	 * @return the number of entries successfully imported
	 */
	public int importFromCsv(String csv, String language)
	{
		return importFromCsv(csv, language, null);
	}

	/** Imports CSV and assigns the configured target to legacy rows without one. */
	public int importFromCsv(String csv, String language, String defaultTargetLanguage)
	{
		if (csv == null || csv.trim().isEmpty())
		{
			return 0;
		}

		List<String[]> rows = parseCsv(csv);
		if (rows.size() < 2)
		{
			log.warn("CSV import: file has no data rows");
			return 0;
		}

		// Validate header
		String[] header = rows.get(0);
		if (!isValidHeader(header))
		{
			log.warn("CSV import: invalid header, expected at least word,translation,language");
			return 0;
		}

		// Skip header row
		int imported = 0;
		for (int i = 1; i < rows.size(); i++)
		{
			String[] fields = rows.get(i);
			if (fields.length == 0 || (fields.length == 1 && fields[0].isEmpty()))
			{
				continue;
			}

			VocabularyEntry entry = parseCsvFields(fields, language, defaultTargetLanguage);
			if (entry != null)
			{
				String key = key(entry.getWord(), entry.getLanguage());
				if (!entries.containsKey(key))
				{
					entries.put(key, entry);
					imported++;
				}
				else
				{
					log.debug("CSV import: skipping duplicate entry for '{}'", entry.getWord());
				}
			}
		}

		if (imported > 0)
		{
			save();
			log.info("Imported {} vocabulary entries from CSV", imported);
		}

		return imported;
	}

	/**
	 * Parses CSV content into rows, handling quoted fields with commas, escaped quotes, and newlines.
	 */
	private List<String[]> parseCsv(String csv)
	{
		List<String[]> rows = new ArrayList<>();
		StringBuilder current = new StringBuilder();
		List<String> fields = new ArrayList<>();
		boolean inQuotes = false;

		for (int i = 0; i < csv.length(); i++)
		{
			char c = csv.charAt(i);

			if (c == '"')
			{
				if (inQuotes && i + 1 < csv.length() && csv.charAt(i + 1) == '"')
				{
					// Escaped quote
					current.append('"');
					i++;
				}
				else
				{
					// Toggle quote state
					inQuotes = !inQuotes;
				}
			}
			else if (c == ',' && !inQuotes)
			{
				fields.add(current.toString());
				current.setLength(0);
			}
			else if ((c == '\n' || c == '\r') && !inQuotes)
			{
				// Handle \r\n and \n line endings
				if (c == '\r' && i + 1 < csv.length() && csv.charAt(i + 1) == '\n')
				{
					i++; // Skip the \n in \r\n
				}
				fields.add(current.toString());
				rows.add(fields.toArray(new String[0]));
				fields.clear();
				current.setLength(0);
			}
			else
			{
				current.append(c);
			}
		}

		// Add the last field/row if not empty
		if (current.length() > 0 || fields.size() > 0)
		{
			fields.add(current.toString());
			rows.add(fields.toArray(new String[0]));
		}

		return rows;
	}

	/**
	 * Validates that the CSV header has the expected columns.
	 */
	private boolean isValidHeader(String[] header)
	{
		if (header.length < 3)
		{
			log.warn("CSV import: header has too few columns: {}", (Object) header);
			return false;
		}
		// Check for required columns (case-insensitive)
		String col0 = header[0].trim().toLowerCase(Locale.ROOT);
		String col1 = header[1].trim().toLowerCase(Locale.ROOT);
		String col2 = header[2].trim().toLowerCase(Locale.ROOT);
		return "word".equals(col0) && "translation".equals(col1) && "language".equals(col2);
	}

	/**
	 * Parses CSV fields into a VocabularyEntry.
	 * Expected format: word,translation,language,known,times_seen,context,context_translation
	 */
	private VocabularyEntry parseCsvFields(String[] fields, String defaultLanguage, String defaultTargetLanguage)
	{
		try
		{
			if (fields.length < 3)
			{
				log.warn("CSV import: row has too few fields: {}", (Object) fields);
				return null;
			}

			String word = unescape(fields[0]);
			String translation = unescape(fields[1]);
			String lang = fields.length > 2 && !fields[2].isEmpty() ? unescape(fields[2]) : defaultLanguage;
			boolean known = fields.length > 3 && Boolean.parseBoolean(unescape(fields[3]));
			int timesSeen = fields.length > 4 ? Integer.parseInt(unescape(fields[4])) : 1;
			String context = fields.length > 5 ? unescape(fields[5]) : "";
			String contextTranslation = fields.length > 6 ? unescape(fields[6]) : "";
			String targetLanguage = fields.length > 7 && !fields[7].isEmpty()
				? unescape(fields[7]) : defaultTargetLanguage;

			if (word == null || word.isEmpty() || translation == null || translation.isEmpty())
			{
				log.warn("CSV import: empty word or translation: {}", (Object) fields);
				return null;
			}

			VocabularyEntry entry = new VocabularyEntry(word, translation, context, contextTranslation, lang, targetLanguage);
			entry.setKnown(known);
			// Set timesSeen directly from CSV (add() increments, but import bypasses add())
			for (int i = 0; i < timesSeen; i++)
			{
				entry.incrementTimesSeen();
			}

			return entry;
		}
		catch (Exception e)
		{
			log.warn("CSV import: failed to parse row: {}", (Object) fields, e);
			return null;
		}
	}

	/**
	 * Removes surrounding quotes and unescapes doubled quotes.
	 */
	private String unescape(String field)
	{
		if (field == null)
		{
			return "";
		}
		field = field.trim();
		if (field.length() >= 2 && field.startsWith("\"") && field.endsWith("\""))
		{
			field = field.substring(1, field.length() - 1);
		}
		return field.replace("\"\"", "\"");
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