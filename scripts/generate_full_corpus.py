#!/usr/bin/env python3
"""
Generate full bilingual corpus (EN + ES) for SmartText keyboard.
Uses wordfreq library to get 10,000+ words per language with real Zipfian distribution.
"""

import json
import os
import math
from collections import OrderedDict
from wordfreq import top_n_list, zipf_frequency

def wordfreq_to_unigrams(lang, n=10000):
    """Get top N words from wordfreq with frequency scaled to max=10000."""
    words = top_n_list(lang, n)
    
    # Get Zipf values for all words
    zipfs = {}
    for w in words:
        z = zipf_frequency(w, lang)
        if z > 0:
            zipfs[w] = z
    
    if not zipfs:
        return OrderedDict()
    
    max_zipf = max(zipfs.values())
    
    # Scale: max frequency = 10000, min frequency = 1
    unigrams = OrderedDict()
    for w in words:
        if w in zipfs:
            z = zipfs[w]
            # Scale Zipf value to 1-10000 range
            freq = max(5, int(10000 * 10 ** (z - max_zipf)))
            unigrams[w] = freq
    
    return unigrams


def get_es_bigrams():
    """Spanish bigrams based on common grammatical patterns."""
    bigrams = OrderedDict()
    
    # Pronouns + verb auxiliaries
    bigrams["yo"] = OrderedDict([("soy", 5000), ("tengo", 4500), ("he", 4000), ("estoy", 3500), ("puedo", 3000), ("quiero", 2800), ("necesito", 2500), ("voy", 2200), ("hago", 2000), ("creo", 1800)])
    bigrams["tú"] = OrderedDict([("eres", 4000), ("tienes", 3500), ("has", 3000), ("estás", 2800), ("puedes", 2500), ("quieres", 2200), ("necesitas", 2000), ("vas", 1800)])
    bigrams["él"] = OrderedDict([("es", 5000), ("tiene", 4000), ("ha", 3500), ("está", 3000), ("puede", 2800), ("quiere", 2500), ("dijo", 2000), ("hace", 1800)])
    bigrams["ella"] = OrderedDict([("es", 4500), ("tiene", 3800), ("ha", 3200), ("está", 2800), ("puede", 2500), ("quiere", 2200)])
    bigrams["nosotros"] = OrderedDict([("somos", 3000), ("tenemos", 2500), ("hemos", 2000), ("estamos", 1800), ("podemos", 1500)])
    bigrams["ellos"] = OrderedDict([("son", 4000), ("tienen", 3000), ("han", 2500), ("están", 2200), ("pueden", 2000)])
    bigrams["ellas"] = OrderedDict([("son", 3000), ("tienen", 2500), ("han", 2000), ("están", 1800)])
    bigrams["usted"] = OrderedDict([("es", 3000), ("tiene", 2500), ("puede", 2000)])
    
    # Common verb forms + prepositions
    bigrams["es"] = OrderedDict([("un", 4000), ("una", 3500), ("la", 3000), ("el", 2800), ("muy", 2500), ("importante", 2000), ("necesario", 1500), ("posible", 1200)])
    bigrams["son"] = OrderedDict([("los", 3000), ("las", 2800), ("muy", 2000)])
    bigrams["fue"] = OrderedDict([("un", 2000), ("una", 1800), ("la", 1500), ("el", 1200), ("muy", 1000)])
    bigrams["era"] = OrderedDict([("un", 2000), ("una", 1500), ("muy", 1000)])
    bigrams["ha"] = OrderedDict([("sido", 3000), ("estado", 2000), ("hecho", 1500), ("tenido", 1200)])
    bigrams["han"] = OrderedDict([("sido", 2000), ("estado", 1500), ("hecho", 1200)])
    bigrams["había"] = OrderedDict([("una", 1500), ("un", 1200)])
    bigrams["está"] = OrderedDict([("en", 3000), ("muy", 2000), ("aquí", 1500), ("bien", 1200), ("listo", 1000)])
    bigrams["están"] = OrderedDict([("en", 2000), ("aquí", 1200)])
    bigrams["puede"] = OrderedDict([("ser", 2000), ("estar", 1500), ("tener", 1000), ("hacer", 800)])
    bigrams["pueden"] = OrderedDict([("ser", 1500), ("estar", 1000), ("tener", 800)])
    bigrams["debe"] = OrderedDict([("ser", 2000), ("estar", 1500), ("tener", 1000), ("hacer", 800)])
    bigrams["deben"] = OrderedDict([("ser", 1000), ("estar", 800)])
    bigrams["va"] = OrderedDict([("a", 3000), ("ser", 1500)])
    bigrams["van"] = OrderedDict([("a", 2000), ("ser", 1000)])
    bigrams["ir"] = OrderedDict([("a", 3000), ("al", 1500)])
    bigrams["voy"] = OrderedDict([("a", 4000), ("al", 1000)])
    bigrams["vas"] = OrderedDict([("a", 2000)])
    bigrams["hace"] = OrderedDict([("que", 2000), ("mucho", 1500), ("un", 1000)])
    bigrams["hacer"] = OrderedDict([("una", 1500), ("un", 1200), ("el", 1000), ("lo", 800)])
    bigrams["tener"] = OrderedDict([("que", 2000), ("una", 1000), ("un", 800)])
    bigrams["tengo"] = OrderedDict([("que", 3000), ("una", 1500), ("un", 1200)])
    bigrams["tiene"] = OrderedDict([("que", 2500), ("una", 1200), ("un", 1000)])
    bigrams["quiero"] = OrderedDict([("que", 2000), ("una", 1000), ("un", 800), ("hacer", 600)])
    bigrams["quieres"] = OrderedDict([("que", 1500)])
    bigrams["saber"] = OrderedDict([("que", 1500), ("si", 1000), ("cómo", 800)])
    bigrams["sé"] = OrderedDict([("que", 2000), ("cómo", 1000)])
    bigrams["creo"] = OrderedDict([("que", 3000)])
    bigrams["pienso"] = OrderedDict([("que", 2000)])
    bigrams["dijo"] = OrderedDict([("que", 3000), ("la", 1000)])
    bigrams["decir"] = OrderedDict([("que", 2000), ("la", 800), ("lo", 800)])
    bigrams["ver"] = OrderedDict([("que", 1000), ("si", 800), ("cómo", 500)])
    bigrams["mirar"] = OrderedDict([("el", 1000), ("la", 800)])
    bigrams["dar"] = OrderedDict([("una", 1000), ("un", 800), ("el", 500)])
    bigrams["darle"] = OrderedDict([("una", 500)])
    bigrams["ven"] = OrderedDict([("aquí", 1000), ("conmigo", 500)])
    bigrams["vamos"] = OrderedDict([("a", 3000), ("al", 1000)])
    bigrams["vaya"] = OrderedDict([("a", 1500)])
    
    # Articles + nouns patterns
    bigrams["la"] = OrderedDict([("vez", 2000), ("casa", 1800), ("persona", 1500), ("vida", 1200), ("manera", 1000), ("primera", 1000), ("misma", 800), ("mayoría", 800)])
    bigrams["el"] = OrderedDict([("que", 2000), ("cual", 1500), ("primer", 1500), ("mismo", 1000), ("problema", 800), ("caso", 800), ("momento", 1200), ("tiempo", 1000)])
    bigrams["los"] = OrderedDict([("que", 2000), ("cuales", 1000), ("demás", 800), ("primeros", 500)])
    bigrams["las"] = OrderedDict([("que", 1500), ("cuales", 800), ("demás", 500)])
    bigrams["una"] = OrderedDict([("vez", 2000), ("persona", 1500), ("cosa", 1200), ("buena", 1000), ("gran", 800), ("misma", 500)])
    bigrams["un"] = OrderedDict([("poco", 2000), ("gran", 1500), ("buen", 1000), ("momento", 800)])
    
    # Prepositions + articles/nouns
    bigrams["de"] = OrderedDict([("la", 6000), ("el", 5000), ("los", 4000), ("las", 3000), ("un", 2500), ("una", 2000), ("que", 1500), ("lo", 1000)])
    bigrams["en"] = OrderedDict([("el", 5000), ("la", 4000), ("los", 3000), ("las", 2000), ("una", 1500), ("un", 1000)])
    bigrams["por"] = OrderedDict([("la", 3000), ("el", 2500), ("lo", 2000), ("los", 1500), ("que", 1000), ("favor", 3000)])
    bigrams["para"] = OrderedDict([("el", 3000), ("la", 2500), ("los", 2000), ("que", 1500), ("una", 1000), ("mí", 500)])
    bigrams["con"] = OrderedDict([("el", 3000), ("la", 2500), ("los", 2000), ("las", 1500), ("un", 1000), ("una", 800), ("usted", 500)])
    bigrams["sin"] = OrderedDict([("el", 1500), ("la", 1200), ("un", 1000), ("una", 800), ("duda", 800)])
    bigrams["entre"] = OrderedDict([("el", 1500), ("la", 1000), ("los", 800)])
    bigrams["sobre"] = OrderedDict([("el", 1500), ("la", 1000), ("los", 500)])
    bigrams["desde"] = OrderedDict([("el", 1500), ("la", 1000), ("que", 500)])
    bigrams["hasta"] = OrderedDict([("el", 1500), ("la", 1000), ("que", 800)])
    bigrams["durante"] = OrderedDict([("el", 1000), ("la", 800), ("los", 500)])
    bigrams["según"] = OrderedDict([("el", 1000), ("la", 500)])
    bigrams["ante"] = OrderedDict([("el", 500), ("la", 500)])
    
    # Common verb + que subjunctive
    bigrams["espero"] = OrderedDict([("que", 2000)])
    bigrams["quisiera"] = OrderedDict([("que", 1000)])
    bigrams["ojalá"] = OrderedDict([("que", 1500)])
    bigrams["pueda"] = OrderedDict([("ser", 1000), ("estar", 500)])
    
    # Time expressions
    bigrams["a"] = OrderedDict([("la", 2000), ("las", 1500), ("los", 1000), ("una", 500), ("través", 800)])
    bigrams["ayer"] = OrderedDict([("fue", 1000), ("estuve", 500), ("vi", 500)])
    bigrams["hoy"] = OrderedDict([("es", 1500), ("estoy", 500), ("tenemos", 500)])
    bigrams["mañana"] = OrderedDict([("voy", 1000), ("será", 500), ("tengo", 500)])
    bigrams["nunca"] = OrderedDict([("he", 1000), ("había", 500), ("pensé", 500)])
    bigrams["siempre"] = OrderedDict([("he", 1000), ("está", 500), ("estoy", 500)])
    bigrams["ya"] = OrderedDict([("que", 1500), ("no", 1000), ("he", 800), ("está", 500)])
    bigrams["todavía"] = OrderedDict([("no", 1000), ("está", 500)])
    bigrams["aún"] = OrderedDict([("no", 800), ("así", 500)])
    bigrams["luego"] = OrderedDict([("de", 1000), ("que", 500)])
    bigrams["después"] = OrderedDict([("de", 2000), ("que", 1000)])
    bigrams["antes"] = OrderedDict([("de", 1500), ("que", 800)])
    bigrams["ahora"] = OrderedDict([("que", 1000), ("mismo", 500), ("estoy", 500)])
    bigrams["entonces"] = OrderedDict([(",", 1500), ("que", 500)])
    bigrams["pronto"] = OrderedDict([(",", 500)])
    
    # Conjunctions and connectors
    bigrams["y"] = OrderedDict([("el", 2000), ("la", 1500), ("los", 1200), ("no", 1000), ("se", 800)])
    bigrams["pero"] = OrderedDict([("no", 2000), ("el", 1000), ("la", 800), ("si", 500)])
    bigrams["aunque"] = OrderedDict([("no", 1000), ("el", 500), ("la", 500)])
    bigrams["sino"] = OrderedDict([("que", 1500)])
    bigrams["además"] = OrderedDict([(",", 1000), ("de", 800)])
    bigrams["también"] = OrderedDict([("es", 1000), ("está", 500), ("puede", 500)])
    bigrams["tampoco"] = OrderedDict([("no", 500)])
    bigrams["incluso"] = OrderedDict([("si", 500), ("el", 500)])
    bigrams["más"] = OrderedDict([("que", 2000), ("de", 1500), ("allá", 500), ("bien", 500)])
    bigrams["como"] = OrderedDict([("el", 1500), ("la", 1000), ("si", 800), ("un", 500), ("por", 500)])
    bigrams["tan"] = OrderedDict([("solo", 1000), ("que", 800), ("bien", 500)])
    bigrams["tanto"] = OrderedDict([("que", 1000), ("como", 500)])
    
    # Question words
    bigrams["qué"] = OrderedDict([("es", 2000), ("haces", 1500), ("dices", 1000), ("tal", 800), ("quieres", 800)])
    bigrams["cómo"] = OrderedDict([("estás", 2000), ("está", 1500), ("se", 1000), ("puedo", 800)])
    bigrams["cuándo"] = OrderedDict([("vas", 1000), ("vamos", 800), ("vuelves", 500)])
    bigrams["dónde"] = OrderedDict([("está", 2000), ("estás", 1500), ("están", 1000)])
    bigrams["quién"] = OrderedDict([("es", 1500), ("está", 500)])
    bigrams["quiénes"] = OrderedDict([("son", 500)])
    # Merge "por qué" into existing "por" entry
    bigrams["por"]["qué"] = 1000
    
    # Negation
    bigrams["no"] = OrderedDict([("sé", 2000), ("tengo", 1500), ("es", 1200), ("está", 1000), ("puedo", 800), ("me", 800), ("lo", 500), ("hay", 1000), ("quiero", 1000), ("creo", 800), ("pasa", 500)])
    
    # Ser/Estar
    bigrams["ser"] = OrderedDict([("muy", 1500), ("más", 1000), ("tan", 500), ("un", 500)])
    bigrams["estar"] = OrderedDict([("en", 1500), ("aquí", 1000), ("bien", 500)])
    bigrams["siendo"] = OrderedDict([("muy", 500)])
    bigrams["estado"] = OrderedDict([("un", 500)])
    bigrams["sido"] = OrderedDict([("muy", 500)])
    bigrams["bien"] = OrderedDict([("venido", 500), ("hecho", 500)])
    
    # Reflexive patterns
    bigrams["me"] = OrderedDict([("gusta", 2000), ("encanta", 1000), ("parece", 1000), ("duele", 500), ("da", 500)])
    bigrams["te"] = OrderedDict([("gusta", 1500), ("quiero", 1000), ("encanta", 500), ("amo", 500)])
    bigrams["se"] = OrderedDict([("puede", 2000), ("debe", 1500), ("hace", 1000), ("dice", 1000), ("llama", 500), ("trata", 500)])
    bigrams["nos"] = OrderedDict([("gusta", 1000), ("vemos", 500), ("encanta", 500)])
    bigrams["les"] = OrderedDict([("gusta", 1000), ("parece", 500)])
    bigrams["le"] = OrderedDict([("gusta", 1000), ("dice", 500)])
    
    # Quantifiers
    bigrams["mucho"] = OrderedDict([("más", 1000), ("menos", 500), ("mejor", 500)])
    bigrams["mucha"] = OrderedDict([("gente", 1000), ("gracia", 500)])
    bigrams["muchos"] = OrderedDict([("de", 1000), ("años", 500)])
    bigrams["muchas"] = OrderedDict([("gracias", 2000), ("personas", 500)])
    bigrams["poco"] = OrderedDict([("más", 500), ("de", 500)])
    bigrams["poca"] = OrderedDict([("gente", 500)])
    bigrams["varios"] = OrderedDict([("de", 500)])
    bigrams["bastante"] = OrderedDict([("más", 500), ("bien", 500)])
    bigrams["demasiado"] = OrderedDict([("tarde", 500), ("pronto", 500)])
    bigrams["algo"] = OrderedDict([("más", 500), ("que", 500)])
    bigrams["nada"] = OrderedDict([("más", 1000), ("que", 500)])
    
    # Interjections / discourse
    bigrams["sí"] = OrderedDict([(",", 500), ("que", 500)])
    bigrams["claro"] = OrderedDict([("que", 1000), (",", 500)])
    bigrams["bueno"] = OrderedDict([(",", 1000), ("que", 500)])
    bigrams["pues"] = OrderedDict([(",", 500), ("que", 500)])
    bigrams["vale"] = OrderedDict([(",", 500)])
    bigrams["gracias"] = OrderedDict([("por", 1500), ("a", 500)])
    bigrams["lo"] = OrderedDict([("que", 3000), ("siento", 1500), ("mismo", 500)])
    bigrams["le"] = OrderedDict([("gusta", 500), ("dije", 500)])
    bigrams["todo"] = OrderedDict([("el", 1000), ("lo", 800), ("esto", 500)])
    bigrams["toda"] = OrderedDict([("la", 1000), ("mi", 500)])
    bigrams["todos"] = OrderedDict([("los", 2000), ("ustedes", 500)])
    bigrams["todas"] = OrderedDict([("las", 1000)])
    bigrams["al"] = OrderedDict([("fin", 1000), ("menos", 800), ("final", 500), ("igual", 500)])
    bigrams["del"] = OrderedDict([("todo", 500)])
    
    return bigrams


def get_en_bigrams():
    """English bigrams (keep the existing comprehensive set from generate_corpus.py)."""
    return OrderedDict([
        ("i", OrderedDict([("am", 8000), ("have", 7000), ("will", 6000), ("can", 5000), ("like", 4000), ("do", 3500), ("need", 3000), ("want", 2500), ("would", 2000), ("know", 1800), ("think", 1600), ("love", 1500), ("had", 1200), ("said", 1000), ("did", 800), ("hope", 700), ("wish", 500), ("believe", 400), ("feel", 300), ("saw", 200)])),
        ("you", OrderedDict([("are", 7000), ("can", 5000), ("will", 4000), ("have", 3500), ("need", 3000), ("should", 2500), ("want", 2000), ("know", 1800), ("like", 1500), ("do", 1200), ("were", 1000), ("did", 800), ("would", 600), ("could", 500), ("must", 400), ("got", 300), ("make", 200), ("see", 100)])),
        ("we", OrderedDict([("are", 6000), ("have", 5000), ("will", 4000), ("can", 3500), ("need", 3000), ("should", 2500), ("would", 2000), ("were", 1500), ("do", 1000), ("did", 800), ("want", 600), ("know", 500), ("like", 400), ("got", 300), ("love", 200), ("had", 100)])),
        ("they", OrderedDict([("are", 5000), ("have", 4000), ("will", 3500), ("can", 3000), ("were", 2500), ("need", 2000), ("should", 1500), ("would", 1000), ("do", 800), ("did", 600), ("want", 500), ("know", 400), ("like", 300), ("said", 200), ("had", 100)])),
        ("he", OrderedDict([("is", 6000), ("was", 5000), ("has", 4000), ("had", 3500), ("will", 3000), ("can", 2500), ("would", 2000), ("said", 1500), ("does", 1000), ("did", 800), ("could", 600), ("should", 500), ("might", 400), ("never", 300), ("also", 200)])),
        ("she", OrderedDict([("is", 5000), ("was", 4500), ("has", 3500), ("had", 3000), ("will", 2500), ("can", 2000), ("said", 1500), ("would", 1000), ("does", 800), ("did", 600), ("could", 400), ("should", 300), ("also", 200), ("never", 100)])),
        ("it", OrderedDict([("is", 7000), ("was", 6000), ("has", 4000), ("had", 3000), ("will", 2500), ("can", 2000), ("would", 1500), ("could", 1000), ("should", 800), ("does", 600), ("did", 400), ("might", 300), ("seems", 200), ("looks", 100)])),
        ("the", OrderedDict([("first", 5000), ("same", 4000), ("other", 3500), ("only", 3000), ("best", 2500), ("new", 2000), ("most", 1500), ("next", 1200), ("last", 1000), ("main", 800), ("big", 600), ("entire", 400), ("whole", 300), ("real", 200)])),
        ("to", OrderedDict([("be", 7000), ("have", 5000), ("do", 4000), ("make", 3500), ("get", 3000), ("go", 2500), ("see", 2000), ("take", 1500), ("come", 1000), ("know", 800), ("say", 600), ("find", 500), ("give", 400), ("work", 300), ("help", 200)])),
        ("and", OrderedDict([("then", 5000), ("also", 3000), ("finally", 2000), ("after", 1500), ("more", 1000), ("now", 800), ("so", 600), ("still", 400), ("therefore", 300), ("thus", 200)])),
        ("for", OrderedDict([("the", 6000), ("a", 5000), ("your", 3000), ("my", 2500), ("example", 2000), ("sure", 1500), ("now", 1000), ("real", 800), ("good", 600), ("long", 400)])),
        ("in", OrderedDict([("the", 7000), ("a", 5000), ("order", 3000), ("fact", 2000), ("general", 1500), ("addition", 1000), ("particular", 800), ("short", 600), ("total", 400), ("case", 200)])),
        ("is", OrderedDict([("a", 6000), ("the", 5000), ("an", 3000), ("not", 2000), ("also", 1500), ("very", 1000), ("more", 800), ("still", 600), ("really", 400), ("quite", 200), ("just", 100)])),
        ("of", OrderedDict([("the", 7000), ("a", 5000), ("course", 2000), ("all", 1500), ("many", 1000), ("some", 800), ("most", 600), ("few", 400), ("these", 200)])),
        ("this", OrderedDict([("is", 7000), ("was", 5000), ("will", 3000), ("has", 2000), ("does", 1500), ("means", 1000), ("should", 800), ("can", 600), ("might", 400), ("could", 200), ("morning", 500), ("afternoon", 400), ("evening", 300), ("week", 500), ("year", 400), ("time", 500)])),
        ("that", OrderedDict([("is", 6000), ("was", 4000), ("will", 3000), ("can", 2000), ("has", 1500), ("does", 1000), ("should", 800), ("would", 600), ("could", 400), ("might", 200)])),
        ("my", OrderedDict([("name", 5000), ("friend", 3000), ("family", 2500), ("favorite", 2000), ("day", 1500), ("life", 1000), ("time", 800), ("love", 600), ("house", 400), ("car", 200), ("home", 100)])),
        ("your", OrderedDict([("name", 4000), ("email", 3000), ("phone", 2500), ("message", 2000), ("family", 1500), ("friend", 1000), ("time", 800), ("help", 600), ("support", 400), ("order", 200)])),
        ("have", OrderedDict([("a", 6000), ("the", 5000), ("been", 4000), ("to", 3000), ("some", 2000), ("all", 1500), ("no", 1000), ("your", 800), ("my", 600), ("many", 400), ("more", 200)])),
        ("not", OrderedDict([("have", 4000), ("be", 3500), ("do", 3000), ("know", 2000), ("see", 1500), ("want", 1000), ("need", 800), ("like", 600), ("only", 400), ("just", 200), ("really", 100)])),
        ("be", OrderedDict([("the", 5000), ("a", 4000), ("able", 3000), ("sure", 2500), ("careful", 2000), ("happy", 1500), ("ready", 1000), ("good", 800), ("nice", 600), ("honest", 400)])),
        ("will", OrderedDict([("be", 6000), ("have", 4000), ("do", 3000), ("work", 2000), ("go", 1500), ("take", 1000), ("make", 800), ("need", 600), ("help", 400), ("get", 200)])),
        ("can", OrderedDict([("be", 5000), ("have", 3000), ("do", 2500), ("make", 2000), ("get", 1500), ("see", 1000), ("take", 800), ("help", 600), ("go", 400), ("use", 200)])),
        ("do", OrderedDict([("not", 5000), ("the", 3000), ("a", 2000), ("your", 1500), ("my", 1000), ("you", 800), ("we", 600), ("they", 400), ("it", 200), ("what", 100)])),
        ("what", OrderedDict([("is", 5000), ("was", 3000), ("are", 2500), ("do", 2000), ("does", 1500), ("will", 1000), ("can", 800), ("about", 600), ("happened", 400), ("if", 200)])),
        ("how", OrderedDict([("are", 4000), ("is", 3000), ("do", 2500), ("does", 2000), ("can", 1500), ("to", 1000), ("much", 800), ("many", 600), ("long", 400), ("about", 200)])),
        ("where", OrderedDict([("is", 4000), ("are", 3000), ("do", 2000), ("does", 1500), ("can", 1000), ("will", 800), ("did", 600), ("have", 400)])),
        ("when", OrderedDict([("is", 4000), ("are", 3000), ("do", 2000), ("does", 1500), ("will", 1000), ("can", 800), ("did", 600)])),
        ("who", OrderedDict([("is", 4000), ("are", 3000), ("was", 2000), ("will", 1500), ("does", 1000), ("can", 600), ("has", 400)])),
        ("why", OrderedDict([("is", 3000), ("are", 2500), ("do", 2000), ("does", 1500), ("did", 1000), ("don't", 800)])),
        ("there", OrderedDict([("is", 6000), ("are", 5000), ("was", 3000), ("were", 2000), ("has", 1500), ("have", 1000), ("will", 800), ("can", 600)])),
        ("here", OrderedDict([("is", 5000), ("are", 4000), ("was", 2000), ("comes", 1500), ("you", 1000)])),
        ("please", OrderedDict([("let", 3000), ("send", 2000), ("call", 1500), ("check", 1000), ("see", 800), ("find", 600), ("contact", 400), ("note", 200)])),
        ("thank", OrderedDict([("you", 8000)])),
        ("thanks", OrderedDict([("for", 4000), ("to", 2000)])),
        ("good", OrderedDict([("morning", 5000), ("afternoon", 3000), ("evening", 2000), ("day", 1500), ("luck", 1000), ("job", 800), ("idea", 600), ("time", 400), ("work", 200)])),
        ("look", OrderedDict([("at", 3000), ("for", 2000), ("forward", 1500), ("like", 1000)])),
        ("well", OrderedDict([("done", 3000), ("said", 2000), ("known", 1500), ("be", 1000)])),
        ("just", OrderedDict([("like", 2000), ("a", 1500), ("the", 1000), ("wanted", 800), ("got", 600), ("said", 400), ("had", 200)])),
        ("like", OrderedDict([("to", 3000), ("a", 2000), ("the", 1500), ("it", 1000), ("this", 800), ("that", 600)])),
        ("about", OrderedDict([("the", 3000), ("a", 2000), ("your", 1000), ("this", 800), ("it", 600), ("how", 400)])),
        ("all", OrderedDict([("the", 4000), ("of", 3000), ("my", 2000), ("your", 1500), ("this", 1000), ("that", 800)])),
        ("some", OrderedDict([("of", 3000), ("people", 2000), ("time", 1500), ("things", 1000)])),
        ("more", OrderedDict([("than", 3000), ("and", 2000), ("people", 1000), ("time", 800), ("important", 600)])),
        ("very", OrderedDict([("good", 3000), ("nice", 2000), ("important", 1500), ("much", 1000), ("well", 800), ("happy", 600)])),
        ("too", OrderedDict([("much", 2000), ("many", 1500), ("late", 1000), ("early", 500)])),
        ("also", OrderedDict([("have", 2000), ("known", 1500), ("called", 1000), ("has", 800), ("can", 600), ("need", 400)])),
        ("should", OrderedDict([("be", 4000), ("have", 2500), ("not", 1500), ("also", 1000)])),
        ("would", OrderedDict([("be", 3000), ("have", 2000), ("like", 1500), ("love", 1000), ("never", 500)])),
        ("could", OrderedDict([("be", 3000), ("have", 2000), ("not", 1000), ("see", 500)])),
        ("need", OrderedDict([("to", 3000), ("a", 2000), ("some", 1000), ("help", 500)])),
        ("want", OrderedDict([("to", 4000), ("a", 2000), ("some", 1000)])),
        ("know", OrderedDict([("that", 2000), ("what", 1500), ("how", 1000), ("if", 500)])),
        ("think", OrderedDict([("about", 2000), ("that", 1000), ("so", 500)])),
        ("love", OrderedDict([("you", 3000), ("it", 1500), ("to", 1000), ("the", 500)])),
        ("make", OrderedDict([("sure", 2000), ("a", 1500), ("the", 1000)])),
        ("take", OrderedDict([("a", 2000), ("the", 1000), ("care", 500)])),
        ("get", OrderedDict([("a", 2000), ("the", 1500), ("to", 1000), ("some", 500)])),
        ("find", OrderedDict([("a", 1500), ("the", 1000), ("out", 500)])),
        ("let", OrderedDict([("me", 3000), ("us", 2000), ("go", 500)])),
        ("tell", OrderedDict([("me", 2000), ("you", 1000), ("them", 500)])),
        ("see", OrderedDict([("you", 2500), ("the", 1500), ("if", 500)])),
        ("call", OrderedDict([("me", 2000), ("you", 1000), ("the", 500)])),
        ("send", OrderedDict([("me", 2000), ("you", 1500), ("a", 500)])),
        ("come", OrderedDict([("to", 2000), ("and", 1000), ("here", 500)])),
        ("go", OrderedDict([("to", 3000), ("a", 1000), ("and", 500)])),
        ("work", OrderedDict([("on", 1500), ("with", 1000), ("for", 500)])),
        ("say", OrderedDict([("that", 1000), ("something", 500)])),
        ("mean", OrderedDict([("that", 1000), ("it", 500)])),
        ("right", OrderedDict([("now", 2000), ("here", 1000)])),
        ("never", OrderedDict([("been", 1500), ("seen", 1000), ("had", 500), ("thought", 500)])),
        ("always", OrderedDict([("been", 1500), ("have", 1000), ("be", 500)])),
        ("still", OrderedDict([("have", 1000), ("be", 500), ("need", 500)])),
        ("even", OrderedDict([("though", 1500), ("if", 1000), ("more", 500)])),
        ("much", OrderedDict([("more", 1500), ("better", 1000)])),
        ("many", OrderedDict([("people", 2000), ("things", 1500), ("years", 1000)])),
        ("each", OrderedDict([("other", 2000), ("one", 1000)])),
        ("another", OrderedDict([("one", 1000), ("day", 500)])),
        ("any", OrderedDict([("other", 1000), ("more", 500)])),
        ("nothing", OrderedDict([("but", 500), ("else", 500)])),
        ("something", OrderedDict([("else", 1000), ("like", 500)])),
        ("maybe", OrderedDict([("we", 1000), ("you", 500)])),
        ("however", OrderedDict([(",", 1500)])),
        ("ok", OrderedDict([(",", 1000), (".", 500)])),
        ("sure", OrderedDict([(",", 1000), (".", 500)])),
        ("yes", OrderedDict([(",", 1000), ("!", 500)])),
        ("no", OrderedDict([(",", 1000), ("one", 500)])),
        ("sorry", OrderedDict([(",", 1000), ("about", 500), ("for", 500)])),
        ("hello", OrderedDict([(",", 1500), ("!", 500)])),
        ("hi", OrderedDict([("there", 1000), (",", 500)])),
        ("hey", OrderedDict([("there", 500)])),
        ("thanks", OrderedDict([("for", 2000), ("!", 500)])),
        ("great", OrderedDict([(",", 1000), ("!", 500)])),
        ("nice", OrderedDict([("to", 1000), (",", 500)])),
        ("happy", OrderedDict([("to", 1000), ("birthday", 500)])),
        ("welcome", OrderedDict([("to", 2000)])),
        ("well", OrderedDict([(",", 2000)])),
        ("so", OrderedDict([("that", 1500), ("the", 1000), ("much", 500)])),
        ("but", OrderedDict([("the", 2000), ("it", 1000), ("not", 500)])),
        ("if", OrderedDict([("you", 3000), ("the", 2000), ("we", 1000)])),
        ("or", OrderedDict([("the", 1500), ("not", 500)])),
        ("because", OrderedDict([("the", 1000), ("of", 500), ("i", 500)])),
        ("as", OrderedDict([("well", 1500), ("much", 500), ("soon", 500)])),
        ("while", OrderedDict([("the", 1000), ("you", 500)])),
        ("during", OrderedDict([("the", 1000)])),
        ("before", OrderedDict([("the", 1000), ("you", 500)])),
        ("after", OrderedDict([("the", 1000), ("a", 500), ("all", 500)])),
        ("since", OrderedDict([("the", 500), ("you", 500)])),
        ("until", OrderedDict([("the", 500), ("you", 500)])),
        ("by", OrderedDict([("the", 2000), ("a", 500)])),
        ("with", OrderedDict([("the", 2000), ("a", 1000), ("your", 500)])),
        ("without", OrderedDict([("a", 1000), ("the", 500)])),
        ("over", OrderedDict([("the", 1500), ("a", 500)])),
        ("under", OrderedDict([("the", 1000)])),
        ("through", OrderedDict([("the", 1000)])),
        ("between", OrderedDict([("the", 500)])),
        ("around", OrderedDict([("the", 1000)])),
        ("down", OrderedDict([("the", 500)])),
        ("up", OrderedDict([("to", 1000), ("the", 500)])),
        ("out", OrderedDict([("of", 1500), ("the", 500)])),
        ("off", OrderedDict([("the", 500)])),
        ("am", OrderedDict([("a", 1500), ("going", 1000), ("not", 500)])),
        ("are", OrderedDict([("you", 2000), ("the", 1500), ("a", 500)])),
        ("was", OrderedDict([("a", 2000), ("the", 1500), ("not", 500)])),
        ("were", OrderedDict([("the", 1000), ("not", 500)])),
        ("had", OrderedDict([("a", 1500), ("the", 1000), ("been", 500)])),
        ("been", OrderedDict([("a", 1000), ("the", 500)])),
        ("did", OrderedDict([("not", 2000), ("the", 500)])),
        ("said", OrderedDict([("that", 1000), ("the", 500)])),
        ("made", OrderedDict([("a", 500), ("the", 500)])),
        ("going", OrderedDict([("to", 3000)])),
        ("trying", OrderedDict([("to", 1000)])),
        ("morning", OrderedDict([(",", 500)])),
        ("next", OrderedDict([("week", 1000), ("time", 500), ("year", 500)])),
        ("last", OrderedDict([("week", 1000), ("year", 500), ("night", 500)])),
        ("first", OrderedDict([("time", 1000), ("day", 500)])),
        ("new", OrderedDict([("year", 1000), ("one", 500)])),
        ("same", OrderedDict([("time", 1000), ("day", 500)])),
        ("big", OrderedDict([("deal", 500)])),
        ("long", OrderedDict([("time", 1000), ("day", 500)])),
        ("lot", OrderedDict([("of", 2000)])),
        ("lots", OrderedDict([("of", 1000)])),
        ("plenty", OrderedDict([("of", 500)])),
        ("kind", OrderedDict([("of", 1000)])),
        ("part", OrderedDict([("of", 1000)])),
        ("number", OrderedDict([("of", 500)])),
        ("way", OrderedDict([("to", 500)])),
        ("time", OrderedDict([("for", 500), ("to", 500)])),
        ("thing", OrderedDict([("is", 500), ("to", 500)])),
        ("things", OrderedDict([("to", 500)])),
        ("people", OrderedDict([("who", 500)])),
        ("today", OrderedDict([(",", 500), ("!", 500)])),
        ("tomorrow", OrderedDict([(",", 500), ("!", 500)])),
    ])


def main():
    script_dir = os.path.dirname(os.path.abspath(__file__))
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
        from glob import glob
        candidates = glob("**/corpus.json", recursive=True)
        if candidates:
            path = candidates[0]
        else:
            path = "corpus.json"

    print(f"Reading corpus from: {path}")

    with open(path, "r", encoding="utf-8") as f:
        corpus = json.load(f)

    print("\n=== Generating English corpus with wordfreq ===")
    en_unigrams = wordfreq_to_unigrams('en', 10000)
    en_bigrams = get_en_bigrams()
    
    # Add missing bigram followers to unigrams
    missing_en = []
    for head, followers in en_bigrams.items():
        for word in followers:
            if word not in (",", ".", "!", "?"):
                if word not in en_unigrams and word.lower() not in en_unigrams:
                    # Assign frequency based on wordfreq or default
                    z = zipf_frequency(word, 'en')
                    if z > 0:
                        max_z = max(zipf_frequency(w, 'en') for w in list(en_unigrams.keys())[:10]) if en_unigrams else 7.73
                        freq = max(5, int(10000 * 10 ** (z - max_z)))
                    else:
                        freq = 500
                    missing_en.append(f"'{word}' (follower of '{head}', freq={freq})")
                    en_unigrams[word] = freq

    if missing_en:
        print(f"✓ Added {len(missing_en)} missing bigram followers to EN unigrams")
    else:
        print("✓ All bigram followers already in EN unigrams")

    corpus["en"] = {
        "unigrams": dict(en_unigrams),
        "bigrams": {h: dict(f) for h, f in en_bigrams.items()}
    }
    print(f"  EN unigrams: {len(en_unigrams)}")
    print(f"  EN bigram heads: {len(en_bigrams)}")

    print("\n=== Generating Spanish corpus with wordfreq ===")
    es_unigrams = wordfreq_to_unigrams('es', 10000)
    es_bigrams = get_es_bigrams()
    
    # Add missing bigram followers to unigrams
    missing_es = []
    for head, followers in es_bigrams.items():
        for word in followers:
            if word not in (",", ".", "!", "?"):
                if word not in es_unigrams and word.lower() not in es_unigrams:
                    z = zipf_frequency(word, 'es')
                    if z > 0:
                        max_z = max(zipf_frequency(w, 'es') for w in list(es_unigrams.keys())[:10]) if es_unigrams else 7.81
                        freq = max(5, int(10000 * 10 ** (z - max_z)))
                    else:
                        freq = 500
                    missing_es.append(f"'{word}' (follower of '{head}', freq={freq})")
                    es_unigrams[word] = freq

    if missing_es:
        print(f"✓ Added {len(missing_es)} missing bigram followers to ES unigrams")
    else:
        print("✓ All bigram followers already in ES unigrams")

    corpus["es"] = {
        "unigrams": dict(es_unigrams),
        "bigrams": {h: dict(f) for h, f in es_bigrams.items()}
    }
    print(f"  ES unigrams: {len(es_unigrams)}")
    print(f"  ES bigram heads: {len(es_bigrams)}")

    # Write the corpus
    with open(path, "w", encoding="utf-8") as f:
        json.dump(corpus, f, indent=2, ensure_ascii=False)

    # Stats
    en_uni = len(corpus["en"]["unigrams"])
    en_bi = len(corpus["en"]["bigrams"])
    en_pairs = sum(len(v) for v in corpus["en"]["bigrams"].values())
    es_uni = len(corpus["es"]["unigrams"])
    es_bi = len(corpus["es"]["bigrams"])
    es_pairs = sum(len(v) for v in corpus["es"]["bigrams"].values())
    size = os.path.getsize(path)

    print(f"\n{'='*50}")
    print("✅ Corpus updated!")
    print(f"{'='*50}")
    print(f"  EN unigrams: {en_uni:,}")
    print(f"  EN bigram heads: {en_bi}")
    print(f"  EN total bigram pairs: {en_pairs}")
    print(f"  ES unigrams: {es_uni:,}")
    print(f"  ES bigram heads: {es_bi}")
    print(f"  ES total bigram pairs: {es_pairs}")
    print(f"  File size: {size/1024:.0f} KB")
    print(f"{'='*50}")

    # Verify Zipfian distribution
    print("\n=== Frequency Distribution Check ===")
    for lang, label in [("en", "EN"), ("es", "ES")]:
        freqs = sorted(corpus[lang]["unigrams"].values(), reverse=True)
        print(f"\n{label}:")
        print(f"  Top 10: {freqs[:10]}")
        print(f"  Words with freq 10000 (top): {sum(1 for f in freqs if f == 10000)}")
        print(f"  Words with freq 1 (bottom): {sum(1 for f in freqs if f == 1)}")
        print(f"  Mean freq: {sum(freqs)/len(freqs):.1f}")
        print(f"  Median freq: {freqs[len(freqs)//2]}")


if __name__ == "__main__":
    main()
