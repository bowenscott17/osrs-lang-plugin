package com.osrslang.translation.provider;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.junit.Before;
import org.junit.Test;
import com.osrslang.translation.Language;
import com.osrslang.translation.TranslationResult;
import com.osrslang.translation.TranslationSource;
import com.osrslang.text.GameText;

public class OnlineTranslationProviderTest
{
	private OnlineTranslationProvider provider;

	@Before
	public void setUp()
	{
		provider = new OnlineTranslationProvider(null, null, new Gson());
	}

	@Test
	public void testIsDisabledByDefault()
	{
		// The provider must never send anything unless the user opted in.
		assertFalse(provider.isEnabled());
	}

	@Test
	public void testDisabledProviderReturnsEmpty() throws ExecutionException, InterruptedException
	{
		Optional<TranslationResult> result = provider
			.translate("Hello", Language.ENGLISH, Language.GERMAN, GameText.Type.NPC_DIALOGUE)
			.get();

		assertFalse(result.isPresent());
	}

	@Test
	public void testDefaultServiceIsMyMemory()
	{
		assertEquals(OnlineTranslationProvider.Service.MYMEMORY, provider.getService());
		assertEquals(OnlineTranslationProvider.MYMEMORY_ENDPOINT, provider.getEndpoint());
	}

	@Test
	public void testServiceFromCode()
	{
		assertEquals(OnlineTranslationProvider.Service.MYMEMORY,
			OnlineTranslationProvider.Service.fromCode("mymemory"));
		assertEquals(OnlineTranslationProvider.Service.LIBRETRANSLATE,
			OnlineTranslationProvider.Service.fromCode("libretranslate"));
		assertEquals(OnlineTranslationProvider.Service.MYMEMORY,
			OnlineTranslationProvider.Service.fromCode("nonsense"));
		assertEquals(OnlineTranslationProvider.Service.MYMEMORY,
			OnlineTranslationProvider.Service.fromCode(null));
	}

	@Test
	public void testSwitchingServiceChangesEndpoint()
	{
		provider.setService(OnlineTranslationProvider.Service.LIBRETRANSLATE);

		assertEquals(OnlineTranslationProvider.LIBRETRANSLATE_ENDPOINT, provider.getEndpoint());
	}

	@Test
	public void testOnlineHasLowerPriorityThanOffline()
	{
		// The offline engine must be tried before the online one.
		OfflineMachineTranslationProvider offline = new OfflineMachineTranslationProvider(null, null, new Gson());

		assertTrue(offline.getPriority() < provider.getPriority());
	}

	@Test
	public void testMyMemoryResponseIsParsed()
	{
		String json = "{\"responseData\":{\"translatedText\":\"Hallo Welt\",\"match\":1},"
			+ "\"quotaFinished\":false,\"responseStatus\":200}";

		Optional<TranslationResult> result = invokeParse(json, "Hello World");

		assertTrue(result.isPresent());
		assertEquals("Hallo Welt", result.get().getTranslatedText());
		assertEquals(TranslationSource.ONLINE, result.get().getSource());
	}

	@Test
	public void testLibreTranslateResponseIsParsed()
	{
		String json = "{\"translatedText\":\"Hallo Welt\"}";

		Optional<TranslationResult> result = invokeParse(json, "Hello World");

		assertTrue(result.isPresent());
		assertEquals("Hallo Welt", result.get().getTranslatedText());
	}

	@Test
	public void testErrorResponseIsRejected()
	{
		String json = "{\"error\":\"Visit https://portal.libretranslate.com to get an API key\"}";

		assertFalse(invokeParse(json, "Hello").isPresent());
	}

	@Test
	public void testEmptyTranslationIsRejected()
	{
		assertFalse(invokeParse("{\"translatedText\":\"\"}", "Hello").isPresent());
		assertFalse(invokeParse("{}", "Hello").isPresent());
		assertFalse(invokeParse("not json", "Hello").isPresent());
	}

	/**
	 * Calls the provider's response parser through reflection-free means, by using a
	 * subclass that exposes it.
	 */
	private Optional<TranslationResult> invokeParse(String json, String source)
	{
		return new ExposedProvider(json).parse(json, source, Language.GERMAN);
	}

	/**
	 * Exposes the protected parser so the response shapes can be tested.
	 */
	private static class ExposedProvider extends OnlineTranslationProvider
	{
		private final String json;

		ExposedProvider(String json)
		{
			super(null, null, new Gson());
			this.json = json;
		}

		Optional<TranslationResult> parse(String body, String source, Language to)
		{
			return parseResponse(body, source, to);
		}
	}
}
