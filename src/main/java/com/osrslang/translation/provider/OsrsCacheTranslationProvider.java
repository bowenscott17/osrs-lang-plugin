package com.osrslang.translation.provider;

import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.Plugin;
import com.osrslang.translation.Language;
import com.osrslang.translation.TranslationResult;
import com.osrslang.translation.TranslationSource;
import com.osrslang.text.GameText;

/**
 * Translates game text by reading Jagex's own German strings from the local OSRS cache.
 * <p>
 * The OSRS client downloads a separate language pack for each supported language. When
 * the user switches their OSRS client to German once (in-game or in the official
 * launcher), the German pack is added to {@code main_file_cache.idx11}. This provider
 * reads that pack at runtime and uses it as a translation source - the same strings the
 * German client uses internally, read locally so no IP is redistributed.
 * <p>
 * If the German pack is not present (the user has never launched OSRS in German), this
 * provider is a no-op: every lookup returns empty and the next provider in the chain
 * answers instead. No exceptions, no logs beyond a single debug line at startup.
 */
@Slf4j
public class OsrsCacheTranslationProvider implements TranslationProvider
{
	/**
	 * The group id used by OSRS for the German language pack in the language index.
	 * <p>
	 * Jagex assigns one group per supported language. The German group id is 7 in
	 * current OSRS; if that ever changes (e.g. a new language is added), this constant
	 * becomes wrong and the provider will silently fail to load - deliberately so, so a
	 * missing translation is reported as a miss rather than as the wrong text.
	 */
	public static final int GERMAN_LANGUAGE_GROUP = 7;

	/**
	 * The default cache directory used by the official RuneLite launcher on Windows.
	 * <p>
	 * RuneLite loads the OSRS cache into a per-user directory under
	 * {@code %USERPROFILE%\.runelite\jagexcache\oldschool\LIVE\}. The provider reads from
	 * this location; if a user has relocated their cache, the OSRS client itself will
	 * not start, so this default is correct.
	 */
	public static final Path DEFAULT_CACHE_DIRECTORY = Path.of(
		System.getProperty("user.home"),
		".runelite",
		"jagexcache",
		"oldschool",
		"LIVE");

	private final JagexCacheReader cache;
	private final OsrsLanguageArchive archiveParser = new OsrsLanguageArchive();

	/**
	 * The parsed German language archive, keyed by English string content.
	 * <p>
	 * Built lazily on first lookup so the provider costs nothing at startup. The lookup
	 * is case-insensitive; {@code null} means the archive has not been loaded yet, and
	 * an empty map means it was loaded but contained nothing useful.
	 */
	private Map<String, String> byEnglish = null;

	/**
	 * The state the parser ended up in after the first load attempt.
	 */
	private LoadState loadState = LoadState.NOT_LOADED;

	private final Plugin plugin;

	public OsrsCacheTranslationProvider(Plugin plugin)
	{
		this(plugin, DEFAULT_CACHE_DIRECTORY);
	}

	public OsrsCacheTranslationProvider(Plugin plugin, Path cacheDirectory)
	{
		this.plugin = plugin;
		this.cache = new JagexCacheReader(cacheDirectory, JagexCacheReader.LANGUAGE_INDEX);
	}

	@Override
	public String getName()
	{
		return "OSRS cache (local German)";
	}

	@Override
	public boolean isEnabled()
	{
		return true;
	}

	@Override
	public boolean supports(Language source, Language target)
	{
		// Only useful for English -> German at the moment. The user's own OSRS cache is
		// always English, and only German is reliably populated in idx11 on a fresh
		// install; other languages would have to be added on demand.
		return source != null && target != null
			&& Language.ENGLISH.isSameLanguage(source)
			&& Language.GERMAN.isSameLanguage(target);
	}

	@Override
	public int getPriority()
	{
		// After the verified database and the dictionary, but before machine translation:
		// a string the OSRS developers wrote in German is more trustworthy than an MT
		// guess, and a verified human translation always beats both.
		return 30;
	}

	@Override
	public CompletableFuture<Optional<TranslationResult>> translate(String source, Language from, Language to, GameText.Type context)
	{
		if (source == null || source.trim().isEmpty())
		{
			return CompletableFuture.completedFuture(Optional.empty());
		}

		loadIfNeeded();

		String key = source.trim().toLowerCase(Locale.ROOT);
		String translated = byEnglish == null ? null : byEnglish.get(key);
		if (translated == null || translated.isEmpty())
		{
			return CompletableFuture.completedFuture(Optional.empty());
		}

		return CompletableFuture.completedFuture(Optional.of(
			new TranslationResult(source, translated, to, TranslationSource.DICTIONARY)));
	}

	private synchronized void loadIfNeeded()
	{
		if (loadState != LoadState.NOT_LOADED)
		{
			return;
		}

		if (!cache.isAvailable())
		{
			log.debug("OSRS cache not found; the German pack provider is a no-op until the cache is downloaded");
			loadState = LoadState.UNAVAILABLE;
			byEnglish = Collections.emptyMap();
			return;
		}

		cache.load();
		if (!cache.isLoaded())
		{
			log.debug("OSRS cache index could not be parsed; German pack provider is a no-op");
			loadState = LoadState.UNAVAILABLE;
			byEnglish = Collections.emptyMap();
			return;
		}

		Optional<byte[]> bytes = cache.readEntry(GERMAN_LANGUAGE_GROUP, 0);
		if (!bytes.isPresent())
		{
			log.debug("OSRS German language pack is not in the cache. Launch OSRS in German once to download it.");
			loadState = LoadState.UNAVAILABLE;
			byEnglish = Collections.emptyMap();
			return;
		}

		Map<String, String> parsed = archiveParser.parse(bytes.get());
		byEnglish = archiveParser.lowercased(parsed);
		loadState = LoadState.LOADED;
		log.debug("Loaded {} German strings from the local OSRS cache", byEnglish.size());
	}

	/**
	 * Whether the German pack is present in the local cache.
	 * <p>
	 * Useful for the config panel: a checkbox that is greyed out until the cache is
	 * populated, with a tooltip explaining what to do.
	 */
	public boolean isGermanPackAvailable()
	{
		loadIfNeeded();
		return loadState == LoadState.LOADED && !byEnglish.isEmpty();
	}

	/**
	 * The number of strings currently loaded.
	 */
	public int loadedCount()
	{
		loadIfNeeded();
		return byEnglish == null ? 0 : byEnglish.size();
	}

	@Override
	public Plugin getPlugin()
	{
		return plugin;
	}

	private enum LoadState
	{
		NOT_LOADED,
		LOADED,
		UNAVAILABLE
	}
}
