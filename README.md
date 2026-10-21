# OSRS Language Helper

A RuneLite plugin that translates Old School RuneScape dialogue and game text, so you can
learn a new language while playing.

## Status

| Milestone | Description | State |
|---|---|---|
| 1 | Working RuneLite plugin from the official template | **Done** |
| 2 | Detect accessible NPC dialogue and display it in an overlay | **Done** (original text only) |
| 3 | Local dictionary and verified translation database | Not started |
| 4 | Common translation-provider interface | Not started |
| 5 | Offline translation engine | Not started |
| 6 | Side-by-side translated dialogue | Not started |
| 7 | Word-level hover translations | Not started |
| 8 | Persistent caching and asynchronous translation | Not started |
| 9 | Optional online translation | Not started |
| 10 | Item and other accessible game-text translation | Not started |
| 11 | Vocabulary saving and export | Not started |
| 12 | Tests, performance, distribution prep | Partially done |

## Architecture

```
Game widgets  ->  DialogueReader  ->  DialogueTracker  ->  TranslationOverlay
                     (reads text)      (dedupes per tick)    (draws the panel)
```

The `text` package owns everything about *getting* text out of the game.
The `overlay` package owns everything about *drawing* it.
The translation engine (milestones 3+) will sit between the tracker and the overlay as a
`TranslationProvider` chain, so the UI never changes when a new provider is added.

## Building

Requires a JDK 17 or 21 to run Gradle (the plugin itself is compiled for Java 11).

```bash
./gradlew build      # compile + run tests
./gradlew test       # tests only
./gradlew run        # launch the RuneLite dev client with this plugin
```

On Windows use `gradlew.bat` instead of `./gradlew`.

## Reading the RuneLite API

The API is not on this machine; it is downloaded by Gradle from `repo.runelite.net`.
To read the API source, fetch the sources jar for the version in use:

```bash
curl -L -o runelite-api-sources.jar \
  https://repo.runelite.net/net/runelite/runelite-api/1.13.3/runelite-api-1.13.3-sources.jar
unzip -q runelite-api-sources.jar -d runelite-api-src
```

Key facts established while building this (all verified against RuneLite 1.13.3):

- NPC dialogue does **not** arrive through the `ChatMessage` event. Its javadoc states the
  event does not fire for NPC dialogue. Dialogue must be read from widgets.
- Dialogue components live in `net.runelite.api.gameval.InterfaceID`:
  `ChatLeft.TEXT` (NPC), `ChatRight.TEXT` (player), `Chatmenu.OPTIONS` (options),
  `ChatLeft.NAME` (NPC name).
- `net.runelite.api.gameval.InterfaceID` constants are full component ids, usable directly
  with `client.getWidget(int)`.
- `PluginDescriptor.internalName` and `Filepath` (for plugin data directories) exist as of
  RuneLite 1.13.3.

## License

BSD-2-Clause.
