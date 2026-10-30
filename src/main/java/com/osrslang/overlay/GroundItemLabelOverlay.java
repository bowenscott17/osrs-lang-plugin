package com.osrslang.overlay;

import com.osrslang.text.GameText;
import com.osrslang.text.HoverTarget;
import com.osrslang.text.HoverTarget.Kind;
import com.osrslang.translation.HoverTranslationService;
import com.osrslang.translation.Language;
import com.osrslang.translation.TranslationResult;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Constants;
import net.runelite.api.ItemComposition;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.TileItem;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Draws the translated name of every ground item in the scene as a label hovering above
 * its sprite, so the player sees the German name without having to hover anything.
 * <p>
 * The same translation pipeline as the hover tooltip is used - the German article
 * attached to a noun is taken from the dictionary entry, the German noun table, or the
 * translation itself, in that order - so what is drawn here matches what the tooltip
 * would show for the same item. Translations already produced are reused, so opening the
 * panel of an item whose translation has been fetched by hovering does not re-fetch it.
 * <p>
 * Nothing in the game is touched. The overlay sits above the scene but below widgets,
 * and only paints text; no menu entry is added, removed or modified, and left- and
 * right-click behaviour on the item is exactly what it would be without the plugin.
 * The label is dropped for items whose translation has not yet arrived and for items
 * that have no translation at all, so the screen is never filled with "..." placeholders
 * or empty strings.
 * <p>
 * The cost is paid every frame: every tile in the scene is walked, the item's name is
 * looked up, and the cached translation is drawn. On a screen with hundreds of items
 * the draw cost is noticeable, so the overlay is opt-in.
 */
@Slf4j
public class GroundItemLabelOverlay extends Overlay
{
	/**
	 * The placeholder name the client reports for an item it has no real name for.
	 */
	private static final String PLACEHOLDER_NAME = "null";

	/**
	 * The colour of the label and its drop shadow, matching the white-with-black-shadow
	 * style used by interface text elsewhere in the plugin.
	 */
	private static final Color SHADOW = Color.BLACK;
	private static final Color TEXT = Color.WHITE;
	private static final int SHADOW_OFFSET = 1;

	/**
	 * How far above the tile's screen position the label is drawn, in pixels.
	 * <p>
	 * The tile's canvas point is the centre of the tile in world space; ground item
	 * sprites hang from that point, so a label a few pixels above the centre sits over
	 * the top of the sprite rather than across its face.
	 */
	private static final int LABEL_VERTICAL_OFFSET = 18;

	private final Client client;
	private final HoverTranslationService hoverService;

	private Language from = Language.ENGLISH;
	private Language to = Language.GERMAN;
	private boolean enabled;

	/**
	 * Whether the developer has already seen this overlay report its layout, so the
	 * diagnostic line is logged once per session rather than on every frame.
	 */
	private boolean loggedLayout;

	@Inject
	public GroundItemLabelOverlay(Plugin plugin, Client client, HoverTranslationService hoverService)
	{
		super(plugin);
		this.client = client;
		this.hoverService = hoverService;

		// The position is set per-frame from the canvas point of each item, so a static
		// position would be wrong; DYNAMIC hands the overlay the full canvas.
		setPosition(OverlayPosition.DYNAMIC);

		// Above the scene itself, so the items are visible underneath, but below widgets
		// so chat, inventory and the rest of the interface are not painted over.
		setLayer(OverlayLayer.ABOVE_SCENE);
		setPriority(PRIORITY_LOW);
	}

	/**
	 * Points the overlay at a language pair, and says whether it should draw at all.
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
		if (!enabled || from == to)
		{
			return null;
		}

		Scene scene = client.getScene();
		if (scene == null)
		{
			return null;
		}

		Tile[][][] tiles = scene.getTiles();
		if (tiles == null)
		{
			return null;
		}

		int plane = client.getPlane();
		if (plane < 0 || plane >= tiles.length)
		{
			return null;
		}

		Font base = graphics.getFont();
		if (base == null)
		{
			return null;
		}

		FontMetrics metrics = graphics.getFontMetrics(base);
		Color previousColor = graphics.getColor();

		try
		{
			for (int x = 0; x < Constants.SCENE_SIZE; x++)
			{
				for (int y = 0; y < Constants.SCENE_SIZE; y++)
				{
					Tile tile = tiles[plane][x][y];
					if (tile == null)
					{
						continue;
					}

					List<TileItem> items = tile.getGroundItems();
					if (items == null || items.isEmpty())
					{
						continue;
					}

					LocalPoint localPoint = tile.getLocalLocation();
					if (localPoint == null)
					{
						continue;
					}

					Point canvasPoint = Perspective.localToCanvas(client, localPoint, plane);
					if (canvasPoint == null)
					{
						continue;
					}

					for (TileItem item : items)
					{
						drawLabel(graphics, metrics, item, canvasPoint);
					}
				}
			}
		}
		finally
		{
			graphics.setColor(previousColor);
		}

		if (!loggedLayout)
		{
			log.debug("Ground item label overlay active, plane={}, font={}, from={}, to={}",
				plane, base.getFontName(), from, to);
			loggedLayout = true;
		}

		return null;
	}

	/**
	 * Draws the label for one item, if a translation is available for it.
	 */
	private void drawLabel(Graphics2D graphics, FontMetrics metrics, TileItem item, Point canvasPoint)
	{
		int itemId = item.getId();
		if (itemId <= 0)
		{
			return;
		}

		String englishName = itemName(itemId);
		if (englishName.isEmpty())
		{
			return;
		}

		HoverTarget target = new HoverTarget(Kind.ITEM, englishName, GameText.Type.ITEM_NAME, itemId, null);
		CompletableFuture<Optional<TranslationResult>> future = hoverService.request(target, from, to);
		if (!future.isDone())
		{
			return;
		}

		Optional<TranslationResult> resolved = future.getNow(Optional.empty());
		if (!resolved.isPresent())
		{
			return;
		}

		String label = label(resolved.get());
		if (label.isEmpty())
		{
			return;
		}

		int quantity = item.getQuantity();
		if (quantity > 1)
		{
			label = label + " (" + quantity + ")";
		}

		drawString(graphics, metrics, label, canvasPoint);
	}

	/**
	 * The German text to draw for an item, with its article attached when one is known.
	 * <p>
	 * The article is lowercased so the label reads as a German noun phrase rather than
	 * the start of a sentence, which is what an item standing alone on the ground is.
	 */
	private static String label(TranslationResult result)
	{
		String translation = result.getTranslatedText();
		if (translation == null || translation.isEmpty())
		{
			return "";
		}

		Optional<String> article = result.getArticle();
		if (article.isPresent() && !article.get().isEmpty())
		{
			return article.get() + " " + translation;
		}
		return translation;
	}

	/**
	 * Draws {@code text} centred above {@code canvasPoint}, with a one-pixel black
	 * shadow dropped to the bottom-right so it stays legible against any background.
	 */
	private static void drawString(Graphics2D graphics, FontMetrics metrics, String text, Point canvasPoint)
	{
		int textWidth = metrics.stringWidth(text);
		int textX = canvasPoint.getX() - textWidth / 2;
		int textY = canvasPoint.getY() - LABEL_VERTICAL_OFFSET;

		graphics.setColor(SHADOW);
		graphics.drawString(text, textX + SHADOW_OFFSET, textY + SHADOW_OFFSET);
		graphics.setColor(TEXT);
		graphics.drawString(text, textX, textY);
	}

	/**
	 * The display name of an item, or an empty string when the client has no name for it.
	 */
	private String itemName(int itemId)
	{
		try
		{
			ItemComposition composition = client.getItemDefinition(itemId);
			if (composition == null)
			{
				return "";
			}
			String name = composition.getName();
			if (name == null || name.isEmpty() || PLACEHOLDER_NAME.equalsIgnoreCase(name))
			{
				return "";
			}
			return name;
		}
		catch (RuntimeException e)
		{
			log.debug("Could not read definition of ground item {}", itemId, e);
			return "";
		}
	}
}
