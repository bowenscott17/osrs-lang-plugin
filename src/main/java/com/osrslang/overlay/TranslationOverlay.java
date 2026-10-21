package com.osrslang.overlay;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.annotation.Nullable;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;

/**
 * Draws the detected dialogue text and, once translation is wired up, its translation.
 * <p>
 * Milestone 2 behaviour: the original dialogue is read from the game widgets and shown
 * in a panel anchored below the chatbox. The translation line only appears once a
 * translation has been produced for the current text.
 */
public class TranslationOverlay extends OverlayPanel
{
	private static final int PANEL_WIDTH = 320;

	private String speakerText;
	private String originalText;
	private String translationText;

	public TranslationOverlay(Plugin plugin)
	{
		super(plugin);
		setPosition(OverlayPosition.ABOVE_CHATBOX_RIGHT);
		setPriority(PRIORITY_LOW);
	}

	/**
	 * Updates the content shown by the overlay.
	 *
	 * @param speaker    the name of the NPC speaking, may be {@code null}
	 * @param original   the original game text, may be {@code null} to hide the panel
	 * @param translation the translated text, may be {@code null} when not yet available
	 */
	public void setContent(@Nullable String speaker, @Nullable String original, @Nullable String translation)
	{
		this.speakerText = speaker;
		this.originalText = original;
		this.translationText = translation;
	}

	public String getOriginalText()
	{
		return originalText;
	}

	public String getTranslationText()
	{
		return translationText;
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (originalText == null || originalText.isEmpty())
		{
			return null;
		}

		if (speakerText != null && !speakerText.isEmpty())
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left(speakerText)
				.leftColor(Color.CYAN)
				.build());
		}

		panelComponent.getChildren().add(LineComponent.builder()
			.left(originalText)
			.leftColor(Color.WHITE)
			.build());

		if (translationText != null && !translationText.isEmpty())
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left(translationText)
				.leftColor(Color.YELLOW)
				.build());
		}

		setPreferredSize(new Dimension(PANEL_WIDTH, 0));

		return super.render(graphics);
	}
}
