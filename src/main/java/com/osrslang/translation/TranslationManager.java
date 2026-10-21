package com.osrslang.translation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import lombok.extern.slf4j.Slf4j;
import com.osrslang.translation.provider.TranslationProvider;
import com.osrslang.translation.provider.VocabularyEntry;
import com.osrslang.text.GameText;

/**
 * Chooses which provider answers a translation request.
 * <p>
 * The lookup order is deliberate and encodes the user's trust model:
 * <ol>
 * <li>the persistent cache, so identical text is never translated twice;</li>
 * <li>verified OSRS-specific translations, which a human checked for this game;</li>
 * <li>the local vocabulary dictionary, for single words;</li>
 * <li>previously stored sentences;</li>
 * <li>the offline machine translation engine;</li>
 * <li>the online provider, only when the user has explicitly enabled it.</li>
 * </ol>
 * A generic dictionary entry can never override a verified translation, because
 * providers are consulted in priority order and the first one to answer wins.
 */
@Slf4j
public class TranslationManager
{
	/**
	 * Consulted in priority order. The first provider that answers wins.
	 */
	private final List<TranslationProvider> providers = new ArrayList<>();

	/**
	 * Consulted only after every primary provider has declined. This is where the
	 * offline engine and the optional online provider live.
	 */
	private final List<TranslationProvider> fallbacks = new ArrayList<>();

	/**
	 * The persistent cache. May be {@code null} until it is wired up.
	 */
	private TranslationCache cache;

	public void setCache(TranslationCache cache)
	{
		this.cache = cache;
	}

	/**
	 * Registers a primary provider.
	 */
	public void register(TranslationProvider provider)
	{
		providers.add(provider);
		sortByPriority(providers);
	}

	/**
	 * Registers a fallback provider.
	 */
	public void registerFallback(TranslationProvider provider)
	{
		fallbacks.add(provider);
		sortByPriority(fallbacks);
	}

	/**
	 * The registered primary providers, in consultation order.
	 */
	public List<TranslationProvider> getProviders()
	{
		return new ArrayList<>(providers);
	}

	/**
	 * The registered fallback providers, in consultation order.
	 */
	public List<TranslationProvider> getFallbacks()
	{
		return new ArrayList<>(fallbacks);
	}

	/**
	 * Translates the given text.
	 *
	 * @param text    the text to translate
	 * @param from    the source language
	 * @param to      the target language
	 * @param context the kind of game text, used by specialised providers
	 * @return a future completing with the translation, or empty if nothing could
	 *         translate the text
	 */
	public CompletableFuture<Optional<TranslationResult>> translate(String text, Language from, Language to, GameText.Type context)
	{
		if (text == null || text.trim().isEmpty() || from == to)
		{
			return CompletableFuture.completedFuture(Optional.empty());
		}

		Optional<TranslationResult> cached = cacheGet(text, from, to);
		if (cached.isPresent())
		{
			log.debug("Cache hit for '{}'", text);
			return CompletableFuture.completedFuture(cached);
		}

		return ask(providers, 0, text, from, to, context)
			.thenCompose(found -> found.isPresent()
				? CompletableFuture.completedFuture(found)
				: ask(fallbacks, 0, text, from, to, context))
			.thenApply(found ->
			{
				if (found.isPresent())
				{
					cachePut(text, from, to, found.get());
				}
				return found;
			});
	}

	/**
	 * Asks the providers in the given list, starting at {@code startIndex}, and returns
	 * the first answer. Providers that decline are skipped.
	 */
	private CompletableFuture<Optional<TranslationResult>> ask(
		List<TranslationProvider> list, int startIndex, String text, Language from, Language to, GameText.Type context)
	{
		for (int i = startIndex; i < list.size(); i++)
		{
			TranslationProvider provider = list.get(i);
			if (!provider.isEnabled() || !provider.supports(from, to))
			{
				continue;
			}

			CompletableFuture<Optional<TranslationResult>> result;
			try
			{
				result = provider.translate(text, from, to, context);
			}
			catch (RuntimeException e)
			{
				log.warn("Provider {} failed on '{}'", provider.getName(), text, e);
				continue;
			}

			if (result == null)
			{
				continue;
			}

			final int nextIndex = i + 1;
			return result.thenCompose(found -> found.isPresent()
				? CompletableFuture.completedFuture(found)
				: ask(list, nextIndex, text, from, to, context));
		}

		return CompletableFuture.completedFuture(Optional.empty());
	}

	/**
	 * Looks up a single word, consulting the dictionary providers only.
	 */
	public Optional<VocabularyEntry> lookupWord(String word, Language from, Language to)
	{
		for (TranslationProvider provider : providers)
		{
			if (!provider.isEnabled() || !provider.supports(from, to))
			{
				continue;
			}

			Optional<VocabularyEntry> entry = provider.lookupWord(word, from, to);
			if (entry.isPresent())
			{
				return entry;
			}
		}

		return Optional.empty();
	}

	/**
	 * Returns the cached translation for the text, or empty if there is no cache or no
	 * cached entry.
	 */
	private Optional<TranslationResult> cacheGet(String text, Language from, Language to)
	{
		return cache == null ? Optional.empty() : cache.get(text, from, to);
	}

	/**
	 * Stores a translation in the cache, if a cache is present.
	 */
	private void cachePut(String text, Language from, Language to, TranslationResult result)
	{
		if (cache != null)
		{
			cache.put(text, from, to, result);
		}
	}

	private static void sortByPriority(List<TranslationProvider> list)
	{
		list.sort(Comparator.comparingInt(TranslationProvider::getPriority));
	}
}
