package com.osrslang.translation;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import com.osrslang.text.GameText;
import com.osrslang.text.GermanNounDatabase;
import com.osrslang.text.HoverTarget;
import com.osrslang.translation.provider.VocabularyEntry;

/**
 * Translates the names of the game entities the player hovers over.
 * <p>
 * The mouse is over the same entity for many consecutive frames, so a translation must
 * only be requested once per entity. Two things prevent repeat requests:
 * <ul>
 * <li>translations already produced are kept in a map, keyed by the entity and the
 * language pair, so a translation is only ever requested once;</li>
 * <li>a request that is already in flight is joined rather than started again.</li>
 * </ul>
 * A name that no provider can translate is remembered as untranslatable too, so it is
 * not asked for again on every frame. That entry is dropped when the language pair
 * changes, so a later switch of target language retries the translation.
 * <p>
 * Each answer is also given the article of the noun it translates, where the dictionary
 * records one. The article is only ever shown for a noun the dictionary already knows -
 * a guess derived from the word's ending is not used, because it would be the kind of
 * answer that looks like a fact and turns out to be wrong more often than not.
 * <p>
 * When the menu entry under the cursor carries an action - "Attack", "Talk-to",
 * "Examine" - that action is translated separately so it can be shown alongside the
 * noun translation in the natural German form. An action that has no dictionary entry
 * is left untranslated rather than guessed at.
 */
@Slf4j
public class HoverTranslationService
{
	private final TranslationManager manager;

	/**
	 * Articles for German nouns the dictionary does not describe, used so a translation
	 * from the OSRS cache or the offline engine still gets an article in front of it.
	 */
	private final GermanNounDatabase nounDatabase;

	/**
	 * The thread pool translations run on, so the client thread is never blocked.
	 */
	private final ExecutorService executor;

	/**
	 * Translations already produced, keyed by entity and language pair.
	 */
	private final Map<String, Optional<TranslationResult>> translations = new ConcurrentHashMap<>();

	/**
	 * Action-verb translations, keyed by action verb and language pair, so an action
	 * that has already been looked up is not asked for again on every frame.
	 */
	private final Map<String, Optional<String>> actionTranslations = new ConcurrentHashMap<>();

	/**
	 * Requests currently in flight, keyed by entity and language pair.
	 */
	private final Map<String, CompletableFuture<Optional<TranslationResult>>> inFlight = new ConcurrentHashMap<>();

	public HoverTranslationService(TranslationManager manager, ExecutorService executor, GermanNounDatabase nounDatabase)
	{
		this.manager = manager;
		this.executor = executor;
		this.nounDatabase = nounDatabase;
	}

	/**
	 * Requests the translation of an entity's name.
	 * <p>
	 * The returned future completes on a background thread, never on the client thread.
	 * A future that has already been resolved for this entity and language pair is
	 * returned immediately without contacting any provider.
	 *
	 * @param target  the entity under the mouse
	 * @param from    the source language
	 * @param to      the target language
	 * @return a future completing with the translation, or empty if nothing could
	 *         translate the name
	 */
	public CompletableFuture<Optional<TranslationResult>> request(HoverTarget target, Language from, Language to)
	{
		if (target == null || target.getName() == null || target.getName().isEmpty() || from == to)
		{
			return CompletableFuture.completedFuture(Optional.empty());
		}

		String key = key(target, from, to);

		Optional<TranslationResult> known = translations.get(key);
		if (known != null)
		{
			return CompletableFuture.completedFuture(known);
		}

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

		String name = target.getName();
		GameText.Type context = target.getType();

		executor.execute(() ->
		{
			try
			{
				manager.translatePhrase(name, from, to, context)
					.whenComplete((result, error) ->
					{
						inFlight.remove(key);

						if (error != null)
						{
							log.warn("Translation failed for '{}'", name, error);
							promise.complete(Optional.empty());
							return;
						}

						Optional<TranslationResult> resolved = result == null
							? Optional.empty()
							: result.map(found -> withArticle(name, found, from, to));

						// Remember the outcome, including a miss, so the same name is not
						// looked up again on every frame.
						translations.put(key, resolved);

						promise.complete(resolved);
					});
			}
			catch (RuntimeException e)
			{
				inFlight.remove(key);
				log.warn("Translation request failed for '{}'", name, e);
				promise.complete(Optional.empty());
			}
		});

		return promise;
	}

	/**
	 * The German translation of the action verb the menu entry carries, or empty when
	 * none is known.
	 * <p>
	 * The lookup is cached for the same reason the noun translation is: the mouse rests
	 * on the same entity for many frames, so a single action verb should only ever be
	 * looked up once.
	 *
	 * @param action the English action verb, may be {@code null}
	 * @param from   the source language
	 * @param to     the target language
	 * @return the German verb, or empty when no translation could be found
	 */
	public Optional<String> actionTranslation(@Nullable String action, Language from, Language to)
	{
		if (action == null || action.trim().isEmpty() || from == to)
		{
			return Optional.empty();
		}

		String lookup = action.trim().toLowerCase(Locale.ROOT);
		String cacheKey = lookup + "|" + from.getCode() + "|" + to.getCode();
		Optional<String> cached = actionTranslations.get(cacheKey);
		if (cached != null)
		{
			return cached;
		}

		Optional<String> translation = manager.lookupWord(lookup, from, to)
			.map(VocabularyEntry::getTranslation)
			.map(String::trim)
			.filter(s -> !s.isEmpty());

		actionTranslations.put(cacheKey, translation);
		return translation;
	}

	/**
	 * Attaches the article of the translated noun to a result.
	 * <p>
	 * The article is attached in three layers of trust, each one reached only when the
	 * previous layer could not answer:
	 * <ol>
	 * <li>the dictionary entry that matches the English name the game drew - the most
	 * authoritative source, because the entry's gender field was checked by a human;</li>
	 * <li>the article the translation already begins with - the OSRS German language
	 * pack occasionally writes "der Schwert" rather than "Schwert", and that article
	 * is taken at face value;</li>
	 * <li>the bundled German noun table, which covers the common OSRS nouns ("Ahornbaum",
	 * "Wand") that come back from the OSRS cache without an article.</li>
	 * </ol>
	 * Each successful lookup is labelled with where it came from, so a derived article
	 * is shown as a hint rather than stated as fact.
	 */
	private TranslationResult withArticle(String name, TranslationResult result, Language from, Language to)
	{
		VocabularyEntry entry = manager.lookupWord(name, from, to).orElse(null);

		String dictionaryArticle = entry == null ? null
			: entry.getArticle().filter(this::isRecognisedArticle).orElse(null);

		if (dictionaryArticle != null)
		{
			return result.withGrammar(entry, dictionaryArticle, "the dictionary");
		}

		if (!Language.GERMAN.isSameLanguage(to))
		{
			// The noun table is German-specific. A translation into French or Spanish
			// has a different article system, and the heuristic of "use the German
			// table" would put a der/die/das in front of a word it does not describe.
			return result.withGrammar(entry, null, null);
		}

		String translated = result.getTranslatedText();
		String leading = TranslationResult.MASCULINE_ARTICLE.equals(translated)
			|| TranslationResult.FEMININE_ARTICLE.equals(translated)
			|| TranslationResult.NEUTER_ARTICLE.equals(translated)
				? null
				: leadingArticle(translated);

		if (leading != null)
		{
			return result.withGrammar(entry, leading, "the translation");
		}

		if (nounDatabase != null)
		{
			Optional<String> fromTable = nounDatabase.articleOf(translated)
				.filter(this::isRecognisedArticle);
			if (fromTable.isPresent())
			{
				return result.withGrammar(entry, fromTable.get(), "the noun table");
			}
		}

		return result.withGrammar(entry, null, null);
	}

	/**
	 * Whether the string is one of the three German articles.
	 */
	private boolean isRecognisedArticle(String article)
	{
		if (article == null)
		{
			return false;
		}

		String trimmed = article.trim().toLowerCase(Locale.ROOT);
		return TranslationResult.MASCULINE_ARTICLE.equals(trimmed)
			|| TranslationResult.FEMININE_ARTICLE.equals(trimmed)
			|| TranslationResult.NEUTER_ARTICLE.equals(trimmed);
	}

	/**
	 * The article the translation already begins with, or {@code null} when it starts
	 * with something else.
	 * <p>
	 * Returns the article as it was written in the translation (lowercased, since the
	 * stored articles are always lowercase), so "Die Riesenratte" yields "die" rather
	 * than "der". A naive case-insensitive prefix match would match the first article in
	 * the iteration order regardless of which one was actually written, which is wrong
	 * here - we use the article as a fact about the noun, not just as a "do we already
	 * have one" check.
	 */
	private static String leadingArticle(String text)
	{
		String trimmed = text == null ? "" : text.trim();
		if (trimmed.length() < 3)
		{
			return null;
		}

		String head = trimmed.substring(0, 3).toLowerCase(Locale.ROOT);
		if (!TranslationResult.MASCULINE_ARTICLE.equals(head)
			&& !TranslationResult.FEMININE_ARTICLE.equals(head)
			&& !TranslationResult.NEUTER_ARTICLE.equals(head))
		{
			return null;
		}

		if (trimmed.length() > 3)
		{
			char next = trimmed.charAt(3);
			if (Character.isLetterOrDigit(next))
			{
				return null;
			}
		}

		return head;
	}

	/**
	 * Forgets everything remembered for a language pair.
	 * <p>
	 * Called when the user changes the source or target language, so the names are
	 * translated again into the new language.
	 */
	public void clear()
	{
		translations.clear();
		actionTranslations.clear();
		inFlight.clear();
	}

	/**
	 * The number of names currently remembered.
	 */
	public int cachedCount()
	{
		return translations.size();
	}

	private static String key(HoverTarget target, Language from, Language to)
	{
		return target.getKind() + "|" + target.getId() + "|" + target.getName() + "|" + from.getCode() + "|" + to.getCode();
	}
}

