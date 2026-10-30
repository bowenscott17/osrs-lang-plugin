package com.osrslang.translation;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * A language the translation provider reported, along with the languages it can translate
 * into.
 * <p>
 * Providers list their own capabilities, so the plugin can offer only combinations that
 * actually work. LibreTranslate's {@code /languages} endpoint returns, for each language,
 * the list of languages a model can translate it into - so a provider that has only the
 * English-to-German model installed lists German as an English target but not as a Spanish
 * one. That is why the targets are kept rather than assuming every listed language can be
 * combined with every other.
 */
public class ProviderLanguage
{
	private final Language language;

	/**
	 * The codes this language can be translated into, lower-cased.
	 * <p>
	 * Empty when the provider did not say, in which case any listed language is assumed to
	 * be combinable.
	 */
	private final Set<String> targets;

	public ProviderLanguage(Language language, Set<String> targets)
	{
		this.language = language;
		this.targets = normalise(targets);
	}

	public Language getLanguage()
	{
		return language;
	}

	/**
	 * The provider's code for this language.
	 */
	public String getCode()
	{
		return language.getCode();
	}

	/**
	 * The codes this language can be translated into, or an empty set if the provider did
	 * not say.
	 */
	public Set<String> getTargets()
	{
		return targets;
	}

	/**
	 * Whether this language can be translated into the other one.
	 * <p>
	 * A provider that lists no targets at all is taken at its word: everything it lists is
	 * treated as combinable. Otherwise the target has to be named explicitly, so a pair
	 * the provider cannot do is never offered as if it could.
	 */
	public boolean canTranslateTo(Language other)
	{
		if (other == null)
		{
			return false;
		}

		if (targets.isEmpty())
		{
			return true;
		}

		return targets.contains(other.getCode().toLowerCase(Locale.ROOT));
	}

	private static Set<String> normalise(Set<String> codes)
	{
		if (codes == null || codes.isEmpty())
		{
			return Collections.emptySet();
		}

		Set<String> lower = new LinkedHashSet<>(codes.size());
		for (String code : codes)
		{
			if (code != null && !code.trim().isEmpty())
			{
				lower.add(code.trim().toLowerCase(Locale.ROOT));
			}
		}

		return Collections.unmodifiableSet(lower);
	}

	@Override
	public String toString()
	{
		return "ProviderLanguage{" + language.getCode() + " -> " + targets + "}";
	}
}