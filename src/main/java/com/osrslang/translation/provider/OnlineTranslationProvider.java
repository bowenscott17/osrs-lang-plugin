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
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;
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
 * The provider is disabled unless the user explicitly turns online translation on, and
 * the config item that turns it on carries the warning RuneLite requires for any
 * feature that talks to a third-party server.
 * <p>
 * Two service shapes are supported:
 * <ul>
 * <li><b>MyMemory</b> (the default) - free, no API key required, and works without
 * any sign-up. The free anonymous tier is limited to 5,000 characters per day, so it
 * suits occasional use rather than heavy translation.</li>
 * <li><b>LibreTranslate</b> - a self-hosted or paid instance. The hosted
 * {@code libretranslate.com} service requires a paid API key; a self-hosted instance
 * needs none. This is the shape to use if the user runs their own service.</li>
 * </ul>
 * No API key is hard-coded anywhere. A key, if the user's service needs one, is read
 * from the config and sent as a parameter.
 */
@Slf4j
public class OnlineTranslationProvider implements TranslationProvider
{
	/**
	 * The free MyMemory endpoint. No key required.
	 */
	public static final String MYMEMORY_ENDPOINT = "https://api.mymemory.translated.net";

	/**
	 * A LibreTranslate instance. The hosted one requires a paid key; a self-hosted one
	 * does not.
	 */
	public static final String LIBRETRANSLATE_ENDPOINT = "https://libretranslate.com";

	/**
	 * How many requests may be made per minute.
	 */
	private static final int RATE_LIMIT_PER_MINUTE = 20;

	/**
	 * The online service to use.
	 */
	public enum Service
	{
		MYMEMORY("MyMemory (free, no key)"),
		LIBRETRANSLATE("LibreTranslate");

		private final String displayName;

		Service(String displayName)
		{
			this.displayName = displayName;
		}

		public String getDisplayName()
		{
			return displayName;
		}

		public String getCode()
		{
			return name().toLowerCase(java.util.Locale.ROOT);
		}

		public static Service fromCode(String code)
		{
			if (code != null)
			{
				for (Service service : values())
				{
					if (service.getCode().equalsIgnoreCase(code.trim()))
					{
						return service;
					}
				}
			}
			return MYMEMORY;
		}
	}

	private final Plugin plugin;
	private final OkHttpClient httpClient;
	private final Gson gson;

	/**
	 * Requests made in the current rate-limit window.
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
	 * Which service to use.
	 */
	private volatile Service service = Service.MYMEMORY;

	/**
	 * The endpoint of the chosen service.
	 */
	private volatile String endpoint = MYMEMORY_ENDPOINT;

	/**
	 * An API key, if the user's service needs one. Never hard-coded.
	 */
	private volatile String apiKey = "";

	public OnlineTranslationProvider(Plugin plugin, OkHttpClient httpClient, Gson gson)
	{
		this.plugin = plugin;
		this.httpClient = httpClient;
		this.gson = gson;
	}

	/**
	 * Turns the provider on or off. This mirrors the config setting.
	 */
	public void setEnabled(boolean enabled)
	{
		this.enabled = enabled;
	}

	/**
	 * Chooses which service to use.
	 */
	public void setService(Service service)
	{
		this.service = service;
		this.endpoint = service == Service.MYMEMORY ? MYMEMORY_ENDPOINT : LIBRETRANSLATE_ENDPOINT;
	}

	public Service getService()
	{
		return service;
	}

	/**
	 * Overrides the endpoint, for a self-hosted instance.
	 */
	public void setEndpoint(String endpoint)
	{
		if (endpoint == null || endpoint.trim().isEmpty())
		{
			this.endpoint = service == Service.MYMEMORY ? MYMEMORY_ENDPOINT : LIBRETRANSLATE_ENDPOINT;
		}
		else
		{
			this.endpoint = endpoint.trim();
		}
	}

	public String getEndpoint()
	{
		return endpoint;
	}

	/**
	 * Sets the API key for services that need one.
	 */
	public void setApiKey(String apiKey)
	{
		this.apiKey = apiKey == null ? "" : apiKey.trim();
	}

	@Override
	public String getName()
	{
		return "Online translation (" + service.getDisplayName() + ")";
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
			log.debug("Online translation rate limit reached, skipping '{}'", text);
			promise.complete(Optional.empty());
			return;
		}

		Request request = service == Service.MYMEMORY
			? buildMyMemoryRequest(text, from, to)
			: buildLibreTranslateRequest(text, from, to);

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
						inFlight.remove(key);
						log.debug("Online translation returned HTTP {}", response.code());
						promise.complete(Optional.empty());
						return;
					}

					Optional<TranslationResult> result = parseResponse(body.string(), text, to);

					promise.complete(result);
				}
				catch (IOException | RuntimeException e)
				{
					inFlight.remove(key);
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
	 * Builds a MyMemory request. MyMemory uses {@code langpair} and answers with
	 * {@code responseData.translatedText}.
	 */
	private Request buildMyMemoryRequest(String text, Language from, Language to)
	{
		HttpUrl.Builder url = HttpUrl.parse(endpoint + "/get")
			.newBuilder()
			.addQueryParameter("q", text)
			.addQueryParameter("langpair", from.getCode() + "|" + to.getCode());

		if (!apiKey.isEmpty())
		{
			url.addQueryParameter("key", apiKey);
		}

		return new Request.Builder().url(url.build()).get().build();
	}

	/**
	 * Builds a LibreTranslate request.
	 */
	private Request buildLibreTranslateRequest(String text, Language from, Language to)
	{
		HttpUrl.Builder url = HttpUrl.parse(endpoint + "/translate")
			.newBuilder()
			.addQueryParameter("q", text)
			.addQueryParameter("source", from.getCode())
			.addQueryParameter("target", to.getCode())
			.addQueryParameter("format", "text");

		if (!apiKey.isEmpty())
		{
			url.addQueryParameter("api_key", apiKey);
		}

		return new Request.Builder().url(url.build()).get().build();
	}

	/**
	 * Parses the response, handling both service shapes.
	 */
	Optional<TranslationResult> parseResponse(String json, String source, Language to)
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

			String translated = null;

			if (object.has("responseData"))
			{
				// MyMemory shape.
				JsonElement responseData = object.get("responseData");
				if (responseData.isJsonObject() && responseData.getAsJsonObject().has("translatedText"))
				{
					translated = responseData.getAsJsonObject().get("translatedText").getAsString();
				}
			}
			else if (object.has("translatedText"))
			{
				translated = object.get("translatedText").getAsString();
			}
			else if (object.has("translated_text"))
			{
				translated = object.get("translated_text").getAsString();
			}

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
			windowEndsAt = now + java.util.concurrent.TimeUnit.MINUTES.toMillis(1);
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
