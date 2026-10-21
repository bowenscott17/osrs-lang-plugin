package com.osrslang.translation;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * A language the plugin can translate between.
 * <p>
 * The set of languages is deliberately a fixed enum for the first release so that the
 * engine, the dictionaries and the UI all agree on the codes. Adding a language means
 * adding a constant here and shipping a language pack for it - no engine changes.
 */
public enum Language
{
	ENGLISH("en", "English", "English"),
	GERMAN("de", "German", "Deutsch"),
	FRENCH("fr", "French", "Français"),
	SPANISH("es", "Spanish", "Español"),
	JAPANESE("ja", "Japanese", "日本語");

	/**
	 * The ISO 639-1 code for this language.
	 */
	private final String code;

	/**
	 * The English name of the language.
	 */
	private final String name;

	/**
	 * The name of the language in that language.
	 */
	private final String nativeName;

	Language(String code, String name, String nativeName)
	{
		this.code = code;
		this.name = name;
		this.nativeName = nativeName;
	}

	public String getCode()
	{
		return code;
	}

	public String getName()
	{
		return name;
	}

	public String getNativeName()
	{
		return nativeName;
	}

	/**
	 * Whether this language separates words with spaces. Used by the tokenizer to decide
	 * whether it can safely split a sentence into individual words.
	 * <p>
	 * Japanese does not use spaces between words, so naive whitespace splitting would
	 * produce meaningless fragments for it.
	 */
	public boolean usesWordSpacing()
	{
		return this != JAPANESE;
	}

	/**
	 * Looks up a language by its ISO code, ignoring case.
	 *
	 * @param code the code to look up, may be {@code null}
	 * @return the language, or {@link Optional#empty()} if the code is unknown
	 */
	public static Optional<Language> fromCode(String code)
	{
		if (code == null)
		{
			return Optional.empty();
		}

		String normalized = code.trim().toLowerCase(Locale.ROOT);
		return Arrays.stream(values())
			.filter(l -> l.code.equals(normalized))
			.findFirst();
	}

	/**
	 * All languages as a map of code to language, in declaration order.
	 */
	public static Map<String, Language> byCode()
	{
		Map<String, Language> map = new LinkedHashMap<>();
		for (Language language : values())
		{
			map.put(language.code, language);
		}
		return Collections.unmodifiableMap(map);
	}

	/**
	 * The language codes as a list, in declaration order.
	 */
	public static List<String> codes()
	{
		return Arrays.stream(values()).map(Language::getCode).collect(java.util.stream.Collectors.toList());
	}
}
