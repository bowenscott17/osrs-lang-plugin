package com.osrslang.text;

import com.osrslang.text.HoverTarget.Kind;
import com.osrslang.translation.HoverTranslationService;
import com.osrslang.translation.Language;
import com.osrslang.translation.TranslationResult;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.Text;

/**
 * Translates the English text of incoming chat messages and keeps a short rolling
 * buffer of the most recent translations for the chat overlay to render.
 * <p>
 * The translation pipeline is the same one the hover tooltip uses — the chat
 * text is wrapped in a {@link HoverTarget} whose kind is {@link Kind#CHAT_MESSAGE}
 * so it does not share a cache slot with item or NPC names. The future returned
 * by {@link HoverTranslationService#request(HoverTarget, Language, Language)}
 * resolves on a background thread, so the buffer is updated asynchronously and
 * the client thread is never blocked.
 * <p>
 * What is and is not translated:
 * <ul>
 * <li>Game messages and the result of {@code Examine} actions, which the client
 * delivers as chat lines, are translated.</li>
 * <li>Lines that are pure numbers, XP gains with only an integer, or other
 * untranslatable content are dropped before a request is made.</li>
 * <li>Player chat, private chat, clan chat and friends chat are passed through
 * unchanged — modifying the player's outgoing messages is on the RuneLite
 * forbidden-features list, and translating other players' chat on the client's
 * behalf is a separate, larger feature.</li>
 * </ul>
 * Nothing about the chatbox widget itself is touched: translations are kept in
 * this service and drawn through {@link com.osrslang.overlay.ChatTranslationOverlay}.
 */
@Slf4j
public class ChatMessageTranslator
{
	/**
	 * How many translated messages are kept for the overlay to show.
	 */
	private static final int MAX_RECENT = 10;

	/**
	 * The minimum length of a chat message worth translating, so a stray
	 * "OK." or "Yes" is not looked up.
	 */
	private static final int MIN_TEXT_LENGTH = 4;

	/**
	 * The placeholder name the client reports for something it has no real
	 * text for. Treated as empty.
	 */
	private static final String PLACEHOLDER_NAME = "null";

	private final HoverTranslationService hoverTranslationService;

	/**
	 * The most recent successful translations, newest first.
	 * <p>
	 * Read by the overlay on the client thread and written from the translation
	 * service's worker threads, so access is guarded by the deque's monitor.
	 */
	private final Deque<ChatTranslation> recent = new ArrayDeque<>();

	@Inject
	public ChatMessageTranslator(HoverTranslationService hoverTranslationService)
	{
		this.hoverTranslationService = hoverTranslationService;
	}

	/**
	 * Listens for new chat lines and translates the ones the policy allows.
	 * <p>
	 * The chatbox is the only place these strings are useful, but RuneLite
	 * delivers them through a single event regardless of where they would
	 * otherwise have appeared, so this is the right hook for all of them.
	 *
	 * @param event the chat message the client just received
	 */
	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (!isTranslatableType(event.getType()))
		{
			return;
		}

		String text = normalize(event.getMessage());
		if (text == null || text.length() < MIN_TEXT_LENGTH)
		{
			return;
		}

		if (isPurelyNumeric(text))
		{
			return;
		}

		HoverTarget target = new HoverTarget(Kind.CHAT_MESSAGE, text, GameText.Type.CHAT_MESSAGE, -1);
		Language from = Language.ENGLISH;
		Language to = Language.GERMAN;

		CompletableFuture<Optional<TranslationResult>> future =
			hoverTranslationService.request(target, from, to);

		future.thenAccept(result -> result.ifPresent(translation -> record(event.getType(), text, translation)));
	}

	/**
	 * Whether the chat type is one this translator handles.
	 * <p>
	 * Game messages are the player-facing lines that benefit most from
	 * translation: skill XP gains, level-up lines, quest progress, and the
	 * result of an {@code Examine} action all arrive as {@code GAMEMESSAGE}.
	 * Player-to-player chat is left for a separate feature, both because it
	 * is harder to render next to the original and because the RuneLite
	 * guidelines restrict what a plugin may do with outgoing messages.
	 */
	private static boolean isTranslatableType(@Nullable ChatMessageType type)
	{
		return type == ChatMessageType.GAMEMESSAGE;
	}

	/**
	 * Strips Jagex formatting tags and whitespace, drops the placeholder name.
	 */
	@Nullable
	private static String normalize(@Nullable String raw)
	{
		if (raw == null)
		{
			return null;
		}
		String stripped = Text.removeTags(raw).replace((char) 0x00A0, ' ').trim();
		if (stripped.isEmpty() || PLACEHOLDER_NAME.equalsIgnoreCase(stripped))
		{
			return null;
		}
		return stripped;
	}

	/**
	 * Whether the line is something a translator would never improve on:
	 * digits, separators, dots and slashes, with no letter content.
	 */
	private static boolean isPurelyNumeric(String text)
	{
		boolean sawDigit = false;
		for (int i = 0; i < text.length(); i++)
		{
			char c = text.charAt(i);
			if (Character.isDigit(c))
			{
				sawDigit = true;
				continue;
			}
			if (Character.isLetter(c))
			{
				return false;
			}
		}
		return sawDigit;
	}

	/**
	 * Adds a translation to the rolling buffer. Called from a worker thread.
	 */
	private void record(ChatMessageType type, String original, TranslationResult translation)
	{
		String translated = translation.getTranslatedText();
		if (translated == null || translated.isEmpty() || translated.equals(original))
		{
			return;
		}

		synchronized (recent)
		{
			recent.addFirst(new ChatTranslation(original, translated, type));
			while (recent.size() > MAX_RECENT)
			{
				recent.removeLast();
			}
		}
	}

	/**
	 * The recent translations, newest first.
	 * <p>
	 * Called on the client thread from the overlay's render method.
	 */
	public ChatTranslation[] getRecent()
	{
		synchronized (recent)
		{
			return recent.toArray(new ChatTranslation[0]);
		}
	}

	/**
	 * Empties the buffer. Called from the plugin's {@code shutDown}.
	 */
	public void clear()
	{
		synchronized (recent)
		{
			recent.clear();
		}
	}

	/**
	 * One translated chat line, retained for the overlay.
	 */
	public static final class ChatTranslation
	{
		private final String original;
		private final String translated;
		private final ChatMessageType type;

		public ChatTranslation(String original, String translated, ChatMessageType type)
		{
			this.original = original;
			this.translated = translated;
			this.type = type;
		}

		public String getOriginal()
		{
			return original;
		}

		public String getTranslated()
		{
			return translated;
		}

		public ChatMessageType getType()
		{
			return type;
		}
	}
}
