package com.osrslang.translation.provider;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.Plugin;
import com.osrslang.translation.Language;
import com.osrslang.translation.TranslationResult;
import com.osrslang.translation.TranslationSource;
import com.osrslang.text.GameText;

/**
 * Provides human-verified translations of specific OSRS text.
 * <p>
 * These are entries that were written or checked by a human for a specific game phrase,
 * item name, NPC name or term. They outrank the generic dictionary: a generic dictionary
 * entry must never override a verified phrase.
 * <p>
 * Entries are keyed by the exact source text, so a whole sentence or a specific item
 * name can be matched precisely.
 */
@Slf4j
public class OsrsTranslationDatabaseProvider implements TranslationProvider
{
	static final String DATABASE_RESOURCE = "/osrs-translations/%s.json";

	/**
	 * One verified entry as stored on disk.
	 */
	static class VerifiedEntry
	{
		String text;
		String translation;
		/**
		 * One of: item, item-description, npc, quest, skill, activity, term, sentence.
		 */
		String category;
		/**
		 * A stable identifier, such as an item id, when one exists.
		 */
		String id;
	}

	/**
	 * The database as stored on disk: a list of entries.
	 */
	static class Database
	{
		List<VerifiedEntry> entries = new ArrayList<>();
	}

	private final Plugin plugin;
	private final Gson gson;

	/**
	 * Keyed by language code pair, then by lowercase source text.
	 */
	private final Map<String, Map<String, TranslationResult>> databases = new HashMap<>();

	/**
	 * Keyed by language code pair, then by item id.
	 */
	private final Map<String, Map<String, TranslationResult>> byId = new HashMap<>();

	public OsrsTranslationDatabaseProvider(Plugin plugin, Gson gson)
	{
		this.plugin = plugin;
		this.gson = gson;
	}

	/**
	 * Loads the verified database for a language pair.
	 */
	public synchronized void load(Language from, Language to)
	{
		String key = key(from, to);
		if (databases.containsKey(key))
		{
			return;
		}

		Map<String, TranslationResult> byText = new HashMap<>();
		Map<String, TranslationResult> byIdentifier = new HashMap<>();

		String resource = String.format(DATABASE_RESOURCE, key);
		try (InputStream in = OsrsTranslationDatabaseProvider.class.getResourceAsStream(resource))
		{
			if (in == null)
			{
				log.debug("No verified OSRS database for {}", key);
				databases.put(key, byText);
				byId.put(key, byIdentifier);
				return;
			}

			Type type = new TypeToken<Database>()
			{
			}.getType();

			try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8))
			{
				Database database = gson.fromJson(reader, type);
				if (database != null && database.entries != null)
				{
					for (VerifiedEntry entry : database.entries)
					{
						if (entry == null || entry.text == null || entry.translation == null)
						{
							continue;
						}

						TranslationResult result = new TranslationResult(
							entry.text, entry.translation, to, TranslationSource.VERIFIED);

						byText.put(entry.text.toLowerCase(Locale.ROOT), result);

						if (entry.id != null && !entry.id.isEmpty())
						{
							byIdentifier.put(entry.id, result);
						}
					}
				}
			}
		}
		catch (IOException | RuntimeException e)
		{
			log.warn("Failed to load verified OSRS database {}", key, e);
		}

		log.debug("Loaded {} verified entries for {}", byText.size(), key);
		databases.put(key, byText);
		byId.put(key, byIdentifier);
	}

	@Override
	public String getName()
	{
		return "Verified OSRS translations";
	}

	@Override
	public boolean isEnabled()
	{
		return true;
	}

	@Override
	public boolean supports(Language source, Language target)
	{
		return source != target;
	}

	@Override
	public int getPriority()
	{
		// Highest priority: a verified translation always wins.
		return 10;
	}

	@Override
	public CompletableFuture<Optional<TranslationResult>> translate(String source, Language from, Language to, GameText.Type context)
	{
		Map<String, TranslationResult> entries = databases.get(key(from, to));
		if (entries == null)
		{
			return CompletableFuture.completedFuture(Optional.empty());
		}

		TranslationResult result = entries.get(source.toLowerCase(Locale.ROOT));
		return CompletableFuture.completedFuture(Optional.ofNullable(result));
	}

	/**
	 * Looks up a verified translation by its stable identifier, such as an item id.
	 */
	public Optional<TranslationResult> lookupById(String id, Language from, Language to)
	{
		Map<String, TranslationResult> entries = byId.get(key(from, to));
		if (entries == null)
		{
			return Optional.empty();
		}
		return Optional.ofNullable(entries.get(id));
	}

	/**
	 * The number of verified entries loaded for a language pair.
	 */
	public int size(Language from, Language to)
	{
		Map<String, TranslationResult> entries = databases.get(key(from, to));
		return entries == null ? 0 : entries.size();
	}

	private static String key(Language from, Language to)
	{
		return from.getCode() + "-" + to.getCode();
	}

	@Override
	public Plugin getPlugin()
	{
		return plugin;
	}
}
