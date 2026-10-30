package com.osrslang.text;

import java.util.Objects;
import javax.annotation.Nullable;
import com.osrslang.text.GameText.Type;

/**
 * A game entity the mouse is currently over.
 * <p>
 * The name carried here is always the <em>target</em> name - the thing itself, not the
 * action that would be performed on it. So hovering a giant rat with the "Attack" option
 * under the cursor yields the name "Giant Rat", not "Attack Giant Rat".
 * <p>
 * When the menu entry the resolver found carries an action - "Attack", "Talk-to",
 * "Examine", "Take" - that action is preserved on the target so the hover tooltip can
 * keep it visible alongside the translated noun. Inventory items have no action and
 * carry {@code null}, so the tooltip shows just the noun there.
 */
public class HoverTarget
{
	/**
	 * The kind of entity being hovered.
	 */
	public enum Kind
	{
		NPC,
		ITEM,
		OBJECT,
		/**
		 * A free-form English string from the chatbox, not tied to any particular
		 * entity. Grouped separately from {@link #ITEM} so an item name and a
		 * chat message with the same wording cannot collide in the translation
		 * cache.
		 */
		CHAT_MESSAGE
	}

	private final Kind kind;
	private final String name;
	private final Type type;

	/**
	 * A stable identifier for the entity, when one is known - an item id or an object id.
	 * Used to recognise the same entity across frames without relying on the name alone.
	 */
	private final int id;

	/**
	 * The action verb from the menu entry under the cursor ("Attack", "Talk-to", ...),
	 * or {@code null} when there is no action - typically for items in the inventory.
	 */
	private final String action;

	/**
	 * How many of this item the cursor is on, for stackable items.
	 * <p>
	 * The translation pipeline uses {@link #getName()} rather than this number, so a
	 * 100-strong stack of bronze swords translates to the same German as a single one
	 * would - the count is shown in the tooltip alongside the translation, never mixed
	 * into the name that is looked up. Always at least one.
	 */
	private final int quantity;

	public HoverTarget(Kind kind, String name, Type type, int id)
	{
		this(kind, name, type, id, null, 1);
	}

	public HoverTarget(Kind kind, String name, Type type, int id, @Nullable String action)
	{
		this(kind, name, type, id, action, 1);
	}

	public HoverTarget(Kind kind, String name, Type type, int id, @Nullable String action, int quantity)
	{
		this.kind = kind;
		this.name = name;
		this.type = type;
		this.id = id;
		this.action = action;
		this.quantity = Math.max(1, quantity);
	}

	public Kind getKind()
	{
		return kind;
	}

	/**
	 * The English name of the entity, as the game shows it.
	 */
	public String getName()
	{
		return name;
	}

	/**
	 * The kind of game text this name represents, for specialised providers.
	 */
	public Type getType()
	{
		return type;
	}

	/**
	 * The entity's identifier, or {@code -1} when it has none.
	 */
	public int getId()
	{
		return id;
	}

	/**
	 * The action verb the menu entry carries, or {@code null} when none is attached.
	 */
	@Nullable
	public String getAction()
	{
		return action;
	}

	/**
	 * The number of items the cursor is on. One for non-stackable or single items.
	 */
	public int getQuantity()
	{
		return quantity;
	}

	@Override
	public boolean equals(Object o)
	{
		if (this == o)
		{
			return true;
		}
		if (!(o instanceof HoverTarget))
		{
			return false;
		}
		HoverTarget other = (HoverTarget) o;
		return kind == other.kind
			&& id == other.id
			&& Objects.equals(name, other.name)
			&& Objects.equals(action, other.action);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(kind, name, id, action);
	}

	@Override
	public String toString()
	{
		return "HoverTarget{" + kind + ", '" + name + "'" + (action == null ? "" : ", action='" + action + "'") + ", qty=" + quantity + "}";
	}
}
