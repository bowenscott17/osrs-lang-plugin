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
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.Plugin;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import com.osrslang.translation.Language;
import com.osrslang.translation.TranslationResult;
import com.osrslang.translation.TranslationSource;
import com.osrslang.text.GameText;

/**
 * Offline machine translation, provided by a LibreTranslate service running on the
 * player's own machine.
 * <p>
 * The plugin does not bundle or start the service. RuneLite's plugin rules forbid
 * launching external processes and forbid JNI, so an in-process translation engine is
 * not possible. Instead the user installs LibreTranslate (which wraps the MIT-licensed
 * Argos Translate engine) once, and the plugin talks to it over plain HTTP on
 * {@code 127.0.0.1:5000}. Everything stays on the machine: no game text leaves the
 * computer.
 * <p>
 * Setup, for the user:
 * <pre>
 *   pip install libretranslate
 *   libretranslate --load-only en,de,fr,es,ja
 * </pre>
 * or, with Docker:
 * <pre>
 *   docker compose up
 * </pre>
 * The models are downloaded once by LibreTranslate itself and live in its own data
 * directory, so the plugin needs no model handling of its own.
 */
@Slf4j
public class OfflineMachineTranslationProvider implements TranslationProvider
{
	/**
	 * The default address of a locally running LibreTranslate.
	 * <p>
	 * {@code 127.0.0.1} rather than {@code localhost}: on Windows, {@code localhost}
	 * can resolve to IPv6 {@code ::1}, while LibreTranslate binds to IPv4 by default.
	 */
	public static final String DEFAULT_ENDPOINT = "http://127.0.0.1:5000";

	/**
	 * How long to wait for the local service before giving up.
	 */
	private static final long TIMEOUT_SECONDS = 10;

	private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

	private final Plugin plugin;
	private final OkHttpClient httpClient;
	private final Gson gson;

	/**
	 * Requests currently in flight, so the same text is not sent twice at once.
	 */
	private final Map<String, CompletableFuture<Optional<TranslationResult>>> inFlight = new ConcurrentHashMap<>();

	/**
	 * Whether the offline engine is enabled by the user.
	 */
	private volatile boolean enabled;

	/**
	 * The address of the LibreTranslate service.
	 */
	private volatile String endpoint = DEFAULT_ENDPOINT;

	/**
	 * Whether the service answered the last time we asked. Shown in the config panel so
	 * the user knows whether the engine is actually available.
	 */
	private volatile boolean serviceAvailable;

	public OfflineMachineTranslationProvider(Plugin plugin, OkHttpClient httpClient, Gson gson)
	{
		this.plugin = plugin;
		this.httpClient = httpClient;
		this.gson = gson;
	}

	/**
	 * Turns the provider on or off.
	 */
	public void setEnabled(boolean enabled)
	{
		this.enabled = enabled;
	}

	/**
	 * Sets the address of the LibreTranslate service.
	 */
	public void setEndpoint(String endpoint)
	{
		if (endpoint == null || endpoint.trim().isEmpty())
		{
			this.endpoint = DEFAULT_ENDPOINT;
		}
		else
		{
			String trimmed = endpoint.trim();

			// LibreTranslate is a plain HTTP service on the user's own machine.
			if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://"))
			{
				trimmed = "http://" + trimmed;
			}

			this.endpoint = trimmed;
		}
	}

	public String getEndpoint()
	{
		return endpoint;
	}

	/**
	 * Whether the service answered the last request.
	 */
	public boolean isServiceAvailable()
	{
		return serviceAvailable;
	}

	@Override
	public String getName()
	{
		return "Offline machine translation (LibreTranslate)";
	}

	@Override
	public boolean isEnabled()
	{
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
		// Consulted after the verified database and the dictionary, but before the
		// online provider.
		return 70;
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
	 * Sends the request on the OkHttp thread pool.
	 */
	private void makeRequest(String text, Language from, Language to, String key,
		CompletableFuture<Optional<TranslationResult>> promise)
	{
		JsonObject body = new JsonObject();
		body.addProperty("q", text);
		body.addProperty("source", from.getCode());
		body.addProperty("target", to.getCode());
		body.addProperty("format", "text");

		Request request = new Request.Builder()
			.url(endpoint + "/translate")
			.post(RequestBody.create(JSON, body.toString()))
			.build();

		httpClient.newCall(request).enqueue(new okhttp3.Callback()
		{
			@Override
			public void onFailure(okhttp3.Call call, IOException e)
			{
				inFlight.remove(key);
				serviceAvailable = false;
				log.debug("Offline translation service is not reachable at {}", endpoint);
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
						serviceAvailable = false;
						log.debug("Offline translation service returned HTTP {}", response.code());
						promise.complete(Optional.empty());
						return;
					}

					String json = body.string();
					Optional<TranslationResult> result = parseResponse(json, text, to);

					serviceAvailable = result.isPresent();

					promise.complete(result);
				}
				catch (IOException | RuntimeException e)
				{
					inFlight.remove(key);
					serviceAvailable = false;
					log.debug("Failed to read offline translation response", e);
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
	 * Parses a LibreTranslate response.
	 * <p>
	 * LibreTranslate answers with {@code translatedText}. Some compatible services use
	 * {@code translated_text} instead, so both are accepted.
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
				log.debug("Offline translation error: {}", object.get("error").getAsString());
				return Optional.empty();
			}

			String translated = null;

			if (object.has("translatedText"))
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

			return Optional.of(new TranslationResult(source, translated, to, TranslationSource.OFFLINE_MACHINE));
		}
		catch (RuntimeException e)
		{
			log.debug("Failed to parse offline translation response", e);
			return Optional.empty();
		}
	}

	/**
	 * Checks whether the service is running, without translating anything.
	 * <p>
	 * Used to show the user whether their setup is working.
	 */
	public CompletableFuture<Boolean> checkAvailability()
	{
		CompletableFuture<Boolean> promise = new CompletableFuture<>();

		Request request = new Request.Builder()
			.url(endpoint + "/languages")
			.get()
			.build();

		httpClient.newCall(request).enqueue(new okhttp3.Callback()
		{
			@Override
			public void onFailure(okhttp3.Call call, IOException e)
			{
				serviceAvailable = false;
				promise.complete(false);
			}

			@Override
			public void onResponse(okhttp3.Call call, Response response)
			{
				try (ResponseBody body = response.body())
				{
					boolean available = response.isSuccessful() && body != null;
					serviceAvailable = available;
					promise.complete(available);
				}
				finally
				{
					response.close();
				}
			}
		});

		return promise;
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
