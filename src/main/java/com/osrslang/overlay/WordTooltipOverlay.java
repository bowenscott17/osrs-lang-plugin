package com.osrslang.overlay;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;
import com.osrslang.text.WordTokenizer;
import com.osrslang.translation.Language;
import com.osrslang.translation.TranslationManager;
import com.osrslang.translation.provider.VocabularyEntry;

/**
 * Shows a tooltip with the dictionary entry for the word under the mouse.
 * <p>
 * The overlay draws the translated sentence one word at a time, recording the screen
 * rectangle each word occupies. The mouse is then hit-tested against those rectangles.
 * When it is over a word the dictionary knows, a tooltip is added through RuneLite's
 * {@link TooltipManager} - the same mechanism RuneLite's own plugins use, so the
 * tooltip is positioned and styled consistently with the rest of the client.
 */
@Slf4j
public class WordTooltipOverlay extends Overlay
{
	private static final int PADDING = 4;

	private final Client client;
	private final TooltipManager tooltipManager;
	private final TranslationManager translationManager;
	private final Language sourceLanguage;
	private final Language targetLanguage;

	private final PanelComponent tooltipPanel = new PanelComponent();

	/**
	 * The sentence whose words can be hovered, in the source language.
	 */
	private String sentence;

	/**
	 * The words of the sentence, in order.
	 */
	private final List<String> words = new ArrayList<>();

	/**
	 * The on-screen rectangle of each word, in the same order as {@link #words}.
	 */
	private final List<Rectangle> wordBounds = new ArrayList<>();

	/**
	 * Where the sentence is drawn.
	 */
	private Rectangle area = new Rectangle();

	public WordTooltipOverlay(Client client, TooltipManager tooltipManager, TranslationManager translationManager,
		Language sourceLanguage, Language targetLanguage)
	{
		this.client = client;
		this.tooltipManager = tooltipManager;
		this.translationManager = translationManager;
		this.sourceLanguage = sourceLanguage;
		this.targetLanguage = targetLanguage;

		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	/**
	 * Sets the sentence whose words can be hovered.
	 */
	public void setSentence(String sentence)
	{
		this.sentence = sentence;
		this.words.clear();
		this.wordBounds.clear();
		this.area = new Rectangle();

		if (sentence == null || sentence.isEmpty())
		{
			return;
		}

		WordTokenizer tokenizer = new WordTokenizer(sourceLanguage);
		for (WordTokenizer.Token token : tokenizer.tokenize(sentence))
		{
			if (token.isWord())
			{
				words.add(token.getBareWord());
			}
		}
	}

	/**
	 * The word currently under the mouse, or {@code null} if the mouse is not over one.
	 */
	public String getHoveredWord()
	{
		Point mouse = client.getMouseCanvasPosition();
		if (mouse == null)
		{
			return null;
		}

		for (int i = 0; i < wordBounds.size(); i++)
		{
			if (wordBounds.get(i).contains(mouse.getX(), mouse.getY()))
			{
				return words.get(i);
			}
		}

		return null;
	}

	/**
	 * Draws the hoverable words and shows a tooltip for the one under the mouse.
	 * <p>
	 * The words are drawn in place of the plain sentence, so this overlay is what
	 * renders the source text when hover translations are enabled.
	 */
	@Override
	public Dimension render(Graphics2D graphics)
	{
		wordBounds.clear();

		if (sentence == null || sentence.isEmpty() || words.isEmpty())
		{
			return null;
		}

		Font font = FontManager.getRunescapeFont();
		FontMetrics metrics = graphics.getFontMetrics(font);

		int x = area.x + PADDING;
		int y = area.y + PADDING + metrics.getAscent();

		graphics.setFont(font);

		for (String word : words)
		{
			int width = metrics.stringWidth(word);

			graphics.setColor(Color.WHITE);
			graphics.drawString(word, x, y);

			wordBounds.add(new Rectangle(x, area.y + PADDING, width, metrics.getHeight()));

			x += width + PADDING;
		}

		showTooltipForHoveredWord();

		return new Dimension(area.width, area.height);
	}

	/**
	 * Sets the area the sentence is drawn in.
	 */
	public void setArea(Rectangle area)
	{
		this.area = area == null ? new Rectangle() : area;
	}

	/**
	 * Adds a tooltip for the hovered word, if the dictionary knows it.
	 */
	private void showTooltipForHoveredWord()
	{
		String word = getHoveredWord();
		if (word == null)
		{
			return;
		}

		Optional<VocabularyEntry> entry = translationManager.lookupWord(word, sourceLanguage, targetLanguage);
		if (!entry.isPresent())
		{
			return;
		}

		tooltipPanel.getChildren().clear();

		for (String line : entry.get().toTooltipLines())
		{
			tooltipPanel.getChildren().add(LineComponent.builder()
				.left(line)
				.leftColor(Color.WHITE)
				.build());
		}

		tooltipManager.addFront(new Tooltip(tooltipPanel));
	}
}
