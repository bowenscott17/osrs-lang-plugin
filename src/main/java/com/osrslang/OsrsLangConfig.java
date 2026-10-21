package com.osrslang;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("osrs-lang")
public interface OsrsLangConfig extends Config
{
	@ConfigSection(
		name = "Languages",
		description = "Which language to translate into",
		position = 0
	)
	String languagesSection = "languagesSection";

	@ConfigItem(
		keyName = "sourceLanguage",
		name = "Source language",
		description = "The language the game text is written in",
		position = 1,
		section = languagesSection
	)
	default String sourceLanguage()
	{
		return "en";
	}

	@ConfigItem(
		keyName = "targetLanguage",
		name = "Target language",
		description = "The language you want to translate the game text into",
		position = 2,
		section = languagesSection
	)
	default String targetLanguage()
	{
		return "de";
	}

	@ConfigItem(
		keyName = "vocabularyLanguage",
		name = "Vocabulary dictionary language",
		description = "The language used for word-by-word dictionary lookups and hover tooltips",
		position = 3,
		section = languagesSection
	)
	default String vocabularyLanguage()
	{
		return "de";
	}

	@ConfigItem(
		keyName = "secondaryLanguage",
		name = "Secondary translation language",
		description = "Optional second translation shown alongside the primary one. Leave as 'none' to disable.",
		position = 4,
		section = languagesSection
	)
	default String secondaryLanguage()
	{
		return "none";
	}

	@ConfigSection(
		name = "Display",
		description = "What to show and how to show it",
		position = 10
	)
	String displaySection = "displaySection";

	@ConfigItem(
		keyName = "showTranslations",
		name = "Show translations",
		description = "Whether translations are displayed at all",
		position = 11,
		section = displaySection
	)
	default boolean showTranslations()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showOriginal",
		name = "Keep original text visible",
		description = "Show the original game text alongside its translation",
		position = 12,
		section = displaySection
	)
	default boolean showOriginal()
	{
		return true;
	}

	@ConfigItem(
		keyName = "translateDialogue",
		name = "Translate NPC dialogue",
		description = "Translate dialogue shown when talking to NPCs",
		position = 13,
		section = displaySection
	)
	default boolean translateDialogue()
	{
		return true;
	}

	@ConfigItem(
		keyName = "translateChat",
		name = "Translate chat messages",
		description = "Translate text that appears in the chatbox",
		position = 14,
		section = displaySection
	)
	default boolean translateChat()
	{
		return false;
	}

	@ConfigItem(
		keyName = "hoverTranslations",
		name = "Hover translations",
		description = "Show a tooltip with the dictionary entry when hovering over a word",
		position = 15,
		section = displaySection
	)
	default boolean hoverTranslations()
	{
		return true;
	}

	@ConfigItem(
		keyName = "fontSize",
		name = "Font size",
		description = "Size of the translation text",
		position = 16,
		section = displaySection
	)
	default int fontSize()
	{
		return 12;
	}

	@ConfigSection(
		name = "Translation engine",
		description = "Where translations come from",
		position = 20
	)
	String engineSection = "engineSection";

	@ConfigItem(
		keyName = "offlineTranslationEnabled",
		name = "Use offline translation",
		description = "Use the bundled offline machine translation engine when no verified or dictionary entry is available",
		position = 21,
		section = engineSection
	)
	default boolean offlineTranslationEnabled()
	{
		return true;
	}

	@ConfigItem(
		keyName = "onlineTranslationEnabled",
		name = "Use online translation",
		description = "Fall back to an online translation service when no other source has a translation. Game text is sent to the service.",
		position = 22,
		section = engineSection,
		warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers"
	)
	default boolean onlineTranslationEnabled()
	{
		return false;
	}

	@ConfigItem(
		keyName = "onlineTranslationEndpoint",
		name = "Online translation endpoint",
		description = "The URL of the LibreTranslate-compatible service to use",
		position = 23,
		section = engineSection
	)
	default String onlineTranslationEndpoint()
	{
		return "https://libretranslate.com";
	}

	@ConfigSection(
		name = "Cache",
		description = "How translations are stored between sessions",
		position = 30
	)
	String cacheSection = "cacheSection";

	@ConfigItem(
		keyName = "cacheEnabled",
		name = "Cache translations",
		description = "Remember translations so the same text is never translated twice",
		position = 31,
		section = cacheSection
	)
	default boolean cacheEnabled()
	{
		return true;
	}

	@ConfigItem(
		keyName = "cacheMaxEntries",
		name = "Maximum cached translations",
		description = "How many translations to keep. Older entries are dropped first.",
		position = 32,
		section = cacheSection
	)
	default int cacheMaxEntries()
	{
		return 5000;
	}

	@ConfigItem(
		keyName = "clearCache",
		name = "Clear cache now",
		description = "Remove every cached translation. Tick this box to empty the cache.",
		position = 33,
		section = cacheSection
	)
	default boolean clearCache()
	{
		return false;
	}
}
