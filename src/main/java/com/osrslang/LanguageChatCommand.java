package com.osrslang;

import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import com.osrslang.translation.Language;
import com.osrslang.translation.LanguageSupportService;
import com.osrslang.translation.ProviderLanguage;

/**
 * {@code !languages} - reports what the configured translation service can actually do.
 * <p>
 * The set of usable languages is not a fixed list: it depends on which models the user has
 * installed on their own machine, so the plugin cannot offer a list baked into its own
 * source. This asks the service instead and reports the truth, including whether the pair
 * currently configured is one of the possible ones.
 * <p>
 * The {@code sourceLanguage} and {@code targetLanguage} settings remain the single place
 * the languages are chosen; this command only tells the user which codes are worth
 * entering there.
 */
@Slf4j
public class LanguageChatCommand
{
	/**
	 * How many language codes go on one chat line, so a long list stays readable.
	 */
	private static final int CODES_PER_LINE = 8;

	@Inject
	private Client client;

	@Inject
	private OsrsLangConfig config;

	private final LanguageSupportService languageSupport;

	public LanguageChatCommand(LanguageSupportService languageSupport)
	{
		this.languageSupport = languageSupport;
	}

	/**
	 * The command's trigger, registered with the chat command manager.
	 */
	public String getTrigger()
	{
		return "languages";
	}

	/**
	 * Runs the command. Registered as a chat command, so it runs off the client thread.
	 */
	public void run()
	{
		say(languageSupport.getStatus());

		List<ProviderLanguage> languages = languageSupport.getLanguages();

		if (languages.isEmpty())
		{
			say("Start LibreTranslate and run this again. Without it only the bundled"
				+ " dictionaries work (English into German, Spanish and French).");
			return;
		}

		reportPair();
		reportLanguages(languages);
	}

	/**
	 * Lists the codes, wrapped so the chatbox stays readable.
	 */
	private void reportLanguages(List<ProviderLanguage> languages)
	{
		StringBuilder line = new StringBuilder("Available: ");
		int onLine = 0;

		for (ProviderLanguage language : languages)
		{
			if (onLine == CODES_PER_LINE)
			{
				say(line.toString());
				line.setLength(0);
				line.append("Available: ");
				onLine = 0;
			}

			line.append(language.getCode()).append(' ');
			onLine++;
		}

		if (onLine > 0)
		{
			say(line.toString());
		}
	}

	/**
	 * Says whether the configured pair can be translated, and how to make it so if not.
	 */
	private void reportPair()
	{
		Language from = Language.fromCode(config.sourceLanguage()).orElse(null);
		Language to = Language.fromCode(config.targetLanguage()).orElse(null);

		if (from == null || to == null)
		{
			say("The configured languages could not be read.");
			return;
		}

		if (from.isSameLanguage(to))
		{
			say("Source and target are both " + from.getName() + ".");
			return;
		}

		if (languageSupport.isSupported(from, to))
		{
			say(from.getName() + " -> " + to.getName() + " is supported.");
			return;
		}

		List<String> codes = new ArrayList<>();
		codes.add(from.getCode());
		codes.add(to.getCode());

		say(from.getName() + " -> " + to.getName() + " is not supported by the service.");
		say("Install the model with: libretranslate --load-only " + String.join(",", codes));
	}

	private void say(String message)
	{
		// The four-argument form is the one the client API offers: type, sender, prefix
		// and message. The prefix is what RuneLite shows as the speaker.
		client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "[OSRS Language Helper] ", message);
	}
}