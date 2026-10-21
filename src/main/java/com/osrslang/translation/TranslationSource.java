package com.osrslang.translation;

/**
 * Where a translation came from.
 * <p>
 * The distinction between {@link #VERIFIED} and the machine-generated sources matters to
 * the user: a verified translation was written or checked by a human for this specific
 * game phrase, while everything else is machine output that may be wrong.
 */
public enum TranslationSource
{
	/**
	 * A human-checked translation of a specific OSRS phrase, item or term.
	 */
	VERIFIED,

	/**
	 * A word-level entry from the local vocabulary dictionary.
	 */
	DICTIONARY,

	/**
	 * A sentence that was translated before and has been stored for reuse.
	 */
	STORED_SENTENCE,

	/**
	 * Produced by the offline machine translation engine.
	 */
	OFFLINE_MACHINE,

	/**
	 * Produced by the optional online translation provider. Only ever used when the user
	 * has explicitly enabled online translation.
	 */
	ONLINE,

	/**
	 * Read back from the persistent translation cache.
	 */
	CACHE;

	/**
	 * Whether this source represents a human-verified translation.
	 */
	public boolean isVerified()
	{
		return this == VERIFIED;
	}

	/**
	 * Whether this source was produced by a machine and may contain errors.
	 */
	public boolean isMachineGenerated()
	{
		return this == OFFLINE_MACHINE || this == ONLINE;
	}

	/**
	 * A short label suitable for showing next to a translation.
	 */
	public String getLabel()
	{
		switch (this)
		{
			case VERIFIED:
				return "verified";
			case DICTIONARY:
				return "dictionary";
			case STORED_SENTENCE:
				return "saved";
			case OFFLINE_MACHINE:
				return "offline MT";
			case ONLINE:
				return "online MT";
			case CACHE:
				return "cached";
			default:
				return "unknown";
		}
	}
}
