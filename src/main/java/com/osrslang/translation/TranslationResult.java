package com.osrslang.translation;

import java.util.Objects;

/**
 * The outcome of a translation lookup.
 */
public class TranslationResult
{
	private final String sourceText;
	private final String translatedText;
	private final Language targetLanguage;
	private final TranslationSource source;

	/**
	 * Whether this result came from a human-verified translation.
	 */
	private final boolean verified;

	public TranslationResult(String sourceText, String translatedText, Language targetLanguage, TranslationSource source)
	{
		this.sourceText = sourceText;
		this.translatedText = translatedText;
		this.targetLanguage = targetLanguage;
		this.source = source;
		this.verified = source.isVerified();
	}

	public String getSourceText()
	{
		return sourceText;
	}

	public String getTranslatedText()
	{
		return translatedText;
	}

	public Language getTargetLanguage()
	{
		return targetLanguage;
	}

	public TranslationSource getSource()
	{
		return source;
	}

	public boolean isVerified()
	{
		return verified;
	}

	/**
	 * A short label describing where this translation came from, for display next to it.
	 */
	public String getSourceLabel()
	{
		return source.getLabel();
	}

	@Override
	public boolean equals(Object o)
	{
		if (this == o)
		{
			return true;
		}
		if (!(o instanceof TranslationResult))
		{
			return false;
		}
		TranslationResult other = (TranslationResult) o;
		return Objects.equals(sourceText, other.sourceText)
			&& Objects.equals(translatedText, other.translatedText)
			&& targetLanguage == other.targetLanguage
			&& source == other.source;
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(sourceText, translatedText, targetLanguage, source);
	}

	@Override
	public String toString()
	{
		return "TranslationResult{" + sourceText + " -> " + translatedText + " (" + source + ")}";
	}
}
