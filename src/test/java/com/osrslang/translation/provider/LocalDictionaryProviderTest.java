package com.osrslang.translation.provider;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import com.osrslang.translation.Language;
import com.osrslang.translation.TranslationResult;
import com.osrslang.translation.TranslationSource;
import com.osrslang.text.GameText;

public class LocalDictionaryProviderTest
{
	private LocalDictionaryProvider provider;

	@Before
	public void setUp()
	{
		provider = new LocalDictionaryProvider(null, new Gson());
		provider.load(Language.ENGLISH, Language.GERMAN);
	}

	@Test
	public void testDictionaryLoads()
	{
		assertTrue(provider.size(Language.ENGLISH, Language.GERMAN) > 0);
	}

	@Test
	public void testLookupWord()
	{
		Optional<VocabularyEntry> entry = provider.lookupWord("sword", Language.ENGLISH, Language.GERMAN);

		assertTrue(entry.isPresent());
		assertEquals("das Schwert", entry.get().getTranslation());
		assertEquals("das", entry.get().getGender().orElse(null));
		assertEquals("noun", entry.get().getPartOfSpeech().orElse(null));
		assertEquals("sword", entry.get().getEnglish().orElse(null));
	}

	@Test
	public void testLookupIsCaseInsensitive()
	{
		assertTrue(provider.lookupWord("SWORD", Language.ENGLISH, Language.GERMAN).isPresent());
		assertTrue(provider.lookupWord("Sword", Language.ENGLISH, Language.GERMAN).isPresent());
	}

	@Test
	public void testUnknownWordIsEmpty()
	{
		assertFalse(provider.lookupWord("xyzzy", Language.ENGLISH, Language.GERMAN).isPresent());
	}

	@Test
	public void testUnknownLanguagePairIsEmpty()
	{
		assertFalse(provider.lookupWord("sword", Language.ENGLISH, Language.JAPANESE).isPresent());
	}

	@Test
	public void testTooltipLines()
	{
		VocabularyEntry entry = provider.lookupWord("sword", Language.ENGLISH, Language.GERMAN).orElseThrow();

		java.util.List<String> lines = entry.toTooltipLines();

		assertTrue(lines.contains("das Schwert"));
		assertTrue(lines.contains("English: sword"));
		assertTrue(lines.contains("Part of speech: noun"));
		assertTrue(lines.contains("Dictionary form: Schwert"));
	}

	@Test
	public void testTranslateSingleWord() throws Exception
	{
		Optional<TranslationResult> result = provider
			.translate("sword", Language.ENGLISH, Language.GERMAN, GameText.Type.NPC_DIALOGUE)
			.get();

		assertTrue(result.isPresent());
		assertEquals("das Schwert", result.get().getTranslatedText());
		assertEquals(TranslationSource.DICTIONARY, result.get().getSource());
		assertFalse(result.get().isVerified());
	}

	@Test
	public void testTranslateMultiWordTextIsEmpty() throws Exception
	{
		Optional<TranslationResult> result = provider
			.translate("give me a sword", Language.ENGLISH, Language.GERMAN, GameText.Type.NPC_DIALOGUE)
			.get();

		assertFalse(result.isPresent());
	}
}
