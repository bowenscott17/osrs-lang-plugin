package com.osrslang.translation;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import com.osrslang.translation.provider.VocabularyEntry;

/**
 * The outcome of a translation lookup.
 * <p>
 * A result may also carry grammar about the translated word: the dictionary entry that
 * describes it, and the article of the noun it names. None of that is part of the
 * translation itself, so it is not persisted - the cache stores translations only, and the
 * grammar is looked up again from the dictionary whenever it is needed.
 */
public class TranslationResult
{
	/**
	 * The German masculine article.
	 */
	public static final String MASCULINE_ARTICLE = "der";

	/**
	 * The German feminine article.
	 */
	public static final String FEMININE_ARTICLE = "die";

	/**
	 * The German neuter article.
	 */
	public static final String NEUTER_ARTICLE = "das";

	/**
	 * Prepends an article to a translation, unless the translation already begins with
	 * one.
	 * <p>
	 * A translation that already starts with an article is returned unchanged, so a
	 * dictionary entry that stored "das Schwert" against an article of "das" does not
	 * become "das das Schwert". The check is case-insensitive and word-boundary-aware,
	 * so "Derrick" is not read as "der" plus a word.
	 * <p>
	 * The article is capitalised when it sits at the start of the returned text, because
	 * German capitalises the article at the start of a sentence and at the head of a
	 * stand-alone label. A noun that the tooltip shows on its own line ("Die Riesenratte")
	 * is the kind of stand-alone label where the capitalised form is the natural one.
	 *
	 * @param translation the translated name
	 * @param article     the article to place in front, may be {@code null}
	 * @return the name with the article in front, or the name unchanged
	 */
	public static String withArticle(String translation, String article)
	{
		if (translation == null || translation.trim().isEmpty() || article == null || article.trim().isEmpty())
		{
			return translation;
		}

		if (leadingArticle(translation) != null)
		{
			return translation;
		}

		String trimmed = article.trim();
		String first = trimmed.substring(0, 1).toUpperCase(Locale.ROOT);
		String capitalised = first + trimmed.substring(1);

		return capitalised + " " + translation.trim();
	}

	/**
	 * The article a name already begins with, or {@code null} when it starts with
	 * something else.
	 */
	private static String leadingArticle(String text)
	{
		String trimmed = text.trim();

		for (String article : new String[]{MASCULINE_ARTICLE, FEMININE_ARTICLE, NEUTER_ARTICLE})
		{
			if (trimmed.length() > article.length()
				&& trimmed.regionMatches(true, 0, article, 0, article.length())
				&& !Character.isLetterOrDigit(trimmed.charAt(article.length())))
			{
				return article;
			}
		}

		return null;
	}

	private final String sourceText;
	private final String translatedText;
	private final Language targetLanguage;
	private final TranslationSource source;

	/**
	 * Whether this result came from a human-verified translation.
	 */
	private final boolean verified;

	/**
	 * The dictionary entry describing the translated word, when one was found.
	 */
	private final transient VocabularyEntry entry;

	/**
	 * The article of the translated noun, when one is known or could be derived.
	 */
	private final transient String article;

	/**
	 * Where {@link #article} came from - "the dictionary", or the ending it was derived
	 * from - so a derived article can be shown as a hint rather than as fact.
	 */
	private final transient String articleBasis;

	public TranslationResult(String sourceText, String translatedText, Language targetLanguage, TranslationSource source)
	{
		this(sourceText, translatedText, targetLanguage, source, null, null, null);
	}

	public TranslationResult(String sourceText, String translatedText, Language targetLanguage,
		TranslationSource source, VocabularyEntry entry, String article, String articleBasis)
	{
		this.sourceText = sourceText;
		this.translatedText = translatedText;
		this.targetLanguage = targetLanguage;
		this.source = source;
		this.entry = entry;
		this.article = article;
		this.articleBasis = articleBasis;
		this.verified = source != null && source.isVerified();
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
	 * The dictionary entry describing the translation, if the dictionary has one.
	 */
	public Optional<VocabularyEntry> getEntry()
	{
		return Optional.ofNullable(entry);
	}

	/**
	 * The article of the translated noun, if one is known or could be derived.
	 */
	public Optional<String> getArticle()
	{
		return Optional.ofNullable(article).filter(a -> !a.trim().isEmpty()).map(String::trim);
	}

	/**
	 * Where the article came from, if an article was found at all.
	 */
	public Optional<String> getArticleBasis()
	{
		return Optional.ofNullable(articleBasis).filter(b -> !b.trim().isEmpty()).map(String::trim);
	}

	/**
	 * A copy of this result carrying the given dictionary entry and article.
	 * <p>
	 * The grammar is attached after the lookup rather than by the providers themselves,
	 * because it describes how the answer will be shown rather than what the answer is.
	 *
	 * @param entry        the dictionary entry, may be {@code null}
	 * @param article      the article of the translated noun, may be {@code null}
	 * @param articleBasis where the article came from, may be {@code null}
	 * @return this result if it already carries that grammar, otherwise a copy
	 */
	public TranslationResult withGrammar(VocabularyEntry entry, String article, String articleBasis)
	{
		if (Objects.equals(this.entry, entry)
			&& Objects.equals(this.article, article)
			&& Objects.equals(this.articleBasis, articleBasis))
		{
			return this;
		}

		return new TranslationResult(sourceText, translatedText, targetLanguage, source, entry, article, articleBasis);
	}

	/**
	 * The translation with its article in front of it.
	 * <p>
	 * The article is never doubled, so a translation that already begins with one is
	 * returned unchanged.
	 */
	public String getDisplayText()
	{
		return withArticle(translatedText, article);
	}

	/**
	 * Two results are the same when they translate the same text the same way.
	 * <p>
	 * The grammar is deliberately left out of the comparison: it is display metadata
	 * attached after the fact, and whether it happens to be known must not change what the
	 * cache considers a duplicate.
	 */
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