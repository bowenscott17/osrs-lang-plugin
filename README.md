# OSRS Language Plugin

**A RuneLite plugin for translating Old School RuneScape into other languages.**

The OSRS Language Plugin adds a translation layer to Old School RuneScape, allowing players to understand game dialogue, items, interfaces, actions and other in-game text in their selected language.

German is the foundation of the project and the first language being developed. Additional languages are planned and are still being worked on.

---

## Contents

- [Features](#features)
- [How It Works](#how-it-works)
- [Translation System](#translation-system)
- [Languages](#languages)
- [Requirements](#requirements)
- [Installation](#installation)
- [LibreTranslate Setup](#libretranslate-setup)
- [Building From Source](#building-from-source)
- [Troubleshooting](#troubleshooting)
- [Development Status](#development-status)

---

## Features

The plugin aims to translate the different parts of Old School RuneScape without changing the original game client.

| Feature | Description |
|---|---|
| NPC Dialogue | Translates conversations with NPCs |
| Item Names | Translates item names and vocabulary |
| Object Names | Translates objects and environmental text |
| Interface Text | Translates supported game interfaces |
| Skill Names | Provides translated skill names |
| Spellbook | Supports translated spell actions and tooltips |
| Right-click Menus | Translates available interaction options |
| Hover Translation | Displays translations when hovering over supported text |
| Dialogue Options | Translates available conversation choices |
| Original Text | Keeps the original English text available alongside translations |

Features and coverage are actively being developed.

## How It Works

The plugin works alongside RuneLite by detecting text displayed by the game and passing it through its translation system.

Rather than sending every piece of text directly to an online translator, the plugin uses several layers to find the most appropriate translation.

### Translation process

1. **Text detection**

   The plugin identifies supported text from NPC dialogue, game interfaces, item names, objects, menus and other game interactions.

2. **Translation lookup**

   The detected text is checked against previously saved translations and verified Old School RuneScape translations.

3. **Local vocabulary**

   If a verified translation is unavailable, the plugin checks its local language dictionaries and vocabulary resources.

4. **Sentence translation**

   Text that cannot be resolved through existing vocabulary can be passed through the sentence translation system.

5. **Translation engine**

   If necessary, the plugin can use its configured translation engine, including local LibreTranslate integration.

6. **Caching**

   Translations are cached so previously processed text can be reused instead of translated repeatedly.

This layered approach helps prioritise game-specific translations over generic machine translations.

## Translation System

The plugin uses a combination of translation resources and processing methods.

**1. Translation cache**

Previously translated text is stored and reused to reduce repeated processing.

**2. Verified OSRS translations**

Game-specific translations take priority where available. This helps preserve the meaning of RuneScape terminology.

**3. Local dictionaries**

The plugin includes language vocabulary resources that can be expanded as development continues.

**4. Sentence translation**

When a complete translation is not available, the system can process longer sentences rather than relying exclusively on individual words.

**5. LibreTranslate**

LibreTranslate provides an optional translation engine that can run locally on the player's computer.

**6. Additional translation providers**

The translation architecture is designed to allow additional translation resources and engines as the project develops.

### German language support

German requires more than replacing English words with German equivalents.

The plugin is being developed with additional German language information, including:

- Noun genders (der, die, das).
- Vocabulary and dictionary entries.
- German sentence translations.
- Game-specific terminology.
- Translation consistency across different interfaces.

The aim is to make translations useful for both understanding the game and learning German.

## Languages

| Language | Status |
|---|---|
| German | Primary development language |
| Other languages | In development |

German is the foundation of the translation system. Additional language support will be expanded as dictionaries, vocabulary and translation resources become available.

---

## Requirements

To run the development version of the plugin, you will need:

- Windows, macOS or Linux.
- Old School RuneScape.
- RuneLite.
- Java 17 for the current development setup.
- Git (to download the source code).
- LibreTranslate (optional, for local translation).

RuneLite and the Jagex Launcher can be downloaded from their official websites.

- https://runelite.net/
- https://www.jagex.com/launcher
- https://adoptium.net/temurin/releases/
- https://git-scm.com/downloads

---

## Installation

There are two ways to use the project: installing a published plugin or running the development version.

### Option 1 – RuneLite Plugin Hub

Once the plugin is available in the RuneLite Plugin Hub:

1. Open RuneLite.
2. Open the Plugin Hub from the sidebar.
3. Search for `OSRS Language Plugin`.
4. Install the plugin.
5. Open the plugin settings.
6. Select your preferred language and configure the available translation options.

## LibreTranslate Setup

LibreTranslate is an optional translation engine that can run locally instead of relying on a paid external translation API.

It provides an HTTP translation service that the plugin can communicate with.

### Step 1 – Install Python

Download Python:

https://www.python.org/downloads/

### Step 2 – Install LibreTranslate

Open PowerShell:

```powershell
python -m venv libretranslate-venv
```

Activate the environment:

```powershell
.\libretranslate-venv\Scripts\Activate.ps1
```

Install LibreTranslate:

```powershell
pip install libretranslate
```

### Step 3 – Start the translation server

Run:

```powershell
libretranslate --host 127.0.0.1 --port 5001
```

The local translation server will be available at:

```text
http://127.0.0.1:5001
```

Keep the terminal open while using the translation server.

### Step 4 – Connect the plugin

If using LibreTranslate, configure the plugin to use the local server address through its available translation settings.

The plugin and translation server must both be running.

**Important:** LibreTranslate is optional. It is not the same as the plugin's local dictionaries and verified translations. A local translation server also needs to be installed separately on each computer using it.

---

## Troubleshooting

| Problem | Possible solution |
|---|---|
| Java version error | Check that Java 17 is installed and selected |
| Gradle build fails | Run `.\gradlew.bat build` again and check the error output |
| Plugin does not load | Check the RuneLite development client and build output |
| Translations remain in English | Check language selection and translation resource availability |
| LibreTranslate does not respond | Check that the server is running on port 5001 |
| Missing translations | The relevant vocabulary or translation resource may not exist yet |
| Old translations appear | Check the translation cache and refresh or restart the client |

---

## Development Status

This project is actively being developed.

The current focus is on improving the German translation system and expanding coverage across the game.

Areas of ongoing development include:

- Expanding German vocabulary and dictionaries.
- Improving noun gender and article handling.
- Increasing NPC dialogue coverage.
- Improving interface and menu translations.
- Expanding hover translation support.
- Improving sentence translation quality.
- Adding additional language resources.
- Improving translation caching and performance.

Not every part of the game is fully translated yet. Some translations may be incomplete or require improvement.

---

## Contributing

Contributions are welcome.

If you would like to help, you can contribute through:

- Improving existing translations.
- Adding vocabulary and dictionary entries.
- Expanding language support.
- Reporting incorrect or missing translations.
- Helping improve the translation system.


---

## License

This project is open source. See the repository's LICENSE file for licensing information.

---

An independent RuneLite project. Not affiliated with or endorsed by Jagex.
