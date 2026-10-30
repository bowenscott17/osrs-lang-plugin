# 🌍 OSRS Language Plugin

### Experience Old School RuneScape in another language.

**OSRS Language Plugin** is an open-source RuneLite plugin built to bring language learning directly into Old School RuneScape.

Instead of constantly switching between a game and a language-learning app, I wanted to bring the learning experience directly into OSRS.

## 🇩🇪 Built around German

German is the foundation of this project.

The translation system, vocabulary resources, dictionaries and language-specific features are being developed around English-to-German translation first. This provides a solid foundation for expanding the plugin to support additional languages.

Other languages are also being worked on, but they are still in development and are not yet as complete as the German implementation.

The long-term goal is to create a flexible language-learning tool that allows players to choose their target language and learn naturally through everyday gameplay.

---

##  What does it do?

The aim is to translate as much of the game as possible while keeping the original OSRS experience intact.

Features being developed include:

- **NPC dialogue** – Understand conversations, interactions and quest dialogue.
- **Items and objects** – See translated names for inventory items, ground loot and objects.
- **Right-click menus** – Translate actions such as Chop down, Attack, Drop and Walk here.
- **Hover translations** – Discover translations when hovering over items, NPCs, skills and other game elements.
- **Game interfaces** – Translate interface text, buttons, banks and trading screens.
- **Skills and prayers** – Learn the terminology used throughout the game.
- **Spell actions** – Keep the original spellbook appearance while translating hover actions.
- **Vocabulary and articles** – Display German grammatical articles (der, die, das) alongside relevant nouns.

The goal isn't simply to replace English text. It's to make the translations useful for actually learning a language.

## 🛠️ Development status

This is an active development project. Features are being implemented, tested and improved individually. Some areas still need considerable work.

| Area | Status |
|---|---|
| German translation foundation | Established / expanding |
| Translation engine | In development |
| NPC dialogue | In development |
| Item and object names | In development |
| Right-click menus | In development |
| Hover translations | In development |
| Skill tooltips | In development |
| Bank and trading interfaces | In development |
| German articles (der/die/das) | In development |
| Additional languages | In development |

*German is the primary development language. Additional languages are being worked on and will be expanded over time.*

## 🧠 How it works

The plugin combines several translation approaches to make translations as consistent and useful as possible.

- RuneLite's game interface and text systems.
- Local translation resources and dictionaries.
- OSRS-specific vocabulary.
- A translation engine for text that isn't already covered by local resources.
- A caching system to avoid repeatedly translating the same text.

A major focus is maintaining consistent terminology throughout the game, rather than relying entirely on generic translations.

## 🗺️ The bigger picture

I'd like to eventually make this a flexible language-learning tool for the wider OSRS community, supporting multiple languages and allowing players to learn through the activities they already enjoy.

Imagine training Woodcutting, completing quests, talking to NPCs or trading with other players while naturally picking up vocabulary along the way.

That's what I'm working towards.

## 🤝 Contributions and feedback

This is a personal open-source project, and there's still plenty left to build.

If you're interested in language learning, RuneLite development, translation or Old School RuneScape, contributions and feedback are welcome.

- Found a translation that doesn't make sense? Open an issue.
- Found something that isn't being translated? Let me know.
- Have suggestions for another language? I'd love to hear them.
- Want to contribute? All help is appreciated.

## 📥 Installation

### From RuneLite Plugin Hub (recommended)
Once accepted, the plugin will be available in the RuneLite plugin panel under **OSRS Language Helper**.

### Manual installation (development builds)
1. Download the latest release JAR from the [releases page](https://github.com/bowenscott17/osrs-lang-plugin/releases)
2. Open RuneLite → Settings → Plugin Hub → **Install plugin manually**
3. Select the downloaded JAR

### Building from source
Requires JDK 17+ and Gradle:

```bash
git clone https://github.com/bowenscott17/osrs-lang-plugin.git
cd osrs-lang-plugin
./gradlew build
```

The built JAR will be in `build/libs/`.

---

## 🔧 Setting up LibreTranslate (offline translation)

The plugin can translate text using a local LibreTranslate service. This keeps all game text on your machine — nothing leaves your computer.

### Option 1: Python (quickest)

```bash
pip install libretranslate
libretranslate --load-only en,de,fr,es
```

The `--load-only` flag pre-loads only the language pairs you need (saves RAM). Supported pairs: `en,de,fr,es,ja`.

The service starts at `http://localhost:5000` by default.

### Option 2: Docker

```bash
docker run -d -p 5000:5000 \
  -e LT_LOAD_ONLY=en,de,fr,es \
  libretranslate/libretranslate
```

Or with Docker Compose (create `docker-compose.yml`):

```yaml
services:
  libretranslate:
    image: libretranslate/libretranslate
    ports:
      - "5000:5000"
    environment:
      - LT_LOAD_ONLY=en,de,fr,es
    restart: unless-stopped
```

Then run `docker compose up -d`.

### Model sizes (approximate disk / RAM)

| Pair | Model size |
|---|---|
| en→de | 144 MB |
| en→fr | 62 MB |
| en→es | 84 MB |
| en→ja | 115 MB |

### Configure the plugin

1. Open RuneLite → Settings → **OSRS Language Helper**
2. Enable **Use offline translation**
3. Set **Offline translation address** to your LibreTranslate URL (default: `http://127.0.0.1:5000`)
4. Select your target language (German, French, Spanish, Japanese)

The plugin will automatically use the local service when no verified or dictionary entry exists.

---

## ⚠️ Disclaimer

This is an independent community project and is not affiliated with, endorsed by or associated with Jagex or Old School RuneScape.

Old School RuneScape and RuneLite are trademarks and properties of their respective owners.

---

**Built by an OSRS player, for OSRS players.**
