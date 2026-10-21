package com.osrslang.translation.provider;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.runelite.client.plugins.Plugin;
import com.osrslang.translation.Language;
import com.osrslang.translation.TranslationResult;
import com.osrslang.text.GameText;

/**
 * A source of translations.
 * <p>
 * Providers are consulted in a fixed order by the translation manager. Each provider
 * reports what it is capable of, so the manager can skip ones that are disabled, have no
 * data for the requested language, or would need network access the user has not allowed.
 */
public interface TranslationProvider
{
	/**
	 * A short human-readable name, shown in the config panel.
	 */
	String getName();

	/**
	 * Whether this provider is currently allowed to run.
	 * <p>
	 * Providers that need the network must return {@code false} unless the user has
	 * explicitly enabled online translation.
	 */
	boolean isEnabled();

	/**
	 * Whether this provider can handle the given language pair.
	 */
	boolean supports(Language source, Language target);

	/**
	 * The lowest number this provider should be consulted at. Lower values are consulted
	 * first. This lets a verified database outrank a generic dictionary without the
	 * manager needing to know the concrete provider types.
	 */
	default int getPriority()
	{
		return 100;
	}

	/**
	 * Translates the given text.
	 * <p>
	 * Implementations must not block the calling thread for long. Providers that need
	 * I/O should return a {@link CompletableFuture} that completes on a background thread.
	 *
	 * @param source   the text to translate
	 * @param from     the language the text is written in
	 * @param to       the language to translate into
	 * @param context  the kind of game text being translated, which lets specialised
	 *                 providers pick a more appropriate entry
	 * @return a future completing with the translation, or empty if this provider has no
	 *         translation for the text
	 */
	CompletableFuture<Optional<TranslationResult>> translate(String source, Language from, Language to, GameText.Type context);

	/**
	 * Looks up a single word in this provider's vocabulary.
	 * <p>
	 * Only dictionary-style providers implement this; others return an empty optional.
	 *
	 * @param word     the word to look up
	 * @param from     the language the word is written in
	 * @param to       the language the definition should be given in
	 * @return the definition, if this provider has one
	 */
	default Optional<VocabularyEntry> lookupWord(String word, Language from, Language to)
	{
		return Optional.empty();
	}

	/**
	 * The plugin instance this provider belongs to, if any.
	 */
	default Plugin getPlugin()
	{
		return null;
	}
}
