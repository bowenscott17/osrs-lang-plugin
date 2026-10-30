package com.osrslang;

import com.google.gson.Gson;
import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Point;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.client.chat.ChatCommandManager;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;
import net.runelite.client.util.ExecutorServiceExceptionLogger;
import net.runelite.client.util.Filepath;
import okhttp3.OkHttpClient;
import com.osrslang.overlay.ChatTranslationOverlay;
import com.osrslang.overlay.GermanMenuOverlay;
import com.osrslang.overlay.GroundItemLabelOverlay;
import com.osrslang.overlay.SkillsPanelOverlay;
import com.osrslang.overlay.SpellbookLabelOverlay;
import com.osrslang.overlay.TranslationOverlay;
import com.osrslang.overlay.WordTooltipOverlay;
import com.osrslang.overlay.EntityTooltipOverlay;
import com.osrslang.text.ChatMessageTranslator;
import com.osrslang.text.DialogueTracker;
import com.osrslang.text.DialogueWordTracker;
import com.osrslang.text.GameText;
import com.osrslang.text.GermanNounDatabase;
import com.osrslang.text.HoverTarget;
import com.osrslang.text.HoverTargetResolver;
import com.osrslang.text.InterfaceStringDatabase;
import com.osrslang.text.InterfaceTextTranslator;
import com.osrslang.text.SkillTooltipDiagnostic;
import com.osrslang.text.SpellTooltipTranslator;
import com.osrslang.text.WordBox;
import com.osrslang.translation.HoverTranslationService;
import com.osrslang.translation.JsonTranslationCache;
import com.osrslang.translation.Language;
import com.osrslang.translation.LanguageSupportService;
import com.osrslang.translation.TranslationManager;
import com.osrslang.translation.TranslationService;
import com.osrslang.translation.WordTranslation;
import com.osrslang.translation.WordTranslationService;
import com.osrslang.translation.provider.LocalDictionaryProvider;
import com.osrslang.translation.provider.OfflineMachineTranslationProvider;
import com.osrslang.translation.provider.OnlineTranslationProvider;
import com.osrslang.translation.provider.OsrsCacheTranslationProvider;
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

	/**
	 * Works out where each word of the current dialogue is drawn, and reuses the answer
	 * for as long as the dialogue has not changed.
	 */
	private DialogueWordTracker dialogueWords;

	@Inject
	private HoverTargetResolver hoverTargetResolver;

	@Inject
	private Gson gson;

	@Inject
	private TooltipManager tooltipManager;

	@Inject
	private OkHttpClient httpClient;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ChatCommandManager chatCommandManager;

	@Inject
	private EventBus eventBus;

	/**
	 * The pool translation work runs on. Wrapped so uncaught exceptions are logged
	 * rather than silently killing a thread.
	 */
	private ScheduledExecutorService translationExecutor;

	private TranslationOverlay overlay;
	private WordTooltipOverlay wordOverlay;
	private EntityTooltipOverlay entityOverlay;
	private TranslationService translationService;

    private final java.util.concurrent.ConcurrentMap<String, String> menuTranslationCache =
        new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Set<String> menuTranslationsInFlight =
        java.util.concurrent.ConcurrentHashMap.newKeySet();
	private HoverTranslationService hoverTranslationService;
	private WordTranslationService wordTranslationService;
	private LanguageSupportService languageSupport;
	private TranslationManager translationManager;
	private OnlineTranslationProvider onlineProvider;
	private OfflineMachineTranslationProvider offlineProvider;
	private OsrsTranslationDatabaseProvider database;
	private LocalDictionaryProvider dictionary;
	private OsrsCacheTranslationProvider cacheTranslationProvider;
	private JsonTranslationCache translationCache;
	private LanguageChatCommand languageChatCommand;
	private InterfaceStringDatabase interfaceStrings;
	private InterfaceTextTranslator interfaceText;
	private SpellTooltipTranslator spellTooltips;

	/**
	 * Temporary diagnostic that reports what the client keeps on the skills panel.
	 * <p>
	 * It only reads, so it changes nothing on screen. It is here to answer the
	 * question of how the native skill tooltip is built, before anything is changed.
	 */
	private SkillTooltipDiagnostic skillTooltipDiagnostic;
	private SkillsPanelOverlay skillsOverlay;
	private SpellbookLabelOverlay spellbookOverlay;
	private GermanMenuOverlay menuOverlay;
	private GroundItemLabelOverlay groundItemOverlay;
	private ChatTranslationOverlay chatTranslationOverlay;
	private ChatMessageTranslator chatTranslator;
	private GermanNounDatabase nounDatabase;

	/**
	 * The entity currently under the mouse, and the translation shown for it.
	 */
	private HoverTarget currentHover;
	private String currentHoverTranslation;

	/**
	 * The article of {@link #currentHoverTranslation}, where one is known or derivable.
	 */
	private String currentHoverArticle;

	/**
	 * Where {@link #currentHoverArticle} came from, so a derived article is shown as a
	 * hint rather than stated as fact.
	 */
	private String currentHoverArticleBasis;

	/**
	 * The German translation of the menu entry's action verb, when the dictionary
	 * knows one. Null when there is no action or the verb has no entry yet.
	 */
	private String currentHoverActionTranslation;

	/**
	 * The dialogue currently displayed, and the translation for it.
	 */
	private String currentOriginal;
	private String currentTranslation;

	/**
	 * The individual dialogue word the pointer is over, and what is known about it.
	 * <p>
	 * Both are cleared when the pointer moves off the word or the dialogue changes, which
	 * is what makes a slow answer to a lookup that is no longer wanted harmless.
	 */
	private String currentWord;
	private String currentWordPair;
	private WordTranslation currentWordTranslation;

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
		database = new OsrsTranslationDatabaseProvider(this, gson);
		dictionary = new LocalDictionaryProvider(this, gson);
		translationManager.register(database);
		translationManager.register(dictionary);

		// The OSRS cache provider reads Jagex's own German strings from the local OSRS
		// client cache. It is consulted after the dictionary because it only has entries
		// when the user has launched OSRS in German at least once; when it is empty the
		// next provider answers instead.
		cacheTranslationProvider = new OsrsCacheTranslationProvider(this);
		translationManager.register(cacheTranslationProvider);

		// The offline engine talks to a LibreTranslate service the user runs on their
		// own machine, so no game text leaves the computer.
		offlineProvider = new OfflineMachineTranslationProvider(this, httpClient, gson);
		offlineProvider.setEnabled(config.offlineTranslationEnabled());
		offlineProvider.setEndpoint(config.offlineTranslationEndpoint());
		translationManager.registerFallback(offlineProvider);

		// The online provider is a fallback, and is only ever consulted when the user
		// has explicitly enabled it.
		onlineProvider = new OnlineTranslationProvider(this, httpClient, gson);
		onlineProvider.setEnabled(config.onlineTranslationEnabled());
		onlineProvider.setService(OnlineTranslationProvider.Service.fromCode(config.onlineTranslationService()));
		onlineProvider.setEndpoint(config.onlineTranslationEndpoint());
		onlineProvider.setApiKey(config.onlineTranslationApiKey());
		translationManager.registerFallback(onlineProvider);

		dialogueWords = new DialogueWordTracker();

		wordOverlay = new WordTooltipOverlay(tooltipManager);
		overlayManager.add(wordOverlay);

		entityOverlay = new EntityTooltipOverlay(tooltipManager);
		overlayManager.add(entityOverlay);

		translationExecutor = new ExecutorServiceExceptionLogger(
			Executors.newScheduledThreadPool(2, r -> {
				Thread thread = new Thread(r, "osrs-lang-translate");
				thread.setDaemon(true);
				return thread;
			}));

		translationService = new TranslationService(translationManager, translationExecutor);
		nounDatabase = new GermanNounDatabase(gson);
		hoverTranslationService = new HoverTranslationService(translationManager, translationExecutor, nounDatabase);

		// Asks the offline service what it can translate, so the language selectors only
		// offer combinations that have a model behind them.
		languageSupport = new LanguageSupportService(offlineProvider);
		wordTranslationService = new WordTranslationService(translationManager, translationExecutor, languageSupport);

		setUpCache();

		// Load the language data off the client thread so startup is not blocked by
		// reading and parsing the dictionaries.
		loadLanguageData(from, to);

		if (config.offlineTranslationEnabled())
		{
			languageSupport.refresh();
		}

		languageChatCommand = new LanguageChatCommand(languageSupport);

		// The interface table is read off the client thread like the dictionaries, so
		// startup is not held up by parsing it.
		interfaceStrings = new InterfaceStringDatabase(gson);
		interfaceText = new InterfaceTextTranslator(client, interfaceStrings);
		interfaceText.setRecordMissing(config.recordUntranslatedInterfaceText());
		interfaceStrings.load(from, to);

		// The skills panel rows hold no widget text, so the widget rewriter cannot reach
		// the names. A separate overlay draws the translated names on top of the rows the
		// client paints itself. It is wired in after the interface table is loaded so
		// every row lookup hits a database that is already populated.
		skillsOverlay = new SkillsPanelOverlay(this, client, interfaceStrings);
		skillsOverlay.setTranslation(config.showTranslations() && config.translateInterfaceText(), from, to);
		overlayManager.add(skillsOverlay);
		refreshSkillsOverlay();

		// The spellbook's per-slot labels are part of the cached sprite, not a widget
		// text the interface translator can rewrite. A separate overlay draws the
		// German on top, the same way the skills panel overlay does for its rows.
		spellbookOverlay = new SpellbookLabelOverlay(this, client, interfaceStrings);
		overlayManager.add(spellbookOverlay);
		refreshSpellbookOverlay();

		// The tooltip that appears when the pointer rests on a spell is drawn by the
		// client from the spell's own action and name, so it is translated by putting
		// the German words into those fields rather than by drawing over the top.
		spellTooltips = new SpellTooltipTranslator(client, interfaceStrings, translationManager, clientThread);

		// Temporary diagnostic: reports what the client keeps on the skills panel, so the
		// way the native skill tooltip is built can be read off the log. It only reads.
		skillTooltipDiagnostic = new SkillTooltipDiagnostic(client);

		// The in-world ground item labels share the hover service's cache, so the overlay
		// is registered after the service it depends on has been built. It is off until
		// the user turns the relevant config on, so being registered does not draw
		// anything yet.
		groundItemOverlay = new GroundItemLabelOverlay(this, client, hoverTranslationService);
		overlayManager.add(groundItemOverlay);
		refreshGroundItemOverlay();

		// The OSRS client draws its right-click menu with a font that has no German
		// umlauts in it, so entries like "FÃ¤llen" come out as a placeholder box. The
		// plugin could redraw the affected rows on top, but the overlay was disabled
		// because the redrawn text still showed alongside OSRS's own drawing and read
		// as a second copy of the menu. Left registered as a field for future
		// re-enablement once a way to hide the original underneath is found.
		menuOverlay = new GermanMenuOverlay(this, client);
		// overlayManager.add(menuOverlay);

		// The chat-message translator subscribes to ChatMessage events itself; the
		// overlay just renders the recent buffer. Both are registered after the
		// hover service is built so the cache they share is ready.
		chatTranslator = new ChatMessageTranslator(hoverTranslationService);
		chatTranslationOverlay = new ChatTranslationOverlay(this, chatTranslator);
		overlayManager.add(chatTranslationOverlay);
		eventBus.register(chatTranslator);
		refreshChatTranslation();

		// Registered asynchronously because the command may refresh the service's
		// language list, and that is network work the client thread must not do.
		chatCommandManager.registerCommandAsync(languageChatCommand.getTrigger(),
			(message, arguments) -> languageChatCommand.run());

		log.info("OSRS Language Helper started");
	}

	@Override
	protected void shutDown() throws Exception
	{
		if (languageChatCommand != null)
		{
			chatCommandManager.unregisterCommand(languageChatCommand.getTrigger());
			languageChatCommand = null;
		}
		if (interfaceText != null)
		{
			// The game's own labels have to go back to the game's language, or a client
			// closed with the plugin still on would be left showing translations.
			interfaceText.clear();
			interfaceText = null;
		}
		if (spellTooltips != null)
		{
			// The spell actions have to go back to the game's language for the same
			// reason as the labels above.
			spellTooltips.clear();
			spellTooltips = null;
		}
		if (skillTooltipDiagnostic != null)
		{
			skillTooltipDiagnostic = null;
		}
		if (interfaceStrings != null)
		{
			interfaceStrings = null;
		}
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
		if (entityOverlay != null)
		{
			overlayManager.remove(entityOverlay);
			entityOverlay = null;
		}
		if (skillsOverlay != null)
		{
			overlayManager.remove(skillsOverlay);
			skillsOverlay = null;
		}
		if (spellbookOverlay != null)
		{
			overlayManager.remove(spellbookOverlay);
			spellbookOverlay = null;
		}
		if (menuOverlay != null)
		{
			overlayManager.remove(menuOverlay);
			menuOverlay = null;
		}
		if (groundItemOverlay != null)
		{
			overlayManager.remove(groundItemOverlay);
			groundItemOverlay = null;
		}
		if (chatTranslationOverlay != null)
		{
			overlayManager.remove(chatTranslationOverlay);
			chatTranslationOverlay = null;
		}
		if (chatTranslator != null)
		{
			eventBus.unregister(chatTranslator);
			chatTranslator.clear();
			chatTranslator = null;
		}
		if (translationService != null)
		{
			translationService.shutdown();
			translationService = null;
		}
		if (hoverTranslationService != null)
		{
			hoverTranslationService.clear();
			hoverTranslationService = null;
		}
		if (wordTranslationService != null)
		{
			wordTranslationService.clear();
			wordTranslationService = null;
		}
		if (languageSupport != null)
		{
			languageSupport.invalidate();
			languageSupport = null;
		}
		if (dialogueWords != null)
		{
			dialogueWords.clear();
			dialogueWords = null;
		}
		if (onlineProvider != null)
		{
			onlineProvider.shutdown();
			onlineProvider = null;
		}
		if (offlineProvider != null)
		{
			offlineProvider.shutdown();
			offlineProvider = null;
		}
		if (translationExecutor != null)
		{
			translationExecutor.shutdownNow();
			translationExecutor = null;
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
	private void loadLanguageData(Language from, Language to)
	{
		OsrsTranslationDatabaseProvider currentDatabase = database;
		LocalDictionaryProvider currentDictionary = dictionary;

		if (currentDatabase == null || currentDictionary == null)
		{
			return;
		}

		Thread loader = new Thread(() ->
		{
			currentDatabase.load(from, to);
			currentDictionary.load(from, to);
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
			else if ("onlineTranslationService".equals(event.getKey()))
			{
				onlineProvider.setService(OnlineTranslationProvider.Service.fromCode(config.onlineTranslationService()));
				onlineProvider.setEndpoint(config.onlineTranslationEndpoint());
			}
			else if ("onlineTranslationEndpoint".equals(event.getKey()))
			{
				onlineProvider.setEndpoint(config.onlineTranslationEndpoint());
			}
			else if ("onlineTranslationApiKey".equals(event.getKey()))
			{
				onlineProvider.setApiKey(config.onlineTranslationApiKey());
			}
		}

		if (offlineProvider != null)
		{
			if ("offlineTranslationEnabled".equals(event.getKey()))
			{
				offlineProvider.setEnabled(config.offlineTranslationEnabled());
				offlineProvider.setLanguages(java.util.Collections.emptyList());

				if (languageSupport != null)
				{
					languageSupport.invalidate();

					if (config.offlineTranslationEnabled())
					{
						languageSupport.refresh();
					}
				}
			}
			else if ("offlineTranslationEndpoint".equals(event.getKey()))
			{
				offlineProvider.setEndpoint(config.offlineTranslationEndpoint());
				offlineProvider.setLanguages(java.util.Collections.emptyList());

				// The language list belonged to the old address, so it cannot be reused.
				if (languageSupport != null)
				{
					languageSupport.invalidate();

					if (config.offlineTranslationEnabled())
					{
						languageSupport.refresh();
					}
				}
			}
		}

		if ("clearCache".equals(event.getKey()) && config.clearCache())
		{
			clearTranslationCache();
		}

		if ("recordUntranslatedInterfaceText".equals(event.getKey()) && interfaceText != null)
		{
			interfaceText.setRecordMissing(config.recordUntranslatedInterfaceText());
		}

		if ("translateInterfaceText".equals(event.getKey()) || "showTranslations".equals(event.getKey()) || "translateSpellbookLabels".equals(event.getKey()))
		{
			refreshSkillsOverlay();
			refreshSpellbookOverlay();
		}

		if ("translateGroundItems".equals(event.getKey()) || "showTranslations".equals(event.getKey()))
		{
			refreshGroundItemOverlay();
		}

		if ("translateChat".equals(event.getKey()) || "showTranslations".equals(event.getKey()))
		{
			refreshChatTranslation();
		}

		if ("sourceLanguage".equals(event.getKey()) || "targetLanguage".equals(event.getKey()))
		{
			// The names already translated are for the old language pair, so they are
			// forgotten and looked up again in the new one.
			if (hoverTranslationService != null)
			{
				hoverTranslationService.clear();
			}
			if (wordTranslationService != null)
			{
				wordTranslationService.clear();
			}
			if (dialogueWords != null)
			{
				dialogueWords.clear();
			}
			clearWord();
			clearHover();

			Language from = language(config.sourceLanguage(), Language.ENGLISH);
			Language to = language(config.targetLanguage(), Language.GERMAN);

			// The labels already on screen are in the old language, so they are put back
			// and the table for the new pair is read.
			if (interfaceText != null)
			{
				interfaceText.clear();
			}
			if (interfaceStrings != null)
			{
				interfaceStrings.load(from, to);
			}

			// The dictionaries for the new pair have not been read yet.
			loadLanguageData(from, to);

			// The skills panel draws translated names from the freshly loaded table.
			refreshSkillsOverlay();

			// The ground item labels need the new language pair too, so what was
			// remembered under the old pair is replaced.
			refreshGroundItemOverlay();
		}
	}

	/**
	 * Points the skills-panel overlay at whatever the current settings say it should be
	 * drawing, or turns it off when the user has asked for either display off or the
	 * interface text off.
	 */
	private void refreshSkillsOverlay()
{
    if (skillsOverlay == null)
    {
        return;
    }

    skillsOverlay.setTranslation(
        config.showTranslations() && config.translateInterfaceText(),
        language(config.sourceLanguage(), Language.ENGLISH),
        language(config.targetLanguage(), Language.GERMAN));
}

	/**
	 * Points the spellbook overlay at whatever the current settings say it should be
	 * drawing, or turns it off when the user has asked for either display off or the
	 * interface text off. The spellbook shares its on/off switch with the other
	 * interface text, because the labels it translates are interface labels too.
	 */
	private void refreshSpellbookOverlay()
	{
		if (spellbookOverlay == null)
		{
			return;
		}

		spellbookOverlay.setTranslation(
			config.showTranslations() && config.translateInterfaceText() && config.translateSpellbookLabels(),
			language(config.sourceLanguage(), Language.ENGLISH),
			language(config.targetLanguage(), Language.GERMAN));
	}

	/**
	 * Points the ground-item label overlay at the current language pair, or turns it off
	 * when the user has asked for either display off or the ground-item labels off.
	 * <p>
	 * The overlay is registered even when it is off, so the frame is still drawn but
	 * {@link GroundItemLabelOverlay#render(java.awt.Graphics2D)} returns early. That
	 * keeps the registration order stable, which is the only reason this exists as a
	 * separate method from {@link #refreshSkillsOverlay()}.
	 */
	private void refreshGroundItemOverlay()
	{
		if (groundItemOverlay == null)
		{
			return;
		}

		groundItemOverlay.setTranslation(
			config.showTranslations() && config.translateGroundItems(),
			language(config.sourceLanguage(), Language.ENGLISH),
			language(config.targetLanguage(), Language.GERMAN));
	}

	/**
	 * Toggles the chat translation overlay in step with the {@code translateChat}
	 * flag. The translator keeps listening either way, so flipping the toggle
	 * back on shows whatever the user has missed.
	 */
	private void refreshChatTranslation()
	{
		if (chatTranslationOverlay == null)
		{
			return;
		}

		chatTranslationOverlay.setEnabled(config.showTranslations() && config.translateChat());
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
    public void onMenuEntryAdded(MenuEntryAdded event)
    {
        if (translationService == null || event == null || event.getMenuEntry() == null)
        {
            return;
        }

        final net.runelite.api.MenuEntry entry = event.getMenuEntry();
        final String originalOption = entry.getOption();
        final String originalTarget = entry.getTarget();

        Language from = language(config.sourceLanguage(), Language.ENGLISH);
        Language to = language(config.targetLanguage(), Language.GERMAN);

        if (originalOption != null && !originalOption.trim().isEmpty())
        {
            translateMenuPart(entry, originalOption, from, to,
                GameText.Type.INTERFACE_TEXT, true);
        }

        if (originalTarget != null && !originalTarget.trim().isEmpty())
        {
            String visibleTarget = originalTarget.replaceAll("<[^>]*>", "").trim();

            if (!visibleTarget.isEmpty())
            {
                translateMenuPart(entry, originalTarget, from, to,
                    GameText.Type.OBJECT_NAME, false);
            }
        }
    }

    private void translateMenuPart(
        net.runelite.api.MenuEntry entry,
        String original,
        Language from,
        Language to,
        GameText.Type context,
        boolean isOption)
    {
        String visibleText = original.replaceAll("<[^>]*>", "").trim();

        if (visibleText.isEmpty() || from == to)
        {
            return;
        }

        // OSRS action wording: use the German imperative for chopping down objects.
        // This changes only the displayed menu option, not the underlying action.
        if (isOption && to == Language.GERMAN
            && visibleText.equalsIgnoreCase("Chop down"))
        {
            applyMenuTranslation(entry, original, "Fällen", true);
            menuTranslationCache.put(
                from.getCode() + "|" + to.getCode() + "|" + context.name() + "|" + visibleText,
                "Fällen");
            return;
        }

        String cacheKey = from.getCode() + "|" + to.getCode() + "|" + context.name() + "|" + visibleText;
        String cached = menuTranslationCache.get(cacheKey);

        if (cached != null)
        {
            applyMenuTranslation(entry, original, cached, isOption);
            return;
        }

        if (!menuTranslationsInFlight.add(cacheKey))
        {
            return;
        }

        translationService.request(visibleText, from, to, context)
            .whenComplete((result, error) ->
            {
                menuTranslationsInFlight.remove(cacheKey);

                if (error != null || result == null || !result.isPresent())
                {
                    return;
                }

                String translated = result.get().getTranslatedText();

                if (translated == null || translated.trim().isEmpty()
                    || translated.equalsIgnoreCase(visibleText))
                {
                    return;
                }

                menuTranslationCache.put(cacheKey, translated);
                clientThread.invoke(() ->
                    applyMenuTranslation(entry, original, translated, isOption));
            });
    }

    private void applyMenuTranslation(
        net.runelite.api.MenuEntry entry,
        String original,
        String translated,
        boolean isOption)
    {
        if (entry == null || translated == null || translated.isEmpty())
        {
            return;
        }

        String current = isOption ? entry.getOption() : entry.getTarget();

        // Only update the entry if RuneLite has not changed it since the request.
        if (current == null || !current.equals(original))
        {
            return;
        }

        String visibleOriginal = original.replaceAll("<[^>]*>", "").trim();
        String updated = original.replace(visibleOriginal, translated);

        if (isOption)
        {
            entry.setOption(updated);
        }
        else
        {
            entry.setTarget(updated);
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
		// The diagnostic only reads widgets, so it runs whatever else is happening.
		if (skillTooltipDiagnostic != null)
		{
			skillTooltipDiagnostic.update();
		}

		if (overlay == null || translationService == null)
		{
			return;
		}

		updateDialogue();
		updateWordHover();
		updateHover();
		updateInterfaceText();
		updateSpellTooltips();
	}

	/**
	 * Rewrites the spell actions so the client's own hover tooltip shows the German
	 * action text.
	 * <p>
	 * The spellbook is rebuilt by the client every time it is opened, which puts the
	 * English action back on the slots, so this runs on every tick. Only the action and
	 * the name of a spell slot are written, never its size or position, so the icons
	 * and the click targets of the spellbook are left as the client laid them out.
	 */
	private void updateSpellTooltips()
	{
		if (spellTooltips == null)
		{
			return;
		}

		spellTooltips.setEnabled(config.showTranslations() && config.translateInterfaceText());
		spellTooltips.update(
			language(config.sourceLanguage(), Language.ENGLISH),
			language(config.targetLanguage(), Language.GERMAN));
	}

	/**
	 * Rewrites the game's own labels into the chosen language.
	 * <p>
	 * The interface tree is walked every tick because the client rebuilds widgets
	 * constantly, and a rebuilt widget comes back with the game's text on it. Only the
	 * text of a widget is ever written, never its size or position, so the clickable
	 * areas of the attack styles and prayers are left as the client laid them out.
	 */
	private void updateInterfaceText()
	{
		if (interfaceText == null || interfaceStrings == null)
		{
			return;
		}

		if (!config.showTranslations() || !config.translateInterfaceText())
		{
			// Restores every label already translated, and is a no-op once they are all
			// back, so this is safe to call every tick while the setting is off.
			interfaceText.restore();
			return;
		}

		interfaceText.update(
			language(config.sourceLanguage(), Language.ENGLISH),
			language(config.targetLanguage(), Language.GERMAN));
	}

	/**
	 * Updates the dialogue panel and the word tooltips.
	 */
	private void updateDialogue()
	{
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

			Language from = language(config.sourceLanguage(), Language.ENGLISH);
			Language to = language(config.targetLanguage(), Language.GERMAN);

			translationService.request(original, from, to, dialogue.getType())
				.thenAccept(result -> result.ifPresent(translation ->
				{
					// The result arrives on a background thread. Hop to the client
					// thread before touching the fields the overlay reads, so the
					// overlay never sees a half-written translation.
					clientThread.invoke(() ->
					{
						// Only apply the result if the dialogue has not changed in the
						// meantime; a slow translation of old text is not wanted.
						if (original.equals(currentOriginal))
						{
							currentTranslation = translation.getTranslatedText();
						}
					});
				}));
		}

		overlay.setContent(dialogue.getSpeaker(), currentOriginal, currentTranslation);
	}

	/**
	 * Finds the dialogue word the pointer is over and asks for its translation.
	 * <p>
	 * The game's own dialogue is measured and laid out by {@link DialogueWordTracker}, so
	 * this compares the pointer against the rectangles the words were actually drawn in.
	 * The pointer is usually resting on the same word for many consecutive ticks, so a
	 * lookup is only started when the word actually changes - and its answer is applied
	 * only while that same word, in that same language pair, is still the one hovered.
	 */
	private void updateWordHover()
	{
		if (wordOverlay == null || wordTranslationService == null || dialogueWords == null)
		{
			return;
		}

		if (!config.showTranslations() || !config.hoverTranslations() || !config.wordHoverTranslation())
		{
			clearWord();
			return;
		}

		Language from = language(config.sourceLanguage(), Language.ENGLISH);
		Language to = language(config.targetLanguage(), Language.GERMAN);

		dialogueWords.update(dialogueTracker.getCurrentWidget(), from);
		wordOverlay.setPairLabel(from.getName() + " → " + to.getName());

		WordBox hovered = hoveredWord();

		if (hovered == null)
		{
			clearWord();
			return;
		}

		String word = hovered.getLookupWord();
		if (word == null || word.trim().isEmpty())
		{
			clearWord();
			return;
		}

		String pair = from.getCode() + "|" + to.getCode();

		if (word.equals(currentWord) && pair.equals(currentWordPair))
		{
			return;
		}

		// A new word, or the same word in a different language pair. Anything remembered
		// for the previous pair was thrown away when the setting changed, so this is a
		// fresh lookup rather than a reused answer.
		currentWord = word;
		currentWordPair = pair;
		currentWordTranslation = WordTranslation.pending(word);
		wordOverlay.setHover(word, currentWordTranslation);

		GameText dialogue = dialogueTracker.getCurrent();
		GameText.Type context = dialogue == null ? GameText.Type.NPC_DIALOGUE : dialogue.getType();

		wordTranslationService.request(word, from, to, context)
			.thenAccept(result -> clientThread.invoke(() ->
			{
				// The lookup finished on a worker thread. Only use it if the pointer is
				// still on this word and the languages have not changed underneath it;
				// a slow answer to a word the mouse has already left is not wanted.
				if (word.equals(currentWord) && pair.equals(currentWordPair))
				{
					currentWordTranslation = result;
					wordOverlay.setHover(word, result);
				}
			}));
	}

	/**
	 * The word the pointer is over, or {@code null} when it is not over one.
	 */
	private WordBox hoveredWord()
	{
		Point mouse = client.getMouseCanvasPosition();

		return mouse == null ? null : dialogueWords.wordAt(mouse.getX(), mouse.getY());
	}

	/**
	 * Hides the tooltip for the hovered word.
	 */
	private void clearWord()
	{
		currentWord = null;
		currentWordPair = null;
		currentWordTranslation = null;

		if (wordOverlay != null)
		{
			wordOverlay.clear();
		}
	}

	/**
	 * Updates the tooltip for the game entity under the mouse.
	 * <p>
	 * The entity is only looked up again when it changes, so hovering a single NPC does
	 * not re-request its translation on every tick.
	 */
	private void updateHover()
	{
		if (entityOverlay == null || hoverTranslationService == null)
		{
			return;
		}

		if (!config.showTranslations() || !config.hoverTranslations())
		{
			clearHover();
			return;
		}

		HoverTarget target = hoverTargetResolver.resolve().orElse(null);

		if (target == null)
		{
			clearHover();
			return;
		}

		if (!target.equals(currentHover))
		{
			// A new entity is under the mouse. Show its name straight away, without a
			// translation, and ask for the translation in the background.
			currentHover = target;
			currentHoverTranslation = null;
			currentHoverArticle = null;
			currentHoverArticleBasis = null;
			currentHoverActionTranslation = null;

			Language from = language(config.sourceLanguage(), Language.ENGLISH);
			Language to = language(config.targetLanguage(), Language.GERMAN);

			hoverTranslationService.request(target, from, to)
				.thenAccept(result -> result.ifPresent(translation ->
				{
					clientThread.invoke(() ->
					{
						// Only apply the result if the pointer is still on the same
						// entity; a slow translation of an entity the mouse has left
						// is not wanted.
						if (target.equals(currentHover))
						{
							log.debug("Hover translated: original=[{}], translated=[{}]", target.getName(), translation.getDisplayText());
							currentHoverTranslation = translation.getDisplayText();
							currentHoverArticle = translation.getArticle().orElse(null);
							currentHoverArticleBasis = translation.getArticleBasis().orElse(null);
						}
					});
				}));

			// The action verb is translated separately so the tooltip can show the
			// German imperative form ("Die Riesenratte angreifen") alongside the noun.
			String action = target.getAction();
			if (action != null && !action.trim().isEmpty())
			{
				String translatedAction = hoverTranslationService
					.actionTranslation(action, from, to).orElse(null);
				clientThread.invoke(() ->
				{
					if (target.equals(currentHover))
					{
						currentHoverActionTranslation = translatedAction;
					}
				});
			}
		}

		entityOverlay.setEntity(currentHover.getName(), currentHoverTranslation,
			currentHoverArticle, currentHoverArticleBasis,
			currentHoverActionTranslation, currentHover.getQuantity());
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
		if (overlay != null)
		{
			overlay.setContent(null, null, null);
		}
	}

	/**
	 * Hides the tooltip for the hovered entity.
	 */
	private void clearHover()
	{
		currentHover = null;
		currentHoverTranslation = null;
		currentHoverArticle = null;
		currentHoverArticleBasis = null;
		currentHoverActionTranslation = null;
		if (entityOverlay != null)
		{
			entityOverlay.clear();
		}
	}

	@Provides
	OsrsLangConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(OsrsLangConfig.class);
	}
}