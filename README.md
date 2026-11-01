# OSRS Language Plugin

### Play Old School RuneScape in another language.

OSRS Language Plugin is a RuneLite addon that brings language translation directly into Old School RuneScape.

It helps translate NPC conversations, items, objects, interfaces, menus and other in-game text, making it easier to experience the game in your chosen language.

The plugin combines its own vocabulary and saved translations with LibreTranslate to provide translation support across multiple languages.

## What does it do?

- Translate NPC dialogue and conversation options.
- Translate item and object names.
- Translate supported interfaces and skill names.
- Translate right-click menus and actions.
- Show translations when hovering over supported text.
- Display original and translated text together.
- Support multiple languages through vocabulary resources and LibreTranslate.

## How does it work?

The plugin checks its vocabulary and saved translations first. If a translation isn't available, it can use LibreTranslate to translate the text.

Previously translated text can be saved and reused, helping reduce repeated translation requests.

LibreTranslate can run locally on your computer, meaning you don't need a paid translation API or subscription.

## Getting Started

### 1. Install RuneLite

Download and install RuneLite from the official website:

https://runelite.net/

Open RuneLite and make sure it is running correctly.

### 2. Install OSRS Language Plugin

Once available on the RuneLite Plugin Hub:

1. Open the Plugin Hub in RuneLite.
2. Search for **OSRS Language Plugin**.
3. Install and enable the plugin.
4. Open its settings and select your preferred language.

### 3. Install LibreTranslate (Optional)

LibreTranslate provides additional translation support and can run locally on your computer.

First, install Python:

https://www.python.org/downloads/

Open PowerShell and run:

```powershell
python -m venv libretranslate-venv
.\libretranslate-venv\Scripts\Activate.ps1
pip install libretranslate
```

Start LibreTranslate:

```powershell
libretranslate --host 127.0.0.1 --port 5001
```

Your local translation server will run at:

`http://127.0.0.1:5001`

Keep the server running while playing and configure the plugin to use this address.

LibreTranslate is optional. The plugin can still use its available vocabulary and saved translations without it.

## Development

The plugin is an ongoing project, with new translations, vocabulary and improvements being added over time.

Language coverage and translation quality can vary depending on the available resources and translation engine.

If you find a translation that needs fixing or have an idea for an improvement, feedback and contributions are always welcome.

---

**Thanks for checking out the project and happy scaping!**

An independent community project for RuneLite and Old School RuneScape. Not affiliated with or endorsed by Jagex.
