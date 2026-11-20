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
import java.util.Optional;

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
		String legacyCsv = "word,translation,language,known,times_seen,context,context_translation\n"
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

	@Test
	public void testMultipleTargetLanguagesForSameSourceWord() throws IOException
	{
		// Store German -> English translation for "Haus"
		list.add(new VocabularyEntry("Haus", "House", "Das ist ein Haus.", "This is a house.", "de", "en"));
		// Store German -> French translation for "Haus"
		list.add(new VocabularyEntry("Haus", "Maison", "Das ist ein Haus.", "C'est une maison.", "de", "fr"));
		// Store German -> Spanish translation for "Haus"
		list.add(new VocabularyEntry("Haus", "Casa", "Das ist ein Haus.", "Es una casa.", "de", "es"));

		assertEquals(3, list.size());

		// Retrieve each translation independently
		Optional<VocabularyEntry> enEntry = list.get("Haus", "de", "en");
		Optional<VocabularyEntry> frEntry = list.get("Haus", "de", "fr");
		Optional<VocabularyEntry> esEntry = list.get("Haus", "de", "es");

		assertTrue(enEntry.isPresent());
		assertTrue(frEntry.isPresent());
		assertTrue(esEntry.isPresent());

		assertEquals("House", enEntry.get().getTranslation());
		assertEquals("Maison", frEntry.get().getTranslation());
		assertEquals("Casa", esEntry.get().getTranslation());

		// Verify saving one pair does not overwrite another
		assertEquals(3, list.size());
	}

	@Test
	public void testDuplicateInsertionForSamePair() throws IOException
	{
		list.add(new VocabularyEntry("Haus", "House", "Das ist ein Haus.", "This is a house.", "de", "en"));
		list.add(new VocabularyEntry("Haus", "Home", "Ein anderes Haus.", "Another house.", "de", "en"));

		// Should be only one entry for de->en
		assertEquals(1, list.size());
		Optional<VocabularyEntry> entry = list.get("Haus", "de", "en");
		assertTrue(entry.isPresent());
		assertEquals("Home", entry.get().getTranslation()); // Updated with new translation
		assertEquals("Ein anderes Haus.", entry.get().getContextSentence()); // Updated context
		assertEquals(2, entry.get().getTimesSeen()); // Incremented times seen
	}

	@Test
	public void testCaseInsensitiveWordLookup() throws IOException
	{
		list.add(new VocabularyEntry("Haus", "House", null, null, "de", "en"));

		assertTrue(list.get("haus", "de", "en").isPresent());
		assertTrue(list.get("HAUS", "de", "en").isPresent());
		assertTrue(list.get("Haus", "de", "en").isPresent());
	}

	@Test
	public void testLanguageCodeNormalisation() throws IOException
	{
		list.add(new VocabularyEntry("Haus", "House", null, null, "de", "en"));

		// Case insensitive language codes
		assertTrue(list.get("Haus", "DE", "en").isPresent());
		assertTrue(list.get("Haus", "de", "EN").isPresent());
		assertTrue(list.get("Haus", "De", "En").isPresent());
	}

	@Test
	public void testRemovalAffectsOnlyRequestedLanguagePair() throws IOException
	{
		list.add(new VocabularyEntry("Haus", "House", null, null, "de", "en"));
		list.add(new VocabularyEntry("Haus", "Maison", null, null, "de", "fr"));
		list.add(new VocabularyEntry("Haus", "Casa", null, null, "de", "es"));

		assertEquals(3, list.size());

		// Remove only the de->en pair
		assertTrue(list.remove("Haus", "de", "en"));
		assertEquals(2, list.size());

		// Other pairs should remain
		assertTrue(list.get("Haus", "de", "fr").isPresent());
		assertTrue(list.get("Haus", "de", "es").isPresent());
		assertFalse(list.get("Haus", "de", "en").isPresent());
	}

	@Test
	public void testMarkingKnownAffectsOnlyRequestedLanguagePair() throws IOException
	{
		list.add(new VocabularyEntry("Haus", "House", null, null, "de", "en"));
		list.add(new VocabularyEntry("Haus", "Maison", null, null, "de", "fr"));

		// Mark de->en as known
		Optional<VocabularyEntry> enEntry = list.get("Haus", "de", "en");
		assertTrue(enEntry.isPresent());
		enEntry.get().setKnown(true);

		// de->fr should not be known
		Optional<VocabularyEntry> frEntry = list.get("Haus", "de", "fr");
		assertTrue(frEntry.isPresent());
		assertFalse(frEntry.get().isKnown());

		assertEquals(1, list.getKnown().size());
		assertEquals(1, list.getUnknown().size());
	}

	@Test
	public void testPersistenceAndReloadPreserveAllLanguagePairs() throws IOException
	{
		Path persistDir = Files.createTempDirectory("vocab-persist-");
		Filepath persistFile = Unchecked.getRooted(persistDir).join("vocabulary.json");

		// Create first list and add entries for multiple language pairs
		VocabularyList list1 = new VocabularyList(persistFile, gson);
		list1.add(new VocabularyEntry("Haus", "House", "Context EN", "Context EN translated", "de", "en"));
		list1.add(new VocabularyEntry("Haus", "Maison", "Context FR", "Context FR translated", "de", "fr"));
		list1.add(new VocabularyEntry("Haus", "Casa", "Context ES", "Context ES translated", "de", "es"));
		list1.get("Haus", "de", "en").get().setKnown(true);
		list1.save();

		// Create a new list that loads from the same file
		VocabularyList reloaded = new VocabularyList(persistFile, gson);

		assertEquals(3, reloaded.size());
		assertTrue(reloaded.get("Haus", "de", "en").isPresent());
		assertTrue(reloaded.get("Haus", "de", "fr").isPresent());
		assertTrue(reloaded.get("Haus", "de", "es").isPresent());
		assertEquals("House", reloaded.get("Haus", "de", "en").get().getTranslation());
		assertEquals("Maison", reloaded.get("Haus", "de", "fr").get().getTranslation());
		assertEquals("Casa", reloaded.get("Haus", "de", "es").get().getTranslation());
		assertTrue(reloaded.get("Haus", "de", "en").get().isKnown());
		assertFalse(reloaded.get("Haus", "de", "fr").get().isKnown());
		assertFalse(reloaded.get("Haus", "de", "es").get().isKnown());

		deleteRecursively(persistDir);
	}

	@Test
	public void testLegacyJsonEntriesMigrateToDefaultTargetLanguage() throws IOException
	{
		Path persistDir = Files.createTempDirectory("vocab-legacy-");
		Filepath persistFile = Unchecked.getRooted(persistDir).join("vocabulary.json");

		// Write legacy JSON without targetLanguage field
		String legacyJson = "{\"haus|de\":{\"word\":\"Haus\",\"translation\":\"House\",\"contextSentence\":\"Das ist ein Haus.\",\"contextTranslation\":\"This is a house.\",\"language\":\"de\",\"targetLanguage\":null,\"known\":false,\"timesSeen\":1}}";
		persistFile.write(legacyJson);

		// Create list with default target language "en"
		VocabularyList reloaded = new VocabularyList(persistFile, gson, "en");

		assertEquals(1, reloaded.size());
		assertTrue(reloaded.get("Haus", "de", "en").isPresent());
		assertEquals("House", reloaded.get("Haus", "de", "en").get().getTranslation());
		assertEquals("en", reloaded.get("Haus", "de", "en").get().getTargetLanguage());

		deleteRecursively(persistDir);
	}

	@Test
	public void testLegacyJsonEntriesWithExistingTargetLanguageRetainIt() throws IOException
	{
		Path persistDir = Files.createTempDirectory("vocab-legacy-");
		Filepath persistFile = Unchecked.getRooted(persistDir).join("vocabulary.json");

		// Write JSON with targetLanguage already set
		String legacyJson = "{\"haus|de|fr\":{\"word\":\"Haus\",\"translation\":\"Maison\",\"contextSentence\":\"Das ist ein Haus.\",\"contextTranslation\":\"C'est une maison.\",\"language\":\"de\",\"targetLanguage\":\"fr\",\"known\":false,\"timesSeen\":1}}";
		persistFile.write(legacyJson);

		// Create list with default target language "en"
		VocabularyList reloaded = new VocabularyList(persistFile, gson, "en");

		assertEquals(1, reloaded.size());
		assertTrue(reloaded.get("Haus", "de", "fr").isPresent());
		assertEquals("Maison", reloaded.get("Haus", "de", "fr").get().getTranslation());
		assertEquals("fr", reloaded.get("Haus", "de", "fr").get().getTargetLanguage());

		deleteRecursively(persistDir);
	}

	@Test
	public void testExistingCsvFilesWithSevenColumnsStillImport() throws IOException
	{
		// CSV without target_language column (legacy 7-column format)
		String legacyCsv = "word,translation,language,known,times_seen,context,context_translation\n"
			+ "Haus,House,de,false,1,Das ist ein Haus.,This is a house.\n"
			+ "Buch,Book,de,false,1,Das ist ein Buch.,This is a book.";

		VocabularyList listWithDefault = new VocabularyList(null, gson, "en");
		int imported = listWithDefault.importFromCsv(legacyCsv, "de", "en");

		assertEquals(2, imported);
		assertEquals(2, listWithDefault.size());
		assertTrue(listWithDefault.get("Haus", "de", "en").isPresent());
		assertTrue(listWithDefault.get("Buch", "de", "en").isPresent());
		assertEquals("en", listWithDefault.get("Haus", "de", "en").get().getTargetLanguage());
	}

	@Test
	public void testCsvFilesWithTargetLanguageImportCorrectly() throws IOException
	{
		// CSV with target_language column (8-column format)
		String newCsv = "word,translation,language,known,times_seen,context,context_translation,target_language\n"
			+ "Haus,House,de,false,1,Das ist ein Haus.,This is a house.,en\n"
			+ "Haus,Maison,de,false,1,Das ist ein Haus.,C'est une maison.,fr\n"
			+ "Haus,Casa,de,false,1,Das ist un Haus.,Es una casa.,es";

		VocabularyList listWithDefault = new VocabularyList(null, gson, "en");
		int imported = listWithDefault.importFromCsv(newCsv, "de", "en");

		assertEquals(3, imported);
		assertEquals(3, listWithDefault.size());
		assertTrue(listWithDefault.get("Haus", "de", "en").isPresent());
		assertTrue(listWithDefault.get("Haus", "de", "fr").isPresent());
		assertTrue(listWithDefault.get("Haus", "de", "es").isPresent());
		assertEquals("House", listWithDefault.get("Haus", "de", "en").get().getTranslation());
		assertEquals("Maison", listWithDefault.get("Haus", "de", "fr").get().getTranslation());
		assertEquals("Casa", listWithDefault.get("Haus", "de", "es").get().getTranslation());
	}

	@Test
	public void testCsvExportAndReimportPreserveMultipleTranslations() throws IOException
	{
		list.add(new VocabularyEntry("Haus", "House", "Context EN", "Context EN translated", "de", "en"));
		list.add(new VocabularyEntry("Haus", "Maison", "Context FR", "Context FR translated", "de", "fr"));
		list.add(new VocabularyEntry("Haus", "Casa", "Context ES", "Context ES translated", "de", "es"));
		list.get("Haus", "de", "en").get().setKnown(true);
		list.get("Haus", "de", "en").get().incrementTimesSeen();

		String csv = list.toCsv();

		// Import into a new list
		Path importDir = Files.createTempDirectory("vocab-import-");
		Filepath importFile = Unchecked.getRooted(importDir).join("vocabulary.json");
		VocabularyList importList = new VocabularyList(importFile, gson);

		int imported = importList.importFromCsv(csv, "de");
		assertEquals(3, imported);
		assertEquals(3, importList.size());
		assertTrue(importList.get("Haus", "de", "en").isPresent());
		assertTrue(importList.get("Haus", "de", "fr").isPresent());
		assertTrue(importList.get("Haus", "de", "es").isPresent());
		assertEquals("House", importList.get("Haus", "de", "en").get().getTranslation());
		assertEquals("Maison", importList.get("Haus", "de", "fr").get().getTranslation());
		assertEquals("Casa", importList.get("Haus", "de", "es").get().getTranslation());
		assertTrue(importList.get("Haus", "de", "en").get().isKnown());
		assertEquals(2, importList.get("Haus", "de", "en").get().getTimesSeen()); // 1 initial + 1 increment

		deleteRecursively(importDir);
	}
}