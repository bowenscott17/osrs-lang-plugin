package com.osrslang.translation.provider;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.Plugin;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import com.osrslang.translation.Language;
import com.osrslang.translation.TranslationResult;
import com.osrslang.translation.TranslationSource;
import com.osrslang.text.GameText;

/**
 * An optional online translation provider.
 * <p>
 * This provider is disabled unless the user explicitly turns online translation on, and
 * the config item that turns it on carries the warning RuneLite requires for any
 * feature that talks to a third-party server.
 * <p>
 * The default endpoint is a LibreTranslate-compatible API. Both LibreTranslate and
 * MyMemory speak a JSON dialect, so the same request and response handling works for
 * either; only the base URL and the source/target parameter names differ, and those
 * are handled by the request builder.
 */
@Slf4j
public class OnlineTranslationProvider implements TranslationProvider
{
	/**
	 * The default endpoint. LibreTranslate-compatible.
	 */
	public static final String DEFAULT_ENDPOINT = "https://libretranslate.com";

	/**
	 * How many requests may be made per minute.
	 */
	private static final int RATE_LIMIT_PER_MINUTE = 20;

	/**
	 * How long to wait before a request is considered failed.
	 */
	private static final int TIMEOUT_SECONDS = 15;

	private final Plugin plugin;
	private final OkHttpClient httpClient;
	private final Gson gson;
	private final ScheduledExecutorService scheduler;

	/**
	 * Requests made in the current window, used for rate limiting.
	 */
	private final AtomicInteger requestsThisWindow = new AtomicInteger();

	/**
	 * When the current rate-limit window ends.
	 */
	private volatile long windowEndsAt;

	/**
	 * Requests currently in flight, so the same text is not sent twice at once.
	 */
	private final Map<String, CompletableFuture<Optional<TranslationResult>>> inFlight = new ConcurrentHashMap<>();

	/**
	 * Whether the user has enabled online translation.
	 */
	private volatile boolean enabled;

	/**
	 * The endpoint to use. May be changed by the config.
	 */
	private volatile String endpoint = DEFAULT_ENDPOINT;

	public OnlineTranslationProvider(Plugin plugin, OkHttpClient httpClient, Gson gson, ScheduledExecutorService scheduler)
	{
		this.plugin = plugin;
		this.httpClient = httpClient;
		this.gson = gson;
		this.scheduler = scheduler;
	}

	/**
	 * Turns the provider on or off. This mirrors the config setting.
	 */
	public void setEnabled(boolean enabled)
	{
		this.enabled = enabled;
	}

	/**
	 * Sets the endpoint to use.
	 */
	public void setEndpoint(String endpoint)
	{
		this.endpoint = endpoint == null || endpoint.trim().isEmpty() ? DEFAULT_ENDPOINT : endpoint.trim();
	}

	public String getEndpoint()
	{
		return endpoint;
	}

	@Override
	public String getName()
	{
		return "Online translation";
	}

	@Override
	public boolean isEnabled()
	{
		// Never sends anything unless the user has explicitly turned it on.
		return enabled;
	}

	@Override
	public boolean supports(Language source, Language target)
	{
		return source != target;
	}

	@Override
	public int getPriority()
	{
		// Consulted last, after the offline engine.
		return 90;
	}

	@Override
	public CompletableFuture<Optional<TranslationResult>> translate(String source, Language from, Language to, GameText.Type context)
	{
		if (!enabled || source == null || source.trim().isEmpty())
		{
			return CompletableFuture.completedFuture(Optional.empty());
		}

		String key = from.getCode() + "|" + to.getCode() + "|" + source;

		CompletableFuture<Optional<TranslationResult>> existing = inFlight.get(key);
		if (existing != null)
		{
			return existing;
		}

		CompletableFuture<Optional<TranslationResult>> promise = new CompletableFuture<>();
		if (inFlight.putIfAbsent(key, promise) != null)
		{
			return inFlight.get(key);
		}

		makeRequest(source, from, to, key, promise);
		return promise;
	}

	/**
	 * Performs the HTTP request on the OkHttp thread pool.
	 */
	private void makeRequest(String text, Language from, Language to, String key,
		CompletableFuture<Optional<TranslationResult>> promise)
	{
		if (!reserveRequestSlot())
		{
			inFlight.remove(key);
			promise.complete(Optional.empty());
			return;
		}

		// The query string carries the text, which keeps the request compatible with
		// every LibreTranslate-compatible endpoint without needing a JSON body.
		HttpUrl url = HttpUrl.parse(endpoint + "/translate")
			.newBuilder()
			.addQueryParameter("q", text)
			.addQueryParameter("source", from.getCode())
			.addQueryParameter("target", to.getCode())
			.addQueryParameter("format", "text")
			.build();

		Request request = new Request.Builder()
			.url(url)
			.get()
			.build();

		httpClient.newCall(request).enqueue(new okhttp3.Callback()
		{
			@Override
			public void onFailure(okhttp3.Call call, IOException e)
			{
				inFlight.remove(key);
				log.debug("Online translation request failed", e);
				promise.complete(Optional.empty());
			}

			@Override
			public void onResponse(okhttp3.Call call, Response response)
			{
				try (ResponseBody body = response.body())
				{
					if (!response.isSuccessful() || body == null)
					{
						log.debug("Online translation returned HTTP {}", response.code());
						promise.complete(Optional.empty());
						return;
					}

					String json = body.string();
					Optional<TranslationResult> result = parseResponse(json, text, to);

					promise.complete(result);
				}
				catch (IOException | RuntimeException e)
				{
					log.debug("Failed to read online translation response", e);
					promise.complete(Optional.empty());
				}
				finally
				{
					inFlight.remove(key);
				}
			}
		});
	}

	/**
	 * Parses a LibreTranslate-compatible response.
	 */
	private Optional<TranslationResult> parseResponse(String json, String source, Language to)
	{
		try
		{
			JsonElement element = new JsonParser().parse(json);
			if (!element.isJsonObject())
			{
				return Optional.empty();
			}

			JsonObject object = element.getAsJsonObject();

			if (object.has("error"))
			{
				log.debug("Online translation error: {}", object.get("error").getAsString());
				return Optional.empty();
			}

			if (!object.has("translatedText"))
			{
				return Optional.empty();
			}

			String translated = object.get("translatedText").getAsString();
			if (translated == null || translated.trim().isEmpty())
			{
				return Optional.empty();
			}

			return Optional.of(new TranslationResult(source, translated, to, TranslationSource.ONLINE));
		}
		catch (RuntimeException e)
		{
			log.debug("Failed to parse online translation response", e);
			return Optional.empty();
		}
	}

	/**
	 * Reserves a slot in the current rate-limit window.
	 *
	 * @return {@code true} if the request may proceed
	 */
	private boolean reserveRequestSlot()
	{
		long now = System.currentTimeMillis();

		if (now >= windowEndsAt)
		{
			windowEndsAt = now + TimeUnit.MINUTES.toMillis(1);
			requestsThisWindow.set(0);
		}

		return requestsThisWindow.incrementAndGet() <= RATE_LIMIT_PER_MINUTE;
	}

	/**
	 * The number of requests made in the current window.
	 */
	public int getRequestsThisWindow()
	{
		return requestsThisWindow.get();
	}

	/**
	 * Cancels anything pending.
	 */
	public void shutdown()
	{
		inFlight.clear();
	}

	@Override
	public Plugin getPlugin()
	{
		return plugin;
	}
}
