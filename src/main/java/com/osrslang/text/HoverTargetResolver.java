package com.osrslang.text;

import java.util.Optional;
import javax.annotation.Nullable;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Point;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.util.Text;
import com.osrslang.text.GameText.Type;
import com.osrslang.text.HoverTarget.Kind;

/**
 * Works out what the mouse is currently hovering over.
 * <p>
 * Two mechanisms are used, because the game exposes the two situations differently:
 * <ul>
 * <li><b>Entities in the world</b> - NPCs, ground items and game objects - are found
 * through the menu entries the client builds for whatever the cursor is over. The
 * client rebuilds them as the cursor moves, and the last entry is the one for the
 * entity directly under the cursor. RuneLite's own plugins use this same mechanism to
 * detect hover targets.</li>
 * <li><b>Items in the interface</b> - the inventory and worn equipment - are found by
 * hit-testing the mouse position against the item widgets, because an interface item
 * carries no world position.</li>
 * </ul>
 * The important detail in both cases is that the entity's own name is what gets
 * translated. The client's menu entry splits the text into an "option" (the action,
 * such as "Attack") and a "target" (the entity, such as "Giant Rat"); it is the target
 * that is wanted, and any leading action word is stripped if it is still attached.
 * <p>
 * Nothing here sends anything to the server: the menu entries are only read, never
 * added, removed or clicked, so left-click and right-click behaviour is untouched.
 */
@Slf4j
public class HoverTargetResolver
{
	/**
	 * The name the client reports for an item it has no real name for.
	 */
	private static final String PLACEHOLDER_NAME = "null";

	/**
	 * The equipment slots that can hold an item the player is wearing.
	 * <p>
	 * These are the worn-items interface's slot components. Each slot widget holds the
	 * item in its second child.
	 */
	private static final int[] EQUIPMENT_SLOTS = {
		InterfaceID.Wornitems.SLOT0,
		InterfaceID.Wornitems.SLOT1,
		InterfaceID.Wornitems.SLOT2,
		InterfaceID.Wornitems.SLOT3,
		InterfaceID.Wornitems.SLOT4,
		InterfaceID.Wornitems.SLOT5,
		InterfaceID.Wornitems.SLOT7,
		InterfaceID.Wornitems.SLOT9,
		InterfaceID.Wornitems.SLOT10,
		InterfaceID.Wornitems.SLOT12,
		InterfaceID.Wornitems.SLOT13,
	};

	/**
	 * The widget containers whose dynamic children are item slots in inventory-style
	 * interfaces opened on top of the player's inventory.
	 * <p>
	 * Each entry is the id of a container widget whose dynamic children are item slots:
	 * the bank rows, the shop's stock, the player's sell side in a shop, and the two
	 * offer sides of a player-to-player trade. The mouse is tested against each slot
	 * in turn, so the cursor only ever lands on the topmost open interface that
	 * contains it.
	 * <p>
	 * The constants come from {@link InterfaceID}, the gameval widget ids RuneLite
	 * publishes with each release. They are looked up through
	 * {@code client.getWidget} - a missing widget means the interface is not open,
	 * and the container is silently skipped, so any one of these returning a hidden
	 * widget is the normal "this window is closed" case rather than an error.
	 */
	private static final int[] ITEM_CONTAINER_WIDGETS = {
		InterfaceID.Bankmain.ITEMS,
		InterfaceID.Shopmain.ITEMS,
		InterfaceID.Shopside.ITEMS,
		InterfaceID.Trademain.YOUR_OFFER,
		InterfaceID.Trademain.OTHER_OFFER,
	};

	private final Client client;

	@Inject
	public HoverTargetResolver(Client client)
	{
		this.client = client;
	}

	/**
	 * The last resolved target, so the caller can tell whether the hover actually
	 * changed. Comparing the previous value avoids re-requesting a translation for the
	 * same entity on every single frame.
	 */
	private HoverTarget last;

	/**
	 * Finds the entity under the mouse, if any.
	 * <p>
	 * This must be called on the client thread.
	 *
	 * @return the hovered entity, or empty if the cursor is not over a translatable
	 *         entity
	 */
	public Optional<HoverTarget> resolve()
	{
		Optional<HoverTarget> target = resolveInterfaceItem();
		if (!target.isPresent())
		{
			target = resolveWorldEntity();
		}

		HoverTarget previous = last;
		last = target.orElse(null);

		// Only log when the hovered entity actually changes, so a static cursor does
		// not fill the log with the same line every tick.
		if (last != null && !last.equals(previous))
		{
			log.debug("Hovering over {} '{}'", last.getKind(), last.getName());
		}

		return target;
	}

	/**
	 * The target resolved by the most recent call to {@link #resolve()}, if any.
	 */
	@Nullable
	public HoverTarget getLast()
	{
		return last;
	}

	/**
	 * Whether the given target is the one currently under the mouse.
	 * <p>
	 * Used to avoid asking for the same translation again on every frame.
	 */
	public boolean isCurrent(HoverTarget target)
	{
		return target != null && target.equals(last);
	}

	/**
	 * Resolves the entity under the mouse from the client's menu entries.
	 */
	private Optional<HoverTarget> resolveWorldEntity()
	{
		MenuEntry[] entries = client.getMenu().getMenuEntries();
		if (entries == null || entries.length == 0)
		{
			return Optional.empty();
		}

		// The last entry is the one for the entity directly under the cursor.
		MenuEntry entry = entries[entries.length - 1];
		if (entry == null || entry.getType() == null)
		{
			return Optional.empty();
		}

		switch (entry.getType())
		{
			case NPC_FIRST_OPTION:
			case NPC_SECOND_OPTION:
			case NPC_THIRD_OPTION:
			case NPC_FOURTH_OPTION:
			case NPC_FIFTH_OPTION:
			case WIDGET_TARGET_ON_NPC:
			case EXAMINE_NPC:
				return resolveNpc(entry);
			case GROUND_ITEM_FIRST_OPTION:
			case GROUND_ITEM_SECOND_OPTION:
			case GROUND_ITEM_THIRD_OPTION:
			case GROUND_ITEM_FOURTH_OPTION:
			case GROUND_ITEM_FIFTH_OPTION:
			case WIDGET_TARGET_ON_GROUND_ITEM:
			case EXAMINE_ITEM_GROUND:
				return resolveGroundItem(entry);
			case GAME_OBJECT_FIRST_OPTION:
			case GAME_OBJECT_SECOND_OPTION:
			case GAME_OBJECT_THIRD_OPTION:
			case GAME_OBJECT_FOURTH_OPTION:
			case GAME_OBJECT_FIFTH_OPTION:
			case WIDGET_TARGET_ON_GAME_OBJECT:
			case EXAMINE_OBJECT:
				return resolveGameObject(entry);
			default:
				return Optional.empty();
		}
	}

	/**
	 * Resolves the name of the NPC an entry points at.
	 */
	private Optional<HoverTarget> resolveNpc(MenuEntry entry)
	{
		String action = action(entry);

		NPC npc = entry.getNpc();
		if (npc != null)
		{
			String name = clean(npc.getName());
			if (!name.isEmpty())
			{
				return Optional.of(new HoverTarget(Kind.NPC, name, Type.NPC_NAME, npc.getId(), action));
			}
		}

		// The NPC may have despawned by the time we look, so fall back to the name the
		// client recorded on the entry itself.
		String name = targetName(entry);
		if (!name.isEmpty())
		{
			return Optional.of(new HoverTarget(Kind.NPC, name, Type.NPC_NAME, entry.getIdentifier(), action));
		}

		return Optional.empty();
	}

	/**
	 * Resolves the name of a ground item an entry points at.
	 * <p>
	 * The item id is the entry's identifier, and the name comes from the item
	 * definition, which is the authoritative source for the item's display name.
	 * The composition name has no quantity on it - OSRS renders the count on the
	 * item icon, not in the menu's target string - so the primary path is already
	 * quantity-free.
	 * <p>
	 * The fallback uses the menu entry's target, which the client occasionally
	 * builds with a trailing count like "Bronze sword (100)" for stacked items
	 * whose composition is unavailable. That string would not match any
	 * dictionary entry, so the count has to be stripped before the name is handed
	 * to the translation pipeline.
	 */
	private Optional<HoverTarget> resolveGroundItem(MenuEntry entry)
	{
		int itemId = entry.getIdentifier();
		if (itemId <= 0)
		{
			return Optional.empty();
		}

		String action = action(entry);

		// Prefer the name from the item definition, which is what the game shows.
		// resolveItemId / itemName walk past noted variants to the base item so the
		// same German noun comes back whether the stack is noted or not.
		ResolvedItem resolved = resolveItem(itemId);
		String name = resolved.name;
		if (name.isEmpty())
		{
			name = stripQuantity(targetName(entry));
		}

		if (name.isEmpty())
		{
			return Optional.empty();
		}

		return Optional.of(new HoverTarget(Kind.ITEM, name, Type.ITEM_NAME, resolved.id, action));
	}

	/**
	 * Removes a trailing quantity in parentheses from a name, so the translation
	 * lookup sees "Bronze sword" rather than "Bronze sword (100)".
	 * <p>
	 * The shape recognised is a parenthesised integer at the very end of the
	 * string, optionally with an "x" before the count ("Sword x 12"). Strings
	 * that do not match the shape are returned unchanged, so a name like "Dwarven
	 * stout" with no count is not mangled.
	 */
	private static String stripQuantity(String name)
	{
		if (name == null || name.isEmpty())
		{
			return name;
		}

		String trimmed = name.trim();
		if (!trimmed.endsWith(")"))
		{
			return trimmed;
		}

		int open = trimmed.lastIndexOf(" (");
		if (open <= 0)
		{
			return trimmed;
		}

		String inside = trimmed.substring(open + 2, trimmed.length() - 1).trim();
		if (!isQuantity(inside))
		{
			return trimmed;
		}

		return trimmed.substring(0, open).trim();
	}

	/**
	 * Whether the string inside a trailing "(...)" is a count rather than a
	 * parenthetical that should be kept ("Abyssal whip (drain)" for example).
	 */
	private static boolean isQuantity(String inside)
	{
		if (inside.isEmpty())
		{
			return false;
		}

		int start = 0;
		if (inside.charAt(0) == 'x' || inside.charAt(0) == 'X')
		{
			start = 1;
		}

		if (start >= inside.length())
		{
			return false;
		}

		for (int i = start; i < inside.length(); i++)
		{
			if (!Character.isDigit(inside.charAt(i)))
			{
				return false;
			}
		}

		return true;
	}

	/**
	 * Resolves the name of a game object an entry points at.
	 */
	private Optional<HoverTarget> resolveGameObject(MenuEntry entry)
	{
		int objectId = entry.getIdentifier();

		// A multiloc can report a placeholder id, so the impostor is followed to the
		// real object before its name is read.
		ObjectComposition composition = client.getObjectDefinition(objectId);
		if (composition != null && composition.getImpostorIds() != null)
		{
			ObjectComposition impostor = composition.getImpostor();
			if (impostor != null)
			{
				composition = impostor;
				objectId = composition.getId();
			}
		}

		String action = action(entry);

		if (composition != null)
		{
			String name = clean(composition.getName());
			if (!name.isEmpty())
			{
				return Optional.of(new HoverTarget(Kind.OBJECT, name, Type.OBJECT_NAME, objectId, action));
			}
		}

		String name = targetName(entry);
		if (!name.isEmpty())
		{
			return Optional.of(new HoverTarget(Kind.OBJECT, name, Type.OBJECT_NAME, objectId, action));
		}

		return Optional.empty();
	}

	/**
	 * The action verb the menu entry carries, normalised.
	 * <p>
	 * "Walk here" and other movement actions are dropped because there is no entity
	 * behind them. Everything else is returned cleaned, or {@code null} when the entry
	 * has no option.
	 */
	private static String action(MenuEntry entry)
	{
		String option = clean(entry.getOption());
		if (option.isEmpty())
		{
			return null;
		}

		String lower = option.toLowerCase();
		if (lower.equals("walk here") || lower.equals("cancel"))
		{
			return null;
		}

		return option;
	}

	/**
	 * Resolves an item in the inventory, the worn equipment slots, or any of the
	 * inventory-style interfaces that show item grids - the bank, shops, player trade
	 * and NPC exchange windows.
	 * <p>
	 * These have no world position, so the mouse position is tested against the item
	 * widgets directly. The bank, shop and trade windows are checked first because they
	 * are opened on top of the inventory: the mouse can be over a bank slot, a shop
	 * stock item or a player's offer, and only the topmost window containing the cursor
	 * is returned.
	 */
	private Optional<HoverTarget> resolveInterfaceItem()
	{
		Point mouse = client.getMouseCanvasPosition();
		if (mouse == null)
		{
			return Optional.empty();
		}

		// Inventory-style interfaces opened on top of the player's inventory. Each is a
		// widget whose dynamic children are item slots; the slot widget itself carries
		// the item id and the stack size on it, so the hover target can be built from
		// one widget lookup per slot.
		for (int containerId : ITEM_CONTAINER_WIDGETS)
		{
			HoverTarget target = itemInContainer(containerId, mouse);
			if (target != null)
			{
				return Optional.of(target);
			}
		}

		// The worn equipment slots each hold one item, in a fixed child of the slot.
		for (int slot : EQUIPMENT_SLOTS)
		{
			Widget slotWidget = client.getWidget(slot);
			if (slotWidget == null || slotWidget.isHidden())
			{
				continue;
			}

			// The slot is a container; the item is its second child.
			Widget item = slotWidget.getChild(1);
			if (item == null || item.getItemId() <= 0)
			{
				continue;
			}

			// The slot widget is the clickable region, so the mouse is tested against
			// it rather than against the item child.
			if (!contains(slotWidget, mouse))
			{
				continue;
			}

			String name = itemName(item.getItemId());
			if (!name.isEmpty())
			{
				int resolvedId = resolveItemId(item.getItemId());
				return Optional.of(new HoverTarget(Kind.ITEM, name, Type.ITEM_NAME, resolvedId));
			}
		}

		// The inventory holds one item per slot, and the slot widget itself is the
		// item.
		Widget inventory = client.getWidget(InterfaceID.Inventory.ITEMS);
		if (inventory == null || inventory.isHidden())
		{
			return Optional.empty();
		}

		Widget[] items = inventory.getDynamicChildren();
		if (items == null)
		{
			return Optional.empty();
		}

		for (Widget item : items)
		{
			HoverTarget target = itemUnderMouse(item, mouse);
			if (target != null)
			{
				return Optional.of(target);
			}
		}

		return Optional.empty();
	}

	/**
	 * Finds the item under the mouse in a single widget container.
	 * <p>
	 * The container's dynamic children are the item slots for the interface - bank
	 * rows, shop stock rows, trade offer rows, NPC exchange rows. Each slot widget
	 * exposes its item id and stack size directly, so a single child lookup is enough
	 * to build a hover target without reading the surrounding slot widgets.
	 *
	 * @return the hovered item in this container, or {@code null} when the cursor is
	 *         not over a populated slot
	 */
	@Nullable
	private HoverTarget itemInContainer(int containerId, Point mouse)
	{
		Widget container = client.getWidget(containerId);
		if (container == null || container.isHidden())
		{
			return null;
		}

		Widget[] slots = container.getDynamicChildren();
		if (slots == null)
		{
			return null;
		}

		for (Widget slot : slots)
		{
			if (slot == null || !contains(slot, mouse))
			{
				continue;
			}

			int itemId = slot.getItemId();
			if (itemId <= 0)
			{
				continue;
			}

			// A widget whose itemQuantity() is zero is an empty slot the client has
			// not yet cleared of its old itemId, so it is treated as not an item.
			if (slot.getItemQuantity() <= 0)
			{
				continue;
			}

			ResolvedItem resolved = resolveItem(itemId);
			if (resolved.name.isEmpty())
			{
				continue;
			}

			return new HoverTarget(Kind.ITEM, resolved.name, Type.ITEM_NAME, resolved.id, null, slot.getItemQuantity());
		}

		return null;
	}

	/**
	 * Builds a hover target for an item widget, if the mouse is over it.
	 */
	@Nullable
	private HoverTarget itemUnderMouse(Widget item, Point mouse)
	{
		if (item == null)
		{
			return null;
		}

		int itemId = item.getItemId();
		if (itemId <= 0)
		{
			return null;
		}

		// A widget whose itemQuantity() is zero is treated as empty. This handles
		// the rare stale-widget case where itemId has not yet been cleared but the
		// stack is gone.
		if (item.getItemQuantity() <= 0)
		{
			return null;
		}

		if (!contains(item, mouse))
		{
			return null;
		}

		ResolvedItem resolved = resolveItem(itemId);
		if (resolved.name.isEmpty())
		{
			return null;
		}

		return new HoverTarget(Kind.ITEM, resolved.name, Type.ITEM_NAME, resolved.id, null, item.getItemQuantity());
	}

	/**
	 * Whether the mouse is over the widget.
	 */
	private static boolean contains(Widget widget, Point mouse)
	{
		java.awt.Rectangle bounds = widget.getBounds();
		return bounds != null && bounds.contains(mouse.getX(), mouse.getY());
	}

	/**
	 * The display name of an item, from its definition.
	 * <p>
	 * For a noted variant the composition is read once for the variant id, then
	 * a second time for the underlying base item, so the translation is keyed
	 * on the un-noted noun. The player sees the same German whether they
	 * hover a stack of bronze swords or a stack of noted bronze swords.
	 */
	private String itemName(int itemId)
	{
		return resolveItem(itemId).name;
	}

	/**
	 * The translation-friendly identifier for an item.
	 * <p>
	 * For a noted variant this is the base item's id, so the cache slot the
	 * translation lives in is the same one a non-noted hover would write to.
	 */
	private int resolveItemId(int itemId)
	{
		return resolveItem(itemId).id;
	}

	/**
	 * The id and name of an item, with noted variants resolved to their base.
	 * <p>
	 * The two are returned together because every caller needs both, and
	 * recomputing the composition lookup twice would double the cache reads
	 * in a per-frame path.
	 */
	private ResolvedItem resolveItem(int itemId)
	{
		try
		{
			ItemComposition composition = client.getItemDefinition(itemId);
			if (composition == null)
			{
				return new ResolvedItem(itemId, "");
			}

			String name = clean(composition.getName());
			if (name.isEmpty())
			{
				return new ResolvedItem(itemId, "");
			}

			// A noted variant's composition.getNote() returns the base item id.
			// Translating on the base id means a stack of noted items and a stack
			// of the un-noted item share the same cache slot.
			int baseId = composition.getNote();
			if (baseId > 0 && baseId != itemId)
			{
				ItemComposition base = client.getItemDefinition(baseId);
				if (base != null)
				{
					String baseName = clean(base.getName());
					if (!baseName.isEmpty())
					{
						return new ResolvedItem(baseId, baseName);
					}
				}
			}

			return new ResolvedItem(itemId, name);
		}
		catch (RuntimeException e)
		{
			log.debug("Could not read the definition of item {}", itemId, e);
			return new ResolvedItem(itemId, "");
		}
	}

	/**
	 * The pair of id and display name the resolver passes downstream. Held in
	 * a tiny holder so the two stay together through the noted-item rewrite.
	 */
	private static final class ResolvedItem
	{
		final int id;
		final String name;

		ResolvedItem(int id, String name)
		{
			this.id = id;
			this.name = name;
		}
	}

	/**
	 * The entity name recorded on a menu entry, with any leading action word removed.
	 * <p>
	 * The client normally keeps the action and the name apart, so the target is already
	 * just the name. Some entries still carry the action in front of it - "Attack Giant
	 * Rat" rather than "Giant Rat" - so the action is stripped when it is there.
	 */
	private static String targetName(MenuEntry entry)
	{
		String target = clean(entry.getTarget());
		if (target.isEmpty())
		{
			return "";
		}

		String option = clean(entry.getOption());
		if (!option.isEmpty() && target.length() > option.length()
			&& target.regionMatches(true, 0, option, 0, option.length())
			&& Character.isWhitespace(target.charAt(option.length())))
		{
			return target.substring(option.length()).trim();
		}

		return target;
	}

	/**
	 * Strips Jagex formatting tags and normalises whitespace.
	 * <p>
	 * The placeholder name "null" is reported as no name, so it is never mistaken for
	 * a real entity name.
	 */
	private static String clean(String raw)
	{
		if (raw == null)
		{
			return "";
		}

		String stripped = Text.removeTags(raw).replace((char) 0x00A0, ' ').trim();
		return PLACEHOLDER_NAME.equalsIgnoreCase(stripped) ? "" : stripped;
	}
}
