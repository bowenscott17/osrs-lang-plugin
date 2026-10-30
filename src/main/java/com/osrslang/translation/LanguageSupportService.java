package com.osrslang.translation;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.LongSupplier;
import lombok.extern.slf4j.Slf4j;
import com.osrslang.translation.provider.OfflineMachineTranslationProvider;

/**
 * Keeps track of which languages the local translation service can actually handle.
 * <p>
 * The set is fetched once from the service's {@code /languages} endpoint and then reused.
 * Asking for it on every render would be a network request per frame, so the list is
 * cached and only refreshed when it has gone stale or the service address changed. While
 * nothing is known yet, every pair is treated as possible - a service that simply is not
 * running must not make the plugin claim a pair is unsupported.
 */
@Slf4j
public class LanguageSupportService implements WordTranslationService.PairSupport
{
	/**
	 * How long a fetched language list is trusted. Models can be installed or removed
	 * while the client is open, so the list cannot be trusted forever.
	 */
	static final long DEFAULT_TTL_MILLIS = 30L * 60L * 1000L;

	private final OfflineMachineTranslationProvider provider;

	private final long ttlMillis;

	private final LongSupplier clock;

	/**
	 * The last list successfully fetched. Empty when nothing has been fetched yet.
	 */
	private volatile List<ProviderLanguage> languages = Collections.emptyList();

	private volatile long fetchedAt;

	/**
	 * Whether a fetch has ever completed. Distinguishes "not fetched yet" from "fetched,
	 * and the service supports nothing".
	 */
	private volatile boolean fetched;

	/**
	 * A fetch currently running, so repeated refreshes join instead of piling up.
	 */
	private volatile CompletableFuture<List<ProviderLanguage>> inFlight;

	public LanguageSupportService(OfflineMachineTranslationProvider provider)
	{
		this(provider, DEFAULT_TTL_MILLIS, System::currentTimeMillis);
	}

	LanguageSupportService(OfflineMachineTranslationProvider provider, long ttlMillis, LongSupplier clock)
	{
		this.provider = provider;
		this.ttlMillis = ttlMillis;
		this.clock = clock;
	}

	/**
	 * Fetches the language list if the cached one has gone stale.
	 * <p>
	 * Does nothing while a fetch is already running, and nothing if the list is still
	 * fresh, so this is safe to call as often as the client tick likes.
	 *
	 * @return a future completing with the current list, or {@code null} when no request
	 *         was made because the cached list was still fresh
	 */
	public CompletableFuture<List<ProviderLanguage>> refreshIfStale()
	{
		if (isFresh())
		{
			return null;
		}

		return refresh();
	}

	/**
	 * Fetches the language list unconditionally.
	 * <p>
	 * A fetch already running is joined rather than duplicated.
	 */
	public CompletableFuture<List<ProviderLanguage>> refresh()
	{
		CompletableFuture<List<ProviderLanguage>> existing = inFlight;
		if (existing != null && !existing.isDone())
		{
			return existing;
		}

		CompletableFuture<List<ProviderLanguage>> promise = provider.fetchLanguages();
		inFlight = promise;

		promise.whenComplete((result, error) ->
		{
			inFlight = null;

			if (error != null)
			{
				log.debug("Could not fetch the translation service's language list", error);
				return;
			}

			languages = result == null ? Collections.<ProviderLanguage>emptyList() : result;
			fetchedAt = clock.getAsLong();
			fetched = true;
		});

		return promise;
	}

	/**
	 * Whether a fetch has completed at least once.
	 */
	public boolean isLoaded()
	{
		return fetched;
	}

	/**
	 * Whether the cached list is still considered current.
	 */
	public boolean isFresh()
	{
		return fetched && clock.getAsLong() - fetchedAt < ttlMillis;
	}

	/**
	 * The languages the service reported, in the order it listed them.
	 */
	public List<ProviderLanguage> getLanguages()
	{
		return languages;
	}

	/**
	 * Whether the service can translate between the two languages.
	 * <p>
	 * Returns {@code true} while nothing is known about the service, so an unreachable
	 * service is never reported as one that lacks the pair.
	 */
	@Override
	public boolean isSupported(Language from, Language to)
	{
		if (from == null || to == null || from.isSameLanguage(to))
		{
			return false;
		}

		if (!fetched || languages.isEmpty())
		{
			return true;
		}

		Optional<ProviderLanguage> source = find(from);
		Optional<ProviderLanguage> target = find(to);

		return source.isPresent() && target.isPresent() && source.get().canTranslateTo(to);
	}

	/**
	 * The reported language matching the given code, ignoring case and any region suffix.
	 */
	private Optional<ProviderLanguage> find(Language language)
	{
		String code = language.getCode();

		for (ProviderLanguage candidate : languages)
		{
			if (candidate.getCode().equalsIgnoreCase(code))
			{
				return Optional.of(candidate);
			}
		}

		int dash = code.indexOf('-');
		if (dash > 0)
		{
			String base = code.substring(0, dash);

			for (ProviderLanguage candidate : languages)
			{
				if (candidate.getCode().equalsIgnoreCase(base))
				{
					return Optional.of(candidate);
				}
			}
		}

		return Optional.empty();
	}

	/**
	 * A short description of the service's language support, for the settings panel.
	 */
	public String getStatus()
	{
		if (!fetched)
		{
			return "Not checked yet";
		}

		if (languages.isEmpty())
		{
			return "Translation service not reachable - only bundled dictionaries are available";
		}

		return languages.size() + " languages available from the translation service";
	}

	/**
	 * Forgets the cached list, so the next refresh fetches again.
	 * <p>
	 * Called when the user points the plugin at a different service address, where the
	 * previous answer no longer applies.
	 */
	public void invalidate()
	{
		languages = Collections.emptyList();
		fetched = false;
		fetchedAt = 0;
	}
}