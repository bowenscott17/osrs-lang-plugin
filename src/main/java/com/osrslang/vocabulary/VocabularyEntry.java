package com.osrslang.vocabulary;

import java.util.Objects;

/**
 * A word the user saved for later review.
 */
public class VocabularyEntry
{
	/**
	 * The word as it appeared in the game.
	 */
	private final String word;

	/**
	 * The word in the target language.
	 */
	private String translation;

	/**
	 * The sentence the word was found in, in the original language.
	 */
	private String contextSentence;

	/**
	 * The sentence the word was found in, translated.
	 */
	private String contextTranslation;

	/**
	 * The language of the word.
	 */
	private final String language;

	/** Target language of this translation; null for legacy entries until migrated. */
	private String targetLanguage;

	/**
	 * Whether the user has marked this word as known.
	 */
	private boolean known;

	/**
	 * How many times this word has been seen in game.
	 */
	private int timesSeen;

	public VocabularyEntry(String word, String translation, String contextSentence,
		String contextTranslation, String language)
	{
		this(word, translation, contextSentence, contextTranslation, language, null);
	}

	public VocabularyEntry(String word, String translation, String contextSentence,
		String contextTranslation, String language, String targetLanguage)
	{
		this.word = word;
		this.translation = translation;
		this.contextSentence = contextSentence;
		this.contextTranslation = contextTranslation;
		this.language = language;
		this.targetLanguage = targetLanguage;
	}

	public String getWord()
	{
		return word;
	}

	public String getTranslation()
	{
		return translation;
	}

	public void setTranslation(String translation)
	{
		this.translation = translation;
	}

	public String getContextSentence()
	{
		return contextSentence;
	}

	public void setContextSentence(String contextSentence)
	{
		this.contextSentence = contextSentence;
	}

	public String getContextTranslation()
	{
		return contextTranslation;
	}

	public void setContextTranslation(String contextTranslation)
	{
		this.contextTranslation = contextTranslation;
	}

	public String getLanguage()
	{
		return language;
	}

	public String getTargetLanguage()
	{
		return targetLanguage;
	}

	public void setTargetLanguage(String targetLanguage)
	{
		this.targetLanguage = targetLanguage;
	}

	public boolean isKnown()
	{
		return known;
	}

	public void setKnown(boolean known)
	{
		this.known = known;
	}

	public int getTimesSeen()
	{
		return timesSeen;
	}

	public void incrementTimesSeen()
	{
		timesSeen++;
	}

	@Override
	public boolean equals(Object o)
	{
		if (this == o)
		{
			return true;
		}
		if (!(o instanceof VocabularyEntry))
		{
			return false;
		}
		VocabularyEntry other = (VocabularyEntry) o;
		return Objects.equals(word, other.word)
			&& Objects.equals(language, other.language)
			&& Objects.equals(targetLanguage, other.targetLanguage);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(word, language, targetLanguage);
	}

	@Override
	public String toString()
	{
		return "VocabularyEntry{" + word + " -> " + translation + "}";
	}
}
