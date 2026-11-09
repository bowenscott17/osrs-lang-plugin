# OSRS Language Helper

Learn a new language while playing Old School RuneScape.

OSRS Language Helper is an open-source RuneLite plugin that translates in-game dialogue and text into your chosen language, helping you learn naturally as you play.

## How it works

The plugin combines several translation sources to provide translations while keeping the original game experience intact.

- **Verified OSRS vocabulary:** A database of translated game terms, dialogue, items, skills and other game content.
- **Local dictionaries:** Translates individual words and provides additional vocabulary information.
- **LibreTranslate:** Provides machine translation through a locally running translation service.
- **Translation cache:** Saves previous translations to reduce repeated requests.

The plugin checks its existing translations and dictionaries first, using machine translation when needed.

Multiple languages are supported through the vocabulary database and LibreTranslate language models. Available languages depend on the installed translation models and vocabulary coverage.

## Getting started

### 1. Install RuneLite

Download and install the official RuneLite client:

https://runelite.net/

### 2. Install OSRS Language Helper

Once available through the RuneLite Plugin Hub:

1. Open RuneLite.
2. Open the Plugin Hub.
3. Search for **OSRS Language Helper**.
4. Install and enable the plugin.

### 3. Install LibreTranslate

LibreTranslate provides offline machine translation using locally installed language models.

You will need Python installed on your computer.

Open PowerShell or a terminal and run:

```bash
pip install libretranslate
```

Start LibreTranslate:

```bash
libretranslate --host 127.0.0.1 --port 5001
```

Keep this running while playing RuneScape.

The first startup may take some time while the translation models are downloaded.

### 4. Configure your language

Choose your target language in the plugin settings and configure the local LibreTranslate connection if required.

The default local address is:

```text
http://127.0.0.1:5001
```

Make sure the corresponding language models are installed in LibreTranslate.

## Supported languages

The plugin is designed to support multiple languages rather than being limited to one language.

Translation coverage varies depending on the available vocabulary database and LibreTranslate language models.

Additional vocabulary and language support can be expanded over time.

## Privacy and costs

- No API key is required for locally hosted LibreTranslate.
- Local machine translation runs on your own computer.
- No paid translation subscription is required for local translation.
- Optional online translation providers may use third-party services.

## Open source

OSRS Language Helper is an open-source project. Contributions, feedback and additional language resources are welcome.

**License:** BSD-2-Clause