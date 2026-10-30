package com.osrslang.text;

import com.osrslang.translation.Language;
import com.osrslang.translation.TranslationManager;
import com.osrslang.translation.provider.VocabularyEntry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;

/**
 * Puts the translated spell name into the client's own hover tooltip.
 * <p>
 * The tooltip that appears when the pointer rests on a spell is drawn by the game
 * client, not by RuneLite: the client builds it from the widget's own fields. A
 * widget's action ("Cast") is what the player can do with it, and the widget's name -
 * the "op base" - is what the action is done to, so the client shows the two together:
 * the quick-prayers button is named "Quick-prayers" and has the action "Activate",
 * which is why hovering it says "Activate Quick-prayers".
 * <p>
 * The spellbook spells do it in two ways. The teleport spells have the action "Cast"
 * on the slot, so the tooltip reads "Cast Varrock Teleport". The combat spells are
 * cast by clicking them and have no action at all, so the client keeps the word in
 * the slot's target verb instead, and the tooltip reads "Cast Wind Strike" with the
 * verb taken from there. Either way the client draws that word at the front in blue
 * and the name after it in the ordinary text colour.
 * <p>
 * Both parts are put into the fields the client reads, so the tooltip keeps the shape
 * the game gives it: the word for casting a spell takes the place of the English one at
 * the front, and the name of the spell follows it in the colour the game writes spell
 * names in. The tooltip on a spell reads "Wirken Windwelle" in German.
 * <p>
 * The word at the front is not fixed to German: it is the word for casting a spell in
 * whichever language the plugin is set to translate into. It is looked up in the
 * dictionary first, which holds the verb - the word for casting a spell, rather than
 * the noun for the spell itself - and only a language the dictionary does not cover is
 * sent to the translation service.
 * <p>
 * Only those two fields are touched. The icon, the size, the position and the click
 * target of the slot are left exactly as the client laid them out, so the spellbook
 * still looks and behaves the way it always did, and no overlay is involved: the
 * tooltip the player sees is the game's own.
 * <p>
 * The client rebuilds the spellbook whenever it is opened, which puts the English
 * text back on the slots, so the rewrite is repeated on every tick. The name of a
 * spell is worked out once and kept, so a spell is never looked up twice, and the
 * translation service is only asked about a spell the bundled table has no entry for.
 */
@Slf4j
public class SpellTooltipTranslator
{
	/**
	 * The word the game uses for the spell action.
	 */
	private static final String ACTION_VERB = "Cast";

	/**
	 * The word the game uses for the spell action, as the dictionary keys it.
	 * <p>
	 * The dictionary holds its words in lower case, and the entry for this one is the
	 * verb - the word for casting a spell, not the noun for the spell itself.
	 */
	private static final String CAST_WORD = "cast";

	private final Client client;
	private final InterfaceStringDatabase database;
	private final TranslationManager translationManager;
	private final ClientThread clientThread;

	/**
	 * The language pair the current translations were made for, or {@code null} when
	 * nothing has been translated yet.
	 */
	private String pair;

	/**
	 * Whether the tooltips should be translated at all.
	 */
	private boolean enabled;

	/**
	 * What each translated slot held before, and what was put there.
	 * <p>
	 * The client throws the spellbook away and builds it again when it is opened, so
	 * the slot is compared against what was written to it: a slot that still carries
	 * the translated text is left alone, and a slot that has been rebuilt is translated
	 * again and remembers the English text for the restore.
	 */
	private final Map<Integer, Written> written = new HashMap<>();

	/**
	 * The translated name of each spell, kept so a spell is only ever translated once.
	 */
	private final Map<Integer, String> translatedNames = new HashMap<>();

	/**
	 * The word the tooltip starts with, in the target language.
	 * <p>
	 * It is worked out once for each language pair and kept, so it is never looked up
	 * twice. It is {@code null} until it is known.
	 */
	private String castWord;

	/**
	 * Whether the translation service has been asked for the casting word for this
	 * language pair.
	 */
	private boolean castWordAsked;

	/**
	 * The English names already put to the translation service, so the same spell is
	 * never asked about twice.
	 */
	private final Set<String> asked = new HashSet<>();

	/**
	 * The slots that were already written to the log, so the same slot is not
	 * described on every tick.
	 */
	private final Set<Integer> reported = new HashSet<>();

	public SpellTooltipTranslator(Client client, InterfaceStringDatabase database,
		TranslationManager translationManager, ClientThread clientThread)
	{
		this.client = client;
		this.database = database;
		this.translationManager = translationManager;
		this.clientThread = clientThread;
	}

	/**
	 * Whether the tooltips should be translated.
	 */
	public void setEnabled(boolean enabled)
	{
		this.enabled = enabled;
	}

	/**
	 * Translates the spell names of the modern spellbook.
	 * <p>
	 * Must be called on the client thread: it writes to widgets.
	 */
	public void update(Language from, Language to)
	{
		String wanted = from.getCode() + "-" + to.getCode();
		if (!wanted.equals(pair))
		{
			// A different table means the translations already on the slots are in the
			// wrong language, and the spell names and the casting word have to be
			// worked out again.
			pair = wanted;
			translatedNames.clear();
			asked.clear();
			castWord = null;
			castWordAsked = false;
			restore();
		}

		if (!enabled)
		{
			restore();
			return;
		}

		// The word the tooltip starts with is worked out first, so a slot is only
		// written once there is a word to write the name next to.
		updateCastWord(from, to);

		// The spellbook is only on screen while the Magic tab is open. Its root widget
		// is what the spell slots live inside, so checking it covers both "the tab is
		// open" and "the modern spellbook is the one selected".
		Widget root = client.getWidget(InterfaceID.MagicSpellbook.UNIVERSE);
		if (root == null || root.isHidden())
		{
			return;
		}

		// A language the dictionary does not cover is asked about only while the
		// spellbook is on screen, so no request is made for a panel nobody is looking
		// at.
		requestCastWord(from, to);

		for (SpellbookSpells.Spell spell : SpellbookSpells.SPELLS)
		{
			updateSpell(spell, from, to);
		}
	}

	/**
	 * Works out the word the tooltip starts with, from the dictionary.
	 * <p>
	 * The dictionary is asked first: it holds the verb - the word for casting a spell,
	 * rather than the noun for the spell itself - and it is read from disk, so nothing
	 * is requested. The interface table is not used for this, because the word it holds
	 * for "cast" is the noun the game uses for a spell.
	 */
	private void updateCastWord(Language from, Language to)
	{
		if (castWord != null || translationManager == null)
		{
			return;
		}

		Optional<VocabularyEntry> entry = translationManager.lookupWord(CAST_WORD, from, to);
		if (!entry.isPresent())
		{
			return;
		}

		String word = entry.get().getTranslation();
		if (word == null || word.trim().isEmpty())
		{
			return;
		}

		castWord = capitalise(word.trim());
		log.debug("Spell tooltip word for {}: {}", pair, castWord);
	}

	/**
	 * Asks the translation service for the casting word, for a language the dictionary
	 * does not cover.
	 * <p>
	 * The service is asked once per language pair: it keeps its own cache, so a word
	 * that was translated in an earlier session is answered from there without a new
	 * request. The answer arrives on a background thread, so it is put on the client
	 * thread before it is used.
	 */
	private void requestCastWord(Language from, Language to)
	{
		if (castWord != null || castWordAsked || translationManager == null || clientThread == null)
		{
			return;
		}

		// The dictionary is only left behind once it has been read, otherwise a word
		// the dictionary holds would be taken from the service instead - and the
		// service answers with the noun the game uses for a spell, not the verb.
		if (!translationManager.isVocabularyReady(from, to))
		{
			return;
		}

		castWordAsked = true;

		String askedFor = pair;
		translationManager.translatePhrase(ACTION_VERB, from, to, GameText.Type.INTERFACE_TEXT)
			.thenAccept(result -> result.ifPresent(translation ->
			{
				String text = translation.getTranslatedText();
				if (text == null || text.trim().isEmpty())
				{
					return;
				}

				clientThread.invoke(() ->
				{
					if (!askedFor.equals(pair))
					{
						// The language was changed while the request was in flight.
						return;
					}

					castWord = capitalise(firstWord(text));
					log.debug("Spell tooltip word for {}: {}", pair, castWord);
				});
			}));
	}

	/**
	 * Translates one spell slot.
	 */
	private void updateSpell(SpellbookSpells.Spell spell, Language from, Language to)
	{
		Widget widget = client.getWidget(spell.getWidgetId());
		if (widget == null)
		{
			return;
		}

		Written current = written.get(spell.getWidgetId());
		if (current != null && current.isWritten(widget))
		{
			// The slot still carries the translated text, so there is nothing to do.
			return;
		}

		String[] actions = widget.getActions();
		int index = actionIndex(actions);
		String verb = widget.getTargetVerb();
		if (index < 0 && !isCastVerb(verb))
		{
			report(spell, widget, actions);
			return;
		}

		String name = widget.getName();
		String translated = translatedNames.get(spell.getWidgetId());
		if (translated == null)
		{
			List<String> candidates = candidates(spell, index < 0 ? null : actions[index], name);
			Optional<String> found = lookup(candidates, from, to);
			if (!found.isPresent())
			{
				// The bundled table has nothing for this spell, so the translation
				// service is asked instead. The answer arrives later and is applied then.
				request(spell, from, to, name);
				return;
			}
			translated = found.get();
			translatedNames.put(spell.getWidgetId(), translated);
		}

		if (castWord == null)
		{
			// The word the tooltip starts with is not known yet, so nothing is written.
			// A tooltip with a translated name and an English word in front of it
			// would be half in each language.
			return;
		}

		apply(spell, translated, index, verb);
	}

	/**
	 * Writes the casting word and the translated name onto a spell slot.
	 * <p>
	 * The word goes into the field the client takes the front of the tooltip from - the
	 * action when the slot has one, the target verb when it does not - so it is drawn
	 * in the client's blue, and the name goes into the name field, so it is drawn after
	 * it in the ordinary text colour.
	 */
	private void apply(SpellbookSpells.Spell spell, String translated, int index, String verb)
	{
		Widget widget = client.getWidget(spell.getWidgetId());
		if (widget == null)
		{
			return;
		}

		String[] actions = widget.getActions();
		if (actions == null)
		{
			return;
		}

		Written current = written.get(spell.getWidgetId());
		if (current != null && current.isWritten(widget))
		{
			return;
		}

		// What the client had is taken before it is written over, so the restore can
		// put the English word and the English name back.
		String originalName = widget.getName();
		String original = index >= 0 ? actions[index] : verb;

		// The name keeps the colour the game wrote the English name in, so it is drawn
		// in the same colour: the spell names are green.
		String name = colourTag(originalName) + translated + "</col>";

		try
		{
			if (index >= 0)
			{
				widget.setAction(index, castWord);
			}
			else
			{
				widget.setTargetVerb(castWord);
			}
			widget.setName(name);
		}
		catch (RuntimeException e)
		{
			log.debug("Could not translate the action of spell {}", spell.getEnglishName(), e);
			return;
		}

		written.put(spell.getWidgetId(),
			new Written(index, original, originalName, castWord, name));
		translatedNames.put(spell.getWidgetId(), translated);

		log.debug("Spell tooltip translated: '{} {}' -> '{} {}'",
			ACTION_VERB, spell.getEnglishName(), castWord, translated);
	}

	/**
	 * Says once what a slot looks like when it has no action the plugin can recognise.
	 * <p>
	 * A slot without a "Cast" action and without a "Cast" target verb is one the
	 * client built in a way the plugin does not know about, so the slot is written to
	 * the log instead of being silently left in English.
	 */
	private void report(SpellbookSpells.Spell spell, Widget widget, String[] actions)
	{
		if (!reported.add(spell.getWidgetId()))
		{
			return;
		}

		log.info("Spell slot has no cast action: '{}' (type {}, text '{}', name '{}', actions '{}', verb '{}')",
			spell.getEnglishName(), widget.getType(), widget.getText(), widget.getName(),
			actions(actions), widget.getTargetVerb());
	}

	/**
	 * The spell names that could be the English name of the slot, best guess first.
	 * <p>
	 * The slot's own name is the spell's name in the modern spellbook, but the client
	 * is free to put the whole phrase in the action instead, in which case the name
	 * follows the word. The name from the plugin's own list is the last resort, and
	 * the one that is there when the client names the slot in a way the table does
	 * not know.
	 */
	private List<String> candidates(SpellbookSpells.Spell spell, String action, String name)
	{
		Set<String> names = new LinkedHashSet<>();

		String plain = plainName(name);
		if (!plain.isEmpty() && !plain.equalsIgnoreCase(ACTION_VERB))
		{
			names.add(plain);
		}

		String afterVerb = afterVerb(action);
		if (afterVerb != null)
		{
			names.add(afterVerb);
		}

		names.add(spell.getEnglishName());

		return new ArrayList<>(names);
	}

	/**
	 * Looks the spell up in the bundled interface table.
	 */
	private Optional<String> lookup(List<String> candidates, Language from, Language to)
	{
		for (String candidate : candidates)
		{
			Optional<String> translated = database.lookup(candidate, from, to);
			if (translated.isPresent())
			{
				return translated;
			}
		}

		return Optional.empty();
	}

	/**
	 * Asks the translation service for a spell the bundled table does not have.
	 * <p>
	 * The service is asked once per spell and language pair: it keeps its own cache, so
	 * a spell that was translated in an earlier session is answered from there without
	 * a new request. The answer arrives on a background thread, so it is put on the
	 * client thread before it touches a widget.
	 */
	private void request(SpellbookSpells.Spell spell, Language from, Language to, String widgetName)
	{
		if (translationManager == null || clientThread == null)
		{
			return;
		}

		// The name the client gave the slot is the one on screen, so it is the one
		// worth translating. The plugin's own name is the fallback for a slot the
		// client has not named.
		String english = plainName(widgetName);
		if (english.isEmpty())
		{
			english = spell.getEnglishName();
		}

		String key = english + "|" + from.getCode() + "-" + to.getCode();
		if (!asked.add(key))
		{
			return;
		}

		translationManager.translatePhrase(english, from, to, GameText.Type.INTERFACE_TEXT)
			.thenAccept(result -> result.ifPresent(translation ->
			{
				String translated = translation.getTranslatedText();
				if (translated == null || translated.isEmpty())
				{
					return;
				}

				clientThread.invoke(() -> translatedNames.put(spell.getWidgetId(), translated));
			}));
	}

	/**
	 * Puts every translated slot back the way the client had it.
	 * <p>
	 * Only what the plugin wrote is put back: a slot the client has rebuilt already
	 * carries the English text, and writing the remembered text over it would put the
	 * wrong word in the wrong place.
	 */
	public void restore()
	{
		for (Map.Entry<Integer, Written> entry : written.entrySet())
		{
			Widget widget = client.getWidget(entry.getKey());
			if (widget == null)
			{
				continue;
			}

			Written written = entry.getValue();
			String[] actions = widget.getActions();
			if (actions == null)
			{
				continue;
			}

			try
			{
				if (written.index >= 0)
				{
					if (written.index < actions.length && written.castWord.equals(actions[written.index]))
					{
						widget.setAction(written.index, written.original);
					}
				}
				else if (written.castWord.equals(nullToEmpty(widget.getTargetVerb())))
				{
					widget.setTargetVerb(written.original);
				}

				if (written.writtenName.equals(nullToEmpty(widget.getName())))
				{
					widget.setName(written.originalName);
				}
			}
			catch (RuntimeException e)
			{
				// The slot was destroyed since it was translated. Nothing to put back.
				log.debug("Could not restore a translated spell slot", e);
			}
		}

		written.clear();
	}

	/**
	 * Forgets everything, restoring the game text first.
	 */
	public void clear()
	{
		restore();
		translatedNames.clear();
		asked.clear();
		reported.clear();
		castWord = null;
		castWordAsked = false;
		pair = null;
	}

	/**
	 * The number of spell slots currently showing a translated name.
	 */
	public int translatedCount()
	{
		return written.size();
	}

	/**
	 * The index of the action that casts the spell.
	 * <p>
	 * The action is either the bare word "Cast" or the whole phrase, "Cast Wind Wave",
	 * depending on how the client built the slot.
	 *
	 * @return the index of the action, or -1 when the slot has no cast action
	 */
	private static int actionIndex(String[] actions)
	{
		if (actions == null)
		{
			return -1;
		}

		for (int i = 0; i < actions.length; i++)
		{
			String action = actions[i];
			if (action == null || action.isEmpty())
			{
				continue;
			}

			String trimmed = action.trim();
			if (trimmed.equalsIgnoreCase(ACTION_VERB)
				|| trimmed.regionMatches(true, 0, ACTION_VERB + " ", 0, ACTION_VERB.length() + 1))
			{
				return i;
			}
		}

		return -1;
	}

	/**
	 * Whether the word the client keeps for the slot is the one for casting a spell.
	 */
	private static boolean isCastVerb(String verb)
	{
		if (verb == null)
		{
			return false;
		}

		String trimmed = verb.trim();
		return trimmed.equalsIgnoreCase(ACTION_VERB)
			|| trimmed.regionMatches(true, 0, ACTION_VERB + " ", 0, ACTION_VERB.length() + 1);
	}

	/**
	 * The part of the action that follows the word, when the action holds the whole
	 * phrase.
	 */
	private static String afterVerb(String action)
	{
		if (action == null || action.length() <= ACTION_VERB.length())
		{
			return null;
		}

		if (!action.regionMatches(true, 0, ACTION_VERB + " ", 0, ACTION_VERB.length() + 1))
		{
			return null;
		}

		String rest = action.substring(ACTION_VERB.length()).trim();
		return rest.isEmpty() ? null : rest;
	}

	/**
	 * The name of a slot without the colour the client writes around it.
	 */
	private static String plainName(String name)
	{
		if (name == null || name.isEmpty())
		{
			return "";
		}

		StringBuilder plain = new StringBuilder(name.length());
		boolean inTag = false;
		for (int i = 0; i < name.length(); i++)
		{
			char c = name.charAt(i);
			if (c == '<')
			{
				inTag = true;
			}
			else if (c == '>')
			{
				inTag = false;
			}
			else if (!inTag)
			{
				plain.append(c);
			}
		}

		return plain.toString().trim();
	}

	/**
	 * The colour the game wrote a name in, as the tag that carries it.
	 * <p>
	 * The game writes a spell's name in green, as "&lt;col=00ff00&gt;Wind
	 * Strike&lt;/col&gt;", and the client draws the name in that colour. The translated
	 * name is written with the same tag around it, so it is drawn in the same colour.
	 *
	 * @return the tag, or an empty string when the name carries none
	 */
	private static String colourTag(String name)
	{
		if (name == null || !name.startsWith("<col="))
		{
			return "";
		}

		int end = name.indexOf('>');
		return end < 0 ? "" : name.substring(0, end + 1);
	}

	/**
	 * The first word of a piece of text.
	 * <p>
	 * The tooltip takes a single word at its front, so a translation that came back as a
	 * phrase is cut down to the word that starts it.
	 */
	private static String firstWord(String text)
	{
		String trimmed = text.trim();
		for (int i = 0; i < trimmed.length(); i++)
		{
			if (Character.isWhitespace(trimmed.charAt(i)))
			{
				return trimmed.substring(0, i);
			}
		}

		return trimmed;
	}

	/**
	 * The word with its first letter in upper case.
	 * <p>
	 * The game's own word for casting a spell starts with a capital, so the translated
	 * one does too.
	 */
	private static String capitalise(String word)
	{
		if (word == null || word.isEmpty())
		{
			return word;
		}

		return Character.toUpperCase(word.charAt(0)) + word.substring(1);
	}

	/**
	 * The actions of a slot as one string, for the log.
	 */
	private static String actions(String[] actions)
	{
		if (actions == null)
		{
			return "";
		}

		StringBuilder builder = new StringBuilder();
		for (String action : actions)
		{
			if (builder.length() > 0)
			{
				builder.append('|');
			}
			builder.append(action == null ? "" : action);
		}

		return builder.toString();
	}

	/**
	 * An empty string for a name the client does not have.
	 */
	private static String nullToEmpty(String name)
	{
		return name == null ? "" : name;
	}

	/**
	 * What a spell slot held before it was translated, and what was put there.
	 */
	private static final class Written
	{
		/**
		 * The index of the action that was rewritten, or -1 when the target verb was
		 * the one that was rewritten.
		 */
		private final int index;

		/**
		 * The action or the target verb the client had.
		 */
		private final String original;

		/**
		 * The name the client had.
		 */
		private final String originalName;

		/**
		 * The word that was written to the front of the tooltip.
		 */
		private final String castWord;

		/**
		 * The translated name that was written, with the colour the game uses for it.
		 */
		private final String writtenName;

		Written(int index, String original, String originalName, String castWord, String writtenName)
		{
			this.index = index;
			this.original = original == null ? "" : original;
			this.originalName = originalName == null ? "" : originalName;
			this.castWord = castWord;
			this.writtenName = writtenName;
		}

		/**
		 * Whether the slot still carries what was written to it.
		 * <p>
		 * A slot that does is the one that was translated, and a slot that does not is
		 * one the client has built again. Every action is looked at rather than only
		 * the one that was rewritten, because the client may have put the action
		 * somewhere else when it built the slot again.
		 */
		boolean isWritten(Widget widget)
		{
			String[] actions = widget.getActions();
			boolean found = false;
			if (index >= 0 && actions != null)
			{
				for (String action : actions)
				{
					if (this.castWord.equals(action))
					{
						found = true;
						break;
					}
				}
			}
			else if (this.castWord.equals(nullToEmpty(widget.getTargetVerb())))
			{
				found = true;
			}

			return found && this.writtenName.equals(nullToEmpty(widget.getName()));
		}
	}
}
