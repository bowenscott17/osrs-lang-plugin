package com.osrslang.overlay;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;
import com.osrslang.translation.TranslationResult;

/**
 * Shows a tooltip with the translation of the game entity under the mouse.
 * <p>
 * The tooltip follows the mouse, so it reads "Giant Rat — Die Riesenratte" wherever the
 * cursor happens to be. It is drawn through RuneLite's own {@link TooltipManager}, the
 * same mechanism the rest of the client uses, so it is positioned and styled
 * consistently with every other tooltip.
 * <p>
 * When the dictionary has a German verb for the menu entry's action verb, the verb is
 * placed after the noun in the natural imperative form: "Die Riesenratte angreifen".
 * A verb the dictionary does not know is left out - the original menu option comes back
 * from the OSRS client in a code page that does not render reliably under RuneLite's
 * tooltip font, so showing it does more harm than good. Inventory items have no action
 * verb and are shown without one.
 * <p>
 * When the article of the translated noun is known it is put in front of the
 * translation, and only there: a learner who is past articles does not need a second
 * line restating which one was used and where it came from. Inventory items have no
 * article line either.
 * <p>
 * The overlay itself does no translation work: it only displays what the plugin has
 * already resolved. Nothing is shown when no provider has a translation for the
 * entity, rather than showing an empty or misleading tooltip.
 */
public class EntityTooltipOverlay extends Overlay
{
	/**
	 * Separates the original name from its translation.
	 */
	static final String SEPARATOR = " — ";

	private final TooltipManager tooltipManager;

	private final PanelComponent tooltipPanel = new PanelComponent();

	/**
	 * The English name of the entity under the mouse.
	 */
	private String original;

	/**
	 * The translation of that name.
	 */
	private String translation;

	/**
	 * The article of the translated noun, or {@code null} when none is known.
	 */
	private String article;

	/**
	 * Where {@link #article} came from, so a derived one can be shown as a hint.
	 */
	private String articleBasis;

	/**
	 * The German translation of the action verb, or {@code null} when none is known.
	 * <p>
	 * The untranslated action is not shown on the tooltip - the OSRS client returns
	 * menu option text in an encoding RuneLite's tooltip font does not render
	 * reliably, so showing it does more harm than good.
	 */
	private String actionTranslation;

	/**
	 * The stack size of the hovered item, or one for non-stackable or non-item
	 * entities. The translation itself does not depend on this number, so it is
	 * never mixed into the German noun: it is shown only as a suffix on the
	 * English side of the tooltip when it is greater than one, alongside the
	 * quantity the player can see in the inventory.
	 */
	private int quantity = 1;

	public EntityTooltipOverlay(TooltipManager tooltipManager)
	{
		this.tooltipManager = tooltipManager;

		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	/**
	 * Sets the entity currently under the mouse and its translation.
	 *
	 * @param original    the English name, or {@code null} to hide the tooltip
	 * @param translation the translated name, or {@code null} when none is available yet
	 */
	public void setEntity(@Nullable String original, @Nullable String translation)
	{
		setEntity(original, translation, null, null, null, 1);
	}

	/**
	 * Sets the entity under the mouse, its translation and the article of that
	 * translation.
	 *
	 * @param original     the English name, or {@code null} to hide the tooltip
	 * @param translation  the translated name, or {@code null} when none is available yet
	 * @param article      the article of the translated noun, or {@code null}
	 * @param articleBasis where the article came from, or {@code null}
	 */
	public void setEntity(@Nullable String original, @Nullable String translation,
		@Nullable String article, @Nullable String articleBasis)
	{
		setEntity(original, translation, article, articleBasis, null, 1);
	}

	/**
	 * Sets the entity under the mouse, its translation, the article of that translation,
	 * and the German translation of the menu entry's action verb.
	 * <p>
	 * The untranslated action verb itself is not set on the overlay: the OSRS client
	 * returns the menu option text in an encoding RuneLite's tooltip font does not
	 * render reliably, so showing it shows a broken umlaut rather than a real word.
	 *
	 * @param original          the English name, or {@code null} to hide the tooltip
	 * @param translation       the translated name, or {@code null} when none is available yet
	 * @param article           the article of the translated noun, or {@code null}
	 * @param articleBasis      where the article came from, or {@code null}
	 * @param actionTranslation the German translation of the action verb, or {@code null}
	 */
	public void setEntity(@Nullable String original, @Nullable String translation,
		@Nullable String article, @Nullable String articleBasis,
		@Nullable String actionTranslation)
	{
		setEntity(original, translation, article, articleBasis, actionTranslation, 1);
	}

	/**
	 * Sets the entity under the mouse, its translation, the article, the action
	 * translation, and the stack size for items.
	 * <p>
	 * The quantity is rendered as a suffix on the English side of the tooltip
	 * when greater than one. The translation is unchanged by the quantity - "Bronze
	 * sword" and "Bronze sword (×100)" translate to the same German noun, so a
	 * learner who looks away from the stack size still sees the right word.
	 *
	 * @param original          the English name, or {@code null} to hide the tooltip
	 * @param translation       the translated name, or {@code null} when none is available yet
	 * @param article           the article of the translated noun, or {@code null}
	 * @param articleBasis      where the article came from, or {@code null}
	 * @param actionTranslation the German translation of the action verb, or {@code null}
	 * @param quantity          the stack size (1 or higher for non-stackable or single items)
	 */
	public void setEntity(@Nullable String original, @Nullable String translation,
		@Nullable String article, @Nullable String articleBasis,
		@Nullable String actionTranslation, int quantity)
	{
		this.original = original;
		this.translation = translation;
		this.article = article;
		this.articleBasis = articleBasis;
		this.actionTranslation = actionTranslation;
		this.quantity = Math.max(1, quantity);
	}

	/**
	 * Hides the tooltip.
	 */
	public void clear()
	{
		original = null;
		translation = null;
		article = null;
		articleBasis = null;
		actionTranslation = null;
		quantity = 1;
	}

	/**
	 * The English name currently shown, if any.
	 */
	@Nullable
	public String getOriginal()
	{
		return original;
	}

	/**
	 * The translation currently shown, if any.
	 */
	@Nullable
	public String getTranslation()
	{
		return translation;
	}

	/**
	 * The article of the translated noun, if one is shown.
	 */
	@Nullable
	public String getArticle()
	{
		return article;
	}

	/**
	 * The German translation of the action verb, if one was found.
	 */
	@Nullable
	public String getActionTranslation()
	{
		return actionTranslation;
	}

	/**
	 * The stack size of the hovered item. One for non-stackable or non-item
	 * entities.
	 */
	public int getQuantity()
	{
		return quantity;
	}

	/**
	 * The headline line for the tooltip.
	 * <p>
	 * Two shapes are possible:
	 * <ul>
	 * <li>"Die Riesenratte angreifen" - the dictionary has both the noun and the
	 * action verb;</li>
	 * <li>"Giant Rat — Die Riesenratte" - either there is no action verb (items), or
	 * the dictionary does not have a German verb for it.</li>
	 * </ul>
	 * An action verb the dictionary does not translate is not shown at all: the original
	 * verb text comes from the OSRS client in an encoding RuneLite's tooltip font does
	 * not render reliably, so passing it through shows a learner a broken umlaut rather
	 * than a real word.
	 * <p>
	 * Items with a stack size above one carry a "× N" suffix on the English side, so
	 * a 100-strong pile of bronze swords reads "Bronze sword × 100 — Das Bronzeschwert".
	 * The German noun never carries the count: the translation is the same for any
	 * stack of the same item, and mixing the count into the noun would put a number in
	 * the middle of a German phrase.
	 */
	@Nullable
	public String getTooltipLine()
	{
		if (original == null)
		{
			return null;
		}

		String noun = translation == null
			? null
			: TranslationResult.withArticle(translation, article);

		if (noun == null)
		{
			return null;
		}

		String germanVerb = actionTranslation == null || actionTranslation.trim().isEmpty()
			? null
			: actionTranslation.trim();

		String leftSide = quantity > 1
			? original + " × " + quantity
			: original;

		if (germanVerb != null)
		{
			return noun + " " + germanVerb;
		}

		return leftSide + SEPARATOR + noun;
	}

	/**
	 * The article line, if an article is being shown.
	 * <p>
	 * An article that came from the dictionary is asserted; one that was derived is
	 * labelled, because a rule about word endings is a pattern to learn from rather than
	 * a guarantee.
	 */
	@Nullable
	public String getArticleLine()
	{
		if (article == null || article.trim().isEmpty())
		{
			return null;
		}

		String line = "Article: " + article.trim();

		if (articleBasis != null && !articleBasis.trim().isEmpty())
		{
			line += "  (from " + articleBasis.trim() + ")";
		}

		return line;
	}

	/**
	 * The tooltip's lines, in order.
	 * <p>
	 * Always exactly one line: the headline. The article is not repeated on a second
	 * line; once it is in front of the translation, a learner can read it there, and a
	 * second "Article: die (from the dictionary)" line did not earn its keep.
	 * <p>
	 * Exposed so the exact wording can be tested without a running client.
	 */
	public List<String> getTooltipLines()
	{
		List<String> lines = new ArrayList<>();

		String headline = getTooltipLine();
		if (headline != null)
		{
			lines.add(headline);
		}

		return lines;
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		List<String> lines = getTooltipLines();
		if (lines.isEmpty())
		{
			return null;
		}

		tooltipPanel.getChildren().clear();

		tooltipPanel.getChildren().add(LineComponent.builder()
			.left(lines.get(0))
			.leftColor(Color.WHITE)
			.build());

		tooltipManager.addFront(new Tooltip(tooltipPanel));

		return null;
	}
}
