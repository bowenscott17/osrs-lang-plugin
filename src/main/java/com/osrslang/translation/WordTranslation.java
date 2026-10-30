package com.osrslang.translation;

import java.util.Optional;
import com.osrslang.translation.provider.VocabularyEntry;

/**
 * What is known about one hovered word.
 * <p>
 * A result is always produced for a lookup, so the tooltip never has to guess whether an
 * answer is still coming. {@link Status#PENDING} is the optimistic value shown while the
 * request is still in flight; {@link Status#NOT_FOUND} is a settled answer, remembered so
 * the same word is never looked up twice.
 * <p>
 * A miss and a pair the provider cannot handle are different things and are kept apart:
 * the first means no source knew the word, the second means the user picked a combination
 * that needs a model they have not installed.
 */
public final class WordTranslation
{
	public enum Status
	{
		/**
		 * The lookup succeeded and {@link #getTranslation()} holds the answer.
		 */
		FOUND,

		/**
		 * The lookup is still running.
		 */
		PENDING,

		/**
		 * No source could translate this word.
		 */
		NOT_FOUND,

		/**
		 * The translation provider cannot translate between the configured languages.
		 */
		UNSUPPORTED_PAIR
	}

	private final String word;

	private final Status status;

	private final String translation;

	private final VocabularyEntry entry;

	private final TranslationSource source;

	private WordTranslation(String word, Status status, String translation, VocabularyEntry entry,
		TranslationSource source)
	{
		this.word = word;
		this.status = status;
		this.translation = translation;
		this.entry = entry;
		this.source = source;
	}

	public static WordTranslation found(String word, String translation, TranslationSource source)
	{
		return found(word, translation, source, null);
	}

	/**
	 * A successful lookup.
	 *
	 * @param word        the word that was looked up
	 * @param translation its translation
	 * @param source      where the translation came from
	 * @param entry       the dictionary entry, when the dictionary knew the word, which
	 *                    carries the part of speech, article and lemma the tooltip shows
	 */
	public static WordTranslation found(String word, String translation, TranslationSource source,
		VocabularyEntry entry)
	{
		return new WordTranslation(word, Status.FOUND, translation, entry, source);
	}

	/**
	 * The optimistic result shown while the lookup is still running.
	 */
	public static WordTranslation pending(String word)
	{
		return new WordTranslation(word, Status.PENDING, null, null, null);
	}

	public static WordTranslation notFound(String word)
	{
		return new WordTranslation(word, Status.NOT_FOUND, null, null, null);
	}

	public static WordTranslation unsupported(String word)
	{
		return new WordTranslation(word, Status.UNSUPPORTED_PAIR, null, null, null);
	}

	/**
	 * The word that was looked up, exactly as it appeared in the dialogue.
	 */
	public String getWord()
	{
		return word;
	}

	public Status getStatus()
	{
		return status;
	}

	public boolean isFound()
	{
		return status == Status.FOUND;
	}

	public boolean isPending()
	{
		return status == Status.PENDING;
	}

	/**
	 * The translation, or empty when there is not one yet.
	 */
	public Optional<String> getTranslation()
	{
		return Optional.ofNullable(translation);
	}

	/**
	 * The dictionary entry for the word, when a dictionary knew it.
	 */
	public Optional<VocabularyEntry> getEntry()
	{
		return Optional.ofNullable(entry);
	}

	/**
	 * Where the translation came from, when there is one.
	 */
	public Optional<TranslationSource> getSource()
	{
		return Optional.ofNullable(source);
	}

	@Override
	public String toString()
	{
		return "WordTranslation{" + word + ", " + status + ", " + translation + "}";
	}
}