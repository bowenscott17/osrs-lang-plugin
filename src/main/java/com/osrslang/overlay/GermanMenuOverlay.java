package com.osrslang.overlay;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.MenuEntry;
import net.runelite.api.Point;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.util.Text;

/**
 * Redraws the OSRS right-click menu entries in a font that contains German umlauts.
 * <p>
 * The OSRS client draws the right-click menu with a font that does not contain
 * German umlauts, so an entry such as "F&#xE4;llen Tree" appears as "F llen Tree"
 * with the umlaut rendered as a small placeholder box. The plugin cannot change
 * the OSRS font, but it can draw on top of the menu. This overlay finds the
 * menu, and for each entry whose option text contains an umlaut it draws the
 * option &#x2014; and its target, if any &#x2014; in a font that does contain the
 * umlaut, laid over the position OSRS drew the original. OSRS's own drawing
 * is left untouched.
 * <p>
 * Entries without umlauts are not touched, so the yellow hover colour, the
 * greying-out of disabled entries and the rest of OSRS's own menu styling are
 * left exactly as the client drew them.
 * <p>
 * Earlier versions of this overlay painted a dark rectangle over each row
 * before drawing the replacement text, on the theory that the rectangle would
 * match OSRS's own menu background exactly and hide any original umlaut
 * artefacts underneath. In practice the rectangle read as an extra dark slab
 * sitting on top of OSRS's menu, even when its colour was correct, because it
 * is a separate draw operation and a separate fill. The overlay now leaves
 * OSRS's own drawing alone and only lays the replacement text over it; if
 * OSRS's original text peeks out behind the new one, the user is seeing the
 * game's own broken umlauts rather than a second copy of the menu.
 */
@Slf4j
public class GermanMenuOverlay extends Overlay
{
	/**
	 * The height of a row in OSRS's right-click menu, in pixels.
	 * <p>
	 * OSRS's standard fixed client uses 14-pixel rows. The resized client may differ;
	 * the overlay reads the live menu's height instead of trusting this constant when
	 * it can.
	 */
	private static final int ROW_HEIGHT = 14;

	/**
	 * The height of the menu's header band ("Choose Option") plus its top/bottom padding.
	 * <p>
	 * {@code client.getMenu().getMenuY()} is the top of the header, not the top of the
	 * first row, so the first row's rectangle has to start {@code HEADER_HEIGHT} pixels
	 * below {@code menuY}. Verified empirically: a 2-entry menu reports
	 * {@code menuHeight=52}, a 3-entry menu reports 66, a 4-entry menu reports 80 — each
	 * extra row adds 14, leaving a constant 24 pixels of header. The fixed client always
	 * uses this value; a resized client is not covered here.
	 */
	private static final int HEADER_HEIGHT = 24;

	/**
	 * The x-offset from the menu's left edge to the option text.
	 */
	private static final int OPTION_INSET = 6;

	/**
	 * The y-offset from the row's top to the text baseline.
	 */
	private static final int TEXT_BASELINE = 11;

	/**
	 * The gap between the option text and the target text, in pixels.
	 */
	private static final int TARGET_GAP = 8;

	/**
	 * The dark-grey background colour OSRS uses for the right-click menu.
	 * <p>
	 * Used to fill a rectangle the exact size of each replacement string before drawing
	 * the string itself. The rectangle has to match OSRS's row colour for the cover not
	 * to read as a separate box - if the colour is wrong, every replacement string
	 * would be visible as a slightly different shade against the menu background.
	 */
	private static final Color MENU_BACKGROUND = new Color(62, 53, 44);

	/**
	 * The yellow colour OSRS uses for the hovered entry.
	 */
	private static final Color HOVER_COLOUR = new Color(255, 255, 0);

	/**
	 * German characters the OSRS menu font does not include.
	 */
	private static final Pattern HAS_UMLAUT = Pattern.compile("[äöüÄÖÜß]");

	private final Client client;

	/**
	 * Set to {@code true} once the overlay has been added to the manager and called back
	 * at least once, so a single debug line confirms the overlay is wired up.
	 */
	private boolean loggedThisSession;

	/**
	 * Set to {@code true} after the first redraw, so the diagnostic line is logged once
	 * per menu opening rather than on every frame.
	 */
	private boolean loggedThisMenu;

	public GermanMenuOverlay(Plugin plugin, Client client)
	{
		super(plugin);
		this.client = client;

		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ALWAYS_ON_TOP);

		log.debug("GermanMenuOverlay constructed");
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!loggedThisSession)
		{
			log.debug("GermanMenuOverlay render() called for the first time");
			loggedThisSession = true;
		}

		if (!client.isMenuOpen())
		{
			loggedThisMenu = false;
			return null;
		}

		MenuEntry[] entries = client.getMenu().getMenuEntries();
		if (entries == null || entries.length == 0)
		{
			return null;
		}

		int menuX = client.getMenu().getMenuX();
		int menuY = client.getMenu().getMenuY();
		int menuWidth = client.getMenu().getMenuWidth();

		// Measure the actual row height from the live menu. The fixed client uses 14px
		// rows below a 24px header, so menuHeight = HEADER_HEIGHT + N * rowHeight; any
		// resize would scale the rows but not the header, so subtracting the header
		// before dividing is the only way to land on the right number for both clients.
		// A flat menuHeight / entries.length dumps the header onto the first row and
		// places its text on the "Choose Option" band instead of row 0.
		int menuHeight = client.getMenu().getMenuHeight();
		int rowHeight = menuHeight > HEADER_HEIGHT && entries.length > 0
			? (menuHeight - HEADER_HEIGHT) / entries.length
			: ROW_HEIGHT;

		// Capture the canvas font before changing it - RuneLite sets a font on the
		// overlay canvas that other overlays downstream still want to read, and the
		// overlay manager is shared across every plugin this frame.
		Font previousFont = graphics.getFont();

		// RuneLite's overlay canvas font is the in-game RuneScape font, which does not
		// contain German umlauts - that is the actual reason the OSRS right-click menu
		// renders "ä" as a placeholder. Building an explicit Java logical font guarantees
		// the umlaut glyph is present, instead of falling back to the missing-glyph box.
		Font base = new Font(Font.DIALOG, Font.PLAIN, 12);
		FontMetrics metrics = graphics.getFontMetrics(base);

		// The OSRS menu turns yellow on the row the cursor is over; if the user has
		// hovered an entry that contains an umlaut, the replacement must also be yellow
		// or the entry will look unhovered even when the cursor is on it.
		int hoveredRow = hoveredRow(menuX, menuY, entries.length, rowHeight);

		if (!loggedThisMenu)
		{
			log.debug("GermanMenuOverlay redrawing menu at {},{} size {}x{}, {} entries (header {}px, row {}px)",
				menuX, menuY, menuWidth, menuHeight, entries.length, HEADER_HEIGHT, rowHeight);
			loggedThisMenu = true;
		}

		Color previousColor = graphics.getColor();

		for (int i = 0; i < entries.length; i++)
		{
			String rawOption = entries[i].getOption();
			if (rawOption == null || !HAS_UMLAUT.matcher(rawOption).find())
			{
				continue;
			}

			// The target may carry a Jagex colour tag such as "<col=fff>Maple tree";
			// drawing the raw string would render the literal tag characters on screen.
			// The overlay sets the colour itself, so the tag can be dropped before the
			// text is measured and drawn.
			String option = Text.removeTags(rawOption);
			String target = Text.removeTags(entries[i].getTarget());

			// menuY is the top of the header band, not the top of row 0 - skip the header
			// before laying out rows, otherwise the first row's text lands on the
			// "Choose Option" band and the rows underneath are missed entirely.
			int rowTop = menuY + HEADER_HEIGHT + i * rowHeight;
			int x = menuX + OPTION_INSET;
			int y = rowTop + TEXT_BASELINE;

			int optionWidth = metrics.stringWidth(option);
			int targetWidth = (target != null && !target.isEmpty()) ? metrics.stringWidth(target) : 0;

			// The replacement strings have to land exactly where OSRS drew the originals
			// to cover the broken umlauts underneath, but a row-wide rectangle sits on
			// top of the menu as a separate dark slab and reads as an extra box. Instead,
			// fill a rectangle the size of each string's footprint - same colour as the
			// menu background, sized to the ink that's about to land there - and then draw
			// the string on top. The rectangle is invisible because it blends into the
			// surrounding menu pixels, and only the area where new text appears gets
			// repainted.
			int ascent = metrics.getAscent();
			int descent = metrics.getDescent();
			int textHeight = ascent + descent;
			int textTop = y - ascent;

			graphics.setColor(MENU_BACKGROUND);
			graphics.fillRect(x, textTop, optionWidth, textHeight);
			if (targetWidth > 0)
			{
				graphics.fillRect(x + optionWidth + TARGET_GAP, textTop, targetWidth, textHeight);
			}

			graphics.setColor(i == hoveredRow ? HOVER_COLOUR : Color.WHITE);
			graphics.setFont(base);
			graphics.drawString(option, x, y);

			if (targetWidth > 0)
			{
				graphics.drawString(target, x + optionWidth + TARGET_GAP, y);
			}
		}

		graphics.setColor(previousColor);
		graphics.setFont(previousFont);
		return null;
	}

	/**
	 * The row index the cursor is over, or {@code -1} when the cursor is not inside
	 * the menu.
	 */
	private int hoveredRow(int menuX, int menuY, int numEntries, int rowHeight)
	{
		Point mouse = client.getMouseCanvasPosition();
		if (mouse == null)
		{
			return -1;
		}

		int menuWidth = client.getMenu().getMenuWidth();
		if (mouse.getX() < menuX || mouse.getX() >= menuX + menuWidth)
		{
			return -1;
		}

		int menuHeight = client.getMenu().getMenuHeight();
		if (mouse.getY() < menuY || mouse.getY() >= menuY + menuHeight)
		{
			return -1;
		}

		// The rows live below the header, so the row index is the offset past the
		// header band divided by the row height - matching where the replacement text
		// was drawn in render().
		int row = (mouse.getY() - menuY - HEADER_HEIGHT) / rowHeight;
		return row >= 0 && row < numEntries ? row : -1;
	}
}
