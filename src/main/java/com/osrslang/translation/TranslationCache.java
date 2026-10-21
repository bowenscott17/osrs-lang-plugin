package com.osrslang.translation;

import java.util.Optional;

/**
 * Stores translations so identical text is never translated twice.
 */
public interface TranslationCache
{
	/**
	 * Looks up a cached translation.
	 */
	Optional<TranslationResult> get(String text, Language from, Language to);

	/**
	 * Stores a translation.
	 */
	void put(String text, Language from, Language to, TranslationResult result);

	/**
	 * The number of entries currently held.
	 */
	int size();

	/**
	 * Removes all entries.
	 */
	void clear();
}
