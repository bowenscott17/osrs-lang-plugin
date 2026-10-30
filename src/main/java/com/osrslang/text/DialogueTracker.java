package com.osrslang.text;

import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.events.ClientTick;
import net.runelite.api.widgets.Widget;

/**
 * Polls the game for dialogue text once per client tick and remembers the last text seen.
 * <p>
 * Polling on the client tick (roughly every 20ms) rather than every frame keeps the
 * work bounded, and reading a handful of widget fields is far cheaper than scanning the
 * whole interface tree.
 */
@Slf4j
public class DialogueTracker
{
	@Inject
	private DialogueReader reader;

	private GameText current;

	/**
	 * The dialogue most recently seen, or {@code null} if none is showing.
	 */
	public GameText getCurrent()
	{
		return current;
	}

	/**
	 * The widget the current dialogue is drawn in.
	 * <p>
	 * Knowing the text is not enough to find a word on screen: the widget also carries
	 * the position and the font the client drew it with, which is what the hover layout
	 * is measured against.
	 *
	 * @return the widget, or {@code null} if no dialogue is showing
	 */
	public Widget getCurrentWidget()
	{
		return current == null ? null : reader.findWidget(current.getType());
	}

	/**
	 * Samples the dialogue widgets and updates {@link #getCurrent()}.
	 * <p>
	 * Returns the newly observed text if it changed since the last tick.
	 *
	 * @return the current dialogue, or {@code null} if no dialogue is showing
	 */
	public GameText poll()
	{
		GameText observed = reader.readNpcDialogue();
		if (observed == null)
		{
			observed = reader.readPlayerDialogue();
		}

		if (observed == null)
		{
			observed = reader.readDialogueOptions();
		}

		if (observed == null)
		{
			current = null;
			return null;
		}

		if (!observed.equals(current))
		{
			log.debug("New dialogue detected: {}", observed);
			current = observed;
		}

		return current;
	}

	/**
	 * Clears any remembered dialogue.
	 */
	public void clear()
	{
		current = null;
	}
}