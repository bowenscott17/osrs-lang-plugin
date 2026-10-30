package com.osrslang.text;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.FontID;
import net.runelite.api.FontTypeFace;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.util.Text;
import com.osrslang.translation.Language;

/**
 * Rewrites the game's own interface text into the chosen language.
 * <p>
 * The client draws every label from a widget that holds the string, so a label can be
 * translated by reading the widget and writing the translation back. The whole interface
 * tree is walked from the client's roots, and only text present in the
 * {@link InterfaceStringDatabase} is replaced - everything else is left exactly as the
 * game wrote it.
 * <p>
 * <b>No box is ever resized.</b> Several of these widgets are click targets - the attack
 * styles and the prayers in particular - and their guidelines forbid moving or resizing
 * those, so width, height and position are never written. German words are often wider
 * than the English ones, though, so a translated word is measured against the box the
 * client already gave it and, when it does not fit, drawn in one of the game's own smaller
 * fonts instead. The clickable area is untouched either way. If a German word does not fit
 * even in the smallest font, the label is left in English rather than drawn clipped or
 * spilling over its neighbour.
 * <p>
 * A widget the client rebuilds reverts to the game's own text, so the tree is walked again
 * on each tick and any label that was reset is translated once more. Each widget's
 * original text and font are remembered so they can be put back when the language changes
 * or the plugin stops.
 * <p>
 * Three things are deliberately left alone:
 * <ul>
 * <li>text this plugin has already translated, so a translation that is also a table key
 * cannot rewrite itself forever;</li>
 * <li>the box the player is currently typing in, which holds their own words;</li>
 * <li>anything hidden, because it is not on screen and has nothing to show.</li>
 * </ul>
 * <p>
 * The dialogue interfaces are excluded as well. The dialogue box, the speaker's name and
 * the list of dialogue options are already translated by {@link DialogueReader} and shown
 * through the plugin's own overlays, and the options are click targets the player chooses
 * from. Rewriting them here would show a second, unhoverable translation of the same
 * words on top of the one that can be hovered.
 */
@Slf4j
public class InterfaceTextTranslator
{
	/**
	 * How deep into the widget tree to walk. Real trees are shallow; this stops a
	 * malformed one from recursing forever.
	 */
	private static final int MAX_DEPTH = 32;

	/**
	 * How many widgets to remember originals for. The client creates and discards widgets
	 * constantly, so this is a bound on memory rather than a promise of completeness -
	 * past this point the oldest entries are dropped and are no longer restored.
	 */
	private static final int MAX_REMEMBERED = 8192;

	/**
	 * Strings longer than this are sentences rather than labels. The game's longest
	 * interface labels are around this length; anything well past it is dialogue or an
	 * item description, which other parts of this plugin already deal with.
	 */
	private static final int MAX_RECORDED_LENGTH = 40;

	/**
	 * How many missing strings to hold on to.
	 */
	private static final int MAX_RECORDED = 4000;

	/**
	 * The game's own smaller fonts, tried in order when a translation does not fit the box
	 * the client laid out. These are real cache fonts, so nothing new has to be loaded -
	 * the widget is simply pointed at the one the game already draws elsewhere.
	 */
	private static final int[] SMALLER_FONTS =
	{
		FontID.VERDANA_11,
		FontID.QUILL_8,
	};

	/**
	 * The skills panel. Its root component is exposed by the gameval constants as
	 * {@link InterfaceID.Stats#UNIVERSE}; the dump works off the group id rather than the
	 * full component id so every row underneath it is captured.
	 */
	private static final int SKILLS_PANEL = InterfaceID.Stats.UNIVERSE >>> 16;

	/**
	 * What a widget looked like before this plugin touched it.
	 */
	private static final class Original
	{
		private final String text;
		private final int fontId;

		private Original(String text, int fontId)
		{
			this.text = text;
			this.fontId = fontId;
		}
	}

	private final Client client;
	private final InterfaceStringDatabase database;

	/**
	 * The widget as it was before this plugin changed it, so it can be put back.
	 */
	private final Map<Widget, Original> remembered = new IdentityHashMap<>();

	/**
	 * Strings this plugin has written. Never looked up, so a translation cannot rewrite
	 * itself on every pass.
	 */
	private Set<String> written = Collections.emptySet();

	/**
	 * English text met on screen with no translation in the table.
	 */
	private final Set<String> missing = new LinkedHashSet<>();

	/**
	 * Translations the client had no room for, so the label stayed in English.
	 */
	private final Set<String> tooLong = new LinkedHashSet<>();

	/**
	 * The exact text this plugin has already written onto a widget.
	 * <p>
	 * {@link #written} covers the plain words in the table, but a composed label is not one
	 * of them: "Pfeilfabrikation XP:" is a translation with an "XP:" tail stuck on, and no
	 * table key matches it. Without this the plugin would keep re-reading its own German
	 * and - worse - write it into the list of strings that need translating, which is the
	 * list the table is grown from.
	 */
	private final Set<String> ownText = new LinkedHashSet<>();

	/**
	 * Words already written to the log as translated, so a widget the client rebuilds
	 * every frame does not produce a line per frame.
	 */
	private final Set<String> reported = new LinkedHashSet<>();

	private String pair;
	private final Set<Integer> dumpedIds = new HashSet<>();
	private long lastDump;
	private boolean recordMissing;

	public InterfaceTextTranslator(Client client, InterfaceStringDatabase database)
	{
		this.client = client;
		this.database = database;
	}

	/**
	 * Switches on recording of strings that have no translation.
	 * <p>
	 * This is how the table gets filled in: every label the client draws that the table
	 * does not cover is written down once, so the table can be extended with the words the
	 * game really uses rather than with guesses about them.
	 */
	public void setRecordMissing(boolean recordMissing)
	{
		this.recordMissing = recordMissing;
	}

	/**
	 * The English text seen so far that the table could not translate.
	 */
	public Set<String> missingStrings()
	{
		return Collections.unmodifiableSet(missing);
	}

	/**
	 * Translations the client had no room for.
	 * <p>
	 * These are words the table can translate but the box cannot show. They are the ones
	 * worth rewriting with a shorter German word, which is why they are kept apart from
	 * the strings the table simply does not have.
	 */
	public Set<String> oversizedStrings()
	{
		return Collections.unmodifiableSet(tooLong);
	}

	/**
	 * Walks the interface tree and translates what it can.
	 * <p>
	 * Must be called on the client thread: it writes to widgets.
	 *
	 * @return how many widgets were translated on this pass
	 */
	public int update(Language from, Language to)
	{
		Widget[] roots = client.getWidgetRoots();
		if (roots == null)
		{
			return 0;
		}

		String wanted = from.getCode() + "-" + to.getCode();
		if (!wanted.equals(pair))
		{
			// The table is different, so the set of strings written for the old one no
			// longer applies - and anything already on screen is in the wrong language.
			pair = wanted;
			written = database.translationsFor(from, to);
			restore();
		}

		int translated = 0;
		for (Widget root : roots)
		{
			translated += walk(root, 0, from, to);
		}

		if (recordMissing && System.currentTimeMillis() - lastDump > 5000)
		{
			// The skills panel keeps its twenty-odd rows behind an ancestor that reports
			// itself hidden, so the walk above never reaches them. The dump walks through
			// hidden containers and logs each panel widget the first time it appears, so
			// the developer can read off exactly where the skill names live. It is gated
			// to once every few seconds so the recursive walk is not paid on every tick.
			lastDump = System.currentTimeMillis();
			dumpSkillsPanel(from, to);
		}

		return translated;
	}

	/**
	 * Logs the bounds of every widget on the skills panel, together with the German
	 * translation and the width the panel's own font would give it.
	 * <p>
	 * The overlay that draws German on top of the skill rows guesses four constants
	 * ({@code NAME_INSET}, {@code LEVEL_INSET}, {@code BAR_INSET}, {@code BACKGROUND}),
	 * and they have to be measured against a real panel. This dump is what that
	 * measurement runs against: each row's bounds are the inset, and the German width is
	 * how far the overlay has to shrink to fit.
	 */
	private void dumpSkillsPanel(Language from, Language to)
	{
		Widget panel = client.getWidget(InterfaceID.Stats.UNIVERSE);
		if (panel == null)
		{
			return;
		}

		Rectangle panelBounds = panel.getBounds();
		log.debug("Skills panel root bounds: {}",
			panelBounds == null ? "null" :
				String.format("x=%d y=%d w=%d h=%d",
					panelBounds.x, panelBounds.y, panelBounds.width, panelBounds.height));

		// The dump walks the tree via roots so the hidden ancestor is followed, but the
		// per-widget bound log is what the developer reads.
		dump(new Widget[]{panel}, SKILLS_PANEL);
	}

	/**
	 * Writes down every widget of one interface, once, for the developer to read in the log.
	 */
	private void dump(Widget[] roots, int group)
	{
		for (Widget root : roots)
		{
			dump(root, 0, group);
		}
	}

	private void dump(Widget widget, int depth, int group)
	{
		if (widget == null || depth > MAX_DEPTH)
		{
			return;
		}

		int id = widget.getId();
		if (id >>> 16 == group && dumpedIds.add(id))
		{
			Rectangle bounds = widget.getBounds();
			String boundsText = bounds == null ? "null" :
				String.format("x=%d y=%d w=%d h=%d", bounds.x, bounds.y, bounds.width, bounds.height);
			log.debug("Skills panel widget {}.{} type={} hidden={} bounds=({}) text=<{}>",
				id >>> 16, id & 0xFFFF, widget.getType(), widget.isHidden(), boundsText, widget.getText());
		}

		for (Widget child : childrenOf(widget))
		{
			dump(child, depth + 1, group);
		}
	}

	private int walk(Widget widget, int depth, Language from, Language to)
	{
		if (widget == null || depth > MAX_DEPTH)
		{
			return 0;
		}

		// A hidden container's children are not drawn, so there is nothing to translate
		// inside it until it is shown.
		int translated = !widget.isHidden() && translate(widget, from, to) ? 1 : 0;

		for (Widget child : childrenOf(widget))
		{
			translated += walk(child, depth + 1, from, to);
		}

		return translated;
	}

	/**
	 * Translates one widget, if any of its lines are in the table.
	 * <p>
	 * The text a widget holds is not a bare sentence. The client writes colour tags into
	 * it - {@code <col=ffff00>Friends</col>} - and joins lines with {@code <br>}. A widget
	 * reading "Friends" is therefore stored as that whole tagged string, which matches no
	 * dictionary key. Each line is taken apart instead, so the tags are seen for what they
	 * are: decoration around the word, not part of it.
	 *
	 * @return whether the widget's text was changed
	 */
	private boolean translate(Widget widget, Language from, Language to)
	{
		// The box being typed into holds the player's own words rather than the game's.
		if (widget == client.getFocusedInputFieldWidget())
		{
			return false;
		}

		// Dialogue is handled elsewhere, and the options are click targets.
		if (isDialogueWidget(widget))
		{
			return false;
		}

		String raw = widget.getText();
		if (raw == null || raw.isEmpty())
		{
			return false;
		}

		// Text this plugin wrote earlier. It is already in the right language, and reading
		// it as though it were the game's own would put our own German into the list of
		// strings still needing a translation.
		if (ownText.contains(raw))
		{
			restoreFont(widget);
			return false;
		}

		String[] lines = raw.split("<br>", -1);

		StringBuilder rebuilt = new StringBuilder(raw.length());
		boolean changed = false;

		for (int i = 0; i < lines.length; i++)
		{
			if (i > 0)
			{
				rebuilt.append("<br>");
			}

			String line = translateLine(widget, lines[i], from, to);
			changed |= !line.equals(lines[i]);
			rebuilt.append(line);
		}

		if (!changed)
		{
			// The client rebuilt this widget, or nothing here is in the table. Either way
			// its text is the game's own, so a font this plugin shrank has to go back.
			restoreFont(widget);
			return false;
		}

		String translated = rebuilt.toString();

		// The German is often wider than the English, and the box is not ours to widen.
		// A word that will not fit is drawn in a smaller of the game's own fonts; one that
		// fits in none of them is left in English rather than drawn clipped.
		//
		// The search starts from the font the client chose, not from whatever font this
		// plugin left on the widget last pass, so a label that has since been rebuilt in
		// English gets its own font back.
		Original previous = remembered.get(widget);
		int baseFont = previous == null ? widget.getFontId() : previous.fontId;

		if (chooseFont(widget, raw, translated, baseFont) < 0)
            {
                // Keep translated tooltip text even when it exceeds the original width.
                widget.setFontId(baseFont);
            }

		// The font is recorded before it is changed, so that whatever is written here can be
		// taken back off again later.
		remember(widget, raw, baseFont);

		// The text is all that is written. Width, height and position are left as the
		// client laid them out, so click targets keep the area the client gave them.
		// chooseFont has already left the widget on whichever font it settled on.
		widget.setText(translated);
		rememberWritten(translated);
		report(widget, raw, translated);

		return true;
	}

	/**
	 * Picks the game's smallest built-in font in which a translated label fits its box.
	 * <p>
	 * Measuring means asking the widget for its font, so the widget is pointed at the font
	 * being tried before each measurement. It is left on whichever font was chosen, and on
	 * the game's own font when none of them fit.
	 * <p>
	 * The width the client reports is only treated as a limit when the English respected
	 * it. Tooltips size themselves to their text and report a width their own words already
	 * overrun, so holding the German to that number would leave whole panels in English
	 * over a box that was never the constraint. Where the English already overflows, the
	 * label is translated at the game's own font and left to be drawn exactly as the client
	 * would have drawn the English.
	 *
	 * @param baseFont the font the client gave the widget, which is where the search starts
	 * @return the font to draw with, or {@code -1} when it fits in none of them
	 */
	private int chooseFont(Widget widget, String original, String translated, int baseFont)
	{
		// A widget that reports no width gives nothing to measure against. The label is
		// translated as the client would have drawn it and keeps its own font, because
		// shrinking what may not have been too wide helps nobody.
		if (widget.getWidth() <= 0)
		{
			return baseFont;
		}

		widget.setFontId(baseFont);

		int available = widget.getWidth();
		int english = widestLine(widget, original);
		int german = widestLine(widget, translated);

		if (english < 0 || german < 0)
		{
			// The font could not be resolved, so nothing can be measured. Leave the label as
			// the client drew it rather than guessing at a size.
			widget.setFontId(baseFont);
			return -1;
		}

		// The client already draws this label wider than the width it reports, so that
		// width is not what limits it. Shrinking the German would achieve nothing.
		if (english > available)
		{
			return baseFont;
		}

		if (german <= available)
		{
			return baseFont;
		}

		for (int font : SMALLER_FONTS)
		{
			widget.setFontId(font);
			int shrunk = widestLine(widget, translated);
			if (shrunk >= 0 && shrunk <= available)
			{
				return font;
			}
		}

		widget.setFontId(baseFont);
		return -1;
	}

	/**
	 * How wide the widest line of a piece of text is in the font the widget now points at.
	 * <p>
	 * The widest line decides, since each line is drawn on its own, and the colour tags are
	 * left out because the client never draws them.
	 *
	 * @return the width, or {@code -1} when the font could not be resolved
	 */
	private int widestLine(Widget widget, String text)
	{
		FontTypeFace face = widget.getFont();
		if (face == null)
		{
			return -1;
		}

		int widest = 0;
		for (String line : text.split("<br>", -1))
		{
			widest = Math.max(widest, face.getTextWidth(Text.removeTags(line)));
		}

		return widest;
	}

	/**
	 * Remembers how a widget looked before this plugin changed it.
	 * <p>
	 * Only the first time a widget is touched is recorded. Recording again on a later pass
	 * would store the smaller font chosen last time as if the client had chosen it, and
	 * that font could then never be put back - so the font is taken as the caller found
	 * it, before any fitting was tried.
	 */
	private void remember(Widget widget, String raw, int baseFont)
	{
		if (!remembered.containsKey(widget))
		{
			if (remembered.size() >= MAX_REMEMBERED)
			{
				remembered.clear();
			}
			remembered.put(widget, new Original(raw, baseFont));
		}
	}

	/**
	 * Notes text this plugin has written, so it is never read as the game's own again.
	 * <p>
	 * The set is a convenience rather than a record: past its bound the oldest entries are
	 * dropped, and those labels are simply re-examined as before, which costs a little work
	 * but changes nothing on screen.
	 */
	private void rememberWritten(String text)
	{
		if (ownText.size() >= MAX_REMEMBERED)
		{
			ownText.clear();
		}
		ownText.add(text);
	}

	/**
	 * Puts a widget's font back if this plugin shrank it and nothing needs it shrunk now.
	 */
	private void restoreFont(Widget widget)
	{
		Original original = remembered.get(widget);
		if (original != null && widget.getFontId() != original.fontId)
		{
			widget.setFontId(original.fontId);
		}
	}

	/**
	 * Translates one line of a widget's text, keeping any tags around it.
	 * <p>
	 * Only the tags wrapping the whole line are held back, which is the shape the client
	 * uses for a coloured label: the word is looked up without its colour, and the colour
	 * is put back around the translation. Tags inside a line are left with the word they
	 * decorate and are not rewritten.
	 *
	 * @return the line with its word translated, or the line unchanged
	 */
	private String translateLine(Widget widget, String line, Language from, Language to)
	{
		// Past any tags the client wrapped around the whole line.
		int start = 0;
		while (start < line.length() && line.charAt(start) == '<')
		{
			int close = line.indexOf('>', start);
			if (close < 0)
			{
				break;
			}
			start = close + 1;
		}

		// Back from any tags closing it.
		int end = line.length();
		while (end > start && line.charAt(end - 1) == '>')
		{
			int open = line.lastIndexOf('<', end - 1);
			if (open < start)
			{
				break;
			}
			end = open;
		}

		String word = Text.removeTags(line.substring(start, end));
		if (word.isEmpty() || written.contains(word))
		{
			return line;
		}

		Optional<String> replacement = database.lookup(word, from, to);
		if (replacement.isPresent())
		{
			if (replacement.get().equals(word))
			{
				return line;
			}

			report(widget, word, replacement.get());
			return line.substring(0, start) + replacement.get() + line.substring(end);
		}

		// The game composes most labels out of a name and a value, so the bare name is
		// rarely stored on its own: the skills panel writes "Attack XP:" and "Attack: 99",
		// not "Attack".
		String composed = translateComposedLabel(widget, word, start, end, line, from, to);
		if (composed != null)
		{
			return composed;
		}

		recordMissing(word);
		return line;
	}

	/**
	 * Translates the name part of a label that the game built by appending a value.
	 * <p>
	 * A table entry is only treated as the name if what follows it looks like a value -
	 * a colon or a number. That is what keeps "Attack: 99" and "Prayer XP:" working
	 * without also mangling "Strength of Saradomin", which is an item whose name merely
	 * begins with a skill name.
	 *
	 * @return the rebuilt line, or {@code null} when this is not a composed label
	 */
	private String translateComposedLabel(Widget widget, String word, int start, int end, String line,
		Language from, Language to)
	{
		// Without a colon or a number anywhere there is no value in sight, and the line is
		// a name rather than a label. This also keeps the cost off content strings.
		boolean valued = false;
		for (int i = 0; i < word.length(); i++)
		{
			char c = word.charAt(i);
			if (c == ':' || (c >= '0' && c <= '9'))
			{
				valued = true;
				break;
			}
		}

		if (!valued)
		{
			return null;
		}

		// Longest name first, so "Attack XP:" is read as the name "Attack" with an "XP:"
		// tail rather than as a name of "Attack XP".
		for (int split = word.length() - 1; split > 0; split--)
		{
			String name = word.substring(0, split);
			if (!isNameBoundary(word, split))
			{
				continue;
			}

			Optional<String> translated = database.lookup(name, from, to);
			if (translated.isPresent() && !translated.get().equals(name))
			{
				String tail = word.substring(split);
				// After matching the prefix, the tail often holds the part the game
				// actually meant. "Level 1: Wind Strike" -> prefix "Level" matches,
				// tail " 1: Wind Strike" still holds "Wind Strike", which translates.
				String translatedTail = translateTail(widget, tail, from, to);
				report(widget, name, translated.get());

				return line.substring(0, start) + translated.get() + translatedTail + line.substring(end);
			}
		}

		return null;
	}

	/**
	 * Translates the trailing portion of a composed label after its prefix has been
	 * matched.
	 * <p>
	 * The common shape is "Level N: Spell Name": the prefix ("Level") has matched and
	 * the tail is " N: Spell Name". The translatable bit is what comes after the last
	 * colon, so the tail is searched from the right and the part after the last colon
	 * is looked up as a single word. Any leading whitespace and the colon itself are
	 * kept, so "Level 1: Wind Strike" becomes "Die Stufe 1: Der Windstoß" rather than
	 * collapsing into "Die Stufe1Der Windstoß".
	 * <p>
	 * When the tail has no colon, the longest-prefix rule still applies - it falls
	 * back to the same composed-label path the prefix did, so a "Name Value" pattern
	 * in the tail is still translated.
	 *
	 * @return the translated tail, or the original tail unchanged when nothing in it
	 *         matches the table
	 */
	private String translateTail(Widget widget, String tail, Language from, Language to)
	{
		if (tail.isEmpty())
		{
			return tail;
		}

		int colon = tail.lastIndexOf(':');
		if (colon >= 0)
		{
			String suffix = tail.substring(colon + 1).trim();
			if (!suffix.isEmpty())
			{
				Optional<String> translated = database.lookup(suffix, from, to);
				if (translated.isPresent() && !translated.get().equals(suffix))
				{
					return tail.substring(0, colon + 1) + " " + translated.get();
				}
			}
		}

		// No colon (or the suffix after the colon did not match) - try the
		// composed-label path on the tail, so a "Name Value" pattern that begins
		// past the prefix is still picked up.
		String composed = translateComposedTail(widget, tail, from, to);
		return composed != null ? composed : tail;
	}

	/**
	 * The longest-prefix match for a tail string, mirroring {@link #translateComposedLabel}
	 * but without recording the prefix as a translation (the caller already did).
	 */
	private String translateComposedTail(Widget widget, String tail, Language from, Language to)
	{
		boolean valued = false;
		for (int i = 0; i < tail.length(); i++)
		{
			char c = tail.charAt(i);
			if (c == ':' || (c >= '0' && c <= '9'))
			{
				valued = true;
				break;
			}
		}

		if (!valued)
		{
			return null;
		}

		for (int split = tail.length() - 1; split > 0; split--)
		{
			if (!isNameBoundary(tail, split))
			{
				continue;
			}

			String name = tail.substring(0, split);
			Optional<String> translated = database.lookup(name, from, to);
			if (translated.isPresent() && !translated.get().equals(name))
			{
				String remainder = tail.substring(split);
				String translatedRemainder = translateTail(widget, remainder, from, to);
				return translated.get() + translatedRemainder;
			}
		}

		return null;
	}

	/**
	 * Whether a name may end just before a given position.
	 * <p>
	 * The name has to be a whole word, so whatever follows the split must not continue it.
	 * Without that test the entry "Attack" would match inside "Attacker: 3" and produce
	 * "Angriffer: 3". A split may otherwise follow a letter, a colon or a space, which is
	 * how "Attack", "Combat Lvl:" and "Prayer" all end their names in real labels.
	 */
	private static boolean isNameBoundary(String word, int split)
	{
		return split > 0 && split < word.length()
			&& !Character.isLetter(word.charAt(split));
	}

	/**
	 * Writes down a translation that was applied.
	 * <p>
	 * Reported once per word <em>per widget</em>, and only when the user has asked for
	 * recording, so that turning the setting on shows both halves of the result: what was
	 * translated and what was not. The component is part of the line and part of the key
	 * because the same word legitimately lives in more than one place - "Mining" is both a
	 * skill tooltip and a row in the skills panel - and those are exactly the cases where
	 * it matters which one was rewritten.
	 */
	private void report(Widget widget, String word, String translated)
	{
		if (!recordMissing)
		{
			return;
		}

		// Components are packed group-then-child, so the group is everything above the
		// lower sixteen bits. The full id is logged rather than a gameval constant so a
		// reader can match it against the dump above for any panel.
		int id = widget.getId();
		String key = id + ":" + word;

		if (reported.contains(key) || reported.size() > MAX_RECORDED)
		{
			return;
		}

		reported.add(key);

		log.info("Interface text translated: {} -> {} [{}:{}]", word, translated,
			id >>> 16, id & 0xFFFF);
	}

	/**
	 * Notes a string the table does not cover.
	 * <p>
	 * Only standalone words and short labels are worth keeping. A long string is dialogue
	 * or an entity name, which other parts of this plugin already deal with.
	 */
	private void recordMissing(String text)
	{
		if (!recordMissing || missing.contains(text))
		{
			return;
		}

		if (text.length() > MAX_RECORDED_LENGTH || text.indexOf('\n') >= 0)
		{
			return;
		}

		// Clock and FPS widgets write a fresh value every tick - "00:01:17" today,
		// "00:01:18" a second later. Recording them would fill the missing-set with
		// every timestamp the player ever sees, so they are dropped here.
		if (isClockText(text))
		{
			return;
		}

		missing.add(text);

		if (missing.size() <= MAX_RECORDED)
		{
			// Logged at debug, not info: this is a developer tool for filling the table,
			// and a widget whose text changes every tick (the clock, the FPS counter, the
			// inventory count) defeats the "once per distinct string" guard. The user has
			// to be at debug to read this anyway, so producing it at info would only
			// pollute the production log.
			log.debug("Interface text with no translation: {}", text);
		}
	}

	/**
	 * Whether the string looks like a clock readout - digits and colons only, with at
	 * least one colon. Matches "00:01:17", "12:34", "1:23:45".
	 */
	private static boolean isClockText(String text)
	{
		boolean seenColon = false;
		for (int i = 0; i < text.length(); i++)
		{
			char c = text.charAt(i);
			if (c == ':')
			{
				seenColon = true;
			}
			else if (!Character.isDigit(c))
			{
				return false;
			}
		}
		return seenColon;
	}

	/**
	 * Notes a translation the box had no room for.
	 * <p>
	 * These are recorded even without the setting turned on, because they are not missing
	 * translations but bad ones: the table has the word and the client cannot show it. They
	 * are also logged at debug level only, since a handful of them is expected rather than
	 * a problem.
	 */
	private void recordTooLong(String translated)
	{
		if (tooLong.size() < MAX_RECORDED_LENGTH && tooLong.add(translated))
		{
			log.debug("Interface text too wide for its box, left in English: {}", translated);
		}
	}

	/**
	 * Puts every remembered widget back the way the client had it.
	 */
	public void restore()
	{
		for (Map.Entry<Widget, Original> entry : remembered.entrySet())
		{
			Widget widget = entry.getKey();
			try
			{
				widget.setText(entry.getValue().text);
				widget.setFontId(entry.getValue().fontId);
			}
			catch (RuntimeException e)
			{
				// The widget was destroyed since it was translated. Nothing to put back.
				log.debug("Could not restore a translated widget", e);
			}
		}

		remembered.clear();
	}

	/**
	 * Forgets everything, restoring the game text first.
	 */
	public void clear()
	{
		restore();
		written = Collections.emptySet();
		missing.clear();
		tooLong.clear();
		ownText.clear();
		reported.clear();
		pair = null;
		dumpedIds.clear();
	}

	/**
	 * The number of widgets currently showing translated text.
	 */
	public int translatedCount()
	{
		return remembered.size();
	}

	/**
	 * Whether a widget is one the dialogue reader works from.
	 * <p>
	 * These hold the conversation text, the name of the NPC being spoken to, and the list
	 * of options the player picks from. All four are already translated by this plugin
	 * through its overlays, so translating the widget itself would put a second,
	 * unhoverable copy of the same translation on screen.
	 */
	private boolean isDialogueWidget(Widget widget)
	{
		return widget == client.getWidget(InterfaceID.ChatLeft.TEXT)
			|| widget == client.getWidget(InterfaceID.ChatLeft.NAME)
			|| widget == client.getWidget(InterfaceID.ChatRight.TEXT)
			|| widget == client.getWidget(InterfaceID.Chatmenu.OPTIONS);
	}

	/**
	 * The children of a widget.
	 * <p>
	 * A widget's children come from three places - the static ones laid out by the cache,
	 * the dynamic ones a script added, and the nested ones belonging to a child component
	 * such as a layer - and a subtree lives in whichever of them applies.
	 */
	private static List<Widget> childrenOf(Widget widget)
	{
		List<Widget> children = new ArrayList<>();
		addAll(children, widget.getStaticChildren());
		addAll(children, widget.getDynamicChildren());
		addAll(children, widget.getNestedChildren());
		return children;
	}

	private static void addAll(List<Widget> into, Widget[] children)
	{
		if (children == null)
		{
			return;
		}

		for (Widget child : children)
		{
			if (child != null)
			{
				into.add(child);
			}
		}
	}
}