package com.osrslang.text;

import java.awt.Rectangle;
import java.util.Objects;

/**
 * One word of on-screen text together with the screen rectangle it occupies.
 * <p>
 * The rectangle is in canvas coordinates - the same coordinate space as
 * {@code client.getMouseCanvasPosition()} and {@code widget.getBounds()} - so the mouse
 * can be hit-tested against it directly.
 * <p>
 * A box holds two strings: the word exactly as the game draws it, punctuation included,
 * and the bare word with punctuation stripped. The bare word is what a dictionary is
 * keyed on, because no dictionary files "Schlüssel." under the full stop.
 */
public class WordBox
{
	private final String text;

	private final String lookupWord;

	private final Rectangle bounds;

	public WordBox(String text, String lookupWord, Rectangle bounds)
	{
		this.text = text;
		this.lookupWord = lookupWord;
		this.bounds = bounds;
	}

	/**
	 * The word exactly as displayed, punctuation included.
	 */
	public String getText()
	{
		return text;
	}

	/**
	 * The word with punctuation stripped, as it should be sent to a dictionary.
	 */
	public String getLookupWord()
	{
		return lookupWord;
	}

	/**
	 * The on-screen rectangle the word occupies.
	 */
	public Rectangle getBounds()
	{
		return bounds;
	}

	/**
	 * Whether the point falls inside this word's rectangle.
	 */
	public boolean contains(int x, int y)
	{
		return bounds.contains(x, y);
	}

	@Override
	public boolean equals(Object o)
	{
		if (this == o)
		{
			return true;
		}
		if (!(o instanceof WordBox))
		{
			return false;
		}
		WordBox other = (WordBox) o;
		return Objects.equals(text, other.text)
			&& Objects.equals(lookupWord, other.lookupWord)
			&& Objects.equals(bounds, other.bounds);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(text, lookupWord, bounds);
	}

	@Override
	public String toString()
	{
		return "WordBox{" + lookupWord + " at " + bounds + "}";
	}
}