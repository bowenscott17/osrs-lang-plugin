package com.osrslang.text;

import java.awt.Rectangle;
import java.util.Collections;
import java.util.List;
import net.runelite.api.widgets.Widget;
import com.osrslang.translation.Language;

/**
 * Keeps the current dialogue's word positions up to date without redoing the work every
 * tick.
 * <p>
 * Laying the dialogue out means measuring every word with the game font, which is more
 * than a client tick should spend on text that has not changed. The layout is therefore
 * recomputed only when the widget, its text, its position, its font or the language
 * actually changes - which in practice is once per line of dialogue.
 */
public class DialogueWordTracker
{
	/**
	 * What the current layout was computed from. A change in any of it means the layout
	 * has to be rebuilt.
	 */
	private String signature = "";

	private List<WordBox> words = Collections.emptyList();

	/**
	 * Re-lays out the widget if anything it depends on has changed.
	 *
	 * @param widget   the dialogue widget, may be {@code null}
	 * @param language the language the text is written in
	 * @return the words and where they are drawn, in reading order
	 */
	public List<WordBox> update(Widget widget, Language language)
	{
		String current = signatureOf(widget, language);

		if (current.equals(signature))
		{
			return words;
		}

		signature = current;
		words = widget == null
			? Collections.<WordBox>emptyList()
			: DialogueWordLayout.layout(widget, language);

		return words;
	}

	/**
	 * The words of the most recent layout.
	 */
	public List<WordBox> getWords()
	{
		return words;
	}

	/**
	 * The word at a canvas position, or {@code null} when the position is not over one.
	 */
	public WordBox wordAt(int x, int y)
	{
		for (WordBox word : words)
		{
			if (word.contains(x, y))
			{
				return word;
			}
		}

		return null;
	}

	/**
	 * Forgets the current layout, so the next update rebuilds it.
	 */
	public void clear()
	{
		signature = "";
		words = Collections.emptyList();
	}

	/**
	 * Builds a key from everything the layout depends on.
	 * <p>
	 * The bounds are folded into the string rather than compared field by field so a
	 * widget that merely moved is recognised as different, which it is.
	 */
	private static String signatureOf(Widget widget, Language language)
	{
		if (widget == null)
		{
			return "none";
		}

		Rectangle area = widget.getBounds();
		String bounds = area == null ? "none" : area.x + "," + area.y + "," + area.width + "," + area.height;

		return String.join("|",
			String.valueOf(System.identityHashCode(widget)),
			String.valueOf(widget.getText()),
			bounds,
			String.valueOf(widget.getFontId()),
			String.valueOf(widget.getLineHeight()),
			String.valueOf(widget.getXTextAlignment()),
			String.valueOf(widget.isHidden()),
			language == null ? "none" : language.getCode());
	}
}