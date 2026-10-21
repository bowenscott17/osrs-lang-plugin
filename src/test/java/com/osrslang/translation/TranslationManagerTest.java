package com.osrslang.translation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.junit.Before;
import org.junit.Test;
import com.osrslang.translation.provider.TranslationProvider;
import com.osrslang.translation.provider.VocabularyEntry;
import com.osrslang.text.GameText;

public class TranslationManagerTest
{
	/**
	 * A provider that answers with a fixed translation, or declines.
	 */
	private static class StubProvider implements TranslationProvider
	{
		private final String name;
		private final int priority;
		private final String answer;
		private boolean enabled = true;

		StubProvider(String name, int priority, String answer)
		{
			this.name = name;
			this.priority = priority;
			this.answer = answer;
		}

		@Override
		public String getName()
		{
			return name;
		}

		@Override
		public boolean isEnabled()
		{
			return enabled;
		}

		@Override
		public boolean supports(Language source, Language target)
		{
			return true;
		}

		@Override
		public int getPriority()
		{
			return priority;
		}

		@Override
		public CompletableFuture<Optional<TranslationResult>> translate(String source, Language from, Language to, GameText.Type context)
		{
			if (answer == null)
			{
				return CompletableFuture.completedFuture(Optional.empty());
			}
			return CompletableFuture.completedFuture(Optional.of(
				new TranslationResult(source, answer, to, TranslationSource.VERIFIED)));
		}
	}

	private TranslationManager manager;

	@Before
	public void setUp()
	{
		manager = new TranslationManager();
	}

	@Test
	public void testFirstProviderWins() throws ExecutionException, InterruptedException
	{
		manager.register(new StubProvider("low", 100, "low answer"));
		manager.register(new StubProvider("high", 10, "high answer"));

		Optional<TranslationResult> result = manager
			.translate("hello", Language.ENGLISH, Language.GERMAN, GameText.Type.NPC_DIALOGUE)
			.get();

		assertTrue(result.isPresent());
		assertEquals("high answer", result.get().getTranslatedText());
	}

	@Test
	public void testFallsThroughToNextProvider() throws ExecutionException, InterruptedException
	{
		manager.register(new StubProvider("declines", 10, null));
		manager.register(new StubProvider("answers", 20, "answer"));

		Optional<TranslationResult> result = manager
			.translate("hello", Language.ENGLISH, Language.GERMAN, GameText.Type.NPC_DIALOGUE)
			.get();

		assertTrue(result.isPresent());
		assertEquals("answer", result.get().getTranslatedText());
	}

	@Test
	public void testFallsBackToFallbackProviders() throws ExecutionException, InterruptedException
	{
		manager.register(new StubProvider("declines", 10, null));
		manager.registerFallback(new StubProvider("fallback", 10, "fallback answer"));

		Optional<TranslationResult> result = manager
			.translate("hello", Language.ENGLISH, Language.GERMAN, GameText.Type.NPC_DIALOGUE)
			.get();

		assertTrue(result.isPresent());
		assertEquals("fallback answer", result.get().getTranslatedText());
	}

	@Test
	public void testNoProviderAnswers() throws ExecutionException, InterruptedException
	{
		manager.register(new StubProvider("declines", 10, null));

		Optional<TranslationResult> result = manager
			.translate("hello", Language.ENGLISH, Language.GERMAN, GameText.Type.NPC_DIALOGUE)
			.get();

		assertFalse(result.isPresent());
	}

	@Test
	public void testDisabledProviderIsSkipped() throws ExecutionException, InterruptedException
	{
		StubProvider disabled = new StubProvider("disabled", 10, "answer");
		disabled.enabled = false;
		manager.register(disabled);
		manager.register(new StubProvider("enabled", 20, "other answer"));

		Optional<TranslationResult> result = manager
			.translate("hello", Language.ENGLISH, Language.GERMAN, GameText.Type.NPC_DIALOGUE)
			.get();

		assertTrue(result.isPresent());
		assertEquals("other answer", result.get().getTranslatedText());
	}

	@Test
	public void testSameLanguageReturnsEmpty() throws ExecutionException, InterruptedException
	{
		manager.register(new StubProvider("answers", 10, "answer"));

		Optional<TranslationResult> result = manager
			.translate("hello", Language.ENGLISH, Language.ENGLISH, GameText.Type.NPC_DIALOGUE)
			.get();

		assertFalse(result.isPresent());
	}

	@Test
	public void testEmptyTextReturnsEmpty() throws ExecutionException, InterruptedException
	{
		manager.register(new StubProvider("answers", 10, "answer"));

		assertFalse(manager.translate("", Language.ENGLISH, Language.GERMAN, GameText.Type.NPC_DIALOGUE).get().isPresent());
		assertFalse(manager.translate("   ", Language.ENGLISH, Language.GERMAN, GameText.Type.NPC_DIALOGUE).get().isPresent());
		assertFalse(manager.translate(null, Language.ENGLISH, Language.GERMAN, GameText.Type.NPC_DIALOGUE).get().isPresent());
	}

	@Test
	public void testCacheIsUsedBeforeProviders() throws ExecutionException, InterruptedException
	{
		// Pre-populate the cache with a different answer than the provider gives.
		TranslationManager withCache = new TranslationManager();
		InMemoryCache cache = new InMemoryCache();
		cache.put("hello", Language.ENGLISH, Language.GERMAN,
			new TranslationResult("hello", "cached answer", Language.GERMAN, TranslationSource.CACHE));
		withCache.setCache(cache);
		withCache.register(new StubProvider("answers", 10, "provider answer"));

		Optional<TranslationResult> result = withCache
			.translate("hello", Language.ENGLISH, Language.GERMAN, GameText.Type.NPC_DIALOGUE)
			.get();

		assertTrue(result.isPresent());
		assertEquals("cached answer", result.get().getTranslatedText());
	}

	@Test
	public void testResultIsCachedAfterTranslation() throws ExecutionException, InterruptedException
	{
		InMemoryCache cache = new InMemoryCache();
		manager.setCache(cache);
		manager.register(new StubProvider("answers", 10, "answer"));

		manager.translate("hello", Language.ENGLISH, Language.GERMAN, GameText.Type.NPC_DIALOGUE).get();

		assertEquals(1, cache.size());
	}

	/**
	 * A simple in-memory cache used to test the manager's cache interaction.
	 */
	private static class InMemoryCache implements TranslationCache
	{
		private final java.util.Map<String, TranslationResult> map = new java.util.HashMap<>();

		@Override
		public Optional<TranslationResult> get(String text, Language from, Language to)
		{
			return Optional.ofNullable(map.get(text.toLowerCase(java.util.Locale.ROOT) + "|" + from + "|" + to));
		}

		@Override
		public void put(String text, Language from, Language to, TranslationResult result)
		{
			map.put(text.toLowerCase(java.util.Locale.ROOT) + "|" + from + "|" + to, result);
		}

		@Override
		public int size()
		{
			return map.size();
		}

		@Override
		public void clear()
		{
			map.clear();
		}
	}
}
