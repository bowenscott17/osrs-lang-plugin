package com.osrslang.text;

import java.util.Objects;

/**
 * A piece of text extracted from the game, along with where it came from.
 */
public class GameText
{
	/**
	 * The category of game text. Used to decide which translation sources apply.
	 */
	public enum Type
	{
		NPC_DIALOGUE,
		PLAYER_DIALOGUE,
		DIALOGUE_OPTIONS,
		CHAT_MESSAGE,

		/**
		 * The name of an NPC the player is hovering over.
		 */
		NPC_NAME,

		/**
		 * The name of an item, whether in the inventory or on the ground.
		 */
		ITEM_NAME,

		/**
		 * The name of an object in the world.
		 */
		OBJECT_NAME,

		/**
		 * A label the game draws on the interface, such as the name of a spell.
		 */
		INTERFACE_TEXT
	}

	private final Type type;
	private final String text;
	private final String speaker;

	public GameText(Type type, String text, String speaker)
	{
		this.type = type;
		this.text = text;
		this.speaker = speaker;
	}

	public GameText(Type type, String text)
	{
		this(type, text, null);
	}

	public Type getType()
	{
		return type;
	}

	public String getText()
	{
		return text;
	}

	/**
	 * The name of the NPC speaking, when known.
	 */
	public String getSpeaker()
	{
		return speaker;
	}

	@Override
	public boolean equals(Object o)
	{
		if (this == o)
		{
			return true;
		}
		if (!(o instanceof GameText))
		{
			return false;
		}
		GameText other = (GameText) o;
		return type == other.type
			&& Objects.equals(text, other.text)
			&& Objects.equals(speaker, other.speaker);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(type, text, speaker);
	}

	@Override
	public String toString()
	{
		return "GameText{" + type + ", '" + text + "'}";
	}
}
