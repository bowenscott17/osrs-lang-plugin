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

public class OfflineMachineTranslationProviderTest
{
	private OfflineMachineTranslationProvider provider;

	@Before
	public void setUp()
	{
		provider = new OfflineMachineTranslationProvider(null, null, new Gson());
	}

	@Test
	public void testDefaultEndpointIsLocalhost()
	{
		// 127.0.0.1 rather than localhost, which can resolve to IPv6 on Windows while
		// LibreTranslate binds to IPv4.
		assertEquals("http://127.0.0.1:5000", provider.getEndpoint());
	}

	@Test
	public void testEndpointGetsHttpScheme()
	{
		provider.setEndpoint("127.0.0.1:5000");

		assertEquals("http://127.0.0.1:5000", provider.getEndpoint());
	}

	@Test
	public void testEndpointKeepsExplicitScheme()
	{
		provider.setEndpoint("https://translate.example.com");

		assertEquals("https://translate.example.com", provider.getEndpoint());
	}

	@Test
	public void testEmptyEndpointFallsBackToDefault()
	{
		provider.setEndpoint("");

		assertEquals("http://127.0.0.1:5000", provider.getEndpoint());
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
	public void testLibreTranslateResponseIsParsed()
	{
		String json = "{\"translatedText\":\"Hallo Welt\",\"detectedLanguage\":{\"confidence\":90.0,\"language\":\"en\"}}";

		Optional<TranslationResult> result = provider.parseResponse(json, "Hello World", Language.GERMAN);

		assertTrue(result.isPresent());
		assertEquals("Hallo Welt", result.get().getTranslatedText());
		assertEquals(TranslationSource.OFFLINE_MACHINE, result.get().getSource());
	}

	@Test
	public void testSnakeCaseResponseIsAlsoAccepted()
	{
		// Some LibreTranslate-compatible services answer with translated_text.
		String json = "{\"translated_text\":\"Hallo Welt\"}";

		Optional<TranslationResult> result = provider.parseResponse(json, "Hello World", Language.GERMAN);

		assertTrue(result.isPresent());
		assertEquals("Hallo Welt", result.get().getTranslatedText());
	}

	@Test
	public void testErrorResponseIsRejected()
	{
		assertFalse(provider.parseResponse("{\"error\":\"no model installed\"}", "Hi", Language.GERMAN).isPresent());
	}

	@Test
	public void testEmptyTranslationIsRejected()
	{
		assertFalse(provider.parseResponse("{\"translatedText\":\"\"}", "Hi", Language.GERMAN).isPresent());
		assertFalse(provider.parseResponse("{}", "Hi", Language.GERMAN).isPresent());
		assertFalse(provider.parseResponse("garbage", "Hi", Language.GERMAN).isPresent());
	}

	@Test
	public void testOfflineBeatsOnlineInPriority()
	{
		OnlineTranslationProvider online = new OnlineTranslationProvider(null, null, new Gson());

		assertTrue(provider.getPriority() < online.getPriority());
	}

	@Test
	public void testOfflineBeatsVerifiedAndDictionaryInPriority()
	{
		// The offline engine is a fallback: it must come after the verified database
		// and the dictionary.
		assertTrue(provider.getPriority() > 10);
		assertTrue(provider.getPriority() > 50);
	}
}
