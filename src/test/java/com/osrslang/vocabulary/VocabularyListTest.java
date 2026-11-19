package com.osrslang.vocabulary;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.runelite.client.util.Filepath;
import net.runelite.client.util.Filepath.Unchecked;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class VocabularyListTest
{
	private VocabularyList list;
	private Gson gson;
	private Path tempDir;
	private Filepath vocabularyFile;

	@Before
	public void setUp() throws IOException
	{
		gson = new GsonBuilder().create();
		tempDir = Files.createTempDirectory("vocab-test-");
		vocabularyFile = Unchecked.getRooted(tempDir).join("vocabulary.json");
		list = new VocabularyList(vocabularyFile, gson);
	}

	@After
	public void tearDown() throws IOException
	{
		if (tempDir != null && Files.exists(tempDir))
		{
			deleteRecursively(tempDir);
		}
	}

	private void deleteRecursively(Path path) throws IOException
	{
		if (Files.isDirectory(path))
		{
			try (var stream = Files.list(path))
			{
				stream.forEach(p ->
				{
					try
					{
						deleteRecursively(p);
					}
					catch (IOException e)
					{
						throw new RuntimeException(e);
					}
				});
			}
		}
		Files.deleteIfExists(path);
	}

	@Test
	public void testAddAndGet()
	{
		list.add(new VocabularyEntry("sword", "das Schwert", "I need a sword", "Ich brauche ein Schwert.", "en"));

		assertEquals(1, list.size());
		assertTrue(list.get("sword", "en").isPresent());
		assertEquals("das Schwert", list.get("sword", "en").get().getTranslation());
	}

	@Test
	public void testPersonalVocabularyIsScopedToTargetLanguage()
	{
		list.add(new VocabularyEntry("sword", "das Schwert", null, null, "en", "de"));
		assertTrue(list.lookupWord("sword", "en", "de").isPresent());
		assertFalse(list.lookupWord("sword", "en", "fr").isPresent());
	}

	@Test
	public void testLegacyCsvUsesConfiguredTargetLanguage()
	{
		String legacyCsv = "word,translation,language,known,times_seen,context,context_translation\\n"
			+ "sword,das Schwert,en,false,1,,";
		assertEquals(1, list.importFromCsv(legacyCsv, "en", "de"));
		assertTrue(list.lookupWord("sword", "en", "de").isPresent());
		assertFalse(list.lookupWord("sword", "en", "fr").isPresent());
	}

	@Test
	public void testLookupIsCaseInsensitive()
	{
		list.add(new VocabularyEntry("Sword", "das Schwert", null, null, "en"));

		assertTrue(list.get("sword", "en").isPresent());
		assertTrue(list.get("SWORD", "en").isPresent());
	}

	@Test
	public void testDifferentLanguagesAreSeparate()
	{
		list.add(new VocabularyEntry("sword", "das Schwert", null, null, "en"));
		list.add(new VocabularyEntry("sword", "l'épée", null, null, "fr"));

		assertEquals(2, list.size());
	}

	@Test
	public void testAddingSameWordUpdatesAndCounts()
	{
		list.add(new VocabularyEntry("sword", "das Schwert", "I need a sword", "Ich brauche ein Schwert.", "en"));
		list.add(new VocabularyEntry("sword", "das Schwert", "A different sentence", "Ein anderer Satz.", "en"));

		assertEquals(1, list.size());

		VocabularyEntry entry = list.get("sword", "en").get();
		assertEquals(2, entry.getTimesSeen());
		assertEquals("A different sentence", entry.getContextSentence());
	}

	@Test
	public void testKnownFlag()
	{
		list.add(new VocabularyEntry("sword", "das Schwert", null, null, "en"));

		assertFalse(list.get("sword", "en").get().isKnown());
		assertEquals(1, list.getUnknown().size());
		assertEquals(0, list.getKnown().size());

		list.get("sword", "en").get().setKnown(true);

		assertEquals(0, list.getUnknown().size());
		assertEquals(1, list.getKnown().size());
	}

	@Test
	public void testRemove()
	{
		list.add(new VocabularyEntry("sword", "das Schwert", null, null, "en"));

		assertTrue(list.remove("sword", "en"));
		assertFalse(list.remove("sword", "en"));
		assertEquals(0, list.size());
	}

	@Test
	public void testCsvExport()
	{
		list.add(new VocabularyEntry("sword", "das Schwert", "I need a sword", "Ich brauche ein Schwert.", "en"));
		list.add(new VocabularyEntry("shield", "der Schild", "Give me a shield", "Gib mir einen Schild.", "en"));

		String csv = list.toCsv();
		List<String> lines = java.util.Arrays.asList(csv.split("\n"));

		assertEquals(3, lines.size());
		assertTrue(lines.get(0).startsWith("word,translation"));
		assertTrue(csv.contains("\"sword\",\"das Schwert\""));
	}

	@Test
	public void testCsvEscapesQuotes()
	{
		list.add(new VocabularyEntry("say", "sagen", "He said \"hello\"", null, "en"));

		String csv = list.toCsv();

		assertTrue(csv.contains("\"\"hello\"\""));
	}

	@Test
	public void testEmptyListCsv()
	{
		String csv = list.toCsv();

		assertEquals(1, csv.split("\n").length);
	}

	@Test
	public void testJsonPersistenceAndReload() throws IOException
	{
		// Use a separate file for this test to avoid Windows file locking issues
		Path persistDir = Files.createTempDirectory("vocab-persist-");
		Filepath persistFile = Unchecked.getRooted(persistDir).join("vocabulary.json");

		// Create first list and add entries
		VocabularyList list1 = new VocabularyList(persistFile, gson);
		list1.add(new VocabularyEntry("sword", "das Schwert", "I need a sword", "Ich brauche ein Schwert.", "en"));
		list1.add(new VocabularyEntry("shield", "der Schild", "Give me a shield", "Gib mir einen Schild.", "en"));
		list1.get("sword", "en").get().setKnown(true);
		list1.save(); // Persist the known flag change

		// Create a new list that loads from the same file
		VocabularyList reloaded = new VocabularyList(persistFile, gson);

		assertEquals(2, reloaded.size());
		assertTrue(reloaded.get("sword", "en").isPresent());
		assertEquals("das Schwert", reloaded.get("sword", "en").get().getTranslation());
		assertTrue(reloaded.get("sword", "en").get().isKnown());
		assertEquals(1, reloaded.get("shield", "en").get().getTimesSeen()); // default is 1
		assertEquals("I need a sword", reloaded.get("sword", "en").get().getContextSentence());
		assertEquals("Ich brauche ein Schwert.", reloaded.get("sword", "en").get().getContextTranslation());

		deleteRecursively(persistDir);
	}

	@Test
	public void testEmptyJsonFile() throws IOException
	{
		// Write an empty JSON object to the file
		vocabularyFile.write("{}");

		VocabularyList reloaded = new VocabularyList(vocabularyFile, gson);
		assertEquals(0, reloaded.size());
	}

	@Test
	public void testMalformedJsonFile() throws IOException
	{
		// Write malformed JSON to the file
		vocabularyFile.write("{ invalid json }");

		VocabularyList reloaded = new VocabularyList(vocabularyFile, gson);
		assertEquals(0, reloaded.size());
	}

	@Test
	public void testMissingJsonFile() throws IOException
	{
		// File doesn't exist - should not crash
		Path missingDir = Files.createTempDirectory("vocab-missing-");
		Filepath missingFile = Unchecked.getRooted(missingDir).join("missing.json");
		VocabularyList newList = new VocabularyList(missingFile, gson);
		assertEquals(0, newList.size());

		// Cleanup
		deleteRecursively(missingDir);
	}

	@Test
	public void testCsvExportImportRoundTrip() throws IOException
	{
		list.add(new VocabularyEntry("sword", "das Schwert", "I need a sword", "Ich brauche ein Schwert.", "en"));
		list.add(new VocabularyEntry("shield", "der Schild", "Give me a shield", "Gib mir einen Schild.", "en"));
		list.get("sword", "en").get().setKnown(true);

		String csv = list.toCsv();

		// Create a new list and import
		Path importDir = Files.createTempDirectory("vocab-import-");
		Filepath importFile = Unchecked.getRooted(importDir).join("vocabulary.json");
		VocabularyList importList = new VocabularyList(importFile, gson);

		int imported = importList.importFromCsv(csv, "en");

		assertEquals(2, imported);
		assertEquals(2, importList.size());
		assertTrue(importList.get("sword", "en").isPresent());
		assertEquals("das Schwert", importList.get("sword", "en").get().getTranslation());
		assertTrue(importList.get("sword", "en").get().isKnown());
		assertEquals(1, importList.get("sword", "en").get().getTimesSeen());

		deleteRecursively(importDir);
	}

	@Test
	public void testUnicodeCharacters() throws IOException
	{
		// German umlauts, French accents, Spanish ñ
		list.add(new VocabularyEntry("schön", "beautiful", "Das ist schön.", "That is beautiful.", "de"));
		list.add(new VocabularyEntry("français", "French", "Je parle français.", "I speak French.", "fr"));
		list.add(new VocabularyEntry("niño", "child", "El niño juega.", "The child plays.", "es"));

		String csv = list.toCsv();

		// Verify Unicode is preserved in CSV
		assertTrue(csv.contains("schön"));
		assertTrue(csv.contains("français"));
		assertTrue(csv.contains("niño"));

		// Re-import and verify
		Path importDir = Files.createTempDirectory("vocab-unicode-");
		Filepath importFile = Unchecked.getRooted(importDir).join("vocabulary.json");
		VocabularyList importList = new VocabularyList(importFile, gson);

		int imported = importList.importFromCsv(csv, "en");
		assertEquals(3, imported);
		assertEquals("beautiful", importList.get("schön", "de").get().getTranslation());
		assertEquals("French", importList.get("français", "fr").get().getTranslation());
		assertEquals("child", importList.get("niño", "es").get().getTranslation());

		deleteRecursively(importDir);
	}

	@Test
	public void testCommasAndEscapedQuotes() throws IOException
	{
		list.add(new VocabularyEntry("say", "sagen", "He said \"hello, world\"", "Er sagte \"hallo, Welt\".", "en"));

		String csv = list.toCsv();

		// Verify quotes are escaped and commas don't break parsing
		assertTrue(csv.contains("\"\"hello, world\"\""));
		assertTrue(csv.contains("\"\"hallo, Welt\"\""));

		// Re-import and verify
		Path importDir = Files.createTempDirectory("vocab-quotes-");
		Filepath importFile = Unchecked.getRooted(importDir).join("vocabulary.json");
		VocabularyList importList = new VocabularyList(importFile, gson);

		int imported = importList.importFromCsv(csv, "en");
		assertEquals(1, imported);
		assertEquals("He said \"hello, world\"", importList.get("say", "en").get().getContextSentence());
		assertEquals("Er sagte \"hallo, Welt\".", importList.get("say", "en").get().getContextTranslation());

		deleteRecursively(importDir);
	}

	@Test
	public void testMultilineContextSentences() throws IOException
	{
		String multilineContext = "First line\nSecond line\nThird line";
		String multilineTranslation = "Erste Zeile\nZweite Zeile\nDritte Zeile";

		list.add(new VocabularyEntry("multiline", "mehrzeilig", multilineContext, multilineTranslation, "en"));

		String csv = list.toCsv();

		// Re-import and verify multiline is preserved
		Path importDir = Files.createTempDirectory("vocab-multiline-");
		Filepath importFile = Unchecked.getRooted(importDir).join("vocabulary.json");
		VocabularyList importList = new VocabularyList(importFile, gson);

		int imported = importList.importFromCsv(csv, "en");
		assertEquals(1, imported);
		assertEquals(multilineContext, importList.get("multiline", "en").get().getContextSentence());
		assertEquals(multilineTranslation, importList.get("multiline", "en").get().getContextTranslation());

		deleteRecursively(importDir);
	}

	@Test
	public void testDuplicateEntriesNotImported() throws IOException
	{
		list.add(new VocabularyEntry("sword", "das Schwert", "I need a sword", "Ich brauche ein Schwert.", "en"));

		String csv = list.toCsv();

		// Import same CSV again
		int imported = list.importFromCsv(csv, "en");

		assertEquals(0, imported); // No new entries imported
		assertEquals(1, list.size());
	}

	@Test
	public void testInvalidCsvHeader() throws IOException
	{
		String invalidCsv = "wrong,header,columns\nword,translation,language";

		int imported = list.importFromCsv(invalidCsv, "en");

		assertEquals(0, imported);
		assertEquals(0, list.size());
	}

	@Test
	public void testMalformedCsvRows() throws IOException
	{
		// Valid header, but some malformed rows
		String csv = "word,translation,language,known,times_seen,context,context_translation\n" +
			"valid,gültig,en,true,1,context,translation\n" +
			"incomplete,row\n" +
			"another,valid,de,false,2,context2,translation2\n" +
			",empty_word,en\n" +
			"empty_translation,,en";

		int imported = list.importFromCsv(csv, "en");

		// Should import 2 valid rows, skip malformed ones
		assertEquals(2, imported);
		assertEquals(2, list.size());
		assertTrue(list.get("valid", "en").isPresent());
		assertTrue(list.get("another", "de").isPresent());
	}

	@Test
	public void testKnownStatusAndTimesSeenPreservation() throws IOException
	{
		list.add(new VocabularyEntry("sword", "das Schwert", "I need a sword", "Ich brauche ein Schwert.", "en"));
		list.get("sword", "en").get().setKnown(true);
		// Increment times seen a few times
		list.get("sword", "en").get().incrementTimesSeen();
		list.get("sword", "en").get().incrementTimesSeen();
		// timesSeen should be 3 (1 initial + 2 increments)

		String csv = list.toCsv();

		Path importDir = Files.createTempDirectory("vocab-known-");
		Filepath importFile = Unchecked.getRooted(importDir).join("vocabulary.json");
		VocabularyList importList = new VocabularyList(importFile, gson);

		int imported = importList.importFromCsv(csv, "en");
		assertEquals(1, imported);

		VocabularyEntry importedEntry = importList.get("sword", "en").get();
		assertTrue(importedEntry.isKnown());
		assertEquals(3, importedEntry.getTimesSeen());

		deleteRecursively(importDir);
	}
}