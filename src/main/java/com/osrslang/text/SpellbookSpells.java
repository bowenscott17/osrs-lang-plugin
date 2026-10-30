package com.osrslang.text;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.runelite.api.gameval.InterfaceID;

/**
 * The spells of the modern spellbook, and the widget each one lives on.
 * <p>
 * The spellbook is drawn by the client itself: every spell is a slot in
 * {@link InterfaceID#MagicSpellbook} whose icon is part of a cached sprite, so the
 * spell's name is not a widget text the plugin can rewrite. Two parts of the plugin
 * need to know which widget belongs to which spell - the overlay that draws the German
 * name on the slot, and the translator that rewrites the slot's action so the client's
 * own hover tooltip shows German - so the list lives here and both use it.
 * <p>
 * The names are the ones the game uses in English. They are the key into the bundled
 * interface table, and the fallback when the widget itself does not name the spell.
 * <p>
 * Only the spells themselves are listed. The panel also carries the headings for the
 * sections it groups the spells into - "Jewellery Enchantments" and "Crossbow Bolt
 * Enchantments" - and those are not spells: they carry no cast action, and a heading
 * has no name to translate.
 */
public final class SpellbookSpells
{
	/**
	 * One spell slot.
	 */
	public static final class Spell
	{
		/**
		 * The packed widget id of the slot.
		 */
		private final int widgetId;

		/**
		 * The spell's name in English, as the game writes it.
		 */
		private final String englishName;

		Spell(int widgetId, String englishName)
		{
			this.widgetId = widgetId;
			this.englishName = englishName;
		}

		/**
		 * The packed widget id of the slot.
		 */
		public int getWidgetId()
		{
			return widgetId;
		}

		/**
		 * The spell's name in English, as the game writes it.
		 */
		public String getEnglishName()
		{
			return englishName;
		}
	}

	/**
	 * The spells of the modern spellbook, in the order the slots are arranged on the
	 * panel.
	 * <p>
	 * Only the modern spellbook is listed. The ancient, lunar, arceuus and necromancy
	 * spellbooks live in the same interface but use other slots, and each of them spells
	 * its own set of names.
	 */
	public static final List<Spell> SPELLS = Collections.unmodifiableList(Arrays.asList(
		new Spell(InterfaceID.MagicSpellbook.WIND_STRIKE, "Wind Strike"),
		new Spell(InterfaceID.MagicSpellbook.CONFUSE, "Confuse"),
		new Spell(InterfaceID.MagicSpellbook.WATER_STRIKE, "Water Strike"),
		new Spell(InterfaceID.MagicSpellbook.ENCHANT_1, "Lvl-1 Enchant"),
		new Spell(InterfaceID.MagicSpellbook.EARTH_STRIKE, "Earth Strike"),
		new Spell(InterfaceID.MagicSpellbook.WEAKEN, "Weaken"),
		new Spell(InterfaceID.MagicSpellbook.FIRE_STRIKE, "Fire Strike"),
		new Spell(InterfaceID.MagicSpellbook.BONES_BANANAS, "Bones to Bananas"),
		new Spell(InterfaceID.MagicSpellbook.WIND_BOLT, "Wind Bolt"),
		new Spell(InterfaceID.MagicSpellbook.CURSE, "Curse"),
		new Spell(InterfaceID.MagicSpellbook.BIND, "Bind"),
		new Spell(InterfaceID.MagicSpellbook.LOW_ALCHEMY, "Low Level Alchemy"),
		new Spell(InterfaceID.MagicSpellbook.WATER_BOLT, "Water Bolt"),
		new Spell(InterfaceID.MagicSpellbook.VARROCK_TELEPORT, "Varrock Teleport"),
		new Spell(InterfaceID.MagicSpellbook.ENCHANT_2, "Lvl-2 Enchant"),
		new Spell(InterfaceID.MagicSpellbook.EARTH_BOLT, "Earth Bolt"),
		new Spell(InterfaceID.MagicSpellbook.LUMBRIDGE_TELEPORT, "Lumbridge Teleport"),
		new Spell(InterfaceID.MagicSpellbook.TELEGRAB, "Telekinetic Grab"),
		new Spell(InterfaceID.MagicSpellbook.FIRE_BOLT, "Fire Bolt"),
		new Spell(InterfaceID.MagicSpellbook.FALADOR_TELEPORT, "Falador Teleport"),
		new Spell(InterfaceID.MagicSpellbook.CRUMBLE_UNDEAD, "Crumble Undead"),
		new Spell(InterfaceID.MagicSpellbook.TELEPORT_TO_YOUR_HOUSE, "Teleport to House"),
		new Spell(InterfaceID.MagicSpellbook.WIND_BLAST, "Wind Blast"),
		new Spell(InterfaceID.MagicSpellbook.SUPERHEAT, "Superheat Item"),
		new Spell(InterfaceID.MagicSpellbook.CAMELOT_TELEPORT, "Camelot Teleport"),
		new Spell(InterfaceID.MagicSpellbook.WATER_BLAST, "Water Blast"),
		new Spell(InterfaceID.MagicSpellbook.KOUREND_TELEPORT, "Kourend Castle Teleport"),
		new Spell(InterfaceID.MagicSpellbook.ENCHANT_3, "Lvl-3 Enchant"),
		new Spell(InterfaceID.MagicSpellbook.IBAN_BLAST, "Iban Blast"),
		new Spell(InterfaceID.MagicSpellbook.SNARE, "Snare"),
		new Spell(InterfaceID.MagicSpellbook.MAGIC_DART, "Magic Dart"),
		new Spell(InterfaceID.MagicSpellbook.ARDOUGNE_TELEPORT, "Ardougne Teleport"),
		new Spell(InterfaceID.MagicSpellbook.EARTH_BLAST, "Earth Blast"),
		new Spell(InterfaceID.MagicSpellbook.FORTIS_TELEPORT, "Fortis Teleport"),
		new Spell(InterfaceID.MagicSpellbook.HIGH_ALCHEMY, "High Level Alchemy"),
		new Spell(InterfaceID.MagicSpellbook.CHARGE_WATER_ORB, "Charge Water Orb"),
		new Spell(InterfaceID.MagicSpellbook.ENCHANT_4, "Lvl-4 Enchant"),
		new Spell(InterfaceID.MagicSpellbook.WATCHTOWER_TELEPORT, "Watchtower Teleport"),
		new Spell(InterfaceID.MagicSpellbook.FIRE_BLAST, "Fire Blast"),
		new Spell(InterfaceID.MagicSpellbook.CHARGE_EARTH_ORB, "Charge Earth Orb"),
		new Spell(InterfaceID.MagicSpellbook.BONES_PEACHES, "Bones to Peaches"),
		new Spell(InterfaceID.MagicSpellbook.SARADOMIN_STRIKE, "Saradomin Strike"),
		new Spell(InterfaceID.MagicSpellbook.CLAWS_OF_GUTHIX, "Claws of Guthix"),
		new Spell(InterfaceID.MagicSpellbook.FLAMES_OF_ZAMORAK, "Flames of Zamorak"),
		new Spell(InterfaceID.MagicSpellbook.TROLLHEIM_TELEPORT, "Trollheim Teleport"),
		new Spell(InterfaceID.MagicSpellbook.WIND_WAVE, "Wind Wave"),
		new Spell(InterfaceID.MagicSpellbook.CHARGE_FIRE_ORB, "Charge Fire Orb"),
		new Spell(InterfaceID.MagicSpellbook.APE_TELEPORT, "Ape Atoll Teleport"),
		new Spell(InterfaceID.MagicSpellbook.WATER_WAVE, "Water Wave"),
		new Spell(InterfaceID.MagicSpellbook.CHARGE_AIR_ORB, "Charge Air Orb"),
		new Spell(InterfaceID.MagicSpellbook.VULNERABILITY, "Vulnerability"),
		new Spell(InterfaceID.MagicSpellbook.ENCHANT_5, "Lvl-5 Enchant"),
		new Spell(InterfaceID.MagicSpellbook.EARTH_WAVE, "Earth Wave"),
		new Spell(InterfaceID.MagicSpellbook.ENFEEBLE, "Enfeeble"),
		new Spell(InterfaceID.MagicSpellbook.TELEOTHER_LUMBRIDGE, "Teleother Lumbridge"),
		new Spell(InterfaceID.MagicSpellbook.FIRE_WAVE, "Fire Wave"),
		new Spell(InterfaceID.MagicSpellbook.ENTANGLE, "Entangle"),
		new Spell(InterfaceID.MagicSpellbook.STUN, "Stun"),
		new Spell(InterfaceID.MagicSpellbook.CHARGE, "Charge"),
		new Spell(InterfaceID.MagicSpellbook.WIND_SURGE, "Wind Surge"),
		new Spell(InterfaceID.MagicSpellbook.TELEOTHER_FALADOR, "Teleother Falador"),
		new Spell(InterfaceID.MagicSpellbook.WATER_SURGE, "Water Surge"),
		new Spell(InterfaceID.MagicSpellbook.TELEPORT_BLOCK, "Tele Block"),
		new Spell(InterfaceID.MagicSpellbook.ENCHANT_6, "Lvl-6 Enchant"),
		new Spell(InterfaceID.MagicSpellbook.TELEOTHER_CAMELOT, "Teleother Camelot"),
		new Spell(InterfaceID.MagicSpellbook.EARTH_SURGE, "Earth Surge"),
		new Spell(InterfaceID.MagicSpellbook.ENCHANT_7, "Lvl-7 Enchant"),
		new Spell(InterfaceID.MagicSpellbook.FIRE_SURGE, "Fire Surge")
	));

	private SpellbookSpells()
	{
	}
}
