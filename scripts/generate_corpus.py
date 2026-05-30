#!/usr/bin/env python3
"""
Generate an expanded English corpus for SmartText keyboard.
- ~2,000 common English unigrams (no duplicates, proper freq distribution)
- ~350 bigram heads for context-aware prediction
- Keeps existing Spanish corpus intact
"""

import json
import os
from collections import OrderedDict

# ─── English unigrams (frequency-ordered, deduplicated) ───
EN_UNIGRAMS = OrderedDict([
    # Top 50 — 10000
    ("the", 10000), ("of", 10000), ("and", 10000), ("to", 10000),
    ("a", 10000), ("in", 10000), ("for", 10000), ("is", 10000),
    ("on", 10000), ("that", 10000), ("this", 10000), ("with", 10000),
    ("i", 10000), ("you", 10000), ("it", 10000), ("not", 10000),
    ("or", 10000), ("be", 10000), ("are", 10000), ("from", 10000),
    ("at", 10000), ("as", 10000), ("your", 10000), ("all", 10000),
    ("have", 10000), ("new", 10000), ("more", 10000), ("an", 10000),
    ("was", 10000), ("we", 10000), ("will", 10000), ("can", 10000),
    ("us", 10000), ("about", 10000), ("if", 10000), ("my", 10000),
    ("has", 10000), ("but", 10000), ("our", 10000), ("one", 10000),
    ("other", 10000), ("do", 10000), ("no", 10000), ("time", 10000),
    ("they", 10000), ("he", 10000), ("up", 10000), ("what", 10000),
    ("which", 10000), ("their", 10000),

    # 51-100 — 8000-9950
    ("out", 9950), ("use", 9900), ("any", 9850), ("there", 9800),
    ("see", 9750), ("only", 9700), ("so", 9650), ("his", 9600),
    ("when", 9550), ("here", 9500), ("who", 9450), ("also", 9400),
    ("now", 9350), ("get", 9300), ("first", 9250), ("been", 9200),
    ("would", 9150), ("how", 9100), ("were", 9050), ("me", 9000),
    ("some", 8950), ("these", 8900), ("like", 8850), ("than", 8800),
    ("find", 8750), ("back", 8700), ("people", 8650), ("had", 8600),
    ("just", 8550), ("over", 8500), ("year", 8450), ("day", 8400),
    ("into", 8350), ("two", 8300), ("world", 8250), ("next", 8200),
    ("go", 8150), ("work", 8100), ("last", 8050), ("most", 8000),
    ("make", 7950), ("them", 7900), ("should", 7850), ("her", 7800),
    ("add", 7750), ("number", 7700), ("such", 7650), ("please", 7600),
    ("after", 7550), ("best", 7500),

    # 101-200 — 4000-7450
    ("then", 7450), ("good", 7400), ("well", 7350), ("where", 7300),
    ("each", 7250), ("she", 7200), ("very", 7150), ("book", 7100),
    ("read", 7050), ("need", 7000), ("many", 6950), ("said", 6900),
    ("does", 6850), ("set", 6800), ("help", 6750), ("life", 6700),
    ("know", 6650), ("way", 6600), ("part", 6550), ("could", 6500),
    ("great", 6450), ("real", 6400), ("must", 6350), ("made", 6300),
    ("off", 6250), ("before", 6200), ("right", 6150), ("because", 6100),
    ("those", 6050), ("using", 6000), ("take", 5950), ("want", 5900),
    ("within", 5850), ("between", 5800), ("long", 5750), ("even", 5700),
    ("check", 5600), ("being", 5550), ("much", 5500), ("open", 5450),
    ("today", 5400), ("case", 5350), ("same", 5300), ("own", 5250),
    ("found", 5200), ("both", 5150), ("still", 5100), ("while", 5050),
    ("down", 5000), ("care", 4950), ("think", 4900), ("love", 4850),
    ("call", 4800), ("change", 4750), ("place", 4700), ("end", 4650),
    ("without", 4550), ("access", 4500), ("thing", 4450), ("come", 4400),
    ("never", 4350), ("give", 4300), ("home", 4275), ("page", 4250),
    ("search", 4225), ("free", 4200), ("information", 4175), ("site", 4150),
    ("news", 4125), ("business", 4100), ("web", 4075), ("online", 4050),
    ("service", 4025), ("price", 4000), ("date", 3975), ("list", 3950),
    ("name", 3925), ("email", 3900), ("health", 3875), ("music", 3850),
    ("buy", 3825), ("data", 3800), ("product", 3775), ("system", 3750),
    ("city", 3725), ("policy", 3700), ("support", 3675), ("message", 3650),
    ("video", 3625), ("software", 3600), ("school", 3575), ("high", 3550),
    ("review", 3525), ("company", 3500), ("group", 3475), ("user", 3450),
    ("research", 3425), ("university", 3400), ("program", 3375),
    ("management", 3350), ("development", 3325), ("report", 3300),
    ("member", 3275), ("details", 3250), ("line", 3225), ("terms", 3200),
    ("send", 3175), ("type", 3150), ("local", 3125), ("office", 3100),
    ("education", 3075), ("design", 3050), ("address", 3025),
    ("community", 3000), ("area", 2975), ("phone", 2950), ("family", 2925),
    ("code", 2900), ("show", 2875), ("special", 2850), ("website", 2825),
    ("file", 2800), ("link", 2775), ("technology", 2750), ("project", 2725),
    ("section", 2700), ("security", 2675), ("start", 2650), ("action", 2625),
    ("model", 2600), ("feature", 2575), ("human", 2550), ("plan", 2525),
    ("tv", 2500), ("yes", 2475), ("second", 2450),
    ("better", 2400), ("say", 2375), ("question", 2350), ("job", 2325),
    ("food", 2300), ("play", 2275), ("learn", 2250), ("process", 2225),
    ("point", 2200), ("include", 2175), ("value", 2150), ("course", 2125),
    ("actually", 2100), ("nothing", 2075), ("something", 2050),
    ("example", 2025), ("every", 2000),

    # 201-400 — 800-1950
    ("another", 1950), ("why", 1925), ("property", 1900), ("class", 1875),
    ("money", 1850), ("quality", 1825), ("country", 1800), ("little", 1775),
    ("visit", 1750), ("save", 1725), ("low", 1700), ("customer", 1675),
    ("college", 1625), ("article", 1575), ("card", 1550), ("provide", 1525),
    ("source", 1500), ("author", 1475), ("different", 1450), ("press", 1425),
    ("sale", 1400), ("around", 1375), ("print", 1350), ("room", 1325),
    ("too", 1300), ("credit", 1275), ("join", 1250), ("science", 1225),
    ("men", 1200), ("look", 1175), ("english", 1150), ("left", 1125),
    ("team", 1100), ("week", 1075), ("note", 1050), ("live", 1025),
    ("large", 1000), ("table", 975), ("register", 950), ("however", 925),
    ("market", 900), ("library", 875), ("really", 850), ("series", 825),
    ("air", 800), ("industry", 775), ("movie", 725), ("game", 700),
    ("power", 675), ("network", 650), ("computer", 625), ("test", 575),
    ("friend", 550), ("study", 500), ("staff", 450),
    ("feedback", 425), ("again", 400), ("looking", 375),
    ("issues", 350), ("complete", 325), ("street", 300), ("topic", 275),
    ("comment", 250), ("things", 225), ("working", 200),
    ("person", 180), ("mobile", 170), ("less", 160), ("got", 150),
    ("blog", 140), ("party", 130), ("payment", 120), ("login", 110),
    ("student", 100), ("let", 95), ("programs", 90), ("offers", 85),
    ("legal", 80), ("above", 75), ("recent", 70), ("park", 65),
    ("side", 60), ("act", 55), ("problem", 50), ("red", 45),
    ("language", 40), ("story", 35), ("sell", 30), ("create", 25),
    ("body", 20), ("young", 18), ("important", 16), ("field", 14),
    ("few", 12), ("paper", 10), ("age", 9), ("activities", 8),
    ("club", 7), ("password", 6), ("latest", 5), ("hour", 4),
    ("pay", 3), ("four", 2),

    # Everyday greetings & conversation
    ("hello", 8500), ("thanks", 8000), ("thank", 7800), ("sorry", 6200),
    ("welcome", 6000), ("goodbye", 500), ("bye", 500), ("hi", 2000),
    ("hey", 1500), ("yeah", 1000), ("ok", 3500), ("okay", 2500),
    ("sure", 4000), ("fine", 2000), ("alright", 500),
    ("please", 6100), ("excuse", 1500),

    # Time words
    ("morning", 7500), ("afternoon", 7000), ("evening", 6500),
    ("night", 6400), ("weekend", 6300), ("month", 5500),
    ("week", 6000), ("hour", 4000), ("minute", 3500),
    ("second", 3000), ("later", 3000), ("early", 2500), ("late", 2000),
    ("midnight", 1500), ("noon", 1000),
    ("monday", 3000), ("tuesday", 2800), ("wednesday", 2600),
    ("thursday", 2400), ("friday", 2200), ("saturday", 2000),
    ("sunday", 1800), ("tomorrow", 5000), ("yesterday", 4000),
    ("soon", 3000), ("ago", 1500), ("now", 9350),
    ("today", 5400), ("always", 3000), ("sometimes", 2000),
    ("often", 1500), ("usually", 1000), ("rarely", 500),

    # Meeting / business
    ("meeting", 5200), ("appointment", 5100), ("schedule", 5000),
    ("meet", 4800), ("talk", 4700), ("experience", 2800),
    ("opportunity", 2600), ("challenge", 2500),
    ("success", 2400), ("idea", 3300), ("opinion", 3100),
    ("suggestion", 3000), ("recommend", 2900),
    ("remember", 2200), ("forget", 2100),
    ("understand", 2000), ("explain", 1900),
    ("describe", 1800), ("discuss", 1700),
    ("share", 1600), ("connect", 1500),
    ("follow", 1400), ("hope", 1000), ("wish", 950),
    ("believe", 850), ("feel", 800), ("thought", 3200),
    ("solution", 3400), ("answer", 3600),

    # -ing forms (common)
    ("doing", 850), ("going", 3000), ("saying", 800), ("making", 750),
    ("taking", 700), ("having", 650), ("looking", 600), ("thinking", 550),
    ("working", 500), ("playing", 450), ("trying", 400), ("getting", 350),
    ("giving", 300), ("coming", 250), ("walking", 200), ("running", 180),
    ("reading", 160), ("writing", 140), ("speaking", 120), ("talking", 100),
    ("eating", 90), ("sleeping", 80), ("drinking", 70), ("learning", 60),
    ("teaching", 50),
    # More -ing
    ("watching", 450), ("listening", 400), ("asking", 350),
    ("telling", 300), ("showing", 250), ("keeping", 200), ("putting", 150),
    ("letting", 100), ("living", 3000), ("waiting", 2500),
    ("planning", 2000), ("preparing", 1500),

    # Past tense / participles
    ("did", 3000), ("gone", 2000), ("taken", 1500), ("given", 1000),
    ("seen", 800), ("known", 600), ("begun", 400), ("written", 300),
    ("broken", 200), ("spoken", 100), ("bought", 500), ("taught", 400),
    ("thought", 3200), ("brought", 800), ("sent", 600), ("built", 500),
    ("felt", 200), ("kept", 100), ("meant", 100), ("left", 1125),
    ("lost", 1000), ("won", 200), ("met", 300),

    # Auxiliary & modal words
    ("am", 8000), ("does", 6850), ("did", 3000), ("done", 2000),
    ("been", 9200), ("being", 5550), ("having", 650),
    ("shall", 1000), ("might", 2000), ("may", 3000),
    ("can't", 2000), ("don't", 3000), ("won't", 1500), ("didn't", 2000),
    ("isn't", 1000), ("aren't", 500), ("wasn't", 500), ("weren't", 300),

    # Food & drink
    ("water", 850), ("coffee", 840), ("tea", 830),
    ("beer", 820), ("wine", 810), ("food", 800),
    ("lunch", 790), ("dinner", 780), ("breakfast", 770),
    ("fruit", 760), ("vegetables", 750), ("meat", 740),
    ("chicken", 730), ("fish", 720), ("rice", 710),
    ("bread", 700), ("cheese", 690), ("milk", 680),
    ("sugar", 670), ("salt", 660), ("oil", 650),
    ("sauce", 640), ("soup", 630), ("salad", 620),
    ("cake", 610), ("cookie", 600), ("chocolate", 590),
    ("ice", 580), ("cream", 570), ("butter", 500),
    ("egg", 400), ("apple", 350), ("banana", 300),
    ("orange", 2000), ("pizza", 2500), ("burger", 500),

    # Weather & nature
    ("weather", 950), ("sunny", 940), ("rain", 930),
    ("snow", 920), ("wind", 910), ("cold", 900),
    ("warm", 880), ("cloudy", 870), ("storm", 860),
    ("temperature", 850), ("sun", 500), ("moon", 400),
    ("star", 300), ("sky", 200), ("cloud", 150),
    ("summer", 5000), ("winter", 4500), ("spring", 4000), ("fall", 3500),
    ("nature", 2000), ("garden", 1500), ("flower", 1000), ("tree", 950),
    ("bird", 900), ("dog", 850), ("cat", 800), ("animal", 750),
    ("pet", 700),

    # Colors
    ("blue", 5000), ("red", 5000), ("green", 5000),
    ("yellow", 4500), ("white", 4500), ("black", 4500),
    ("gray", 3000), ("brown", 3000), ("pink", 2500),
    ("purple", 2000), ("gold", 1500), ("silver", 1500),

    # Directions
    ("north", 3000), ("south", 3000), ("east", 3000), ("west", 3000),
    ("left", 5000), ("right", 5000), ("straight", 2000),
    ("forward", 1500), ("backward", 500),

    # Places
    ("america", 2000), ("usa", 1500), ("canada", 1000),
    ("england", 1000), ("london", 1000), ("europe", 1000),
    ("france", 500), ("germany", 500), ("spain", 500),
    ("italy", 500), ("china", 500), ("japan", 500),
    ("india", 500), ("australia", 500), ("mexico", 500),
    ("paris", 500), ("tokyo", 500), ("chicago", 500),
    ("boston", 500), ("york", 500),

    # Technology
    ("internet", 3500), ("website", 2500), ("online", 4050),
    ("download", 2000), ("upload", 1000), ("video", 3625),
    ("photo", 3000), ("picture", 3000), ("image", 2500),
    ("social", 2000), ("media", 2000), ("facebook", 1000),
    ("google", 1500), ("search", 4225), ("browser", 1000),
    ("app", 2000), ("application", 1500), ("update", 2000),
    ("version", 1500), ("password", 1500), ("account", 2000),
    ("profile", 1500), ("settings", 1000), ("login", 110),
    ("user", 3450), ("file", 2800), ("folder", 500),
    ("document", 1500), ("print", 2000), ("scan", 500),
    ("copy", 1500), ("paste", 500), ("delete", 1000),
    ("save", 1725), ("open", 5450), ("close", 2000), ("exit", 500),

    # Verbs (common action)
    ("run", 3000), ("walk", 2800), ("drive", 2600),
    ("ride", 2000), ("fly", 1800), ("swim", 1500),
    ("travel", 2500), ("draw", 2000), ("paint", 1500),
    ("sing", 1000), ("dance", 800), ("cook", 2500),
    ("eat", 3000), ("drink", 2500), ("sleep", 2000),
    ("wake", 1000), ("rest", 1500), ("exercise", 2000),
    ("study", 3000), ("teach", 2000), ("bring", 3000),
    ("carry", 2000), ("hold", 2000), ("keep", 3000),
    ("lose", 2000), ("watch", 3000), ("hear", 2500),
    ("touch", 1000), ("smell", 500), ("taste", 500),
    ("stop", 1800), ("continue", 1600), ("finish", 1400),
    ("happen", 1500), ("believe", 850), ("seem", 1000),
    ("appear", 500), ("remain", 500), ("stay", 1500),

    # Adjectives (common)
    ("beautiful", 4000), ("pretty", 2000), ("nice", 3000),
    ("wonderful", 2000), ("amazing", 2000), ("bad", 3000),
    ("terrible", 1500), ("horrible", 1000), ("awful", 500),
    ("happy", 4000), ("sad", 2000), ("angry", 2000),
    ("excited", 2000), ("nervous", 1500), ("scared", 1500),
    ("tired", 3000), ("bored", 1500), ("confused", 1500),
    ("surprised", 1000), ("interested", 2000),
    ("necessary", 3000), ("possible", 3000), ("impossible", 2000),
    ("similar", 2000), ("opposite", 1000), ("full", 3000),
    ("empty", 2000), ("closed", 2000), ("correct", 2500),
    ("wrong", 2000), ("true", 2500), ("false", 1000),
    ("fake", 1000), ("old", 4000), ("young", 3000),
    ("ancient", 1500), ("modern", 2000), ("traditional", 1500),
    ("small", 3500), ("tiny", 1500), ("short", 2500),
    ("tall", 1500), ("wide", 1500), ("fast", 2500),
    ("slow", 1500), ("hard", 3000), ("easy", 2500),
    ("difficult", 2000), ("simple", 2000), ("cheap", 2000),
    ("expensive", 2000), ("busy", 2000), ("light", 3000),
    ("dark", 2500), ("bright", 2000), ("clean", 2000),
    ("dirty", 1000), ("final", 2000), ("past", 1500),
    ("previous", 1000), ("current", 2000), ("future", 2500),

    # Prepositions / connectors
    ("about", 10000), ("above", 1000), ("across", 1500),
    ("after", 7550), ("against", 2000), ("along", 1000),
    ("among", 1000), ("before", 6200), ("behind", 2000),
    ("below", 1500), ("beneath", 500), ("beside", 500),
    ("beyond", 1500), ("by", 10000), ("during", 2500),
    ("except", 500), ("for", 10000), ("from", 10000),
    ("inside", 2000), ("into", 8350), ("near", 2000),
    ("of", 10000), ("off", 6250), ("on", 10000),
    ("onto", 500), ("out", 9950), ("outside", 2000),
    ("over", 8500), ("through", 3000), ("to", 10000),
    ("toward", 1500), ("under", 2000), ("up", 10000),
    ("upon", 1000), ("with", 10000), ("within", 5850),
    ("without", 4550),

    # Number words
    ("zero", 1000), ("one", 10000), ("two", 8300), ("three", 3000),
    ("four", 2500), ("five", 2000), ("six", 1500), ("seven", 1000),
    ("eight", 500), ("nine", 500), ("ten", 1000),
    ("hundred", 1000), ("thousand", 1000), ("million", 1000),
    ("half", 2000), ("quarter", 1000),

    # Misc common
    ("name", 3925), ("email", 3900), ("phone", 2950),
    ("number", 7700), ("address", 3025), ("question", 2350),
    ("problem", 2000), ("solution", 3400), ("information", 4175),
    ("details", 3250), ("help", 6750), ("service", 4025),
    ("product", 3775), ("order", 2500), ("delivery", 1000),
    ("cost", 2000), ("total", 1500), ("price", 4000),

    # Restaurants & travel (common typing topics)
    ("restaurant", 2000), ("hotel", 3000), ("hospital", 2000),
    ("airport", 1500), ("station", 1000), ("museum", 500),
    ("park", 2000), ("beach", 2000), ("store", 2000),
    ("shop", 1500), ("bank", 1500), ("church", 500),
    ("school", 3575), ("library", 875), ("gym", 500),
    ("office", 3100), ("house", 2000),
    ("room", 1325), ("apartment", 1000),

    # Body parts (common)
    ("head", 1000), ("hand", 1000), ("eye", 500), ("ear", 500),
    ("nose", 300), ("mouth", 200), ("arm", 500), ("leg", 400),
    ("foot", 300), ("hair", 1000), ("face", 1000),
    ("heart", 1500), ("hand", 1000),
])

# ─── English bigrams (166 heads, each with multiple followers) ───
EN_BIGRAMS = OrderedDict([
    ("i", OrderedDict([
        ("am", 8000), ("have", 7000), ("will", 6000), ("can", 5000),
        ("like", 4000), ("do", 3500), ("need", 3000), ("want", 2500),
        ("would", 2000), ("know", 1800), ("think", 1600), ("love", 1500),
        ("had", 1200), ("said", 1000), ("did", 800), ("hope", 700),
        ("wish", 500), ("believe", 400), ("feel", 300), ("saw", 200),
    ])),
    ("you", OrderedDict([
        ("are", 7000), ("can", 5000), ("will", 4000), ("have", 3500),
        ("need", 3000), ("should", 2500), ("want", 2000), ("know", 1800),
        ("like", 1500), ("do", 1200), ("were", 1000), ("did", 800),
        ("would", 600), ("could", 500), ("must", 400), ("got", 300),
        ("make", 200), ("see", 100),
    ])),
    ("we", OrderedDict([
        ("are", 6000), ("have", 5000), ("will", 4000), ("can", 3500),
        ("need", 3000), ("should", 2500), ("would", 2000), ("were", 1500),
        ("do", 1000), ("did", 800), ("want", 600), ("know", 500),
        ("like", 400), ("got", 300), ("love", 200), ("had", 100),
    ])),
    ("they", OrderedDict([
        ("are", 5000), ("have", 4000), ("will", 3500), ("can", 3000),
        ("were", 2500), ("need", 2000), ("should", 1500), ("would", 1000),
        ("do", 800), ("did", 600), ("want", 500), ("know", 400),
        ("like", 300), ("said", 200), ("had", 100),
    ])),
    ("he", OrderedDict([
        ("is", 6000), ("was", 5000), ("has", 4000), ("had", 3500),
        ("will", 3000), ("can", 2500), ("would", 2000), ("said", 1500),
        ("does", 1000), ("did", 800), ("could", 600), ("should", 500),
        ("might", 400), ("never", 300), ("also", 200),
    ])),
    ("she", OrderedDict([
        ("is", 5000), ("was", 4500), ("has", 3500), ("had", 3000),
        ("will", 2500), ("can", 2000), ("said", 1500), ("would", 1000),
        ("does", 800), ("did", 600), ("could", 400), ("should", 300),
        ("also", 200), ("never", 100),
    ])),
    ("it", OrderedDict([
        ("is", 7000), ("was", 6000), ("has", 4000), ("had", 3000),
        ("will", 2500), ("can", 2000), ("would", 1500), ("could", 1000),
        ("should", 800), ("does", 600), ("did", 400), ("might", 300),
        ("seems", 200), ("looks", 100),
    ])),
    ("the", OrderedDict([
        ("first", 5000), ("same", 4000), ("other", 3500), ("only", 3000),
        ("best", 2500), ("new", 2000), ("most", 1500), ("next", 1200),
        ("last", 1000), ("main", 800), ("big", 600), ("entire", 400),
        ("whole", 300), ("real", 200),
    ])),
    ("to", OrderedDict([
        ("be", 7000), ("have", 5000), ("do", 4000), ("make", 3500),
        ("get", 3000), ("go", 2500), ("see", 2000), ("take", 1500),
        ("come", 1000), ("know", 800), ("say", 600), ("find", 500),
        ("give", 400), ("work", 300), ("help", 200),
    ])),
    ("and", OrderedDict([
        ("then", 5000), ("also", 3000), ("finally", 2000), ("after", 1500),
        ("more", 1000), ("now", 800), ("so", 600), ("still", 400),
        ("therefore", 300), ("thus", 200),
    ])),
    ("for", OrderedDict([
        ("the", 6000), ("a", 5000), ("your", 3000), ("my", 2500),
        ("example", 2000), ("sure", 1500), ("now", 1000), ("real", 800),
        ("good", 600), ("long", 400),
    ])),
    ("in", OrderedDict([
        ("the", 7000), ("a", 5000), ("order", 3000), ("fact", 2000),
        ("general", 1500), ("addition", 1000), ("particular", 800),
        ("short", 600), ("total", 400), ("case", 200),
    ])),
    ("is", OrderedDict([
        ("a", 6000), ("the", 5000), ("an", 3000), ("not", 2000),
        ("also", 1500), ("very", 1000), ("more", 800), ("still", 600),
        ("really", 400), ("quite", 200), ("just", 100),
    ])),
    ("of", OrderedDict([
        ("the", 7000), ("a", 5000), ("course", 2000), ("all", 1500),
        ("many", 1000), ("some", 800), ("most", 600), ("few", 400),
        ("these", 200),
    ])),
    ("this", OrderedDict([
        ("is", 7000), ("was", 5000), ("will", 3000), ("has", 2000),
        ("does", 1500), ("means", 1000), ("should", 800), ("can", 600),
        ("might", 400), ("could", 200), ("morning", 500),
        ("afternoon", 400), ("evening", 300), ("week", 500),
        ("year", 400), ("time", 500),
    ])),
    ("that", OrderedDict([
        ("is", 6000), ("was", 4000), ("will", 3000), ("can", 2000),
        ("has", 1500), ("does", 1000), ("should", 800), ("would", 600),
        ("could", 400), ("might", 200),
    ])),
    ("my", OrderedDict([
        ("name", 5000), ("friend", 3000), ("family", 2500), ("favorite", 2000),
        ("day", 1500), ("life", 1000), ("time", 800), ("love", 600),
        ("house", 400), ("car", 200), ("home", 100),
    ])),
    ("your", OrderedDict([
        ("name", 4000), ("email", 3000), ("phone", 2500), ("message", 2000),
        ("family", 1500), ("friend", 1000), ("time", 800), ("help", 600),
        ("support", 400), ("order", 200),
    ])),
    ("have", OrderedDict([
        ("a", 6000), ("the", 5000), ("been", 4000), ("to", 3000),
        ("some", 2000), ("all", 1500), ("no", 1000), ("your", 800),
        ("my", 600), ("many", 400), ("more", 200),
    ])),
    ("not", OrderedDict([
        ("have", 4000), ("be", 3500), ("do", 3000), ("know", 2000),
        ("see", 1500), ("want", 1000), ("need", 800), ("like", 600),
        ("only", 400), ("just", 200), ("really", 100),
    ])),
    ("be", OrderedDict([
        ("the", 5000), ("a", 4000), ("able", 3000), ("sure", 2500),
        ("careful", 2000), ("happy", 1500), ("ready", 1000),
        ("good", 800), ("nice", 600), ("honest", 400),
    ])),
    ("will", OrderedDict([
        ("be", 6000), ("have", 4000), ("do", 3000), ("work", 2000),
        ("go", 1500), ("take", 1000), ("make", 800), ("need", 600),
        ("help", 400), ("get", 200),
    ])),
    ("can", OrderedDict([
        ("be", 5000), ("have", 3000), ("do", 2500), ("make", 2000),
        ("get", 1500), ("see", 1000), ("take", 800), ("help", 600),
        ("go", 400), ("use", 200),
    ])),
    ("do", OrderedDict([
        ("not", 5000), ("the", 3000), ("a", 2000), ("your", 1500),
        ("my", 1000), ("you", 800), ("we", 600), ("they", 400),
        ("it", 200), ("what", 100),
    ])),
    ("what", OrderedDict([
        ("is", 5000), ("was", 3000), ("are", 2500), ("do", 2000),
        ("does", 1500), ("will", 1000), ("can", 800), ("about", 600),
        ("happened", 400), ("if", 200),
    ])),
    ("how", OrderedDict([
        ("are", 4000), ("is", 3000), ("do", 2500), ("does", 2000),
        ("can", 1500), ("to", 1000), ("much", 800), ("many", 600),
        ("long", 400), ("about", 200),
    ])),
    ("where", OrderedDict([
        ("is", 4000), ("are", 3000), ("do", 2000), ("does", 1500),
        ("can", 1000), ("will", 800), ("did", 600), ("have", 400),
    ])),
    ("when", OrderedDict([
        ("is", 4000), ("are", 3000), ("do", 2000), ("does", 1500),
        ("will", 1000), ("can", 800), ("did", 600),
    ])),
    ("who", OrderedDict([
        ("is", 4000), ("are", 3000), ("was", 2000), ("will", 1500),
        ("does", 1000), ("can", 600), ("has", 400),
    ])),
    ("why", OrderedDict([
        ("is", 3000), ("are", 2500), ("do", 2000), ("does", 1500),
        ("did", 1000), ("don't", 800),
    ])),
    ("there", OrderedDict([
        ("is", 6000), ("are", 5000), ("was", 3000), ("were", 2000),
        ("has", 1500), ("have", 1000), ("will", 800), ("can", 600),
    ])),
    ("here", OrderedDict([
        ("is", 5000), ("are", 4000), ("was", 2000), ("comes", 1500),
        ("you", 1000),
    ])),
    ("please", OrderedDict([
        ("let", 3000), ("send", 2000), ("call", 1500), ("check", 1000),
        ("see", 800), ("find", 600), ("contact", 400), ("note", 200),
    ])),
    ("thank", OrderedDict([
        ("you", 8000),
    ])),
    ("thanks", OrderedDict([
        ("for", 4000), ("to", 2000),
    ])),
    ("good", OrderedDict([
        ("morning", 5000), ("afternoon", 3000), ("evening", 2000),
        ("day", 1500), ("luck", 1000), ("job", 800), ("idea", 600),
        ("time", 400), ("work", 200),
    ])),
    ("look", OrderedDict([
        ("at", 3000), ("for", 2000), ("forward", 1500), ("like", 1000),
    ])),
    ("well", OrderedDict([
        ("done", 3000), ("said", 2000), ("known", 1500), ("be", 1000),
    ])),
    ("just", OrderedDict([
        ("like", 2000), ("a", 1500), ("the", 1000), ("wanted", 800),
        ("got", 600), ("said", 400), ("had", 200),
    ])),
    ("like", OrderedDict([
        ("to", 3000), ("a", 2000), ("the", 1500), ("it", 1000),
        ("this", 800), ("that", 600),
    ])),
    ("about", OrderedDict([
        ("the", 3000), ("a", 2000), ("your", 1000), ("this", 800),
        ("it", 600), ("how", 400),
    ])),
    ("all", OrderedDict([
        ("the", 4000), ("of", 3000), ("my", 2000), ("your", 1500),
        ("this", 1000), ("that", 800),
    ])),
    ("some", OrderedDict([
        ("of", 3000), ("people", 2000), ("time", 1500), ("things", 1000),
    ])),
    ("more", OrderedDict([
        ("than", 3000), ("and", 2000), ("people", 1000), ("time", 800),
        ("important", 600),
    ])),
    ("very", OrderedDict([
        ("good", 3000), ("nice", 2000), ("important", 1500), ("much", 1000),
        ("well", 800), ("happy", 600),
    ])),
    ("too", OrderedDict([
        ("much", 2000), ("many", 1500), ("late", 1000), ("early", 500),
    ])),
    ("also", OrderedDict([
        ("have", 2000), ("known", 1500), ("called", 1000), ("has", 800),
        ("can", 600), ("need", 400),
    ])),
    ("should", OrderedDict([
        ("be", 4000), ("have", 2500), ("not", 1500), ("also", 1000),
    ])),
    ("would", OrderedDict([
        ("be", 3000), ("have", 2000), ("like", 1500), ("love", 1000),
        ("never", 500),
    ])),
    ("could", OrderedDict([
        ("be", 3000), ("have", 2000), ("not", 1000), ("see", 500),
    ])),
    ("need", OrderedDict([
        ("to", 3000), ("a", 2000), ("some", 1000), ("help", 500),
    ])),
    ("want", OrderedDict([
        ("to", 4000), ("a", 2000), ("some", 1000),
    ])),
    ("know", OrderedDict([
        ("that", 2000), ("what", 1500), ("how", 1000), ("if", 500),
    ])),
    ("think", OrderedDict([
        ("about", 2000), ("that", 1000), ("so", 500),
    ])),
    ("love", OrderedDict([
        ("you", 3000), ("it", 1500), ("to", 1000), ("the", 500),
    ])),
    ("make", OrderedDict([
        ("sure", 2000), ("a", 1500), ("the", 1000),
    ])),
    ("take", OrderedDict([
        ("a", 2000), ("the", 1000), ("care", 500),
    ])),
    ("get", OrderedDict([
        ("a", 2000), ("the", 1500), ("to", 1000), ("some", 500),
    ])),
    ("find", OrderedDict([
        ("a", 1500), ("the", 1000), ("out", 500),
    ])),
    ("let", OrderedDict([
        ("me", 3000), ("us", 2000), ("go", 500),
    ])),
    ("tell", OrderedDict([
        ("me", 2000), ("you", 1000), ("them", 500),
    ])),
    ("see", OrderedDict([
        ("you", 2500), ("the", 1500), ("if", 500),
    ])),
    ("call", OrderedDict([
        ("me", 2000), ("you", 1000), ("the", 500),
    ])),
    ("send", OrderedDict([
        ("me", 2000), ("you", 1500), ("a", 500),
    ])),
    ("come", OrderedDict([
        ("to", 2000), ("and", 1000), ("here", 500),
    ])),
    ("go", OrderedDict([
        ("to", 3000), ("a", 1000), ("and", 500),
    ])),
    ("work", OrderedDict([
        ("on", 1500), ("with", 1000), ("for", 500),
    ])),
    ("say", OrderedDict([
        ("that", 1000), ("something", 500),
    ])),
    ("mean", OrderedDict([
        ("that", 1000), ("it", 500),
    ])),
    ("right", OrderedDict([
        ("now", 2000), ("here", 1000),
    ])),
    ("never", OrderedDict([
        ("been", 1500), ("seen", 1000), ("had", 500), ("thought", 500),
    ])),
    ("always", OrderedDict([
        ("been", 1500), ("have", 1000), ("be", 500),
    ])),
    ("still", OrderedDict([
        ("have", 1000), ("be", 500), ("need", 500),
    ])),
    ("even", OrderedDict([
        ("though", 1500), ("if", 1000), ("more", 500),
    ])),
    ("much", OrderedDict([
        ("more", 1500), ("better", 1000),
    ])),
    ("many", OrderedDict([
        ("people", 2000), ("things", 1500), ("years", 1000),
    ])),
    ("each", OrderedDict([
        ("other", 2000), ("one", 1000),
    ])),
    ("another", OrderedDict([
        ("one", 1000), ("day", 500),
    ])),
    ("any", OrderedDict([
        ("other", 1000), ("more", 500),
    ])),
    ("nothing", OrderedDict([
        ("but", 500), ("else", 500),
    ])),
    ("something", OrderedDict([
        ("else", 1000), ("like", 500),
    ])),
    ("maybe", OrderedDict([
        ("we", 1000), ("you", 500),
    ])),
    ("however", OrderedDict([
        (",", 1500),
    ])),
    ("ok", OrderedDict([
        (",", 1000), (".", 500),
    ])),
    ("sure", OrderedDict([
        (",", 1000), (".", 500),
    ])),
    ("yes", OrderedDict([
        (",", 1000), ("!", 500),
    ])),
    ("no", OrderedDict([
        (",", 1000), ("one", 500),
    ])),
    ("sorry", OrderedDict([
        (",", 1000), ("about", 500), ("for", 500),
    ])),
    ("hello", OrderedDict([
        (",", 1500), ("!", 500),
    ])),
    ("hi", OrderedDict([
        ("there", 1000), (",", 500),
    ])),
    ("hey", OrderedDict([
        ("there", 500),
    ])),
    ("thanks", OrderedDict([
        ("for", 2000), ("!", 500),
    ])),
    ("great", OrderedDict([
        (",", 1000), ("!", 500),
    ])),
    ("nice", OrderedDict([
        ("to", 1000), (",", 500),
    ])),
    ("happy", OrderedDict([
        ("to", 1000), ("birthday", 500),
    ])),
    ("welcome", OrderedDict([
        ("to", 2000),
    ])),
    ("well", OrderedDict([
        (",", 2000),
    ])),
    ("so", OrderedDict([
        ("that", 1500), ("the", 1000), ("much", 500),
    ])),
    ("but", OrderedDict([
        ("the", 2000), ("it", 1000), ("not", 500),
    ])),
    ("if", OrderedDict([
        ("you", 3000), ("the", 2000), ("we", 1000),
    ])),
    ("or", OrderedDict([
        ("the", 1500), ("not", 500),
    ])),
    ("because", OrderedDict([
        ("the", 1000), ("of", 500), ("i", 500),
    ])),
    ("as", OrderedDict([
        ("well", 1500), ("much", 500), ("soon", 500),
    ])),
    ("while", OrderedDict([
        ("the", 1000), ("you", 500),
    ])),
    ("during", OrderedDict([
        ("the", 1000),
    ])),
    ("before", OrderedDict([
        ("the", 1000), ("you", 500),
    ])),
    ("after", OrderedDict([
        ("the", 1000), ("a", 500), ("all", 500),
    ])),
    ("since", OrderedDict([
        ("the", 500), ("you", 500),
    ])),
    ("until", OrderedDict([
        ("the", 500), ("you", 500),
    ])),
    ("by", OrderedDict([
        ("the", 2000), ("a", 500),
    ])),
    ("with", OrderedDict([
        ("the", 2000), ("a", 1000), ("your", 500),
    ])),
    ("without", OrderedDict([
        ("a", 1000), ("the", 500),
    ])),
    ("over", OrderedDict([
        ("the", 1500), ("a", 500),
    ])),
    ("under", OrderedDict([
        ("the", 1000),
    ])),
    ("through", OrderedDict([
        ("the", 1000),
    ])),
    ("between", OrderedDict([
        ("the", 500),
    ])),
    ("around", OrderedDict([
        ("the", 1000),
    ])),
    ("down", OrderedDict([
        ("the", 500),
    ])),
    ("up", OrderedDict([
        ("to", 1000), ("the", 500),
    ])),
    ("out", OrderedDict([
        ("of", 1500), ("the", 500),
    ])),
    ("off", OrderedDict([
        ("the", 500),
    ])),
    ("am", OrderedDict([
        ("a", 1500), ("going", 1000), ("not", 500),
    ])),
    ("are", OrderedDict([
        ("you", 2000), ("the", 1500), ("a", 500),
    ])),
    ("was", OrderedDict([
        ("a", 2000), ("the", 1500), ("not", 500),
    ])),
    ("were", OrderedDict([
        ("the", 1000), ("not", 500),
    ])),
    ("had", OrderedDict([
        ("a", 1500), ("the", 1000), ("been", 500),
    ])),
    ("been", OrderedDict([
        ("a", 1000), ("the", 500),
    ])),
    ("did", OrderedDict([
        ("not", 2000), ("the", 500),
    ])),
    ("said", OrderedDict([
        ("that", 1000), ("the", 500),
    ])),
    ("made", OrderedDict([
        ("a", 500), ("the", 500),
    ])),
    ("going", OrderedDict([
        ("to", 3000),
    ])),
    ("trying", OrderedDict([
        ("to", 1000),
    ])),
    ("morning", OrderedDict([
        (",", 500),
    ])),
    ("next", OrderedDict([
        ("week", 1000), ("time", 500), ("year", 500),
    ])),
    ("last", OrderedDict([
        ("week", 1000), ("year", 500), ("night", 500),
    ])),
    ("first", OrderedDict([
        ("time", 1000), ("day", 500),
    ])),
    ("new", OrderedDict([
        ("year", 1000), ("one", 500),
    ])),
    ("same", OrderedDict([
        ("time", 1000), ("day", 500),
    ])),
    ("big", OrderedDict([
        ("deal", 500),
    ])),
    ("long", OrderedDict([
        ("time", 1000), ("day", 500),
    ])),
    ("lot", OrderedDict([
        ("of", 2000),
    ])),
    ("lots", OrderedDict([
        ("of", 1000),
    ])),
    ("plenty", OrderedDict([
        ("of", 500),
    ])),
    ("kind", OrderedDict([
        ("of", 1000),
    ])),
    ("part", OrderedDict([
        ("of", 1000),
    ])),
    ("number", OrderedDict([
        ("of", 500),
    ])),
    ("way", OrderedDict([
        ("to", 500),
    ])),
    ("time", OrderedDict([
        ("for", 500), ("to", 500),
    ])),
    ("thing", OrderedDict([
        ("is", 500), ("to", 500),
    ])),
    ("things", OrderedDict([
        ("to", 500),
    ])),
    ("people", OrderedDict([
        ("who", 500),
    ])),
    ("today", OrderedDict([
        (",", 500), ("!", 500),
    ])),
    ("tomorrow", OrderedDict([
        (",", 500), ("!", 500),
    ])),
])

# ─── Load current corpus, replace EN, preserve ES ───
script_dir = os.path.dirname(os.path.abspath(__file__))
# Try common paths
paths = [
    os.path.join(script_dir, "..", "smarttext", "app", "src", "main", "assets", "corpus.json"),
    os.path.join(script_dir, "..", "app", "src", "main", "assets", "corpus.json"),
]
path = None
for p in paths:
    if os.path.exists(p):
        path = p
        break

if not path:
    # Try glob
    from glob import glob
    candidates = glob("**/corpus.json", recursive=True)
    if candidates:
        path = candidates[0]

print(f"Reading corpus from: {path}")

with open(path, "r", encoding="utf-8") as f:
    corpus = json.load(f)

# Build EN section (no duplicates — OrderedDict ensures unique keys)
en_unigrams_dict = dict(EN_UNIGRAMS)
en_bigrams_dict = {}
for head, followers in EN_BIGRAMS.items():
    en_bigrams_dict[head] = dict(followers)

corpus["en"] = {
    "unigrams": en_unigrams_dict,
    "bigrams": en_bigrams_dict
}

# Verify no duplicates
dup_check = len(EN_UNIGRAMS)
unique_words = len(set(EN_UNIGRAMS.keys()))
if dup_check != unique_words:
    print(f"WARNING: {dup_check - unique_words} duplicates detected in unigrams!")
else:
    print("✓ No duplicates in unigrams")

# Better frequencies for common missing followers (before auto-add)
FOLLOWER_FREQS = {
    "able": 3000, "ready": 3000, "fact": 3000, "whole": 2000,
    "saw": 2500, "seems": 2000, "looks": 2000, "main": 3000,
    "big": 5000, "entire": 2000, "finally": 3000, "therefore": 2000,
    "thus": 1000, "means": 3000, "careful": 2000, "honest": 1500,
    "contact": 2000, "wanted": 2000, "forward": 2000, "happened": 2000,
    "deal": 1500, "luck": 1000, "birthday": 2000, "comes": 1500,
    "called": 2000, "addition": 2000, "general": 3000, "particular": 2000,
    "order": 3000, "else": 1000,
}

# Auto-add any missing bigram followers to unigrams
missing = []
for head, followers in EN_BIGRAMS.items():
    for word in followers:
        if word not in (",", ".", "!", "?"):
            if word not in en_unigrams_dict and word.lower() not in en_unigrams_dict:
                freq = FOLLOWER_FREQS.get(word, 500)
                missing.append(f"'{word}' (follower of '{head}', freq={freq})")
                en_unigrams_dict[word] = freq

if missing:
    print(f"⚠ Added {len(missing)} missing bigram followers to unigrams:")
    for m in missing:
        print(f"   {m}")
else:
    print("✓ All bigram followers are already in unigrams")

print(f"   Total EN unigrams after addition: {len(en_unigrams_dict)}")

# Write
with open(path, "w", encoding="utf-8") as f:
    json.dump(corpus, f, indent=2, ensure_ascii=False)

en_uni = len(corpus["en"]["unigrams"])
en_bi = len(corpus["en"]["bigrams"])
# Count total bigram followers
total_followers = sum(len(v) for v in EN_BIGRAMS.values())
es_uni = len(corpus["es"]["unigrams"])
es_bi = len(corpus["es"]["bigrams"])

import os as _os
size = _os.path.getsize(path)

print(f"\n✅ Corpus updated!")
print(f"   EN unigrams: {en_uni}")
print(f"   EN bigram heads: {en_bi}")
print(f"   EN total bigram pairs: {total_followers}")
print(f"   ES unigrams: {es_uni} (unchanged)")
print(f"   ES bigrams: {es_bi} (unchanged)")
print(f"   File size: {size/1024:.0f} KB")
