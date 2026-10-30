package com.osrslang.text;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import net.runelite.api.FontTypeFace;
import net.runelite.api.widgets.Widget;
import net.runelite.client.util.Text;
import com.osrslang.translation.Language;

/**
 * Works out where each individual word of a dialogue widget is drawn on screen.
 * <p>
 * The game draws its own dialogue text, so the words cannot simply be read back off the
 * screen. Instead the text is laid out again here using the game's own font metrics, from
 * {@link Widget#getFont()}. Measuring with the real font is what makes the result match
 * what the player sees: the advance widths of "Schlüssel" in the RuneScape font are the
 * advance widths the client itself used when it drew the word.
 * <p>
 * Layout follows the rules the client uses for wrapped text - greedy wrapping at the
 * widget's right edge, a hard break at {@code <br>}, and a new line every
 * {@link Widget#getLineHeight()} pixels - so the rectangles line up with the rendered
 * dialogue. The algorithm is kept free of any RuneLite type so it can be tested directly.
 */
public final class DialogueWordLayout
{
	/**
	 * Used when a widget does not report a usable line height.
	 */
	private static final int DEFAULT_LINE_HEIGHT = 16;

	/**
	 * A box is widened by this much on each side so a word is still hovered when the
	 * pointer is a pixel or two outside the glyphs. It stays well below the width of a
	 * space, so neighbouring words never claim the same pixel.
	 */
	public static final int WORD_PADDING = 1;

	/**
	 * Measures how wide a piece of text is drawn.
	 */
	public interface TextMeasurer
	{
		int widthOf(String text);
	}

	/**
	 * How a widget's text is positioned horizontally within its bounds.
	 * <p>
	 * These are the layout codes the client uses on {@link Widget#getXTextAlignment()}.
	 * Anything unrecognised is treated as left-aligned, which is what the dialogue
	 * interfaces use.
	 */
	public enum Alignment
	{
		LEFT,
		CENTER,
		RIGHT;

		public static Alignment fromCode(int code)
		{
			switch (code)
			{
				case 1:
					return CENTER;
				case 2:
					return RIGHT;
				default:
					return LEFT;
			}
		}
	}

	/**
	 * One laid-out line of text: the words on it and how wide they are together.
	 */
	private static class Line
	{
		private final List<String> words = new ArrayList<>();

		private final List<Integer> widths = new ArrayList<>();

		private int used;

		private void add(String word, int width)
		{
			words.add(word);
			widths.add(width);
			used += width;
		}
	}

	private DialogueWordLayout()
	{
	}

	/**
	 * Lays out the text of a dialogue widget.
	 * <p>
	 * Returns an empty list when the widget is missing, hidden, has no size, or has no
	 * font to measure with - any of which means there is nothing hoverable on screen.
	 *
	 * @param widget   the dialogue widget, may be {@code null}
	 * @param language the language the text is written in, used to split it into words
	 * @return the words and where they are drawn, in reading order
	 */
	public static List<WordBox> layout(Widget widget, Language language)
	{
		if (widget == null || widget.isHidden())
		{
			return Collections.emptyList();
		}

		Rectangle area = widget.getBounds();
		if (area == null || area.width <= 0 || area.height <= 0)
		{
			return Collections.emptyList();
		}

		FontTypeFace font = widget.getFont();
		if (font == null)
		{
			return Collections.emptyList();
		}

		int lineHeight = widget.getLineHeight() > 0 ? widget.getLineHeight() : DEFAULT_LINE_HEIGHT;

		return layout(
			widget.getText(),
			area,
			lineHeight,
			language,
			font::getTextWidth,
			Alignment.fromCode(widget.getXTextAlignment()));
	}

	/**
	 * Lays out a piece of text inside a rectangle.
	 *
	 * @param rawText   the text as the widget stores it, tags and {@code <br>} included
	 * @param area      the rectangle the text is drawn in
	 * @param lineHeight the height of one line in pixels
	 * @param language  the language the text is written in, used to split it into words
	 * @param measurer  measures text width in the game's font
	 * @param alignment how each line is positioned within {@code area}
	 * @return the words and where they are drawn, in reading order
	 */
	public static List<WordBox> layout(String rawText, Rectangle area, int lineHeight, Language language,
		TextMeasurer measurer, Alignment alignment)
	{
		if (rawText == null || rawText.isEmpty() || area == null || measurer == null)
		{
			return Collections.emptyList();
		}

		if (lineHeight <= 0)
		{
			lineHeight = DEFAULT_LINE_HEIGHT;
		}

		int spaceWidth = measurer.widthOf(" ");

		// The client wraps the text itself; re-wrapping it the same way is what puts
		// each word back on the line it was drawn on.
		List<Line> lines = wrap(rawText, area, language, measurer, spaceWidth);

		List<WordBox> boxes = new ArrayList<>();

		int y = area.y;

		for (Line line : lines)
		{
			int x = startX(alignment, area, line.used);

			for (int i = 0; i < line.words.size(); i++)
			{
				int width = line.widths.get(i);

				Rectangle bounds = new Rectangle(
					x - WORD_PADDING,
					y,
					width + WORD_PADDING * 2,
					lineHeight);

				boxes.add(new WordBox(line.words.get(i), WordTokenizer.stripPunctuation(line.words.get(i)), bounds));

				// The client separates words by a space, and the space is part of the
				// rendered line even though it is not a word.
				x += width + spaceWidth;
			}

			y += lineHeight;
		}

		return boxes;
	}

	/**
	 * Splits the text into the lines the client would draw.
	 */
	private static List<Line> wrap(String rawText, Rectangle area, Language language,
		TextMeasurer measurer, int spaceWidth)
	{
		List<Line> lines = new ArrayList<>();
		Line current = new Line();
		lines.add(current);

		WordTokenizer tokenizer = new WordTokenizer(language);

		List<String> segments = splitOnLineBreak(rawText);

		for (int segment = 0; segment < segments.size(); segment++)
		{
			// <br> is a hard line break the client honours, so everything after the first
			// break starts on a line of its own, however much room is left.
			if (segment > 0)
			{
				current = new Line();
				lines.add(current);
			}

			// Tags such as <col=ff0000> are drawn as nothing, so they must not be measured.
			String plain = Text.removeTags(segments.get(segment));

			for (WordTokenizer.Token token : tokenizer.tokenize(plain))
			{
				if (!token.isWord())
				{
					continue;
				}

				String word = token.getText();
				int width = measurer.widthOf(word);

				int needed = current.words.isEmpty() ? width : spaceWidth + width;

				// A word that does not fit starts the next line, unless it is the first
				// word on the line - a word too wide for the widget is still drawn rather
				// than dropped.
				if (!current.words.isEmpty() && current.used + needed > area.width)
				{
					current = new Line();
					lines.add(current);
				}

				if (!current.words.isEmpty())
				{
					current.used += spaceWidth;
				}

				current.add(word, width);
			}
		}

		// An empty trailing line means the text ended in a break; drop it so no blank
		// row of boxes is produced.
		while (lines.size() > 1 && lines.get(lines.size() - 1).words.isEmpty())
		{
			lines.remove(lines.size() - 1);
		}

		return lines;
	}

	/**
	 * The left edge a line starts at, given how wide the line is.
	 */
	private static int startX(Alignment alignment, Rectangle area, int lineWidth)
	{
		if (alignment == Alignment.CENTER)
		{
			return area.x + Math.max(0, (area.width - lineWidth) / 2);
		}
		if (alignment == Alignment.RIGHT)
		{
			return area.x + Math.max(0, area.width - lineWidth);
		}
		return area.x;
	}

	/**
	 * Splits on {@code <br>}, which the client renders as a line break.
	 */
	static List<String> splitOnLineBreak(String text)
	{
		List<String> segments = new ArrayList<>();

		String lower = text.toLowerCase(Locale.ROOT);
		int start = 0;

		while (true)
		{
			int index = lower.indexOf("<br>", start);
			if (index < 0)
			{
				segments.add(text.substring(start));
				return segments;
			}

			segments.add(text.substring(start, index));
			start = index + "<br>".length();
		}
	}
}