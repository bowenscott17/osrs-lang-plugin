package com.osrslang.vocabulary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Before;
import org.junit.Test;

public class VocabularyListTest
{
	private VocabularyList list;

	@Before
	public void setUp()
	{
		list = new VocabularyList();
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
}
