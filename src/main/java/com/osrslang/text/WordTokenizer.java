package com.osrslang.text;

import java.util.ArrayList;
import java.util.List;
import com.osrslang.translation.Language;

/**
 * Splits a sentence into words while keeping enough information to rebuild the original
 * formatting.
 * <p>
 * Each token records where it starts and ends in the original string, so the sentence
 * can be reconstructed exactly by joining the tokens in order. Punctuation is attached
 * to the word it belongs to rather than being thrown away, because in several languages
 * it is part of the word.
 */
public class WordTokenizer
{
	private final Language language;

	public WordTokenizer(Language language)
	{
		this.language = language;
	}

	/**
	 * One word or punctuation mark within a sentence.
	 */
	public static class Token
	{
		private final String text;

		/**
		 * The index of the first character of this token in the original sentence.
		 */
		private final int start;

		/**
		 * The index just past the last character of this token.
		 */
		private final int end;

		/**
		 * The word with punctuation stripped, suitable for a dictionary lookup.
		 */
		private final String bareWord;

		public Token(String text, int start, int end, String bareWord)
		{
			this.text = text;
			this.start = start;
			this.end = end;
			this.bareWord = bareWord;
		}

		/**
		 * The token exactly as it appears in the sentence.
		 */
		public String getText()
		{
			return text;
		}

		public int getStart()
		{
			return start;
		}

		public int getEnd()
		{
			return end;
		}

		/**
		 * The token with leading and trailing punctuation removed.
		 */
		public String getBareWord()
		{
			return bareWord;
		}

		/**
		 * Whether this token is a real word rather than standalone punctuation.
		 */
		public boolean isWord()
		{
			return !bareWord.isEmpty();
		}

		@Override
		public String toString()
		{
			return "Token{" + text + "}";
		}
	}

	/**
	 * Splits the sentence into tokens.
	 *
	 * @param sentence the sentence to split
	 * @return the tokens, in order
	 */
	public List<Token> tokenize(String sentence)
	{
		List<Token> tokens = new ArrayList<>();

		if (sentence == null || sentence.isEmpty())
		{
			return tokens;
		}

		if (!language.usesWordSpacing())
		{
			// Languages without word spacing cannot be split reliably on whitespace, so
			// the whole sentence is returned as one token. A proper segmenter for these
			// languages is a separate concern from this plugin.
			tokens.add(new Token(sentence, 0, sentence.length(), sentence));
			return tokens;
		}

		int i = 0;
		int length = sentence.length();

		while (i < length)
		{
			char c = sentence.charAt(i);

			if (Character.isWhitespace(c))
			{
				i++;
				continue;
			}

			int start = i;

			// Consume a run of word characters.
			while (i < length && !Character.isWhitespace(sentence.charAt(i)))
			{
				i++;
			}

			String text = sentence.substring(start, i);
			tokens.add(new Token(text, start, i, stripPunctuation(text)));
		}

		return tokens;
	}

	/**
	 * Rebuilds a sentence from its tokens. Since the tokens cover the sentence
	 * contiguously apart from whitespace, the original can be recovered exactly.
	 */
	public static String join(List<Token> tokens, String original)
	{
		if (tokens.isEmpty())
		{
			return original;
		}

		StringBuilder sb = new StringBuilder();
		int cursor = 0;

		for (Token token : tokens)
		{
			if (token.getStart() > cursor)
			{
				sb.append(original, cursor, token.getStart());
			}
			sb.append(token.getText());
			cursor = token.getEnd();
		}

		if (cursor < original.length())
		{
			sb.append(original.substring(cursor));
		}

		return sb.toString();
	}

	/**
	 * Removes leading and trailing punctuation from a token, leaving the word itself.
	 */
	public static String stripPunctuation(String text)
	{
		if (text == null || text.isEmpty())
		{
			return "";
		}

		int start = 0;
		int end = text.length();

		while (start < end && isPunctuation(text.charAt(start)))
		{
			start++;
		}

		while (end > start && isPunctuation(text.charAt(end - 1)))
		{
			end--;
		}

		return text.substring(start, end);
	}

	/**
	 * Whether the character is punctuation rather than part of a word.
	 * <p>
	 * Apostrophes are deliberately included: in English and French a trailing apostrophe
	 * belongs to the word, and stripping it would turn "don't" into "don".
	 */
	private static boolean isPunctuation(char c)
	{
		if (c == '\'' || c == '’')
		{
			return false;
		}

		return !Character.isLetterOrDigit(c);
	}
}
