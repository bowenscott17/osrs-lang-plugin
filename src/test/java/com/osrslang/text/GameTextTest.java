package com.osrslang.text;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GameTextTest
{
	@Test
	public void testEquality()
	{
		GameText a = new GameText(GameText.Type.NPC_DIALOGUE, "Hello", "Bob");
		GameText b = new GameText(GameText.Type.NPC_DIALOGUE, "Hello", "Bob");
		GameText c = new GameText(GameText.Type.NPC_DIALOGUE, "Hello", "Alice");

		assertEquals(a, b);
		assertEquals(a.hashCode(), b.hashCode());
		assertNotEquals(a, c);
	}

	@Test
	public void testTypeIsPartOfEquality()
	{
		GameText a = new GameText(GameText.Type.NPC_DIALOGUE, "Hello");
		GameText b = new GameText(GameText.Type.PLAYER_DIALOGUE, "Hello");

		assertNotEquals(a, b);
	}

	@Test
	public void testSpeakerDefaultsToNull()
	{
		GameText text = new GameText(GameText.Type.NPC_DIALOGUE, "Hello");
		assertNull(text.getSpeaker());
	}

	@Test
	public void testGetters()
	{
		GameText text = new GameText(GameText.Type.NPC_DIALOGUE, "Hello", "Bob");
		assertEquals(GameText.Type.NPC_DIALOGUE, text.getType());
		assertEquals("Hello", text.getText());
		assertEquals("Bob", text.getSpeaker());
	}

	@Test
	public void testToStringContainsText()
	{
		GameText text = new GameText(GameText.Type.NPC_DIALOGUE, "Hello", "Bob");
		assertTrue(text.toString().contains("Hello"));
	}
}
