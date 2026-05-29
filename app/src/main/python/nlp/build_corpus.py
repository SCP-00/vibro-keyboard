"""
Builds a bilingual corpus for the SmartText predictor.
Generates unigrams from web word lists and creates realistic bigrams 
based on common language patterns in English and Spanish.

The bigrams are derived from known grammatical patterns rather than
random associations, making the predictive text more useful.
"""

import json
import urllib.request
import re
from collections import defaultdict

# URLs for word lists
URL_EN_WORDS = "https://raw.githubusercontent.com/first20hours/google-10000-english/master/google-10000-english-no-swears.txt"
URL_ES_WORDS = "https://raw.githubusercontent.com/javierarce/palabras/master/listado-general.txt"

# ─── Common English bigrams (word → [(following_word, relative_frequency)]) ───
# These represent real grammatical patterns in English
ENGLISH_BIGRAMS = {
    "i":         [("am", 50), ("have", 40), ("want", 30), ("need", 25), ("like", 20),
                   ("think", 18), ("know", 15), ("love", 12), ("went", 10), ("will", 8),
                   ("can", 7), ("do", 6), ("would", 5), ("had", 5), ("said", 4)],
    "you":       [("are", 40), ("have", 30), ("can", 25), ("will", 20), ("need", 18),
                   ("want", 15), ("know", 12), ("do", 10), ("like", 8), ("should", 7),
                   ("must", 5), ("could", 5), ("were", 4), ("go", 3), ("see", 3)],
    "he":        [("is", 50), ("was", 45), ("has", 30), ("had", 20), ("said", 18),
                   ("would", 10), ("could", 8), ("did", 7), ("went", 5), ("does", 4)],
    "she":       [("is", 50), ("was", 45), ("has", 30), ("had", 20), ("said", 15),
                   ("would", 10), ("could", 8), ("did", 7), ("went", 5)],
    "it":        [("is", 55), ("was", 45), ("has", 25), ("had", 15), ("can", 10),
                   ("will", 10), ("would", 8), ("does", 6), ("could", 5)],
    "we":        [("are", 50), ("have", 40), ("can", 25), ("will", 20), ("should", 15),
                   ("need", 12), ("want", 10), ("would", 8), ("must", 6), ("do", 5)],
    "they":      [("are", 50), ("have", 35), ("can", 20), ("will", 18), ("would", 15),
                   ("were", 12), ("had", 10), ("need", 8), ("want", 6), ("do", 5)],
    "to":        [("be", 40), ("have", 25), ("do", 20), ("get", 18), ("make", 15),
                   ("go", 12), ("take", 10), ("see", 8), ("come", 7), ("find", 6),
                   ("know", 5), ("say", 4), ("help", 4), ("work", 3), ("use", 3)],
    "the":       [("best", 15), ("same", 12), ("first", 10), ("new", 8), ("world", 7),
                   ("way", 6), ("time", 5), ("day", 5), ("most", 4), ("only", 4),
                   ("other", 4), ("last", 3), ("next", 3), ("end", 3), ("top", 3)],
    "a":         [("new", 18), ("good", 12), ("great", 10), ("small", 8), ("big", 7),
                   ("different", 6), ("large", 5), ("few", 5), ("long", 4), ("special", 4),
                   ("simple", 3), ("free", 3), ("real", 3), ("local", 3), ("nice", 2)],
    "an":        [("important", 15), ("example", 12), ("email", 10), ("option", 8),
                   ("error", 7), ("answer", 6), ("article", 5), ("image", 4)],
    "not":       [("be", 25), ("have", 20), ("only", 15), ("just", 12), ("know", 10),
                   ("able", 8), ("all", 6), ("want", 5), ("like", 4), ("sure", 3)],
    "is":        [("the", 30), ("a", 25), ("an", 15), ("not", 10), ("very", 8),
                   ("also", 6), ("one", 5), ("more", 4), ("just", 3), ("now", 3)],
    "are":       [("you", 30), ("they", 15), ("we", 12), ("not", 10), ("the", 8),
                   ("very", 6), ("also", 5), ("all", 4), ("now", 3), ("just", 3)],
    "was":       [("the", 30), ("a", 20), ("an", 10), ("not", 8), ("very", 6),
                   ("just", 5), ("also", 5), ("one", 4)],
    "were":      [("the", 25), ("not", 15), ("a", 12), ("very", 8), ("just", 6),
                   ("also", 5), ("all", 4), ("able", 3)],
    "have":      [("the", 20), ("a", 15), ("been", 12), ("not", 10), ("your", 8),
                   ("all", 6), ("many", 5), ("some", 4), ("more", 3)],
    "has":       [("the", 25), ("a", 15), ("been", 12), ("not", 10), ("been", 8),
                   ("many", 5), ("some", 4)],
    "had":       [("the", 20), ("a", 15), ("been", 12), ("not", 10), ("his", 8),
                   ("their", 6), ("no", 5), ("some", 4)],
    "do":        [("not", 40), ("you", 15), ("we", 10), ("they", 8), ("it", 6),
                   ("this", 5), ("what", 4), ("your", 3)],
    "does":      [("not", 50), ("the", 10), ("this", 8), ("that", 6), ("it", 5)],
    "did":       [("not", 45), ("the", 10), ("he", 8), ("she", 6), ("they", 5),
                   ("you", 4), ("we", 3)],
    "will":      [("be", 35), ("have", 20), ("not", 15), ("need", 8), ("take", 6),
                   ("make", 5), ("get", 5), ("work", 4), ("help", 3)],
    "can":       [("be", 30), ("have", 15), ("get", 12), ("make", 10), ("help", 8),
                   ("take", 6), ("see", 5), ("find", 5), ("use", 4), ("do", 3)],
    "would":     [("be", 25), ("have", 15), ("like", 12), ("not", 10), ("need", 8),
                   ("make", 6), ("take", 5), ("go", 4), ("say", 3)],
    "could":     [("be", 25), ("have", 15), ("not", 10), ("make", 8), ("take", 6),
                   ("see", 5), ("help", 4), ("get", 4), ("find", 3)],
    "should":    [("be", 30), ("have", 20), ("not", 15), ("able", 10), ("take", 6),
                   ("make", 5), ("get", 4), ("see", 3)],
    "may":       [("be", 35), ("have", 15), ("not", 10), ("also", 8), ("need", 6)],
    "must":      [("be", 30), ("have", 20), ("not", 10), ("know", 8), ("take", 6)],
    "like":      [("the", 20), ("a", 15), ("to", 12), ("this", 8), ("that", 6),
                   ("it", 5), ("you", 4), ("your", 3)],
    "your":      [("own", 15), ("name", 12), ("email", 10), ("account", 8),
                   ("home", 6), ("first", 5), ("work", 4), ("life", 3)],
    "my":        [("name", 20), ("own", 12), ("life", 10), ("work", 8), ("home", 6),
                   ("best", 5), ("first", 4), ("last", 3)],
    "his":       [("own", 20), ("name", 15), ("life", 10), ("work", 8), ("home", 6),
                   ("first", 5), ("best", 4)],
    "her":       [("own", 20), ("name", 15), ("life", 10), ("work", 8), ("home", 6),
                   ("first", 5), ("best", 4)],
    "our":       [("own", 20), ("home", 12), ("work", 10), ("best", 8), ("first", 6),
                   ("life", 5), ("new", 4)],
    "their":     [("own", 20), ("first", 12), ("new", 10), ("own", 8), ("own", 6),
                   ("best", 5), ("life", 4)],
    "this":      [("is", 40), ("was", 15), ("will", 10), ("has", 8), ("can", 6),
                   ("one", 5), ("way", 4), ("year", 3)],
    "that":      [("is", 30), ("was", 20), ("the", 15), ("are", 8), ("will", 6),
                   ("can", 5), ("have", 4), ("has", 3)],
    "for":       [("the", 25), ("a", 20), ("your", 10), ("more", 8), ("example", 6),
                   ("free", 5), ("sale", 4), ("all", 3)],
    "with":      [("the", 25), ("a", 20), ("your", 10), ("his", 8), ("her", 6),
                   ("more", 5), ("some", 4)],
    "on":        [("the", 30), ("a", 20), ("your", 10), ("this", 8), ("top", 6),
                   ("sale", 5), ("all", 4), ("how", 3)],
    "at":        [("the", 30), ("a", 20), ("work", 10), ("home", 8), ("least", 6),
                   ("all", 5), ("this", 4), ("your", 3)],
    "by":        [("the", 30), ("a", 20), ("using", 8), ("many", 6), ("some", 5),
                   ("far", 4), ("default", 3)],
    "from":      [("the", 30), ("a", 20), ("your", 10), ("his", 8), ("her", 6),
                   ("our", 5), ("all", 4)],
    "or":        [("the", 20), ("a", 15), ("not", 10), ("your", 8), ("more", 6),
                   ("you", 5), ("all", 4)],
    "and":       [("the", 20), ("a", 15), ("then", 10), ("more", 8), ("all", 6),
                   ("many", 5), ("most", 4), ("other", 3)],
    "but":       [("the", 20), ("not", 15), ("it", 10), ("this", 8), ("they", 6),
                   ("we", 5), ("you", 4), ("also", 3)],
    "so":        [("that", 20), ("the", 15), ("much", 10), ("many", 8), ("far", 6),
                   ("you", 5), ("we", 4)],
    "if":        [("you", 20), ("the", 15), ("we", 10), ("they", 8), ("it", 6),
                   ("this", 5), ("there", 4), ("your", 3)],
    "when":      [("you", 15), ("the", 12), ("we", 10), ("they", 8), ("it", 6),
                   ("i", 5), ("he", 4), ("she", 3)],
    "where":     [("you", 15), ("the", 12), ("we", 10), ("they", 8), ("it", 6),
                   ("he", 5), ("she", 4)],
    "what":      [("is", 20), ("are", 15), ("the", 10), ("you", 8), ("we", 6),
                   ("they", 5), ("can", 4), ("was", 3)],
    "which":     [("is", 20), ("was", 15), ("the", 10), ("are", 8), ("can", 6),
                   ("has", 5), ("will", 4)],
    "who":       [("is", 25), ("are", 15), ("was", 12), ("the", 10), ("has", 6),
                   ("will", 5), ("can", 4)],
    "how":       [("to", 30), ("many", 10), ("much", 8), ("long", 6), ("far", 5),
                   ("you", 4), ("does", 3)],
    "why":       [("is", 15), ("the", 12), ("not", 10), ("you", 8), ("we", 6),
                   ("they", 5), ("does", 4)],
    "all":       [("the", 30), ("of", 15), ("your", 10), ("this", 8), ("over", 6),
                   ("new", 5), ("about", 4)],
    "some":      [("of", 20), ("people", 12), ("other", 10), ("kind", 8), ("time", 6),
                   ("good", 5), ("new", 4)],
    "any":       [("of", 20), ("other", 15), ("more", 10), ("questions", 8),
                   ("information", 6), ("time", 5), ("kind", 4)],
    "many":      [("of", 20), ("people", 15), ("other", 10), ("thanks", 8),
                   ("different", 6), ("great", 5)],
    "more":      [("than", 20), ("information", 12), ("people", 8), ("time", 6),
                   ("about", 5), ("details", 4)],
    "most":      [("of", 20), ("people", 12), ("important", 10), ("popular", 8),
                   ("common", 6), ("recent", 4)],
    "no":        [("one", 20), ("more", 10), ("matter", 8), ("way", 6), ("longer", 5),
                   ("problem", 4)],
    "just":      [("the", 15), ("a", 12), ("like", 10), ("about", 8), ("want", 6),
                   ("need", 5), ("thought", 4)],
    "only":      [("the", 20), ("a", 15), ("way", 10), ("time", 8), ("one", 6),
                   ("person", 5)],
    "very":      [("important", 15), ("good", 12), ("much", 10), ("well", 8),
                   ("nice", 6), ("different", 5), ("large", 4)],
    "well":      [("as", 15), ("done", 12), ("known", 10), ("said", 8), ("over", 6),
                   ("before", 5)],
    "good":      [("way", 15), ("time", 12), ("idea", 10), ("news", 8), ("day", 6),
                   ("luck", 5), ("job", 4)],
    "about":     [("the", 30), ("a", 15), ("your", 10), ("how", 8), ("what", 6),
                   ("this", 5), ("being", 4)],
    "into":      [("the", 30), ("a", 15), ("an", 8), ("your", 6), ("this", 5)],
    "over":      [("the", 30), ("a", 15), ("time", 10), ("and", 8), ("all", 6)],
    "such":      [("as", 25), ("a", 15), ("an", 10), ("that", 8), ("the", 6)],
    "than":      [("the", 15), ("a", 10), ("ever", 8), ("before", 6), ("most", 5),
                   ("you", 4), ("we", 3)],
    "too":       [("much", 20), ("many", 15), ("late", 10), ("long", 8), ("far", 6)],
    "also":      [("has", 15), ("have", 12), ("is", 10), ("was", 8), ("includes", 6),
                   ("known", 5)],
    "up":        [("to", 25), ("with", 10), ("for", 8), ("and", 6), ("the", 5),
                   ("in", 4)],
    "out":       [("of", 25), ("the", 15), ("there", 8), ("about", 6), ("in", 5)],
    "off":       [("the", 20), ("your", 12), ("to", 10), ("and", 8), ("with", 6)],
    "down":      [("the", 20), ("to", 15), ("from", 8), ("in", 6), ("on", 5)],
    "get":       [("the", 20), ("a", 15), ("to", 10), ("your", 8), ("more", 6),
                   ("out", 5), ("started", 4)],
    "set":       [("the", 15), ("up", 12), ("a", 10), ("your", 8), ("out", 5)],
    "put":       [("the", 20), ("your", 12), ("it", 10), ("them", 8), ("a", 6)],
    "take":      [("the", 20), ("a", 15), ("your", 10), ("care", 8), ("time", 6),
                   ("look", 5)],
    "make":      [("the", 20), ("a", 15), ("your", 10), ("sure", 8), ("it", 6),
                   ("more", 5)],
    "look":      [("at", 25), ("for", 15), ("like", 10), ("the", 8), ("your", 6)],
    "find":      [("out", 20), ("a", 15), ("the", 12), ("your", 8), ("more", 6),
                   ("it", 5)],
    "need":      [("to", 30), ("a", 15), ("more", 10), ("your", 8), ("help", 6)],
    "want":      [("to", 30), ("a", 15), ("more", 10), ("your", 8), ("know", 6)],
    "know":      [("that", 15), ("what", 12), ("if", 10), ("how", 8), ("about", 6)],
    "think":     [("that", 15), ("about", 12), ("it", 10), ("i", 8), ("you", 6)],
    "help":      [("you", 20), ("me", 12), ("us", 10), ("make", 8), ("keep", 6)],
    "use":       [("the", 20), ("a", 15), ("your", 10), ("this", 8), ("our", 6)],
    "work":      [("with", 12), ("for", 10), ("on", 10), ("together", 8), ("in", 6),
                   ("from", 5), ("at", 4)],
    "start":     [("with", 15), ("by", 12), ("the", 10), ("your", 8), ("a", 6)],
    "need":      [("to", 35), ("a", 12), ("more", 8), ("some", 6), ("help", 5)],
    "show":      [("the", 20), ("you", 12), ("how", 10), ("that", 8), ("your", 6)],
    "leave":     [("the", 15), ("a", 10), ("your", 8), ("us", 6), ("me", 5)],
    "keep":      [("the", 15), ("your", 12), ("it", 10), ("up", 8), ("me", 6)],
    "bring":     [("the", 15), ("your", 12), ("it", 10), ("us", 8), ("more", 6)],
    "come":      [("to", 20), ("back", 12), ("with", 10), ("in", 8), ("from", 6)],
    "go":        [("to", 25), ("back", 12), ("out", 10), ("through", 8), ("into", 6)],
    "say":       [("that", 15), ("it", 12), ("they", 10), ("you", 8), ("yes", 6)],
    "try":       [("to", 25), ("and", 12), ("it", 8), ("your", 6), ("again", 5)],
    "tell":      [("me", 20), ("you", 15), ("them", 10), ("us", 8), ("your", 6)],
    "ask":       [("for", 20), ("the", 12), ("your", 10), ("about", 8), ("me", 6)],
    "always":    [("be", 20), ("have", 12), ("wanted", 10), ("been", 8), ("make", 6)],
    "never":     [("be", 15), ("have", 12), ("thought", 10), ("seen", 8), ("forget", 6)],
}

# ─── Common Spanish bigrams ───
SPANISH_BIGRAMS = {
    "el":        [("amor", 15), ("sol", 12), ("día", 10), ("año", 8), ("mundo", 7),
                   ("tiempo", 6), ("hombre", 5), ("mejor", 4), ("nuevo", 3), ("gran", 3)],
    "la":        [("casa", 15), ("vida", 12), ("mujer", 10), ("ciudad", 8), ("familia", 7),
                   ("noche", 6), ("mañana", 5), ("idea", 4), ("mejor", 3), ("nueva", 3)],
    "los":       [("días", 12), ("años", 10), ("meses", 8), ("amigos", 7), ("hijos", 6),
                   ("padres", 5), ("hermanos", 4), ("mejores", 3)],
    "las":       [("cosas", 15), ("mujeres", 10), ("horas", 8), ("noches", 7), ("semanas", 6),
                   ("personas", 5), ("mejores", 4)],
    "un":        [("hombre", 15), ("día", 12), ("año", 10), ("amigo", 8), ("niño", 7),
                   ("momento", 6), ("lugar", 5), ("gran", 4), ("buen", 3)],
    "una":       [("mujer", 15), ("casa", 12), ("vez", 10), ("hora", 8), ("noche", 7),
                   ("familia", 6), ("idea", 5), ("gran", 4), ("buena", 3)],
    "en":        [("el", 25), ("la", 20), ("los", 12), ("las", 10), ("un", 8),
                   ("una", 6), ("casa", 5), ("todo", 4)],
    "de":        [("los", 20), ("las", 15), ("la", 12), ("el", 10), ("un", 8),
                   ("una", 6), ("todo", 5), ("cada", 4)],
    "que":       [("el", 15), ("la", 12), ("los", 10), ("las", 8), ("se", 7),
                   ("no", 6), ("es", 5), ("hay", 4), ("tiene", 3)],
    "y":         [("el", 15), ("la", 12), ("los", 10), ("las", 8), ("su", 6),
                   ("más", 5), ("también", 4), ("no", 3)],
    "a":         [("los", 20), ("las", 15), ("la", 12), ("el", 10), ("una", 8),
                   ("su", 6), ("un", 5), ("casa", 4)],
    "se":        [("puede", 15), ("hace", 12), ("encuentra", 10), ("trata", 8),
                   ("dice", 7), ("pone", 5), ("encuentra", 4)],
    "no":        [("es", 20), ("se", 15), ("hay", 10), ("puede", 8), ("tiene", 6),
                   ("está", 5), ("son", 4), ("fue", 3)],
    "su":        [("casa", 12), ("vida", 10), ("familia", 8), ("trabajo", 7),
                   ("nombre", 6), ("hijo", 5), ("amor", 4), ("mejor", 3)],
    "con":       [("su", 15), ("una", 10), ("un", 8), ("los", 6), ("las", 5),
                   ("la", 4), ("el", 3), ("todo", 3)],
    "para":      [("el", 20), ("la", 15), ("los", 12), ("las", 10), ("su", 8),
                   ("un", 6), ("una", 5), ("que", 4)],
    "por":       [("el", 20), ("la", 15), ("los", 12), ("las", 10), ("su", 8),
                   ("un", 6), ("una", 5), ("todo", 4)],
    "como":      [("el", 12), ("la", 10), ("los", 8), ("las", 6), ("un", 5),
                   ("una", 4), ("se", 3), ("es", 3)],
    "más":       [("importante", 15), ("grande", 10), ("bueno", 8), ("allá", 6),
                   ("nuevo", 5), ("allá", 4), ("bajo", 3)],
    "todo":      [("el", 15), ("lo", 12), ("los", 10), ("las", 8), ("esto", 6),
                   ("eso", 5), ("aquello", 4)],
    "pero":      [("no", 20), ("el", 10), ("la", 8), ("su", 6), ("se", 5),
                   ("es", 4), ("más", 3)],
    "muy":       [("importante", 15), ("grande", 12), ("bueno", 10), ("bien", 8),
                   ("fácil", 6), ("difícil", 5), ("bonito", 4)],
    "tiene":     [("un", 20), ("una", 15), ("la", 10), ("el", 8), ("su", 6),
                   ("mucho", 5), ("mucha", 4)],
    "está":      [("en", 20), ("muy", 12), ("bien", 10), ("mal", 8), ("aquí", 6),
                   ("allí", 5), ("cerca", 4)],
    "son":       [("los", 20), ("las", 15), ("muy", 10), ("tan", 8), ("más", 6),
                   ("importantes", 5)],
    "entre":     [("los", 20), ("las", 15), ("el", 10), ("la", 8), ("ellos", 6),
                   ("ellas", 5)],
    "sido":      [("una", 15), ("un", 12), ("el", 10), ("la", 8), ("muy", 6),
                   ("siempre", 5)],
    "también":   [("se", 12), ("es", 10), ("puede", 8), ("hay", 6), ("tiene", 5),
                   ("está", 4)],
    "cuando":    [("se", 15), ("el", 12), ("la", 10), ("llegó", 8), ("está", 6)],
    "donde":     [("se", 15), ("el", 12), ("la", 10), ("está", 8), ("vive", 6)],
    "siempre":   [("está", 12), ("he", 10), ("ha", 8), ("dijo", 6), ("puede", 5)],
    "después":   [("de", 20), ("del", 12), ("que", 10), ("vino", 8), ("llegó", 6)],
    "antes":     [("de", 20), ("del", 12), ("que", 10), ("no", 6), ("era", 5)],
    "durante":   [("el", 20), ("la", 15), ("los", 12), ("las", 10), ("mucho", 5)],
    "sin":       [("embargo", 15), ("duda", 10), ("problemas", 8), ("miedo", 6),
                   ("ningún", 5)],
    "sobre":     [("el", 20), ("la", 15), ("los", 12), ("las", 10), ("todo", 6)],
    "hasta":     [("el", 20), ("la", 15), ("los", 12), ("que", 10), ("las", 8)],
    "mi":        [("vida", 15), ("casa", 12), ("familia", 10), ("trabajo", 8),
                   ("amor", 6), ("corazón", 5)],
    "tu":        [("vida", 12), ("casa", 10), ("familia", 8), ("trabajo", 6),
                   ("amor", 5), ("nombre", 4)],
    "del":       [("año", 12), ("día", 10), ("mundo", 8), ("país", 6), ("siglo", 5)],
    "este":      [("es", 15), ("año", 10), ("mundo", 8), ("país", 6), ("lugar", 5)],
    "cada":      [("vez", 15), ("día", 12), ("año", 8), ("mes", 6), ("semana", 5)],
    "gran":      [("parte", 12), ("mayoría", 10), ("número", 8), ("cantidad", 6)],
    "tan":       [("importante", 12), ("grande", 10), ("bueno", 8), ("bien", 6)],
}


def fetch_words(url, encoding='utf-8'):
    """Fetch words from a URL and return as a list."""
    try:
        response = urllib.request.urlopen(url)
        content = response.read().decode(encoding)
        words = [w.strip().lower() for w in content.split('\n') if w.strip()]
        return words
    except Exception as e:
        print(f"Error fetching {url}: {e}")
        return []


def build_unigrams(words, url):
    """Build unigram dictionary with Zipfian-like frequency distribution."""
    unigrams = {}
    for i, w in enumerate(words):
        if not re.match(r'^[a-záéíóúñü]+$', w):
            continue
        # Zipfian-like distribution: freq ∝ 1/(rank+1)
        rank = i + 1
        freq = max(1, int(100000 / max(rank, 1)) - rank)
        # Ensure reasonable range
        freq = max(1, min(10000, freq))
        unigrams[w] = freq
    return unigrams


def build_bigrams(unigrams, bigram_patterns):
    """
    Build bigram dictionary from predefined patterns.
    Each word is mapped to a list of (following_word, frequency) tuples.
    The frequency is scaled based on the follower's unigram frequency.
    """
    bigrams = {}
    for word, followers in bigram_patterns.items():
        if word not in unigrams:
            continue
        word_bigrams = []
        for follower, strength in followers:
            if follower in unigrams:
                # Scale frequency by both pattern strength and unigram frequency
                scaled_freq = max(1, int(unigrams[follower] * strength / 20))
                word_bigrams.append((follower, scaled_freq))
        if word_bigrams:
            # Sort by frequency descending
            word_bigrams.sort(key=lambda x: x[1], reverse=True)
            bigrams[word] = word_bigrams[:20]  # Keep top 20
    return bigrams


def _ensure_common_words(unigrams, bigram_patterns, base_freq=5000):
    """Ensure common function words used as bigram heads exist in unigrams."""
    for word in bigram_patterns:
        if word not in unigrams:
            unigrams[word] = base_freq
    # Also ensure follower words exist
    for followers in bigram_patterns.values():
        for follower, _ in followers:
            if follower not in unigrams:
                unigrams[follower] = 1000
    return unigrams


def build_corpus():
    """Build the complete bilingual corpus."""
    print("Building English corpus...")
    en_words = fetch_words(URL_EN_WORDS)
    en_unigrams = build_unigrams(en_words, URL_EN_WORDS)
    en_unigrams = _ensure_common_words(en_unigrams, ENGLISH_BIGRAMS)
    print(f"  {len(en_unigrams)} English unigrams")

    print("Building English bigrams...")
    en_bigrams = build_bigrams(en_unigrams, ENGLISH_BIGRAMS)
    print(f"  {len(en_bigrams)} English bigram heads")

    print("Building Spanish corpus...")
    es_words = fetch_words(URL_ES_WORDS)
    es_unigrams = build_unigrams(es_words, URL_ES_WORDS)
    # Keep top 10000 Spanish words
    es_unigrams = dict(sorted(es_unigrams.items(), key=lambda x: x[1], reverse=True)[:10000])
    # Ensure all bigram-related words exist
    es_unigrams = _ensure_common_words(es_unigrams, SPANISH_BIGRAMS, base_freq=5000)
    print(f"  {len(es_unigrams)} Spanish unigrams")

    print("Building Spanish bigrams...")
    es_bigrams = build_bigrams(es_unigrams, SPANISH_BIGRAMS)
    print(f"  {len(es_bigrams)} Spanish bigram heads")

    data = {
        "en": {
            "unigrams": en_unigrams,
            "bigrams": en_bigrams
        },
        "es": {
            "unigrams": es_unigrams,
            "bigrams": es_bigrams
        }
    }

    output_path = "corpus.json"
    with open(output_path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False)

    print(f"\nCorpus saved to {output_path}")
    print(f"English: {len(en_unigrams)} words, {sum(len(v) for v in en_bigrams.values())} bigrams")
    print(f"Spanish: {len(es_unigrams)} words, {sum(len(v) for v in es_bigrams.values())} bigrams")


if __name__ == "__main__":
    build_corpus()
