package com.osrslang.translation.provider;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A single word and what is known about it.
 * <p>
 * Kept deliberately generic: grammatical gender, part of speech and inflection data are
 * language-specific, so they are optional rather than required.
 */
public class VocabularyEntry
{
	private final String word;

	/**
	 * The translation of the word into the target language.
	 */
	private final String translation;

	/**
	 * The part of speech, where the dictionary knows it (e.g. "noun", "verb").
	 */
	private final String partOfSpeech;

	/**
	 * The dictionary or lemma form of the word, where the language inflects.
	 */
	private final String dictionaryForm;

	/**
	 * Grammatical gender, where the language has it. For German this is "der", "die"
	 * or "das"; for others it may be a different system or absent entirely.
	 */
	private final String gender;

	/**
	 * An example sentence containing the word.
	 */
	private final String example;

	/**
	 * The English gloss of the word.
	 */
	private final String english;

	public VocabularyEntry(String word, String translation)
	{
		this(word, translation, null, null, null, null, null);
	}

	public VocabularyEntry(String word, String translation, String partOfSpeech, String dictionaryForm,
		String gender, String example, String english)
	{
		this.word = word;
		this.translation = translation;
		this.partOfSpeech = partOfSpeech;
		this.dictionaryForm = dictionaryForm;
		this.gender = gender;
		this.example = example;
		this.english = english;
	}

	public String getWord()
	{
		return word;
	}

	public String getTranslation()
	{
		return translation;
	}

	public Optional<String> getPartOfSpeech()
	{
		return Optional.ofNullable(partOfSpeech);
	}

	public Optional<String> getDictionaryForm()
	{
		return Optional.ofNullable(dictionaryForm);
	}

	public Optional<String> getGender()
	{
		return Optional.ofNullable(gender);
	}

	public Optional<String> getExample()
	{
		return Optional.ofNullable(example);
	}

	public Optional<String> getEnglish()
	{
		return Optional.ofNullable(english);
	}

	/**
	 * The English gloss, or the translation itself if no English gloss is recorded.
	 */
	public String getEnglishOrTranslation()
	{
		return english != null ? english : translation;
	}

	/**
	 * The article of the noun, where the dictionary records one.
	 * <p>
	 * German nouns take one of three articles and the wrong choice is a mistake a learner
	 * will repeat, so it is shown rather than left implicit in the translation. Languages
	 * without a gendered article system simply have no value here.
	 */
	public Optional<String> getArticle()
	{
		return Optional.ofNullable(gender).filter(g -> !g.trim().isEmpty()).map(String::trim);
	}

	/**
	 * The translation with the article in front of it.
	 * <p>
	 * Dictionaries disagree about whether the article belongs to the translation: some
	 * store "das Schwert", others store "Schwert" with the article in the gender field.
	 * Both are shown the same way here, and the article is never doubled.
	 */
	public String getDisplayTranslation()
	{
		if (gender != null && !gender.trim().isEmpty() && !startsWithWord(translation, gender.trim()))
		{
			return gender.trim() + " " + translation;
		}

		return translation;
	}

	/**
	 * The lines to show in a hover tooltip for this word.
	 */
	public List<String> toTooltipLines()
	{
		List<String> lines = new java.util.ArrayList<>();

		lines.add(getDisplayTranslation());

		if (english != null && !english.isEmpty())
		{
			lines.add("English: " + english);
		}

		if (partOfSpeech != null && !partOfSpeech.isEmpty())
		{
			lines.add("Part of speech: " + partOfSpeech);
		}

		if (dictionaryForm != null && !dictionaryForm.isEmpty() && !dictionaryForm.equals(word))
		{
			lines.add("Dictionary form: " + dictionaryForm);
		}

		if (example != null && !example.isEmpty())
		{
			lines.add("Example: " + example);
		}

		return Collections.unmodifiableList(lines);
	}

	/**
	 * Whether the text begins with the given word, on a word boundary.
	 */
	private static boolean startsWithWord(String text, String word)
	{
		if (text == null || word == null || word.isEmpty())
		{
			return false;
		}

		if (!text.regionMatches(true, 0, word, 0, word.length()))
		{
			return false;
		}

		if (text.length() == word.length())
		{
			return true;
		}

		char next = text.charAt(word.length());
		return !Character.isLetterOrDigit(next);
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
			&& Objects.equals(translation, other.translation)
			&& Objects.equals(partOfSpeech, other.partOfSpeech)
			&& Objects.equals(dictionaryForm, other.dictionaryForm)
			&& Objects.equals(gender, other.gender)
			&& Objects.equals(example, other.example)
			&& Objects.equals(english, other.english);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(word, translation, partOfSpeech, dictionaryForm, gender, example, english);
	}

	@Override
	public String toString()
	{
		return "VocabularyEntry{" + word + " -> " + translation + "}";
	}
}