package com.osrslang.text;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Loads translation entries from the {@code /dictionary/extra-*.txt} resources that
 * the build pipeline writes from external open-source datasets.
 * <p>
 * The plugin ships with a curated {@code en-de.json} of hand-checked entries. The extra
 * text files hold larger, less-curated sets - interface commands from RuneLingual, the
 * OSRS German cache, or any future open-source dump - that the importer filters to the
 * entries safe to merge in (single tokens on both sides, no dialogue).
 * <p>
 * Filtering lives here rather than in the resource file because the importer is the
 * single place that decides what the dictionary accepts: every entry that comes in
 * through it has been checked for the same shape, so the dictionary can stay a plain
 * JSON map without any per-entry metadata to filter on at lookup time.
 * <p>
 * The output is keyed by the lowercase English term so case-insensitive lookups stay
 * cheap, and preserves the original English spelling as {@code english} for callers that
 * want to show it back.
 */
public final class DictionaryImporter
{
	/**
	 * Anything outside letters, digits and the basic ASCII range - used to reject entries
	 * that contain dialogue, NPC names or game formatting.
	 */
	private static final Pattern UNSAFE = Pattern.compile("[\\s,.;:?!(){}\"'<>]");

	private DictionaryImporter()
	{
	}

	/**
	 * Reads {@code /dictionary/<resourceName>} from the plugin resources, parses each
	 * {@code english|translation} line, and returns the filtered entries.
	 *
	 * @param resourceName the relative resource path (e.g. {@code extra-runelingual.txt})
	 * @return the entries, in load order
	 */
	public static Map<String, ImportedEntry> loadResource(String resourceName)
	{
		Map<String, ImportedEntry> result = new LinkedHashMap<>();

		InputStream in = DictionaryImporter.class.getResourceAsStream("/dictionary/" + resourceName);
		if (in == null)
		{
			return result;
		}

		try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8);
			 BufferedReader br = new BufferedReader(reader))
		{
			String line;
			while ((line = br.readLine()) != null)
			{
				ImportedEntry entry = parseLine(line);
				if (entry != null)
				{
					result.putIfAbsent(entry.english.toLowerCase(Locale.ROOT), entry);
				}
			}
		}
		catch (IOException e)
		{
			// A malformed extra file is not an error - the curated dictionary still works.
		}

		return result;
	}

	/**
	 * Parses one {@code english|translation} line into an entry, or returns {@code null}
	 * when the line is blank, a comment, or fails the safety filter.
	 */
	static ImportedEntry parseLine(String raw)
	{
		if (raw == null)
		{
			return null;
		}

		String line = raw.trim();
		if (line.isEmpty() || line.startsWith("#"))
		{
			return null;
		}

		int pipe = line.indexOf('|');
		if (pipe < 0)
		{
			return null;
		}

		String english = line.substring(0, pipe).trim();
		String translation = line.substring(pipe + 1).trim();
		if (english.isEmpty() || translation.isEmpty() || english.equalsIgnoreCase(translation))
		{
			return null;
		}

		if (UNSAFE.matcher(english).find() || UNSAFE.matcher(translation).find())
		{
			return null;
		}

		// Reject anything that does not have at least one letter on either side.
		if (!english.chars().anyMatch(Character::isLetter) || !translation.chars().anyMatch(Character::isLetter))
		{
			return null;
		}

		return new ImportedEntry(english, translation);
	}

	/**
	 * One parsed line of an external dictionary file.
	 *
	 * @param english    the source-language term as it was written in the source file
	 * @param translation the target-language translation as it was written
	 */
	public static final class ImportedEntry
	{
		public final String english;
		public final String translation;

		ImportedEntry(String english, String translation)
		{
			this.english = english;
			this.translation = translation;
		}
	}
}
