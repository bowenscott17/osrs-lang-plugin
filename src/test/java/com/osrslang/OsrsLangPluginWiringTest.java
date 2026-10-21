package com.osrslang;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import com.google.gson.Gson;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import net.runelite.api.Client;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;
import org.junit.Before;
import org.junit.Test;
import com.osrslang.text.DialogueReader;
import com.osrslang.text.DialogueTracker;
import com.osrslang.translation.Language;

/**
 * Verifies that the components the plugin injects can actually be constructed.
 * <p>
 * A mistake in a constructor signature or a missing binding shows up here as a Guice
 * provisioning error, rather than as a silent failure when the plugin starts in game.
 */
public class OsrsLangPluginWiringTest
{
	private Injector injector;

	@Before
	public void setUp()
	{
		injector = Guice.createInjector(new AbstractModule()
		{
			@Override
			protected void configure()
			{
				bind(Client.class).toInstance(mock(Client.class));
				bind(OverlayManager.class).toInstance(mock(OverlayManager.class));
				bind(TooltipManager.class).toInstance(new TooltipManager());
				bind(Gson.class).toInstance(new Gson());
			}
		});
	}

	@Test
	public void testDialogueReaderIsInjectable()
	{
		DialogueReader reader = injector.getInstance(DialogueReader.class);
		assertNotNull(reader);
	}

	@Test
	public void testDialogueTrackerIsInjectable()
	{
		DialogueTracker tracker = injector.getInstance(DialogueTracker.class);
		assertNotNull(tracker);
	}

	@Test
	public void testWordTooltipOverlayIsConstructible()
	{
		// The overlay needs a TranslationManager, which the plugin builds by hand
		// rather than injecting, so it is constructed directly here.
		com.osrslang.overlay.WordTooltipOverlay overlay = new com.osrslang.overlay.WordTooltipOverlay(
			mock(Client.class),
			new TooltipManager(),
			new com.osrslang.translation.TranslationManager(),
			Language.ENGLISH,
			Language.GERMAN);

		assertNotNull(overlay);
	}

	@Test
	public void testTranslationOverlayIsConstructible()
	{
		com.osrslang.overlay.TranslationOverlay overlay =
			new com.osrslang.overlay.TranslationOverlay(null);
		assertNotNull(overlay);
	}

	@Test
	public void testLanguageResolution()
	{
		assertTrue(Language.fromCode("de").isPresent());
		assertTrue(Language.fromCode("de").get() == Language.GERMAN);
	}
}
