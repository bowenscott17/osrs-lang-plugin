package com.osrslang.overlay;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;
import com.osrslang.translation.WordTranslation;
import com.osrslang.translation.provider.VocabularyEntry;

/**
 * Shows the translation of the single dialogue word the pointer is resting on.
 * <p>
 * The overlay draws nothing of its own - the game already draws the dialogue, and drawing
 * a second copy would fight with it. All it does is put a tooltip on screen for the word
 * currently hovered, through RuneLite's {@link TooltipManager}, so it is positioned and
 * styled like every other tooltip in the client and never covers more of the dialogue than
 * the tooltip itself needs.
 * <p>
 * Which word is hovered is decided by the plugin, which knows where the dialogue is
 * drawn; this class is handed the answer and only renders it.
 */
@Slf4j
public class WordTooltipOverlay extends Overlay
{
	/**
	 * The tooltip is capped so it cannot grow over the dialogue. Everything below the
	 * translation is a nicety, and is dropped before the translation itself is.
	 */
	private static final int MAX_LINES = 6;

	/**
	 * An example sentence is the longest line and the least important, so it is clipped.
	 */
	private static final int MAX_EXAMPLE_LENGTH = 48;

	private final TooltipManager tooltipManager;

	private final PanelComponent tooltipPanel = new PanelComponent();

	/**
	 * The word under the pointer, or {@code null} when the pointer is not over one.
	 */
	private String word;

	/**
	 * What is known about {@link #word}. Never {@code null} while a word is hovered - a
	 * lookup still running is represented by {@link WordTranslation#pending(String)}.
	 */
	private WordTranslation translation;

	/**
	 * The configured language pair, named so an unsupported pair can be spelled out.
	 */
	private String pairLabel = "";

	/**
	 * Whether the pointer is over a word that cannot be translated, rather than over one
	 * that simply has not been translated yet.
	 */
	private boolean pending;

	public WordTooltipOverlay(TooltipManager tooltipManager)
	{
		this.tooltipManager = tooltipManager;

		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	/**
	 * Shows a word as hovered, before its translation has arrived.
	 */
	public void setHover(String word)
	{
		setHover(word, WordTranslation.pending(word));
	}

	/**
	 * Shows a word as hovered, along with what is known about it.
	 */
	public void setHover(String word, WordTranslation translation)
	{
		this.word = word;
		this.translation = translation;
		this.pending = translation != null && translation.isPending();
	}

	/**
	 * Sets how the configured languages are named in the tooltip.
	 */
	public void setPairLabel(String pairLabel)
	{
		this.pairLabel = pairLabel == null ? "" : pairLabel;
	}

	/**
	 * The word currently hovered, or {@code null}.
	 */
	@Nullable
	public String getWord()
	{
		return word;
	}

	/**
	 * Hides the tooltip.
	 */
	public void clear()
	{
		word = null;
		translation = null;
		pending = false;
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (word == null || word.trim().isEmpty())
		{
			// RuneLite clears the tooltip manager between frames, so simply returning
			// hides the tooltip until the pointer is over a word again.
			return null;
		}

		List<Line> lines = buildLines();

		if (lines.isEmpty())
		{
			return null;
		}

		tooltipPanel.getChildren().clear();
		for (Line line : lines)
		{
			tooltipPanel.getChildren().add(LineComponent.builder()
				.left(line.text)
				.leftColor(line.color)
				.build());
		}

		tooltipManager.addFront(new Tooltip(tooltipPanel));

		return null;
	}

	/**
	 * The tooltip lines, in order, for the word currently hovered.
	 * <p>
	 * Exposed so the exact wording can be tested without a running client.
	 */
	public List<String> getTooltipLines()
	{
		List<String> texts = new ArrayList<>();

		for (Line line : buildLines())
		{
			texts.add(line.text);
		}

		return texts;
	}

	/**
	 * Whether the hovered word is still waiting for a translation.
	 */
	public boolean isPending()
	{
		return pending;
	}

	/**
	 * Builds the tooltip: the word, then its translation or why there is none, then
	 * whatever the dictionary knows about it.
	 */
	private List<Line> buildLines()
	{
		List<Line> lines = new ArrayList<>();

		if (word == null || word.trim().isEmpty())
		{
			return lines;
		}

		lines.add(new Line(word, Color.WHITE));

		if (translation == null)
		{
			lines.add(new Line("Translating...", Color.GRAY));
			return lines;
		}

		Optional<String> translated = translation.getTranslation();

		if (translated.isPresent())
		{
			lines.add(new Line(translated.get(), Color.YELLOW));
		}
		else if (translation.getStatus() == WordTranslation.Status.UNSUPPORTED_PAIR)
		{
			lines.add(new Line(unsupportedMessage(), new Color(220, 150, 60)));
		}
		else if (translation.isPending())
		{
			lines.add(new Line("Translating...", Color.GRAY));
		}
		else
		{
			lines.add(new Line("No translation found", Color.GRAY));
		}

		Optional<VocabularyEntry> entry = translation.getEntry();
		if (entry.isPresent())
		{
			lines.addAll(detailLines(entry.get()));
		}

		return truncate(lines);
	}

	/**
	 * The extra information a dictionary entry can contribute.
	 * <p>
	 * Every field is optional and none is invented: a word the dictionary only knows a
	 * translation for contributes just that translation, and the article, part of speech
	 * and lemma appear only when they were actually recorded.
	 */
	private List<Line> detailLines(VocabularyEntry entry)
	{
		List<Line> lines = new ArrayList<>();

		// The article is the single most useful thing for a learner in a gendered
		// language, and it is the one piece of grammar the translation string itself does
		// not carry, so it gets its own labelled line.
		entry.getArticle().ifPresent(article -> lines.add(new Line("Article: " + article, Color.CYAN)));

		entry.getPartOfSpeech()
			.filter(part -> !part.trim().isEmpty())
			.ifPresent(part -> lines.add(new Line("Part of speech: " + part.trim(), Color.LIGHT_GRAY)));

		entry.getDictionaryForm()
			.filter(form -> !form.trim().isEmpty() && !form.trim().equalsIgnoreCase(word))
			.ifPresent(form -> lines.add(new Line("Base form: " + form.trim(), Color.LIGHT_GRAY)));

		entry.getEnglish()
			.filter(gloss -> !gloss.trim().isEmpty())
			.ifPresent(gloss -> lines.add(new Line("English: " + gloss.trim(), Color.LIGHT_GRAY)));

		entry.getExample()
			.filter(example -> !example.trim().isEmpty())
			.ifPresent(example -> lines.add(new Line("Example: " + clip(example.trim()), Color.LIGHT_GRAY)));

		return lines;
	}

	/**
	 * Keeps the first line - the word and its translation - and as much detail as fits
	 * underneath it.
	 */
	private static List<Line> truncate(List<Line> lines)
	{
		if (lines.size() <= MAX_LINES)
		{
			return lines;
		}

		List<Line> clipped = new ArrayList<>(lines.subList(0, MAX_LINES));
		return clipped;
	}

	/**
	 * Clips a long example so it cannot dominate the tooltip.
	 */
	private static String clip(String example)
	{
		if (example.length() <= MAX_EXAMPLE_LENGTH)
		{
			return example;
		}

		return example.substring(0, MAX_EXAMPLE_LENGTH - 1) + "…";
	}

	private String unsupportedMessage()
	{
		if (pairLabel.isEmpty())
		{
			return "Language pair not supported by the translation service";
		}

		return pairLabel + " is not supported by the translation service";
	}

	/**
	 * One line of the tooltip: its text and the colour it is drawn in.
	 */
	private static class Line
	{
		private final String text;

		private final Color color;

		private Line(String text, Color color)
		{
			this.text = text;
			this.color = color;
		}
	}
}