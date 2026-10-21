# OSRS Language Helper

A RuneLite plugin that translates Old School RuneScape dialogue and game text, so you can
learn a new language while playing.

## Status

| Milestone | Description | State |
|---|---|---|
| 1 | Working RuneLite plugin from the official template | **Done** |
| 2 | Detect accessible NPC dialogue and display it in an overlay | **Done** |
| 3 | Local dictionary and verified translation database | **Done** (en→de/fr/es) |
| 4 | Common translation-provider interface | **Done** |
| 5 | Offline translation engine | **Done** (LibreTranslate sidecar) |
| 6 | Side-by-side translated dialogue | **Done** |
| 7 | Word-level hover translations | **Done** |
| 8 | Persistent caching and asynchronous translation | **Done** |
| 9 | Optional online translation | **Done** (MyMemory / LibreTranslate) |
| 10 | Item and other accessible game-text translation | Partially (verified item names) |
| 11 | Vocabulary saving and export | Data model + CSV export done; UI not done |
| 12 | Tests, performance, distribution prep | 85 tests, not yet hub-submitted |

## Architecture

```
                     game widgets
                          |
                   DialogueReader          reads the text out of the dialogue widgets
                          |
                   DialogueTracker         remembers the last text seen, per client tick
                          |
                  TranslationService        async, de-duplicated requests
                          |
                  TranslationManager        picks the provider
                          |
        +-----------------+-----------------+
        |                                   |
  cache --> verified DB --> dictionary   offline engine --> online provider
```

Every provider implements `TranslationProvider` and reports a priority. The manager
consults them in priority order and the first one to answer wins, so a human-verified
phrase always beats a dictionary entry, and a dictionary entry always beats machine
translation.

| Provider | Priority | Notes |
|---|---|---|
| Translation cache | first | Checked before anything else |
| `OsrsTranslationDatabaseProvider` | 10 | Human-verified OSRS phrases, items, skills, quests |
| `LocalDictionaryProvider` | 50 | Single words, with gender, part of speech, examples |
| `OfflineMachineTranslationProvider` | 70 | Local LibreTranslate service, fallback tier |
| `OnlineTranslationProvider` | 90 | MyMemory (free) or LibreTranslate, fallback tier |

## Offline translation

RuneLite's plugin rules forbid JNI, JNA, reflection and launching external processes,
so an in-process translation engine is not possible - CTranslate2 and ONNX Runtime both
bind to Java through JNI. There is also no pure-Java port of Argos Translate or
Bergamot.

The plugin therefore talks to a **LibreTranslate** service that the user runs on their
own machine. LibreTranslate wraps the MIT-licensed Argos Translate engine, so no game
text ever leaves the computer.

Setup, once:

```bash
pip install libretranslate
libretranslate --load-only en,de,fr,es,ja
```

Or with Docker: `docker compose up`. LibreTranslate downloads the models itself on
first run; the plugin does not handle models at all.

Model sizes on disk, and approximate RAM while loaded:

| Pair | Model size |
|---|---|
| en→de | 144 MB |
| en→fr | 62 MB |
| en→es | 84 MB |
| en→ja | 115 MB |

Japanese **is** supported - the `en_ja` and `ja_en` packages both exist.

## Online translation

Disabled by default. The config item that enables it carries the third-party-server
warning RuneLite requires.

- **MyMemory** (default) - free, no API key, no sign-up. The anonymous tier is capped
  at 5,000 characters per day, so it suits occasional use.
- **LibreTranslate** - the hosted `libretranslate.com` service requires a **paid** API
  key (there is no free public instance), but a self-hosted instance needs none.

No API key is hard-coded anywhere.

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
- `TooltipManager` is a public injectable singleton, used by RuneLite's own plugins for
  hover tooltips. Word-level hover uses it.
- `OkHttpClient` and `Gson` are already on the classpath and must be injected, never
  constructed. OkHttp is 3.14.9, so `RequestBody.create(MediaType, String)` uses the
  older argument order.

## License

BSD-2-Clause.
