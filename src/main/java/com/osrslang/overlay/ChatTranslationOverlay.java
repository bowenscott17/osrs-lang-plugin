package com.osrslang.overlay;

import com.osrslang.text.ChatMessageTranslator;
import com.osrslang.text.ChatMessageTranslator.ChatTranslation;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;

/**
 * Shows the most recent translated chat messages above the chatbox.
 * <p>
 * The chatbox itself is left untouched - the original English text the player
 * types and reads is drawn by the client and is never replaced or covered.
 * This overlay sits next to it as a separate panel, much like the dialogue
 * translation panel, so a learner can match each English line to its German
 * counterpart without losing the original.
 * <p>
 * The panel is hidden whenever the {@link ChatMessageTranslator} has nothing
 * recent to show, so an empty buffer never adds an empty black rectangle to
 * the screen.
 */
public class ChatTranslationOverlay extends OverlayPanel
{
	/**
	 * The width of the panel, matching the dialogue translation overlay so the
	 * two read as a pair.
	 */
	private static final int PANEL_WIDTH = 320;

	private final ChatMessageTranslator translator;

	/**
	 * Whether the overlay should draw at all. Tied to the {@code translateChat}
	 * config flag so the user can turn chat translations off without disabling
	 * the rest of the plugin.
	 */
	private boolean enabled;

	@Inject
	public ChatTranslationOverlay(Plugin plugin, ChatMessageTranslator translator)
	{
		super(plugin);
		this.translator = translator;

		setPosition(OverlayPosition.ABOVE_CHATBOX_RIGHT);
		setPriority(PRIORITY_LOW);
	}

	/**
	 * Turns the overlay on or off without removing it from the overlay manager.
	 * <p>
	 * The {@link ChatMessageTranslator} keeps translating in the background
	 * either way, so a fresh translation is available the moment the user turns
	 * the overlay back on.
	 */
	public void setEnabled(boolean enabled)
	{
		this.enabled = enabled;
	}

	/**
	 * Whether the overlay will draw on the next render. Exposed for tests so the
	 * toggling behaviour can be verified without a running client.
	 */
	public boolean isEnabled()
	{
		return enabled;
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!enabled)
		{
			return null;
		}

		ChatTranslation[] recent = translator.getRecent();
		if (recent == null || recent.length == 0)
		{
			return null;
		}

		for (ChatTranslation entry : recent)
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left(entry.getOriginal())
				.leftColor(Color.WHITE)
				.build());

			panelComponent.getChildren().add(LineComponent.builder()
				.left(entry.getTranslated())
				.leftColor(Color.YELLOW)
				.build());
		}

		setPreferredSize(new Dimension(PANEL_WIDTH, 0));
		return super.render(graphics);
	}
}
