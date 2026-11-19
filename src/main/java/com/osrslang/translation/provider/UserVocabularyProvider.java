package com.osrslang.translation.provider;

import com.osrslang.vocabulary.VocabularyList;
import com.osrslang.translation.Language;
import com.osrslang.translation.TranslationResult;
import com.osrslang.translation.TranslationSource;
import com.osrslang.text.GameText;
import net.runelite.client.plugins.Plugin;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import lombok.extern.slf4j.Slf4j;

/**
 * Provides translations from the user's personal saved vocabulary.
 * <p>
 * This provider is consulted after the verified database but before the generic dictionary,
 * so the user's own saved words always take priority over dictionary entries.
 */
@Slf4j
public class UserVocabularyProvider implements TranslationProvider
{
	private final VocabularyList vocabularyList;
	private final com.osrslang.OsrsLangPlugin plugin;

	public UserVocabularyProvider(VocabularyList vocabularyList, com.osrslang.OsrsLangPlugin plugin)
	{
		this.vocabularyList = vocabularyList;
		this.plugin = plugin;
	}

	@Override
	public String getName()
	{
		return "User Vocabulary";
	}

	@Override
	public boolean isEnabled()
	{
		return true;
	}

	@Override
	public boolean supports(Language source, Language target)
	{
		return true;
	}

	@Override
	public int getPriority()
	{
		// Priority 50: after verified database (10), before dictionary (100)
		return 50;
	}

	@Override
	public CompletableFuture<Optional<TranslationResult>> translate(String source, Language from, Language to, GameText.Type context)
	{
		// User vocabulary only handles single words, not phrases
		return CompletableFuture.completedFuture(Optional.empty());
	}

	@Override
	public Optional<VocabularyEntry> lookupWord(String word, Language from, Language to)
	{
		if (word == null || word.trim().isEmpty())
		{
			return Optional.empty();
		}

		return vocabularyList.lookupWord(word, from.getCode());
	}

	@Override
	public Plugin getPlugin()
	{
		return plugin;
	}
}