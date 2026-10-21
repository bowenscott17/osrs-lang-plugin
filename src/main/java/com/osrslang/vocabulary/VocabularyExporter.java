package com.osrslang.vocabulary;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardOpenOption;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.util.Filepath;

/**
 * Writes the saved vocabulary out as a CSV file.
 */
@Slf4j
public class VocabularyExporter
{
	private final VocabularyList vocabulary;

	public VocabularyExporter(VocabularyList vocabulary)
	{
		this.vocabulary = vocabulary;
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
		}
		catch (IOException | RuntimeException e)
		{
			log.warn("Failed to export vocabulary to {}", file, e);
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
