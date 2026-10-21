package com.osrslang.translation;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import com.osrslang.text.GameText;

/**
 * Runs translation requests off the client thread and de-duplicates identical requests.
 * <p>
 * The game can redraw the same dialogue many times a second. Without de-duplication the
 * same sentence would be sent to the translation engine dozens of times, so every
 * in-flight request is keyed by its text and language pair, and a request for text that
 * is already being translated simply attaches to the existing future.
 */
@Slf4j
public class TranslationService
{
	private final TranslationManager manager;

	/**
	 * The thread pool translations run on. Separate from the client thread so a slow
	 * engine can never stall the game.
	 */
	private final ExecutorService executor;

	/**
	 * In-flight requests, keyed by text and language pair.
	 */
	private final Map<String, CompletableFuture<Optional<TranslationResult>>> inFlight = new ConcurrentHashMap<>();

	public TranslationService(TranslationManager manager, ExecutorService executor)
	{
		this.manager = manager;
		this.executor = executor;
	}

	/**
	 * Requests a translation.
	 * <p>
	 * The returned future completes on a background thread, never on the client thread.
	 *
	 * @param text    the text to translate
	 * @param from    the source language
	 * @param to      the target language
	 * @param context the kind of game text
	 * @return a future completing with the translation, or empty if nothing could
	 *         translate the text
	 */
	public CompletableFuture<Optional<TranslationResult>> request(String text, Language from, Language to, GameText.Type context)
	{
		if (text == null || text.trim().isEmpty())
		{
			return CompletableFuture.completedFuture(Optional.empty());
		}

		String key = key(text, from, to);

		// If the same text is already being translated, attach to that request instead
		// of starting a second one.
		CompletableFuture<Optional<TranslationResult>> existing = inFlight.get(key);
		if (existing != null)
		{
			log.debug("Joining in-flight translation for '{}'", text);
			return existing;
		}

		CompletableFuture<Optional<TranslationResult>> promise = new CompletableFuture<>();
		CompletableFuture<Optional<TranslationResult>> raced = inFlight.putIfAbsent(key, promise);

		if (raced != null)
		{
			return raced;
		}

		executor.execute(() ->
		{
			try
			{
				manager.translate(text, from, to, context)
					.whenComplete((result, error) ->
					{
						inFlight.remove(key);

						if (error != null)
						{
							log.warn("Translation failed for '{}'", text, error);
							promise.complete(Optional.empty());
						}
						else
						{
							promise.complete(result);
						}
					});
			}
			catch (RuntimeException e)
			{
				inFlight.remove(key);
				log.warn("Translation request failed for '{}'", text, e);
				promise.complete(Optional.empty());
			}
		});

		return promise;
	}

	/**
	 * The number of translations currently in flight.
	 */
	public int inFlightCount()
	{
		return inFlight.size();
	}

	/**
	 * Stops the worker threads.
	 */
	public void shutdown()
	{
		inFlight.clear();
	}

	private static String key(String text, Language from, Language to)
	{
		return from.getCode() + "|" + to.getCode() + "|" + text;
	}
}
