import io
import json
import os

FR = {
    "bucket": ("le seau", "noun", "seau", "le", "Peux-tu m'apporter un seau d'eau ?", "bucket"),
    "water": ("l'eau", "noun", "eau", "le", "L'eau est claire.", "water"),
    "sword": ("l'épée", "noun", "épée", "la", "J'ai besoin d'une épée.", "sword"),
    "shield": ("le bouclier", "noun", "bouclier", "le", "Le bouclier te protège.", "shield"),
    "axe": ("la hache", "noun", "hache", "la", "Il porte une hache.", "axe"),
    "pickaxe": ("la pioche", "noun", "pioche", "la", "La pioche se brise.", "pickaxe"),
    "hammer": ("le marteau", "noun", "marteau", "le", "Le marteau est lourd.", "hammer"),
    "bread": ("le pain", "noun", "pain", "le", "J'ai une tranche de pain.", "bread"),
    "cheese": ("le fromage", "noun", "fromage", "le", "Elle aime le fromage.", "cheese"),
    "fish": ("le poisson", "noun", "poisson", "le", "Le poisson est frais.", "fish"),
    "chicken": ("le poulet", "noun", "poulet", "le", "Le poulet pond des œufs.", "chicken"),
    "coins": ("les pièces", "noun", "pièce", "la", "J'ai besoin de pièces.", "coins"),
    "gold": ("l'or", "noun", "or", "le", "L'or est lourd.", "gold"),
    "iron": ("le fer", "noun", "fer", "le", "Le fer est dur.", "iron"),
    "ore": ("le minerai", "noun", "minerai", "le", "Je cherche du minerai.", "ore"),
    "fire": ("le feu", "noun", "feu", "le", "Le feu brûle fort.", "fire"),
    "wood": ("le bois", "noun", "bois", "le", "Le bois est sec.", "wood"),
    "rope": ("la corde", "noun", "corde", "la", "La corde est longue.", "rope"),
    "spell": ("le sort", "noun", "sort", "le", "Le sort est puissant.", "spell"),
    "quest": ("la quête", "noun", "quête", "la", "La quête est longue.", "quest"),
    "shop": ("la boutique", "noun", "boutique", "la", "La boutique ouvre à huit heures.", "shop"),
    "bank": ("la banque", "noun", "banque", "la", "La banque est sûre.", "bank"),
    "friend": ("l'ami", "noun", "ami", "le", "Mon ami m'aide.", "friend"),
    "master": ("le maître", "noun", "maître", "le", "Le maître m'enseigne.", "master"),
    "help": ("l'aide", "noun", "aide", "le", "J'ai besoin d'aide.", "help"),
    "please": ("s'il te plaît", "adverb", "s'il te plaît", None, "Un seau d'eau, s'il te plaît.", "please"),
    "yes": ("oui", "adverb", "oui", None, "Oui, c'est vrai.", "yes"),
    "no": ("non", "adverb", "non", None, "Non, merci.", "no"),
    "hello": ("bonjour", "interjection", "bonjour", None, "Bonjour, comment ça va ?", "hello"),
    "goodbye": ("au revoir", "interjection", "au revoir", None, "Au revoir à demain.", "goodbye"),
    "thanks": ("merci", "interjection", "merci", None, "Merci pour ton aide.", "thanks"),
    "sorry": ("pardon", "noun", "pardon", "le", "Pardon, ce n'était pas moi.", "sorry"),
    "come": ("venir", "verb", "venir", None, "Viens, s'il te plaît.", "come"),
    "go": ("aller", "verb", "aller", None, "Je dois aller.", "go"),
    "give": ("donner", "verb", "donner", None, "Donne-moi l'épée.", "give"),
    "take": ("prendre", "verb", "prendre", None, "Prends le seau.", "take"),
    "buy": ("acheter", "verb", "acheter", None, "Je veux acheter une épée.", "buy"),
    "sell": ("vendre", "verb", "vendre", None, "Vends le bois.", "sell"),
    "talk": ("parler", "verb", "parler", None, "Je veux te parler.", "talk"),
    "need": ("avoir besoin", "verb", "avoir besoin", None, "J'ai besoin d'une épée.", "need"),
    "want": ("vouloir", "verb", "vouloir", None, "Je veux des aventures.", "want"),
    "can": ("pouvoir", "verb", "pouvoir", None, "Peux-tu m'aider ?", "can"),
    "you": ("tu", "pronoun", "tu", None, "Tu es mon ami.", "you"),
    "i": ("je", "pronoun", "je", None, "J'ai besoin d'aide.", "I"),
    "the": ("le", "article", "le", None, "L'épée est bonne.", "the"),
    "and": ("et", "conjunction", "et", None, "Bouclier et épée.", "and"),
    "or": ("ou", "conjunction", "ou", None, "Bouclier ou épée ?", "or"),
    "with": ("avec", "preposition", "avec", None, "Viens avec moi.", "with"),
}

ES = {
    "bucket": ("el balde", "noun", "balde", "el", "¿Puedes traerme un balde de agua?", "bucket"),
    "water": ("el agua", "noun", "agua", "el", "El agua está limpia.", "water"),
    "sword": ("la espada", "noun", "espada", "la", "Necesito una espada.", "sword"),
    "shield": ("el escudo", "noun", "escudo", "el", "El escudo te protege.", "shield"),
    "axe": ("el hacha", "noun", "hacha", "el", "Lleva un hacha.", "axe"),
    "pickaxe": ("el pico", "noun", "pico", "el", "El pico se rompe.", "pickaxe"),
    "hammer": ("el martillo", "noun", "martillo", "el", "El martillo es pesado.", "hammer"),
    "bread": ("el pan", "noun", "pan", "el", "Tengo un trozo de pan.", "bread"),
    "cheese": ("el queso", "noun", "queso", "el", "Le gusta el queso.", "cheese"),
    "fish": ("el pez", "noun", "pez", "el", "El pez está fresco.", "fish"),
    "chicken": ("la gallina", "noun", "gallina", "la", "La gallina pone huevos.", "chicken"),
    "coins": ("las monedas", "noun", "moneda", "la", "Necesito monedas.", "coins"),
    "gold": ("el oro", "noun", "oro", "el", "El oro es pesado.", "gold"),
    "iron": ("el hierro", "noun", "hierro", "el", "El hierro es duro.", "iron"),
    "ore": ("el mineral", "noun", "mineral", "el", "Busco mineral.", "ore"),
    "fire": ("el fuego", "noun", "fuego", "el", "El fuego arde fuerte.", "fire"),
    "wood": ("la madera", "noun", "madera", "la", "La madera está seca.", "wood"),
    "rope": ("la cuerda", "noun", "cuerda", "la", "La cuerda es larga.", "rope"),
    "spell": ("el hechizo", "noun", "hechizo", "el", "El hechizo es poderoso.", "spell"),
    "quest": ("la misión", "noun", "misión", "la", "La misión es larga.", "quest"),
    "shop": ("la tienda", "noun", "tienda", "la", "La tienda abre a las ocho.", "shop"),
    "bank": ("el banco", "noun", "banco", "el", "El banco es seguro.", "bank"),
    "friend": ("el amigo", "noun", "amigo", "el", "Mi amigo me ayuda.", "friend"),
    "master": ("el maestro", "noun", "maestro", "el", "El maestro me enseña.", "master"),
    "help": ("la ayuda", "noun", "ayuda", "la", "Necesito ayuda.", "help"),
    "please": ("por favor", "adverb", "por favor", None, "Un balde de agua, por favor.", "please"),
    "yes": ("sí", "adverb", "sí", None, "Sí, es verdad.", "yes"),
    "no": ("no", "adverb", "no", None, "No, gracias.", "no"),
    "hello": ("hola", "interjection", "hola", None, "Hola, ¿cómo estás?", "hello"),
    "goodbye": ("adiós", "interjection", "adiós", None, "Adiós hasta mañana.", "goodbye"),
    "thanks": ("gracias", "interjection", "gracias", None, "Gracias por tu ayuda.", "thanks"),
    "sorry": ("perdón", "noun", "perdón", "el", "Perdón, no fui yo.", "sorry"),
    "come": ("venir", "verb", "venir", None, "Ven, por favor.", "come"),
    "go": ("ir", "verb", "ir", None, "Tengo que ir.", "go"),
    "give": ("dar", "verb", "dar", None, "Dame la espada.", "give"),
    "take": ("tomar", "verb", "tomar", None, "Toma el balde.", "take"),
    "buy": ("comprar", "verb", "comprar", None, "Quiero comprar una espada.", "buy"),
    "sell": ("vender", "verb", "vender", None, "Vende la madera.", "sell"),
    "talk": ("hablar", "verb", "hablar", None, "Quiero hablar contigo.", "talk"),
    "need": ("necesitar", "verb", "necesitar", None, "Necesito una espada.", "need"),
    "want": ("querer", "verb", "querer", None, "Quiero aventuras.", "want"),
    "can": ("poder", "verb", "poder", None, "¿Puedes ayudarme?", "can"),
    "you": ("tú", "pronoun", "tú", None, "Eres mi amigo.", "you"),
    "i": ("yo", "pronoun", "yo", None, "Necesito ayuda.", "I"),
    "the": ("el", "article", "el", None, "La espada es buena.", "the"),
    "and": ("y", "conjunction", "y", None, "Escudo y espada.", "and"),
    "or": ("o", "conjunction", "o", None, "¿Escudo o espada?", "or"),
    "with": ("con", "preposition", "con", None, "Ven conmigo.", "with"),
}


def build(data):
    out = {}
    for word, (translation, pos, form, gender, example, english) in data.items():
        entry = {
            "translation": translation,
            "partOfSpeech": pos,
            "dictionaryForm": form,
            "example": example,
            "english": english,
        }
        if gender is not None:
            entry["gender"] = gender
        out[word] = entry
    return out


def main():
    base = os.path.join("src", "main", "resources", "dictionary")
    os.makedirs(base, exist_ok=True)

    for code, data in (("fr", FR), ("es", ES)):
        path = os.path.join(base, "en-%s.json" % code)
        with io.open(path, "w", encoding="utf-8", newline="\n") as f:
            f.write(json.dumps(build(data), ensure_ascii=False, indent=2, sort_keys=True))
            f.write("\n")
        print("wrote", path)


if __name__ == "__main__":
    main()
