package com.osrslang.translation;

import java.util.ArrayList;
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
 * Languages are identified by their provider code rather than by an enum constant, because
 * a translation provider can return codes that are not simple two-letter ISO codes - for
 * example {@code zh-Hans} for Simplified Chinese or {@code pt-BR} for Brazilian
 * Portuguese. A fixed enum would make those codes unrepresentable, so a language is a
 * plain value object instead.
 * <p>
 * A handful of languages are known ahead of time, so the plugin can offer a sensible
 * default and can tokenise text without contacting the provider first. Any other code
 * returned by the provider is still usable: it simply has no curated name until the
 * provider's language list has been fetched.
 */
public final class Language
{
	public static final String ENGLISH_CODE = "en";
	public static final String GERMAN_CODE = "de";
	public static final String FRENCH_CODE = "fr";
	public static final String SPANISH_CODE = "es";
	public static final String JAPANESE_CODE = "ja";

	/**
	 * English.
	 */
	public static final Language ENGLISH = new Language(ENGLISH_CODE, "English", "English", true);

	/**
	 * German.
	 */
	public static final Language GERMAN = new Language(GERMAN_CODE, "German", "Deutsch", true);

	/**
	 * French.
	 */
	public static final Language FRENCH = new Language(FRENCH_CODE, "French", "Français", true);

	/**
	 * Spanish.
	 */
	public static final Language SPANISH = new Language(SPANISH_CODE, "Spanish", "Español", true);

	/**
	 * Japanese.
	 */
	public static final Language JAPANESE = new Language(JAPANESE_CODE, "Japanese", "日本語", false);

	/**
	 * The languages the plugin knows about without asking the provider.
	 * <p>
	 * These cover the languages the bundled dictionaries and verified databases were
	 * written for. The provider may well support more; those are discovered at runtime.
	 */
	private static final Map<String, Language> BUILT_IN = new LinkedHashMap<>();

	/**
	 * Languages the provider reported that the plugin has no curated data for, keyed by
	 * code. Bounded so a misbehaving provider cannot exhaust memory.
	 */
	private static final Map<String, Language> UNKNOWN = Collections.synchronizedMap(
		new LinkedHashMap<String, Language>(16, 0.75f, true)
		{
			@Override
			protected boolean removeEldestEntry(Map.Entry<String, Language> eldest)
			{
				return size() > 256;
			}
		});

	static
	{
		for (Language language : Arrays.asList(ENGLISH, GERMAN, FRENCH, SPANISH, JAPANESE))
		{
			BUILT_IN.put(language.code, language);
		}
	}

	/**
	 * The code the provider uses for this language, for example {@code de} or
	 * {@code zh-Hans}. The code is stored exactly as the provider spelled it, because
	 * some providers distinguish codes that differ only in their region suffix.
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

	/**
	 * Whether the language separates words with spaces.
	 * <p>
	 * Languages that do not cannot be split into individual words reliably by
	 * whitespace, so the tokenizer treats them as a single word.
	 */
	private final boolean usesWordSpacing;

	private Language(String code, String name, String nativeName, boolean usesWordSpacing)
	{
		this.code = code;
		this.name = name;
		this.nativeName = nativeName;
		this.usesWordSpacing = usesWordSpacing;
	}

	/**
	 * The language's provider code.
	 */
	public String getCode()
	{
		return code;
	}

	/**
	 * The English name of the language.
	 */
	public String getName()
	{
		return name;
	}

	/**
	 * The name of the language in that language.
	 */
	public String getNativeName()
	{
		return nativeName;
	}

	/**
	 * Whether this language separates words with spaces.
	 */
	public boolean usesWordSpacing()
	{
		return usesWordSpacing;
	}

	/**
	 * Looks up a language by its code, ignoring case.
	 * <p>
	 * A code the plugin does not know about is still usable: it is passed straight
	 * through to the provider. The language is given a name derived from its code so
	 * that it can be shown in the interface.
	 *
	 * @param code the code to look up, may be {@code null}
	 * @return the language, or {@link Optional#empty()} if the code is null or blank
	 */
	public static Optional<Language> fromCode(String code)
	{
		if (code == null || code.trim().isEmpty())
		{
			return Optional.empty();
		}

		String trimmed = code.trim();

		Language builtIn = BUILT_IN.get(trimmed);
		if (builtIn == null)
		{
			builtIn = BUILT_IN.get(trimmed.toLowerCase(Locale.ROOT));
		}

		if (builtIn != null)
		{
			return Optional.of(builtIn);
		}

		return Optional.of(unknown(trimmed));
	}

	/**
	 * Creates a language for a code the plugin does not know about.
	 * <p>
	 * The name is derived from the code, because a provider only guarantees the code.
	 * <p>
	 * Languages are cached so that the same code always yields the same instance. That
	 * keeps the identity comparisons the plugin used to rely on meaningful, and stops
	 * the unknown-code map from growing without bound.
	 */
	private static Language unknown(String code)
	{
		return UNKNOWN.computeIfAbsent(code, c -> new Language(c, c, c, usesWordSpacingFor(c)));
	}

	/**
	 * Creates a language from the data a provider returned.
	 *
	 * @param code       the provider's code for the language
	 * @param name       the English name, may be {@code null}
	 * @param nativeName the name in the language itself, may be {@code null}
	 * @return the language
	 */
	public static Language of(String code, String name, String nativeName)
	{
		if (code == null || code.trim().isEmpty())
		{
			throw new IllegalArgumentException("A language must have a code");
		}

		String trimmed = code.trim();

		// A built-in language keeps its curated word-spacing rule, but adopts the
		// provider's spelling of the code.
		Language builtIn = BUILT_IN.get(trimmed);
		if (builtIn == null)
		{
			builtIn = BUILT_IN.get(trimmed.toLowerCase(Locale.ROOT));
		}

		if (builtIn != null)
		{
			return new Language(
				trimmed,
				name != null && !name.isEmpty() ? name : builtIn.name,
				nativeName != null && !nativeName.isEmpty() ? nativeName : builtIn.nativeName,
				builtIn.usesWordSpacing);
		}

		return new Language(
			trimmed,
			name != null && !name.isEmpty() ? name : trimmed,
			nativeName != null && !nativeName.isEmpty() ? nativeName : trimmed,
			usesWordSpacingFor(trimmed));
	}

	/**
	 * Whether a language code names a script that does not separate words with spaces.
	 * <p>
	 * The provider may report languages the plugin has never heard of, so this is a
	 * best-effort rule based on the language part of the code. Getting this wrong only
	 * affects how the sentence is split into hoverable words.
	 */
	private static boolean usesWordSpacingFor(String code)
	{
		String language = code.toLowerCase(Locale.ROOT);

		// Scripts written without spaces between words.
		if (language.startsWith("zh") || language.startsWith("ja") || language.startsWith("ko")
			|| language.startsWith("th") || language.startsWith("lo") || language.startsWith("my")
			|| language.startsWith("km") || language.startsWith("bo"))
		{
			return false;
		}

		return true;
	}

	/**
	 * All built-in languages as a map of code to language, in declaration order.
	 */
	public static Map<String, Language> byCode()
	{
		return Collections.unmodifiableMap(BUILT_IN);
	}

	/**
	 * The codes of all built-in languages, in declaration order.
	 */
	public static List<String> codes()
	{
		return Collections.unmodifiableList(new ArrayList<>(BUILT_IN.keySet()));
	}

	/**
	 * The built-in languages, in declaration order.
	 */
	public static List<Language> builtIns()
	{
		return Collections.unmodifiableList(new ArrayList<>(BUILT_IN.values()));
	}

	/**
	 * Whether two languages are the same.
	 * <p>
	 * Codes are compared case-insensitively, so a language configured as {@code DE} and
	 * one the provider reports as {@code de} are recognised as the same language.
	 */
	public boolean isSameLanguage(Language other)
	{
		return other != null && code.equalsIgnoreCase(other.code);
	}

	@Override
	public boolean equals(Object o)
	{
		if (this == o)
		{
			return true;
		}
		if (!(o instanceof Language))
		{
			return false;
		}
		Language other = (Language) o;
		return code.equalsIgnoreCase(other.code);
	}

	@Override
	public int hashCode()
	{
		return code.toLowerCase(Locale.ROOT).hashCode();
	}

	@Override
	public String toString()
	{
		return name + " (" + code + ")";
	}
}