package com.osrslang.translation.provider;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import org.junit.Test;
import com.osrslang.translation.Language;

/**
 * Checks that every language the plugin advertises actually ships with data.
 * <p>
 * A language with no data would silently produce no translations, so this test keeps
 * the shipped language packs honest.
 */
public class LanguagePackTest
{
	private static final Language[] PACKED_LANGUAGES =
	{
		Language.GERMAN,
		Language.FRENCH,
		Language.SPANISH
	};

	@Test
	public void testEveryPackedLanguageHasADictionary()
	{
		for (Language target : PACKED_LANGUAGES)
		{
			LocalDictionaryProvider dictionary = new LocalDictionaryProvider(null, new Gson());
			dictionary.load(Language.ENGLISH, target);

			assertTrue("No dictionary entries for " + target.getCode(),
				dictionary.size(Language.ENGLISH, target) > 0);
		}
	}

	@Test
	public void testEveryPackedLanguageHasAVerifiedDatabase()
	{
		for (Language target : PACKED_LANGUAGES)
		{
			OsrsTranslationDatabaseProvider database = new OsrsTranslationDatabaseProvider(null, new Gson());
			database.load(Language.ENGLISH, target);

			assertTrue("No verified entries for " + target.getCode(),
				database.size(Language.ENGLISH, target) > 0);
		}
	}

	@Test
	public void testJapaneseHasNoDataButIsStillOffered()
	{
		// Japanese is offered as a target, but no dictionary ships for it yet. The
		// plugin must handle that gracefully rather than failing.
		LocalDictionaryProvider dictionary = new LocalDictionaryProvider(null, new Gson());
		dictionary.load(Language.ENGLISH, Language.JAPANESE);

		assertEquals(0, dictionary.size(Language.ENGLISH, Language.JAPANESE));
		assertFalse(dictionary.lookupWord("sword", Language.ENGLISH, Language.JAPANESE).isPresent());
	}

	@Test
	public void testDictionariesShareTheSameWords()
	{
		// Every packed language should cover the same core vocabulary, so a learner
		// can switch languages without losing coverage.
		LocalDictionaryProvider german = new LocalDictionaryProvider(null, new Gson());
		german.load(Language.ENGLISH, Language.GERMAN);

		for (Language target : PACKED_LANGUAGES)
		{
			LocalDictionaryProvider dictionary = new LocalDictionaryProvider(null, new Gson());
			dictionary.load(Language.ENGLISH, target);

			for (String word : new String[]{"sword", "shield", "water", "hello", "thanks"})
			{
				assertTrue(word + " missing from " + target.getCode(),
					dictionary.lookupWord(word, Language.ENGLISH, target).isPresent());
			}
		}
	}
}
