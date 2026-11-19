package com.osrslang;

import com.osrslang.vocabulary.VocabularyExporter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import javax.inject.Inject;

/**
 * {@code !vocab} - manages the user's personal vocabulary.
 * <p>
 * Provides chat commands to import, export, and view the saved vocabulary.
 */
@Slf4j
public class VocabularyChatCommand
{
	@Inject
	private Client client;

	private final VocabularyExporter vocabularyExporter;

	public VocabularyChatCommand(VocabularyExporter vocabularyExporter)
	{
		this.vocabularyExporter = vocabularyExporter;
	}

	/**
	 * The command's trigger, registered with the chat command manager.
	 */
	public String getTrigger()
	{
		return "vocab";
	}

	/**
	 * Runs the command. Registered as a chat command, so it runs off the client thread.
	 */
	public void run(String arguments)
	{
		if (arguments == null || arguments.trim().isEmpty())
		{
			showHelp();
			return;
		}

		String[] parts = arguments.trim().split("\\s+", 2);
		String subCommand = parts[0].toLowerCase();

		switch (subCommand)
		{
			case "export":
				exportVocabulary();
				break;
			case "import":
				importVocabulary();
				break;
			case "list":
				listVocabulary();
				break;
			case "clear":
				clearVocabulary();
				break;
			case "help":
			default:
				showHelp();
				break;
		}
	}

	private void showHelp()
	{
		say("Vocabulary commands:");
		say("  !vocab export  - Export vocabulary to a CSV file");
		say("  !vocab import  - Import vocabulary from a CSV file");
		say("  !vocab list    - List saved vocabulary words");
		say("  !vocab clear   - Clear all saved vocabulary");
		say("  !vocab help    - Show this help");
	}

	private void exportVocabulary()
	{
		vocabularyExporter.exportToFile(client);
		say("Opening file chooser to export vocabulary...");
	}

	private void importVocabulary()
	{
		vocabularyExporter.importFromFile(client);
		say("Opening file chooser to import vocabulary...");
	}

	private void listVocabulary()
	{
		int total = vocabularyExporter.getVocabularyList().size();
		int known = vocabularyExporter.getVocabularyList().getKnown().size();
		int unknown = vocabularyExporter.getVocabularyList().getUnknown().size();

		say("Vocabulary: " + total + " words (" + known + " known, " + unknown + " learning)");
	}

	private void clearVocabulary()
	{
		vocabularyExporter.getVocabularyList().clear();
		say("Vocabulary cleared.");
	}

	private void say(String message)
	{
		client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "[OSRS Language Helper] ", message);
	}
}