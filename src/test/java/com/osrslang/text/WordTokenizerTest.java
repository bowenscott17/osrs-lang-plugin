package com.osrslang.text;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;
import com.osrslang.translation.Language;

public class WordTokenizerTest
{
	private final WordTokenizer tokenizer = new WordTokenizer(Language.ENGLISH);

	@Test
	public void testSimpleSentence()
	{
		List<WordTokenizer.Token> tokens = tokenizer.tokenize("I need a sword");

		assertEquals(4, tokens.size());
		assertEquals("I", tokens.get(0).getBareWord());
		assertEquals("need", tokens.get(1).getBareWord());
		assertEquals("a", tokens.get(2).getBareWord());
		assertEquals("sword", tokens.get(3).getBareWord());
	}

	@Test
	public void testPunctuationIsStrippedFromBareWord()
	{
		List<WordTokenizer.Token> tokens = tokenizer.tokenize("Hello, world!");

		assertEquals(2, tokens.size());
		assertEquals("Hello", tokens.get(0).getBareWord());
		assertEquals("Hello,", tokens.get(0).getText());
		assertEquals("world", tokens.get(1).getBareWord());
		assertEquals("world!", tokens.get(1).getText());
	}

	@Test
	public void testApostropheStaysInWord()
	{
		List<WordTokenizer.Token> tokens = tokenizer.tokenize("don't");

		assertEquals(1, tokens.size());
		assertEquals("don't", tokens.get(0).getBareWord());
	}

	@Test
	public void testTokenPositionsCoverTheSentence()
	{
		String sentence = "Bring me a bucket of water.";
		List<WordTokenizer.Token> tokens = tokenizer.tokenize(sentence);

		assertEquals(sentence, WordTokenizer.join(tokens, sentence));
	}

	@Test
	public void testJoinPreservesOriginalExactly()
	{
		String sentence = "Could you bring me  a bucket?";
		List<WordTokenizer.Token> tokens = tokenizer.tokenize(sentence);

		assertEquals(sentence, WordTokenizer.join(tokens, sentence));
	}

	@Test
	public void testEmptySentence()
	{
		assertTrue(tokenizer.tokenize("").isEmpty());
		assertTrue(tokenizer.tokenize(null).isEmpty());
	}

	@Test
	public void testPunctuationOnlyTokenIsNotAWord()
	{
		List<WordTokenizer.Token> tokens = tokenizer.tokenize("Hello --- world");

		assertEquals(3, tokens.size());
		assertFalse(tokens.get(1).isWord());
		assertEquals("", tokens.get(1).getBareWord());
	}

	@Test
	public void testJapaneseIsNotSplitOnSpaces()
	{
		WordTokenizer japanese = new WordTokenizer(Language.JAPANESE);
		List<WordTokenizer.Token> tokens = japanese.tokenize("剣を取ってください");

		assertEquals(1, tokens.size());
	}

	@Test
	public void testStripPunctuation()
	{
		assertEquals("word", WordTokenizer.stripPunctuation("word,"));
		assertEquals("word", WordTokenizer.stripPunctuation("\"word\""));
		assertEquals("word", WordTokenizer.stripPunctuation("(word)"));
		assertEquals("", WordTokenizer.stripPunctuation("..."));
		assertEquals("don't", WordTokenizer.stripPunctuation("don't."));
	}
}
