package com.osrslang;

import com.google.gson.Gson;
import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import java.io.IOException;
import java.util.concurrent.ScheduledExecutorService;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;
import net.runelite.client.util.Filepath;
import okhttp3.OkHttpClient;
import com.osrslang.overlay.TranslationOverlay;
import com.osrslang.overlay.WordTooltipOverlay;
import com.osrslang.text.DialogueTracker;
import com.osrslang.text.GameText;
import com.osrslang.translation.JsonTranslationCache;
import com.osrslang.translation.Language;
import com.osrslang.translation.TranslationManager;
import com.osrslang.translation.TranslationService;
import com.osrslang.translation.provider.LocalDictionaryProvider;
import com.osrslang.translation.provider.OnlineTranslationProvider;
import com.osrslang.translation.provider.OsrsTranslationDatabaseProvider;

@Slf4j
@PluginDescriptor(
	name = "OSRS Language Helper",
	description = "Translate OSRS dialogue and game text to help you learn a new language",
	tags = {"language", "translation", "learning", "education"},
	internalName = "osrs-lang"
)
public class OsrsLangPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private OsrsLangConfig config;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private DialogueTracker dialogueTracker;

	@Inject
	private Gson gson;

	@Inject
	private TooltipManager tooltipManager;

	@Inject
	private OkHttpClient httpClient;

	@Inject
	private ScheduledExecutorService scheduler;

	private TranslationOverlay overlay;
	private WordTooltipOverlay wordOverlay;
	private TranslationService translationService;
	private TranslationManager translationManager;
	private OnlineTranslationProvider onlineProvider;
	private JsonTranslationCache translationCache;

	/**
	 * The dialogue currently displayed, and the translation for it.
	 */
	private String currentOriginal;
	private String currentTranslation;
	private String currentSourceLabel;

	@Override
	protected void startUp() throws Exception
	{
		overlay = new TranslationOverlay(this);
		overlayManager.add(overlay);

		Language from = language(config.sourceLanguage(), Language.ENGLISH);
		Language to = language(config.targetLanguage(), Language.GERMAN);

		translationManager = new TranslationManager();

		// The verified database is consulted before the generic dictionary, so a
		// human-checked phrase always wins over a dictionary word.
		OsrsTranslationDatabaseProvider database = new OsrsTranslationDatabaseProvider(this, gson);
		LocalDictionaryProvider dictionary = new LocalDictionaryProvider(this, gson);
		translationManager.register(database);
		translationManager.register(dictionary);

		// The online provider is a fallback, and is only ever consulted when the user
		// has explicitly enabled it.
		onlineProvider = new OnlineTranslationProvider(this, httpClient, gson, scheduler);
		onlineProvider.setEnabled(config.onlineTranslationEnabled());
		onlineProvider.setEndpoint(config.onlineTranslationEndpoint());
		translationManager.registerFallback(onlineProvider);

		wordOverlay = new WordTooltipOverlay(client, tooltipManager, translationManager, from, to);
		overlayManager.add(wordOverlay);

		translationService = new TranslationService(translationManager);

		setUpCache();

		// Load the language data off the client thread so startup is not blocked by
		// reading and parsing the dictionaries.
		loadLanguageData(database, dictionary, from, to);

		log.info("OSRS Language Helper started");
	}

	@Override
	protected void shutDown() throws Exception
	{
		if (overlay != null)
		{
			overlayManager.remove(overlay);
			overlay = null;
		}
		if (wordOverlay != null)
		{
			overlayManager.remove(wordOverlay);
			wordOverlay = null;
		}
		if (translationService != null)
		{
			translationService.shutdown();
			translationService = null;
		}
		if (onlineProvider != null)
		{
			onlineProvider.shutdown();
			onlineProvider = null;
		}
		if (translationCache != null)
		{
			translationCache.save();
			translationCache = null;
		}
		dialogueTracker.clear();
		clearContent();
		log.info("OSRS Language Helper stopped");
	}

	/**
	 * Sets up the persistent translation cache, if caching is enabled.
	 */
	private void setUpCache()
	{
		if (!config.cacheEnabled())
		{
			return;
		}

		try
		{
			Filepath directory = getPluginDirectory();
			JsonTranslationCache cache = new JsonTranslationCache(
				gson, directory.join("translation-cache.json"), config.cacheMaxEntries());

			cache.load();
			translationManager.setCache(cache);
			translationCache = cache;
		}
		catch (IOException | RuntimeException e)
		{
			log.warn("Could not open the translation cache", e);
		}
	}

	/**
	 * Loads the verified database and dictionary for a language pair in the background.
	 */
	private void loadLanguageData(OsrsTranslationDatabaseProvider database, LocalDictionaryProvider dictionary,
		Language from, Language to)
	{
		Thread loader = new Thread(() ->
		{
			database.load(from, to);
			dictionary.load(from, to);
		}, "osrs-lang-load");
		loader.setDaemon(true);
		loader.start();
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!"osrs-lang".equals(event.getGroup()))
		{
			return;
		}

		if (onlineProvider != null)
		{
			if ("onlineTranslationEnabled".equals(event.getKey()))
			{
				onlineProvider.setEnabled(config.onlineTranslationEnabled());
			}
			else if ("onlineTranslationEndpoint".equals(event.getKey()))
			{
				onlineProvider.setEndpoint(config.onlineTranslationEndpoint());
			}
		}

		if ("clearCache".equals(event.getKey()) && config.clearCache())
		{
			clearTranslationCache();
		}
	}

	/**
	 * Empties the translation cache.
	 */
	private void clearTranslationCache()
	{
		if (translationCache != null)
		{
			translationCache.clear();
			translationCache.save();
			log.info("Translation cache cleared");
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged)
	{
		if (gameStateChanged.getGameState() == GameState.LOGGED_IN)
		{
			log.debug("Logged in to OSRS");
		}
	}

	@Subscribe
	public void onClientTick(ClientTick clientTick)
	{
		if (overlay == null || translationService == null)
		{
			return;
		}

		if (!config.showTranslations() || !config.translateDialogue())
		{
			clearContent();
			return;
		}

		GameText dialogue = dialogueTracker.poll();
		if (dialogue == null)
		{
			clearContent();
			return;
		}

		String original = dialogue.getText();

		if (!original.equals(currentOriginal))
		{
			// New dialogue: show the original immediately and request a translation.
			currentOriginal = original;
			currentTranslation = null;
			currentSourceLabel = null;

			Language from = language(config.sourceLanguage(), Language.ENGLISH);
			Language to = language(config.targetLanguage(), Language.GERMAN);

			translationService.request(original, from, to, dialogue.getType())
				.thenAccept(result -> result.ifPresent(translation ->
				{
					// The result arrives on a background thread; only record it here and
					// let the client thread pick it up on the next tick.
					currentTranslation = translation.getTranslatedText();
					currentSourceLabel = translation.getSourceLabel();
				}));
		}

		overlay.setContent(dialogue.getSpeaker(), currentOriginal, currentTranslation);

		if (wordOverlay != null)
		{
			if (config.hoverTranslations())
			{
				wordOverlay.setSentence(currentOriginal);
			}
			else
			{
				wordOverlay.setSentence(null);
			}
		}
	}

	/**
	 * Resolves a configured language code, falling back to a default.
	 */
	private static Language language(String code, Language fallback)
	{
		return Language.fromCode(code).orElse(fallback);
	}

	private void clearContent()
	{
		currentOriginal = null;
		currentTranslation = null;
		currentSourceLabel = null;
		if (overlay != null)
		{
			overlay.setContent(null, null, null);
		}
		if (wordOverlay != null)
		{
			wordOverlay.setSentence(null);
		}
	}

	@Provides
	OsrsLangConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(OsrsLangConfig.class);
	}
}
