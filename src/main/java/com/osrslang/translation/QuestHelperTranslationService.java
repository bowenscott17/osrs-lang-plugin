package com.osrslang.translation;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import com.osrslang.text.GameText;
import com.osrslang.text.HoverTarget;
import com.osrslang.translation.provider.VocabularyEntry;
import net.runelite.api.Client;
import net.runelite.api.events.ClientTick;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;
import net.runelite.client.ui.overlay.components.LayoutableRenderableEntity;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.client.ui.overlay.components.TextComponent;
import net.runelite.client.util.Text;

/**
 * Translates the Zoinkwiz Quest Helper plugin's UI text.
 * <p>
 * The Quest Helper plugin has two main places where text appears:
 * <ol>
 * <li>The sidebar panel (a {@link PluginPanel} with Swing components) - contains
 * step descriptions, requirements, item lists, etc.</li>
 * <li>The in-game overlay (drawn via {@link OverlayManager} with Graphics2D) - shows
 * step hints, tile markers, etc. on the game canvas.</li>
 * </ol>
 * Both are translated by intercepting the text before it is rendered, using the
 * same translation pipeline as the rest of the plugin.
 * <p>
 * The Quest Helper plugin is not a dependency - we use reflection to detect it
 * and hook into its components when present, so this plugin still works without it.
 */
@Slf4j
public class QuestHelperTranslationService
{
	private final TranslationManager translationManager;
	private final ExecutorService executor;
	private final Client client;
	private final ClientThread clientThread;
	private final OverlayManager overlayManager;
	private final PluginManager pluginManager;

	// Cache for already-translated strings, keyed by original text + language pair
	private final Map<String, String> translationCache = new ConcurrentHashMap<>();

	// In-flight translation requests to avoid duplicate work
	private final Map<String, CompletableFuture<Optional<String>>> inFlight = new ConcurrentHashMap<>();

	// Whether the Quest Helper plugin was found and hooked
	private volatile boolean questHelperFound = false;
	private volatile Object questHelperPlugin = null;
	private volatile Object questHelperPanel = null;
	private volatile Object questOverlayManager = null;

	public QuestHelperTranslationService(TranslationManager translationManager, ExecutorService executor,
		Client client, ClientThread clientThread, OverlayManager overlayManager, PluginManager pluginManager)
	{
		this.translationManager = translationManager;
		this.executor = executor;
		this.client = client;
		this.clientThread = clientThread;
		this.overlayManager = overlayManager;
		this.pluginManager = pluginManager;
	}

	/**
	 * Attempts to find and hook into the Quest Helper plugin.
	 * Called on startup and when config changes.
	 */
	public void tryHookQuestHelper()
	{
		if (questHelperFound)
		{
			return; // Already hooked
		}

		// Look for the Quest Helper plugin by class name
		for (Object plugin : pluginManager.getPlugins())
		{
			if (plugin.getClass().getSimpleName().equals("QuestHelperPlugin"))
			{
				questHelperPlugin = plugin;
				questHelperFound = true;
				log.info("Found Quest Helper plugin, hooking translation service");
				hookQuestHelperPanel();
				hookQuestOverlayManager();
				break;
			}
		}

		if (!questHelperFound)
		{
			log.debug("Quest Helper plugin not found (not installed or not enabled)");
		}
	}

	/**
	 * Hooks into the Quest Helper sidebar panel to translate its text components.
	 * Uses reflection to access the panel's components and wrap their text.
	 */
	private void hookQuestHelperPanel()
	{
		if (questHelperPlugin == null)
		{
			return;
		}

		try
		{
			// The Quest Helper plugin has a getPanel() method returning a PluginPanel
			java.lang.reflect.Method getPanelMethod = questHelperPlugin.getClass().getMethod("getPanel");
			Object panel = getPanelMethod.invoke(questHelperPlugin);
			if (panel == null)
			{
				log.debug("Quest Helper panel is null");
				return;
			}

			questHelperPanel = panel;

			// The panel is a javax.swing.JPanel containing various components.
			// We need to recursively find all text-containing components and wrap them.
			translateSwingComponents(panel);
		}
		catch (ReflectiveOperationException e)
		{
			log.debug("Could not hook Quest Helper panel (may be different version): {}", e.getMessage());
		}
	}

	/**
	 * Recursively finds and translates text in Swing components.
	 */
	private void translateSwingComponents(Object component)
	{
		if (component == null)
		{
			return;
		}

		// Handle JLabel, JButton, JTextArea, etc.
		Class<?> clazz = component.getClass();
		String className = clazz.getSimpleName();

		try
		{
			// Check for getText/setText methods (common to JLabel, JButton, etc.)
			java.lang.reflect.Method getTextMethod = clazz.getMethod("getText");
			java.lang.reflect.Method setTextMethod = clazz.getMethod("setText", String.class);

			Object textObj = getTextMethod.invoke(component);
			if (textObj instanceof String)
			{
				String originalText = (String) textObj;
				if (!originalText.trim().isEmpty())
				{
					// Store original and set translated version
					String translated = translate(originalText).join();
					if (translated != null && !translated.equals(originalText))
					{
						setTextMethod.invoke(component, translated);
					}
				}
			}
		}
		catch (NoSuchMethodException ignored)
		{
			// Component doesn't have getText/setText
		}
		catch (ReflectiveOperationException e)
		{
			log.debug("Could not translate component {}: {}", className, e.getMessage());
		}

		// Recurse into child components if it's a Container
		if (component instanceof java.awt.Container)
		{
			java.awt.Container container = (java.awt.Container) component;
			for (java.awt.Component child : container.getComponents())
			{
				translateSwingComponents(child);
			}
		}
	}

	/**
	 * Hooks into the Quest Helper overlay manager to translate overlay text.
	 */
	private void hookQuestOverlayManager()
	{
		if (questHelperPlugin == null)
		{
			return;
		}

		try
		{
			// The Quest Helper plugin has a getOverlayManager() method
			java.lang.reflect.Method getOverlayManagerMethod = questHelperPlugin.getClass().getMethod("getOverlayManager");
			Object overlayManagerObj = getOverlayManagerMethod.invoke(questHelperPlugin);
			if (overlayManagerObj == null)
			{
				log.debug("Quest Helper overlay manager is null");
				return;
			}

			questOverlayManager = overlayManagerObj;
			log.debug("Hooked Quest Helper overlay manager");
		}
		catch (ReflectiveOperationException e)
		{
			log.debug("Could not hook Quest Helper overlay manager: {}", e.getMessage());
		}
	}

	/**
	 * Translates a string using the translation pipeline.
	 * Returns the original string if no translation is available.
	 */
	public CompletableFuture<String> translate(String text)
	{
		return translate(text, GameText.Type.QUEST_HELPER);
	}

	/**
	 * Translates a string with a specific context type.
	 */
	public CompletableFuture<String> translate(String text, GameText.Type context)
	{
		if (text == null || text.trim().isEmpty())
		{
			return CompletableFuture.completedFuture(text);
		}

		// Check cache first
		Language from = Language.ENGLISH;
		Language to = Language.GERMAN;
		String cacheKey = text + "|" + from.getCode() + "|" + to.getCode();
		String cached = translationCache.get(cacheKey);
		if (cached != null)
		{
			return CompletableFuture.completedFuture(cached);
		}

		// Check in-flight
		CompletableFuture<Optional<String>> existing = inFlight.get(cacheKey);
		if (existing != null)
		{
			return existing.thenApply(opt -> opt.orElse(text));
		}

		CompletableFuture<Optional<String>> promise = new CompletableFuture<>();
		if (inFlight.putIfAbsent(cacheKey, promise) != null)
		{
			return inFlight.get(cacheKey).thenApply(opt -> opt.orElse(text));
		}

		executor.execute(() ->
		{
			try
			{
				translationManager.translatePhrase(text, from, to, context)
					.whenComplete((result, error) ->
					{
						inFlight.remove(cacheKey);

						if (error != null || result == null || !result.isPresent())
						{
							promise.complete(Optional.empty());
							return;
						}

						String translated = result.get().getTranslatedText();
						if (translated != null && !translated.trim().isEmpty() && !translated.equalsIgnoreCase(text))
						{
							translationCache.put(cacheKey, translated);
							promise.complete(Optional.of(translated));
						}
						else
						{
							promise.complete(Optional.empty());
						}
					});
			}
			catch (RuntimeException e)
			{
				inFlight.remove(cacheKey);
				log.warn("Quest Helper translation failed for '{}'", text, e);
				promise.complete(Optional.empty());
			}
		});

		return promise.thenApply(opt -> opt.orElse(text));
	}

	/**
	 * Translates a string synchronously (blocking) for use in Swing event thread.
	 * Uses a short timeout since Swing must not block.
	 */
	public String translateSync(String text)
	{
		return translateSync(text, GameText.Type.QUEST_HELPER);
	}

	public String translateSync(String text, GameText.Type context)
	{
		if (text == null || text.trim().isEmpty())
		{
			return text;
		}

		Language from = Language.ENGLISH;
		Language to = Language.GERMAN;
		String cacheKey = text + "|" + from.getCode() + "|" + to.getCode();
		String cached = translationCache.get(cacheKey);
		if (cached != null)
		{
			return cached;
		}

		// Try to get from in-flight or start new
		try
		{
			CompletableFuture<String> future = translate(text, context);
			String result = future.get(100, java.util.concurrent.TimeUnit.MILLISECONDS);
			return result != null ? result : text;
		}
		catch (Exception e)
		{
			// Timeout or error - return original
			return text;
		}
	}

	/**
	 * Clears the translation cache (called when language changes).
	 */
	public void clear()
	{
		translationCache.clear();
		inFlight.clear();
	}

	/**
	 * Whether the Quest Helper plugin was found and hooked.
	 */
	public boolean isQuestHelperFound()
	{
		return questHelperFound;
	}

	/**
	 * Re-translates the Quest Helper panel components.
	 * Called when the panel content changes (e.g., quest step changes).
	 */
	public void refreshQuestHelperPanel()
	{
		if (questHelperPanel != null)
		{
			translateSwingComponents(questHelperPanel);
		}
	}
}