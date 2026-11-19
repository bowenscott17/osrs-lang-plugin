package com.osrslang.vocabulary;

import com.google.gson.Gson;
import net.runelite.api.Client;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.util.Filepath;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardOpenOption;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * Handles exporting and importing the saved vocabulary as CSV files.
 * <p>
 * Uses RuneLite's Filepath.Chooser for user-selected file operations.
 */
@Slf4j
public class VocabularyExporter
{
	private final VocabularyList vocabulary;
	private final Plugin plugin;
	private final Gson gson;

	public VocabularyExporter(VocabularyList vocabulary, Plugin plugin, Gson gson)
	{
		this.vocabulary = vocabulary;
		this.plugin = plugin;
		this.gson = gson;
	}

	/**
	 * Returns the underlying vocabulary list.
	 */
	public VocabularyList getVocabularyList()
	{
		return vocabulary;
	}

	/**
	 * Opens a file chooser and exports the vocabulary to the selected CSV file.
	 */
	public void exportToFile(Client client)
	{
		Filepath.Chooser chooser = new Filepath.Chooser()
			.setDialogTitle("Export Vocabulary")
			.setFileName(suggestedFileName())
			.setIsSave()
			.setAcceptsFiles();

		List<Filepath> files = chooser.showDialog(client);
		if (files != null && !files.isEmpty())
		{
			export(files.get(0));
		}
	}

	/**
	 * Writes the vocabulary to the given file.
	 */
	public void export(Filepath file)
	{
		try
		{
			file.createDirectories();
			try (Writer writer = file.openWriter(StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))
			{
				writer.write(vocabulary.toCsv());
			}
			log.info("Exported {} vocabulary entries to {}", vocabulary.size(), file);
		}
		catch (IOException | RuntimeException e)
		{
			log.warn("Failed to export vocabulary to {}", file, e);
		}
	}

	/**
	 * Opens a file chooser and imports vocabulary from the selected CSV file.
	 */
	public void importFromFile(Client client)
	{
		Filepath.Chooser chooser = new Filepath.Chooser()
			.setDialogTitle("Import Vocabulary")
			.setIsOpen()
			.setAcceptsFiles()
			.setDefaultExtension("csv");

		List<Filepath> files = chooser.showDialog(client);
		if (files != null && !files.isEmpty())
		{
			importFromFile(files.get(0));
		}
	}

	/**
	 * Imports vocabulary from the given CSV file.
	 */
	public void importFromFile(Filepath file)
	{
		try (Reader reader = file.openReader())
		{
			StringBuilder content = new StringBuilder();
			char[] buffer = new char[8192];
			int read;
			while ((read = reader.read(buffer)) != -1)
			{
				content.append(buffer, 0, read);
			}

			int imported = vocabulary.importFromCsv(content.toString(), "en");
			if (imported > 0)
			{
				log.info("Imported {} vocabulary entries from {}", imported, file);
			}
			else
			{
				log.warn("No valid vocabulary entries found in {}", file);
			}
		}
		catch (IOException | RuntimeException e)
		{
			log.warn("Failed to import vocabulary from {}", file, e);
		}
	}

	/**
	 * The suggested file name for an export.
	 */
	public String suggestedFileName()
	{
		return "osrs-vocabulary.csv";
	}
}