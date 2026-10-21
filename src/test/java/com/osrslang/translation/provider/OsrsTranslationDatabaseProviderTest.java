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

public class OsrsTranslationDatabaseProviderTest
{
	private OsrsTranslationDatabaseProvider provider;

	@Before
	public void setUp()
	{
		provider = new OsrsTranslationDatabaseProvider(null, new Gson());
		provider.load(Language.ENGLISH, Language.GERMAN);
	}

	@Test
	public void testDatabaseLoads()
	{
		assertTrue(provider.size(Language.ENGLISH, Language.GERMAN) > 0);
	}

	@Test
	public void testTranslateVerifiedSentence() throws Exception
	{
		Optional<TranslationResult> result = provider
			.translate("Could you bring me a bucket of water?", Language.ENGLISH, Language.GERMAN, GameText.Type.NPC_DIALOGUE)
			.get();

		assertTrue(result.isPresent());
		assertEquals("Kannst du mir einen Eimer Wasser bringen?", result.get().getTranslatedText());
		assertEquals(TranslationSource.VERIFIED, result.get().getSource());
		assertTrue(result.get().isVerified());
	}

	@Test
	public void testTranslateVerifiedItem() throws Exception
	{
		Optional<TranslationResult> result = provider
			.translate("Bronze sword", Language.ENGLISH, Language.GERMAN, GameText.Type.NPC_DIALOGUE)
			.get();

		assertTrue(result.isPresent());
		assertEquals("Bronzeschwert", result.get().getTranslatedText());
	}

	@Test
	public void testLookupById() throws Exception
	{
		Optional<TranslationResult> result = provider
			.lookupById("item.bucket", Language.ENGLISH, Language.GERMAN);

		assertTrue(result.isPresent());
		assertEquals("Eimer", result.get().getTranslatedText());
	}

	@Test
	public void testUnknownTextIsEmpty() throws Exception
	{
		Optional<TranslationResult> result = provider
			.translate("Some sentence nobody has ever translated", Language.ENGLISH, Language.GERMAN, GameText.Type.NPC_DIALOGUE)
			.get();

		assertFalse(result.isPresent());
	}

	@Test
	public void testVerifiedProviderHasHigherPriorityThanDictionary()
	{
		LocalDictionaryProvider dictionary = new LocalDictionaryProvider(null, new Gson());
		dictionary.load(Language.ENGLISH, Language.GERMAN);

		assertTrue(provider.getPriority() < dictionary.getPriority());
	}
}
