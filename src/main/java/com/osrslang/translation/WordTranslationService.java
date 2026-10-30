package com.osrslang.translation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import lombok.extern.slf4j.Slf4j;
import com.osrslang.text.GameText;
import com.osrslang.translation.provider.VocabularyEntry;

/**
 * Translates the single word the pointer is resting on.
 * <p>
 * The pointer spends many frames over the same word, so the service is built around never
 * asking twice:
 * <ul>
 * <li>every answer is remembered, keyed by word <em>and</em> by both language codes, so
 * switching languages can never show a translation made for the previous pair;</li>
 * <li>a miss is remembered as firmly as a hit, so a word no source knows is not sent to
 * the translation engine again on every frame;</li>
 *<li>a request that is already in flight is joined rather than started again.</li>
 * </ul>
 * Everything runs on a background thread: no lookup ever touches the network on the
 * client thread, and the mouse can keep moving freely while one is outstanding.
 */
@Slf4j
public class WordTranslationService
{
	/**
	 * Whether the configured translation provider can translate between two languages.
	 * <p>
	 * Supplied by the plugin, which knows what the provider reported. It exists so a word
	 * the provider cannot reach is reported as an unsupported pair rather than as a word
	 * nobody could translate.
	 */
	public interface PairSupport
	{
		boolean isSupported(Language from, Language to);

		/**
		 * Used when the provider's language list has not been fetched, so an unreachable
		 * provider is never mistaken for one that lacks the pair.
		 */
		PairSupport ASSUME_SUPPORTED = (from, to) -> true;
	}

	/**
	 * Answers are kept per word and language pair. Bounded so a long session cannot grow
	 * without limit; the least recently used entry is dropped first.
	 */
	private static final int MAX_ENTRIES = 2000;

	private final TranslationManager manager;

	/**
	 * The thread pool lookups run on, so the client thread is never blocked.
	 */
	private final ExecutorService executor;

	private final PairSupport pairSupport;

	/**
	 * Settled answers, misses included, keyed by word and language pair.
	 */
	private final Map<String, WordTranslation> cache = Collections.synchronizedMap(
		new LinkedHashMap<String, WordTranslation>(64, 0.75f, true)
		{
			@Override
			protected boolean removeEldestEntry(Map.Entry<String, WordTranslation> eldest)
			{
				return size() > MAX_ENTRIES;
			}
		});

	/**
	 * Lookups currently running, keyed by word and language pair.
	 */
	private final Map<String, CompletableFuture<WordTranslation>> inFlight = new ConcurrentHashMap<>();

	public WordTranslationService(TranslationManager manager, ExecutorService executor)
	{
		this(manager, executor, PairSupport.ASSUME_SUPPORTED);
	}

	public WordTranslationService(TranslationManager manager, ExecutorService executor, PairSupport pairSupport)
	{
		this.manager = manager;
		this.executor = executor;
		this.pairSupport = pairSupport == null ? PairSupport.ASSUME_SUPPORTED : pairSupport;
	}

	/**
	 * Looks up a single word.
	 * <p>
	 * Sources are consulted in the order the user can trust: the verified game-specific
	 * data, then the bundled dictionary, then the translation provider, and only then a
	 * "no translation found". A word the provider echoes back unchanged is treated as a
	 * miss, so the original is never shown as if it were its own translation.
	 * <p>
	 * The returned future completes on a background thread, never on the client thread.
	 *
	 * @param word    the word to translate
	 * @param from    the language the word is written in
	 * @param to      the language to translate into
	 * @param context the kind of game text the word came from
	 * @return a future completing with the result, which is never empty
	 */
	public CompletableFuture<WordTranslation> request(String word, Language from, Language to, GameText.Type context)
	{
		if (word == null || word.trim().isEmpty() || from == null || to == null)
		{
			return CompletableFuture.completedFuture(WordTranslation.notFound(word));
		}

		if (from.isSameLanguage(to))
		{
			// Nothing to do: the word is already in the language the user reads.
			return CompletableFuture.completedFuture(WordTranslation.notFound(word));
		}

		String key = key(word, from, to);

		WordTranslation known = cache.get(key);
		if (known != null)
		{
			return CompletableFuture.completedFuture(known);
		}

		CompletableFuture<WordTranslation> existing = inFlight.get(key);
		if (existing != null)
		{
			return existing;
		}

		CompletableFuture<WordTranslation> promise = new CompletableFuture<>();
		if (inFlight.putIfAbsent(key, promise) != null)
		{
			return inFlight.get(key);
		}

		executor.execute(() -> lookup(word, from, to, context, key, promise));

		return promise;
	}

	/**
	 * The remembered answer for a word, if there is one.
	 */
	public Optional<WordTranslation> peek(String word, Language from, Language to)
	{
		if (word == null || from == null || to == null)
		{
			return Optional.empty();
		}

		return Optional.ofNullable(cache.get(key(word, from, to)));
	}

	/**
	 * Runs the lookup off the client thread and settles the future.
	 */
	private void lookup(String word, Language from, Language to, GameText.Type context, String key,
		CompletableFuture<WordTranslation> promise)
	{
		try
		{
			// The dictionary entry carries the part of speech, article and lemma the
			// tooltip shows. It is read here, on the worker thread, rather than on the
			// client thread, and it is in memory so it costs nothing.
			Optional<VocabularyEntry> entry = manager.lookupWord(word, from, to);

			manager.translatePhrase(word, from, to, context)
				.whenComplete((result, error) ->
				{
					inFlight.remove(key);

					if (error != null)
					{
						log.warn("Word translation failed for '{}'", word, error);
						settle(key, promise, WordTranslation.notFound(word));
						return;
					}

					settle(key, promise, toWordTranslation(word, from, to, result, entry));
				});
		}
		catch (RuntimeException e)
		{
			inFlight.remove(key);
			log.warn("Word translation request failed for '{}'", word, e);
			settle(key, promise, WordTranslation.notFound(word));
		}
	}

	/**
	 * Turns what the providers answered into a result, and remembers it either way.
	 */
	private void settle(String key, CompletableFuture<WordTranslation> promise, WordTranslation translation)
	{
		cache.put(key, translation);
		promise.complete(translation);
	}

	/**
	 * Interprets the provider chain's answer.
	 * <p>
	 * Three things are treated as a miss: nothing answered, the provider echoed the word
	 * back unchanged, and the provider cannot translate between the two configured
	 * languages at all. The last of those is reported separately so the user is told to
	 * install a model rather than being told the word is unknown.
	 */
	private WordTranslation toWordTranslation(String word, Language from, Language to,
		Optional<TranslationResult> result, Optional<VocabularyEntry> entry)
	{
		if (result != null && result.isPresent())
		{
			TranslationResult translation = result.get();
			String text = translation.getTranslatedText();

			if (text == null || text.trim().isEmpty() || echoes(text, word))
			{
				return WordTranslation.notFound(word);
			}

			return WordTranslation.found(word, text.trim(), translation.getSource(), agreeing(entry, text));
		}

		// A dictionary entry that its own provider declined to answer - which happens if
		// the provider is disabled - is still better than nothing.
		if (entry.isPresent() && !entry.get().getTranslation().trim().equalsIgnoreCase(word.trim()))
		{
			return WordTranslation.found(word, entry.get().getTranslation().trim(),
				TranslationSource.DICTIONARY, entry.get());
		}

		if (!pairSupport.isSupported(from, to))
		{
			return WordTranslation.unsupported(word);
		}

		return WordTranslation.notFound(word);
	}

	/**
	 * The dictionary entry for a word, but only when it describes the same translation.
	 * <p>
	 * A verified translation may be the bare form of a dictionary entry - "Kohle" against
	 * "die Kohle". That is close enough to keep the article and part of speech. Anything
	 * else is dropped, so the tooltip never pairs one wording with another's grammar.
	 */
	private static VocabularyEntry agreeing(Optional<VocabularyEntry> entry, String translation)
	{
		if (entry == null || !entry.isPresent())
		{
			return null;
		}

		VocabularyEntry found = entry.get();
		String candidate = found.getTranslation() == null ? "" : found.getTranslation().trim();
		String answer = translation == null ? "" : translation.trim();

		if (candidate.equalsIgnoreCase(answer))
		{
			return found;
		}

		// "die Kohle" and "Kohle" describe the same noun.
		String bare = found.getGender().map(g -> g + " ").orElse("");
		if (!bare.isEmpty() && candidate.regionMatches(true, 0, bare, 0, bare.length())
			&& candidate.substring(bare.length()).trim().equalsIgnoreCase(answer))
		{
			return found;
		}

		return null;
	}

	/**
	 * Whether a provider answer is just the word handed back.
	 * <p>
	 * A translation engine asked to translate a word it has no model for can answer with
	 * the input unchanged. Showing that as a translation would tell the user nothing and
	 * would look like a working lookup, so it counts as a miss instead.
	 */
	private static boolean echoes(String translation, String word)
	{
		return translation.trim().equalsIgnoreCase(word.trim());
	}

	/**
	 * Forgets every remembered answer, in-flight lookups included.
	 * <p>
	 * Called when the source or target language changes: the answers on file were made
	 * for the previous pair and must not be shown against the new one.
	 */
	public void clear()
	{
		cache.clear();
		inFlight.clear();
	}

	/**
	 * The number of words currently remembered.
	 */
	public int cachedCount()
	{
		return cache.size();
	}

	private static String key(String word, Language from, Language to)
	{
		return from.getCode() + "|" + to.getCode() + "|" + word;
	}
}