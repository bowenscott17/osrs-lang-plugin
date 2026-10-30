package com.osrslang.overlay;

import com.osrslang.text.InterfaceStringDatabase;
import com.osrslang.text.SpellbookSpells;
import com.osrslang.translation.Language;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Draws the translated spell names over the icons of the modern spellbook.
 * <p>
 * The spellbook spell slots are drawn by the client as TYPE 5 widgets (sprite plus
 * text), but the visible label that sits inside the icon is part of the cached sprite
 * image rather than a separately settable widget text. {@link
 * com.osrslang.text.InterfaceTextTranslator} can only rewrite text it can read, so
 * those labels stay in English even though the dict has every spell name. The
 * tooltip ("Level 1: Wind Strike") is a real widget and is translated; the always-on
 * label next to the icon is not.
 * <p>
 * The overlay fills the gap. For each spell slot whose name is in the dict, the
 * German is drawn over the slot's bottom third, where the client painted the English.
 * A drop shadow masks any stray English pixels, so the cover is just text rather than
 * a rectangle that would hide the icon. The slot widget itself is never written or
 * moved, so the click target keeps the area the client gave it.
 */
@Slf4j
public class SpellbookLabelOverlay extends Overlay
{
	/**
	 * The spellbook spells, in the order the slots are arranged on the panel.
	 * <p>
	 * The list is shared with the tooltip translator, which rewrites the action of the
	 * same slots so the client's own hover tooltip shows German.
	 */
	private static final List<SpellbookSpells.Spell> SPELLS = SpellbookSpells.SPELLS;

	/**
	 * The colour of the German label, and the shadow it is drawn with.
	 * <p>
	 * Both are the game's own: interface text is white with a black shadow dropped
	 * one pixel down and right, and drawing it that way keeps the translation looking
	 * like part of the game rather than pasted on top of it.
	 */
	private static final Color TEXT = Color.WHITE;
	private static final Color SHADOW = Color.BLACK;

	/**
	 * How far the shadow sits from the text, in pixels.
	 */
	private static final int SHADOW_OFFSET = 1;

	/**
	 * The smallest the label is shrunk to before the slot is left in English.
	 * Below this the words stop being readable, which is worse than not translating
	 * them at all.
	 */
	private static final int MIN_FONT_SIZE = 6;

	/**
	 * The label is drawn across the lower third of the icon - the same vertical band
	 * the client uses for the cached label. Pulled in from the slot's left and right
	 * so the German stays clear of the icon's own edge pixels.
	 */
	private static final int LABEL_TOP_FRACTION_NUM = 2;
	private static final int LABEL_TOP_FRACTION_DEN = 3;
	private static final int LABEL_SIDE_INSET = 2;

	private final Client client;
	private final InterfaceStringDatabase database;

	/**
	 * Fonts already built this session, by size. Every slot asks for the same
	 * handful of sizes, and this runs on every frame, so building them once keeps
	 * the overlay from allocating its way through a set of identical fonts.
	 */
	private final Map<Integer, Font> fonts = new HashMap<>();

	private Language from = Language.ENGLISH;
	private Language to = Language.GERMAN;
	private boolean enabled;

	public SpellbookLabelOverlay(Plugin plugin, Client client, InterfaceStringDatabase database)
	{
		super(plugin);
		this.client = client;
		this.database = database;

		// The spellbook's own bounds, so the labels can be drawn anywhere on screen
		// rather than only where RuneLite would place an overlay.
		setPosition(OverlayPosition.DYNAMIC);

		// Above the panel itself, or every name drawn here would be painted over by it.
		setLayer(OverlayLayer.ALWAYS_ON_TOP);
		setPriority(PRIORITY_HIGH);
	}

	/**
	 * Points the overlay at a language pair, and says whether it should draw at all.
	 * <p>
	 * Turning it off draws nothing, which leaves the client's own English on screen -
	 * nothing has to be put back, because nothing was ever taken away.
	 */
	public void setTranslation(boolean enabled, Language from, Language to)
	{
		this.enabled = enabled;
		this.from = from;
		this.to = to;
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!enabled)
		{
			return null;
		}

		// The spellbook is only on screen while the Magic tab of the sidebar is open and
		// the modern spellbook is selected. Its root widget is what the spell list lives
		// inside, so checking it covers both "the tab is open" and "the right book is up".
		Widget root = client.getWidget(InterfaceID.MagicSpellbook.UNIVERSE);
		if (root == null || root.isHidden())
		{
			return null;
		}

		// RuneLite sets a font before calling an overlay; this is the game's own interface
		// font, so the labels match the spellbook they are drawn over. It is put back at
		// the end, because the frame is shared with every other overlay.
		Font base = graphics.getFont();
		if (base == null)
		{
			return null;
		}
		FontMetrics baseMetrics = graphics.getFontMetrics(base);

		Color previousColor = graphics.getColor();

		for (SpellbookSpells.Spell spell : SPELLS)
		{
			Widget slot = client.getWidget(spell.getWidgetId());
			if (slot == null || slot.isHidden())
			{
				continue;
			}

			// Each slot's canvas-space position comes from the widget itself - the
			// script-driven originalX/originalY values are written every time the
			// spellbook is laid out, so they reflect whatever the player currently sees.
			Point slotOrigin = slot.getCanvasLocation();
			if (slotOrigin == null)
			{
				continue;
			}
			int slotWidth = slot.getWidth();
			int slotHeight = slot.getHeight();
			if (slotWidth <= 0 || slotHeight <= 0)
			{
				continue;
			}

			Optional<String> translated = database.lookup(spell.getEnglishName(), from, to);
			if (!translated.isPresent())
			{
				continue;
			}

			draw(graphics, base, baseMetrics, slotOrigin, slotWidth, slotHeight, translated.get());
		}

		graphics.setColor(previousColor);
		graphics.setFont(base);

		return null;
	}

	/**
	 * Covers one slot's English label and draws the German over it.
	 * <p>
	 * A slot whose label will not fit at any readable size is left entirely alone: the
	 * cover is only drawn once there is a translation going on top of it, so a slot
	 * that is left in English shows the client's own label rather than a blank space.
	 */
	private void draw(Graphics2D graphics, Font base, FontMetrics baseMetrics,
		Point origin, int slotWidth, int slotHeight, String text)
	{
		int labelTop = origin.getY() + (slotHeight * LABEL_TOP_FRACTION_NUM) / LABEL_TOP_FRACTION_DEN;
		int labelBottom = origin.getY() + slotHeight;
		int labelHeight = labelBottom - labelTop;
		if (labelHeight <= 0)
		{
			return;
		}

		int availableWidth = slotWidth - 2 * LABEL_SIDE_INSET;

		Font font = fitting(base, graphics, baseMetrics, text, availableWidth, labelHeight);
		if (font == null)
		{
			// Cannot fit at any size we are willing to use - leave the English visible
			// rather than draw a clipped label that is harder to read than the original.
			return;
		}

		FontMetrics metrics = graphics.getFontMetrics(font);
		int baseline = labelTop + (labelHeight - metrics.getHeight()) / 2 + metrics.getAscent();
		int textX = origin.getX() + (slotWidth - metrics.stringWidth(text)) / 2;

		graphics.setFont(font);
		graphics.setColor(SHADOW);
		graphics.drawString(text, textX + SHADOW_OFFSET, baseline + SHADOW_OFFSET);
		graphics.setColor(TEXT);
		graphics.drawString(text, textX, baseline);
	}

	/**
	 * The font the label will be drawn in, which is the overlay's own unless the
	 * German is too wide for the space the English had.
	 * <p>
	 * One proportional step rather than a search: the slot width is fixed, so the only
	 * question is how much wider the German is, and dividing by that answers it. If
	 * even that leaves it too wide the slot keeps its English - a label cut off
	 * mid-word helps nobody.
	 *
	 * @return the font to draw with, or {@code null} when the label cannot be shown
	 */
	private Font fitting(Font base, Graphics2D graphics, FontMetrics baseMetrics,
		String text, int width, int height)
	{
		int measured = baseMetrics.stringWidth(text);
		if (measured <= width && base.getSize() <= height)
		{
			return base;
		}

		int size;
		if (measured > width)
		{
			size = Math.max(MIN_FONT_SIZE, base.getSize() * width / measured);
		}
		else
		{
			size = Math.max(MIN_FONT_SIZE, Math.min(base.getSize(), height));
		}
		if (size >= base.getSize())
		{
			return null;
		}

		Font smaller = font(base, size);
		return graphics.getFontMetrics(smaller).stringWidth(text) <= width ? smaller : null;
	}

	/**
	 * The overlay's own font at a given size, built once per size rather than per frame.
	 */
	private Font font(Font base, int size)
	{
		return fonts.computeIfAbsent(size, s -> new Font(base.getName(), base.getStyle(), s));
	}
}
