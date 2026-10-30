package com.osrslang.translation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Optional;
import org.junit.Test;

public class LanguageTest
{
	@Test
	public void testFromCode()
	{
		assertEquals(Optional.of(Language.GERMAN), Language.fromCode("de"));
		assertEquals(Optional.of(Language.GERMAN), Language.fromCode("DE"));
		assertEquals(Optional.of(Language.GERMAN), Language.fromCode(" de "));
		assertEquals(Optional.of(Language.ENGLISH), Language.fromCode("en"));
	}

	@Test
	public void testFromCodeUnknownReturnsLanguage()
	{
		// Unknown codes still get a Language instance (with the code as name)
		Optional<Language> unknown = Language.fromCode("xx");
		assertTrue(unknown.isPresent());
		assertEquals("xx", unknown.get().getCode());
		assertEquals("xx", unknown.get().getName());

		// But null/blank codes return empty
		assertFalse(Language.fromCode(null).isPresent());
		assertFalse(Language.fromCode("").isPresent());
		assertFalse(Language.fromCode("   ").isPresent());
	}

	@Test
	public void testCodesAreUnique()
	{
		assertEquals(Language.builtIns().size(), Language.codes().size());
	}

	@Test
	public void testJapaneseDoesNotUseWordSpacing()
	{
		assertFalse(Language.JAPANESE.usesWordSpacing());
		assertTrue(Language.GERMAN.usesWordSpacing());
		assertTrue(Language.FRENCH.usesWordSpacing());
		assertTrue(Language.SPANISH.usesWordSpacing());
	}

	@Test
	public void testNativeNames()
	{
		assertEquals("Deutsch", Language.GERMAN.getNativeName());
		assertEquals("English", Language.ENGLISH.getNativeName());
	}
}