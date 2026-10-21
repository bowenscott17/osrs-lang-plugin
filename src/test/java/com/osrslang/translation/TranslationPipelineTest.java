package com.osrslang.translation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.junit.Before;
import org.junit.Test;
import com.osrslang.text.GameText;
import com.osrslang.translation.provider.LocalDictionaryProvider;
import com.osrslang.translation.provider.OsrsTranslationDatabaseProvider;

/**
 * End-to-end test of the provider chain: a verified translation must win over a
 * dictionary entry, and the dictionary must be used when nothing is verified.
 */
public class TranslationPipelineTest
{
	private TranslationManager manager;
	private OsrsTranslationDatabaseProvider database;
	private LocalDictionaryProvider dictionary;

	@Before
	public void setUp()
	{
		manager = new TranslationManager();
		database = new OsrsTranslationDatabaseProvider(null, new Gson());
		dictionary = new LocalDictionaryProvider(null, new Gson());

		database.load(Language.ENGLISH, Language.GERMAN);
		dictionary.load(Language.ENGLISH, Language.GERMAN);

		manager.register(database);
		manager.register(dictionary);
	}

	@Test
	public void testVerifiedTranslationWinsOverDictionary() throws ExecutionException, InterruptedException
	{
		// "Coal" is in the verified database as a term and in the dictionary.
		Optional<TranslationResult> result = manager
			.translate("Coal", Language.ENGLISH, Language.GERMAN, GameText.Type.NPC_DIALOGUE)
			.get();

		assertTrue(result.isPresent());
		assertEquals(TranslationSource.VERIFIED, result.get().getSource());
		assertEquals("Kohle", result.get().getTranslatedText());
	}

	@Test
	public void testDictionaryIsUsedWhenNothingIsVerified() throws ExecutionException, InterruptedException
	{
		Optional<TranslationResult> result = manager
			.translate("hammer", Language.ENGLISH, Language.GERMAN, GameText.Type.NPC_DIALOGUE)
			.get();

		assertTrue(result.isPresent());
		assertEquals(TranslationSource.DICTIONARY, result.get().getSource());
		assertEquals("der Hammer", result.get().getTranslatedText());
	}

	@Test
	public void testUntranslatableTextReturnsEmpty() throws ExecutionException, InterruptedException
	{
		Optional<TranslationResult> result = manager
			.translate("A completely unremarkable sentence nobody translated", Language.ENGLISH, Language.GERMAN, GameText.Type.NPC_DIALOGUE)
			.get();

		assertFalse(result.isPresent());
	}

	@Test
	public void testWordLookupUsesDictionary() throws Exception
	{
		Optional<com.osrslang.translation.provider.VocabularyEntry> entry =
			manager.lookupWord("shield", Language.ENGLISH, Language.GERMAN);

		assertTrue(entry.isPresent());
		assertEquals("der Schild", entry.get().getTranslation());
	}
}
