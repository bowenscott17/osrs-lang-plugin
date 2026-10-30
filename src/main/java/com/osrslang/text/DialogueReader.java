package com.osrslang.text;

import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.util.Text;

/**
 * Reads text that the RuneLite API exposes through widgets.
 * <p>
 * NPC dialogue is not delivered through the {@code ChatMessage} event (the event's own
 * javadoc states it does not fire for NPC dialogue), so it is read from the dialogue
 * widgets instead. All component ids below are the {@code gameval} constants for the
 * standard dialogue interfaces.
 */
public class DialogueReader
{
	private static final char NBSP = (char) 0x00A0;

	@Inject
	private Client client;

	/**
	 * Reads the currently displayed NPC dialogue, if any.
	 *
	 * @return the NPC's line of dialogue, or {@code null} if no NPC dialogue is showing
	 */
	public GameText readNpcDialogue()
	{
		Widget text = client.getWidget(InterfaceID.ChatLeft.TEXT);
		if (text == null || text.isHidden())
		{
			return null;
		}

		String content = clean(text.getText());
		if (content.isEmpty())
		{
			return null;
		}

		return new GameText(GameText.Type.NPC_DIALOGUE, content, readNpcName());
	}

	/**
	 * Reads the name of the NPC currently being talked to, if any.
	 *
	 * @return the NPC name, or {@code null} if it cannot be determined
	 */
	public String readNpcName()
	{
		Widget name = client.getWidget(InterfaceID.ChatLeft.NAME);
		if (name == null || name.isHidden())
		{
			return null;
		}

		String content = clean(name.getText());
		return content.isEmpty() ? null : content;
	}

	/**
	 * Reads the currently displayed player dialogue, if any.
	 *
	 * @return the player's line of dialogue, or {@code null} if none is showing
	 */
	public GameText readPlayerDialogue()
	{
		Widget text = client.getWidget(InterfaceID.ChatRight.TEXT);
		if (text == null || text.isHidden())
		{
			return null;
		}

		String content = clean(text.getText());
		if (content.isEmpty())
		{
			return null;
		}

		return new GameText(GameText.Type.PLAYER_DIALOGUE, content);
	}

	/**
	 * Reads the dialogue option list, if one is showing.
	 * <p>
	 * The option list is a single widget whose text contains one option per line.
	 *
	 * @return the options, or {@code null} if no option list is showing
	 */
	public GameText readDialogueOptions()
	{
		Widget options = client.getWidget(InterfaceID.Chatmenu.OPTIONS);
		if (options == null || options.isHidden())
		{
			return null;
		}

		String content = clean(options.getText());
		if (content.isEmpty())
		{
			return null;
		}

		return new GameText(GameText.Type.DIALOGUE_OPTIONS, content);
	}

	/**
	 * Finds the widget a piece of dialogue is drawn in.
	 * <p>
	 * The text alone is not enough to work out where a word is on screen - that also needs
	 * the widget's bounds and its font, so the widget itself has to be handed on. Returns
	 * {@code null} when the widget is missing or hidden, which is also what
	 * {@link com.osrslang.text.DialogueWordLayout} expects for "nothing on screen".
	 *
	 * @param type the kind of game text
	 * @return the widget the text is drawn in, or {@code null}
	 */
	public Widget findWidget(GameText.Type type)
	{
		Widget widget = null;

		if (type == GameText.Type.NPC_DIALOGUE)
		{
			widget = client.getWidget(InterfaceID.ChatLeft.TEXT);
		}
		else if (type == GameText.Type.PLAYER_DIALOGUE)
		{
			widget = client.getWidget(InterfaceID.ChatRight.TEXT);
		}
		else if (type == GameText.Type.DIALOGUE_OPTIONS)
		{
			widget = client.getWidget(InterfaceID.Chatmenu.OPTIONS);
		}

		return widget == null || widget.isHidden() ? null : widget;
	}

	/**
	 * Strips Jagex formatting tags and normalises the text.
	 */
	private String clean(String raw)
	{
		if (raw == null)
		{
			return "";
		}

		// removeTags drops every tag, including the <lt>/<gt>/<at> that
		// removeFormattingTags deliberately keeps - we want plain text to translate.
		return Text.removeTags(raw).replace(NBSP, ' ').trim();
	}
}