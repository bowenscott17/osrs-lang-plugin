import io
import json
import os

# (english, french, spanish, category, id)
ENTRIES = [
    ("Could you bring me a bucket of water?",
     "Peux-tu m'apporter un seau d'eau ?",
     "¿Puedes traerme un balde de agua?", "sentence", "sentence.bucket-of-water"),
    ("Hello, how are you?",
     "Bonjour, comment ça va ?",
     "Hola, ¿cómo estás?", "sentence", "sentence.hello-how-are-you"),
    ("How are you?",
     "Comment ça va ?",
     "¿Cómo estás?", "sentence", "sentence.how-are-you"),
    ("What do you want?",
     "Que veux-tu ?",
     "¿Qué quieres?", "sentence", "sentence.what-do-you-want"),
    ("Do you have any...?",
     "As-tu... ?",
     "¿Tienes alguno...?", "sentence", "sentence.do-you-have-any"),
    ("Thank you very much.",
     "Merci beaucoup.",
     "Muchas gracias.", "sentence", "sentence.thank-you-very-much"),
    ("You are welcome.",
     "Je t'en prie.",
     "De nada.", "sentence", "sentence.you-are-welcome"),
    ("Please wait a moment.",
     "Attends un instant, s'il te plaît.",
     "Espera un momento, por favor.", "sentence", "sentence.please-wait"),
    ("Come back later.",
     "Reviens plus tard.",
     "Vuelve más tarde.", "sentence", "sentence.come-back-later"),
    ("Kill the dragon!",
     "Tue le dragon !",
     "¡Mata al dragón!", "sentence", "sentence.kill-the-dragon"),
    ("Bucket", "Seau", "Balde", "item", "item.bucket"),
    ("Water", "Eau", "Agua", "item", "item.water"),
    ("Bread", "Pain", "Pan", "item", "item.bread"),
    ("Bronze sword", "Épée en bronze", "Espada de bronce", "item", "item.bronze_sword"),
    ("Iron sword", "Épée en fer", "Espada de hierro", "item", "item.iron_sword"),
    ("Wooden shield", "Bouclier en bois", "Escudo de madera", "item", "item.wooden_shield"),
    ("Coal", "Charbon", "Carbón", "item", "item.coal"),
    ("Bronze ore", "Minerai de bronze", "Mineral de bronce", "item", "item.bronze_ore"),
    ("Iron ore", "Minerai de fer", "Mineral de hierro", "item", "item.iron_ore"),
    ("Abyssal whip", "Fouet abyssal", "Látigo abisal", "item", "item.abyssal_whip"),
    ("A bucket of water", "Un seau d'eau", "Un balde de agua", "item-description", "item-desc.bucket_of_water"),
    ("A coil of rope", "Une corde", "Una cuerda", "item-description", "item-desc.coil_of_rope"),
    ("Rune", "Rune", "Runa", "term", "term.rune"),
    ("Runes", "Runes", "Runas", "term", "term.runes"),
    ("Attack", "Attaque", "Ataque", "skill", "skill.attack"),
    ("Strength", "Force", "Fuerza", "skill", "skill.strength"),
    ("Defence", "Défense", "Defensa", "skill", "skill.defence"),
    ("Magic", "Magie", "Magia", "skill", "skill.magic"),
    ("Woodcutting", "Bûcheronnage", "Tala", "skill", "skill.woodcutting"),
    ("Mining", "Minage", "Minería", "skill", "skill.mining"),
    ("Fishing", "Pêche", "Pesca", "skill", "skill.fishing"),
    ("Cooking", "Cuisine", "Cocina", "skill", "skill.cooking"),
    ("Firemaking", "Fabrication de feccles", "Fabricación de fuego", "skill", "skill.firemaking"),
    ("Smithing", "Forge", "Fundición", "skill", "skill.smithing"),
    ("The Resting Fist", "Le Poing au Repos", "El Puño en Reposo", "quest", "quest.the_resting_fist"),
    ("Wolves' Fen", "La Fosse aux Loups", "La Mina de los Lobos", "quest", "quest.wolves_fen"),
]


def build(index):
    entries = []
    for row in ENTRIES:
        text, fr, es, category, ident = row
        entries.append({
            "text": text,
            "translation": row[index],
            "category": category,
            "id": ident,
        })
    return {"entries": entries}


def main():
    base = os.path.join("src", "main", "resources", "osrs-translations")
    os.makedirs(base, exist_ok=True)

    for code, index in (("fr", 1), ("es", 2)):
        path = os.path.join(base, "en-%s.json" % code)
        with io.open(path, "w", encoding="utf-8", newline="\n") as f:
            f.write(json.dumps(build(index), ensure_ascii=False, indent=2))
            f.write("\n")
        print("wrote", path)


if __name__ == "__main__":
    main()
