"""
SmartText - Suite de pruebas exhaustiva para el predictor.
Evalúa precisión, rendimiento, y corrección ortográfica en ambos idiomas.
"""

import os
import sys
os.environ['SMARTTEXT_DEBUG'] = '0'  # Desactivar debug para no contaminar mediciones

import json
import time
import shutil
import statistics

sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..'))
from engine.predictor import Predictor
from ai.fuzzy_logic import levenshtein_distance, FuzzyScorer


# ─── Escenarios de prueba ─────────────────────────────────────────────

# Escenarios en inglés: (current_word, previous_word, [palabras esperadas])
EN_SCENARIOS = [
    # Prefijo básico
    ("th",  None,   ["the", "they", "their", "this", "that", "them", "then", "think", "than", "there"]),
    ("ho",  None,   ["home", "how", "hot", "hope", "house", "hour", "hold", "horse", "hotel", "hole"]),
    ("hel", None,   ["help", "hello", "held", "hell", "helmet", "helen"]),
    ("com", None,   ["come", "common", "company", "computer", "community", "comment"]),
    ("wor", None,   ["work", "world", "word", "worry", "worse", "worth", "worker"]),
    ("peo", None,   ["people"]),
    ("int", None,   ["into", "interest", "international", "internet", "interview"]),

    # Palabras completas con bigrama contextual
    ("",    "how",  ["to", "many", "much", "long", "far"]),
    ("",    "i",    ["am", "have", "want", "need", "like"]),
    ("",    "you",  ["are", "have", "can", "will", "need"]),
    ("",    "to",   ["be", "have", "do", "get", "make"]),
    ("",    "the",  ["best", "same", "first", "new", "world"]),
    ("",    "we",   ["are", "have", "can", "will", "should"]),
    ("",    "they", ["are", "have", "can", "will", "would"]),

    # Contexto + prefijo (escribiendo después de una palabra)
    ("i",   "how",  ["important", "if", "idea"]),

    # Corrección de errores ortográficos (Levenshtein)
    ("recieve", None, ["receive"]),   # Error común: ie → ei
    ("teh",     None, ["the", "teach", "tech"]),  # Transposición
    ("adress",  None, ["address", "across"]),      # Error común
    ("calender", None, ["calendar", "calm"]),      # Error común
    ("definately", None, ["definitely", "define"]), # Error común
    ("beleive", None, ["believe", "bell"]),         # Error común
    ("goverment", None, ["government", "govern"]),   # Error común
    ("occured", None, ["occurred", "occur"]),        # Error común
    ("thier",   None, ["their", "thick"]),           # Error común
    ("wierd",   None, ["weird", "wild"]),            # Error común
    
    # Palabras cortas
    ("a",   None,   ["a", "about", "all", "an", "and", "as", "at"]),
    ("i",   None,   ["i", "if", "in", "into", "is", "it", "its"]),
    
    # Sin prefijo (vacío)
    ("",    None,   []),
]

# Escenarios en español
ES_SCENARIOS = [
    # Prefijo básico
    ("cas", None,  ["casa", "caso", "casi", "casual", "castigo", "casilla"]),
    ("perr", None, ["perro", "perra", "perrito"]),
    ("gato", None, ["gato"]),
    ("comput", None, ["computadora", "computador", "computación"]),

    # Bigramas contextuales
    ("",    "la",   ["casa", "vida", "mujer", "ciudad", "familia"]),
    ("",    "el",   ["amor", "sol", "día", "año", "mundo"]),
    ("",    "en",   ["el", "la", "los", "las", "un"]),
    ("",    "no",   ["es", "se", "hay", "puede", "tiene"]),
    ("",    "muy",  ["importante", "grande", "bueno", "bien", "fácil"]),
    ("",    "y",    ["el", "la", "los", "las", "su"]),
    
    # Corrección ortográfica español
    ("haser",  None, ["hacer", "has"]),
    ("bueno",  None, ["bueno", "buena"]),
    ("coche",  None, ["coche"]),
    ("jente",  None, ["gente"]),
    ("pais",   None, ["país", "pies"]),
]

# Escenarios de rendimiento (estrés)
STRESS_SCENARIOS = [
    ("",        None,    100),  # Sin input
    ("a",       None,    100),  # Prefijo muy corto (muchos resultados)
    ("th",      None,    100),  # Prefijo común
    ("z",       None,    100),  # Prefijo raro
    ("developm", None,   100),  # Prefijo largo
    ("",        "the",   100),  # Bigrama
    ("developm", "the",  100),  # Prefijo + contexto
]


def run_scenario(predictor, current, previous, expected, scenario_name):
    """Ejecuta un escenario y mide precisión."""
    start = time.perf_counter()
    result = predictor.predict(current, previous_word=previous, top_k=5)
    elapsed_ms = (time.perf_counter() - start) * 1000

    # Calcular precisión
    result_lower = [w.lower() for w in result]
    expected_lower = [w.lower() for w in expected]

    top1_hit = len(result_lower) > 0 and result_lower[0] in expected_lower
    top3_hits = sum(1 for w in result_lower[:3] if w in expected_lower)
    top5_hits = sum(1 for w in result_lower if w in expected_lower)

    top1_acc = 1.0 if top1_hit else 0.0
    top3_acc = min(top3_hits / min(3, len(expected)), 1.0) if expected else 1.0
    top5_acc = min(top5_hits / min(5, len(expected)), 1.0) if expected else 1.0

    return {
        'scenario': scenario_name,
        'current': current,
        'previous': previous,
        'expected': expected[:3],
        'got': result,
        'top1_acc': top1_acc,
        'top3_acc': top3_acc,
        'top5_acc': top5_acc,
        'elapsed_ms': round(elapsed_ms, 2),
    }


def run_stress(predictor, current, previous, iterations):
    """Mide rendimiento bajo estrés."""
    times = []
    for _ in range(iterations):
        start = time.perf_counter()
        predictor.predict(current, previous_word=previous, top_k=5)
        elapsed_ms = (time.perf_counter() - start) * 1000
        times.append(elapsed_ms)

    return {
        'scenario': f"stress('{current}', prev={previous})",
        'iterations': iterations,
        'min_ms': round(min(times), 2),
        'max_ms': round(max(times), 2),
        'avg_ms': round(statistics.mean(times), 2),
        'median_ms': round(statistics.median(times), 2),
        'p95_ms': round(sorted(times)[int(len(times) * 0.95)], 2),
    }


def evaluate_fuzzy_vs_raw():
    """Compara Fuzzy Logic vs ranking por frecuencia pura."""
    import random
    scorer = FuzzyScorer()
    
    # Simular casos
    test_cases = [
        # (target, input, freq, has_context)
        ("the", "th", 99899, False),     # Match casi exacto, freq alta
        ("they", "th", 50000, False),     # Match cercano, freq alta
        ("thy", "th", 100, False),        # Match exacto, freq baja
        ("home", "ho", 8000, False),      # Match parcial, freq media
        ("how", "ho", 15000, False),      # Match parcial, freq alta
        ("receive", "recieve", 5000, False),  # Error ortográfico
        ("the", "teh", 99899, False),     # Transposición
        ("i", "", 99999, True),           # Contexto alto (bigrama)
    ]
    
    results = []
    for target, inp, freq, ctx in test_cases:
        score = scorer.get_score(target, inp, freq, ctx)
        results.append({
            'target': target,
            'input': inp,
            'freq': freq,
            'has_context': ctx,
            'lev_distance': levenshtein_distance(inp.lower(), target.lower()),
            'fuzzy_score': round(score, 1),
        })
    return results


def main():
    print("=" * 70)
    print("  SMARTText - Suite de Pruebas Exhaustiva")
    print("=" * 70)

    # ─── Preparar directorios temporales ─────────────────────────────
    base_dir = os.path.join(os.path.dirname(__file__), 'tmp_user')
    if os.path.exists(base_dir):
        shutil.rmtree(base_dir)

    results = {
        'en': {'tests': 0, 'top1': 0, 'top3': 0, 'times': []},
        'es': {'tests': 0, 'top1': 0, 'top3': 0, 'times': []},
    }
    all_details = []

    # ═══════════════════════════════════════════════════════════════════
    #  INGLÉS
    # ═══════════════════════════════════════════════════════════════════
    print("\n" + "─" * 70)
    print("  📚 INGLÉS")
    print("─" * 70)

    user_dir_en = os.path.join(base_dir, 'en')
    os.makedirs(user_dir_en, exist_ok=True)
    predictor_en = Predictor(user_dir_en, lang='en')

    for current, previous, expected in EN_SCENARIOS:
        name = f"EN: prefix='{current}' ctx='{previous or '-'}'"
        r = run_scenario(predictor_en, current, previous, expected, name)
        all_details.append(r)
        results['en']['tests'] += 1
        results['en']['top1'] += r['top1_acc']
        results['en']['top3'] += r['top3_acc']
        results['en']['times'].append(r['elapsed_ms'])

        status = "✅" if r['top1_acc'] > 0 else "⚠️" if r['top3_acc'] > 0 else "❌"
        print(f"  {status} {name}")
        print(f"      Esperado: {expected[:3]} | Obtenido: {r['got'][:3]} | "
              f"Top-1: {r['top1_acc']:.0%} Top-3: {r['top3_acc']:.0%} | "
              f"{r['elapsed_ms']:.1f}ms")

    # ═══════════════════════════════════════════════════════════════════
    #  ESPAÑOL
    # ═══════════════════════════════════════════════════════════════════
    print("\n" + "─" * 70)
    print("  📚 ESPAÑOL")
    print("─" * 70)

    user_dir_es = os.path.join(base_dir, 'es')
    os.makedirs(user_dir_es, exist_ok=True)
    predictor_es = Predictor(user_dir_es, lang='es')

    for current, previous, expected in ES_SCENARIOS:
        name = f"ES: prefix='{current}' ctx='{previous or '-'}'"
        r = run_scenario(predictor_es, current, previous, expected, name)
        all_details.append(r)
        results['es']['tests'] += 1
        results['es']['top1'] += r['top1_acc']
        results['es']['top3'] += r['top3_acc']
        results['es']['times'].append(r['elapsed_ms'])

        status = "✅" if r['top1_acc'] > 0 else "⚠️" if r['top3_acc'] > 0 else "❌"
        print(f"  {status} {name}")
        print(f"      Esperado: {expected[:3]} | Obtenido: {r['got'][:3]} | "
              f"Top-1: {r['top1_acc']:.0%} Top-3: {r['top3_acc']:.0%} | "
              f"{r['elapsed_ms']:.1f}ms")

    # ═══════════════════════════════════════════════════════════════════
    #  PRUEBAS DE ESTRÉS (Rendimiento)
    # ═══════════════════════════════════════════════════════════════════
    print("\n" + "─" * 70)
    print("  ⚡ PRUEBAS DE ESTRÉS (Rendimiento)")
    print("─" * 70)

    stress_results = []
    for current, previous, iterations in STRESS_SCENARIOS:
        r = run_stress(predictor_en, current, previous, iterations)
        stress_results.append(r)
        print(f"  {r['scenario']}")
        print(f"      ({r['iterations']} iteraciones) "
              f"Media: {r['avg_ms']}ms | Mediana: {r['median_ms']}ms | "
              f"P95: {r['p95_ms']}ms | Min: {r['min_ms']}ms | Max: {r['max_ms']}ms")

    # ═══════════════════════════════════════════════════════════════════
    #  EVALUACIÓN DE FUZZY LOGIC
    # ═══════════════════════════════════════════════════════════════════
    print("\n" + "─" * 70)
    print("  🧠 EVALUACIÓN DE FUZZY LOGIC")
    print("─" * 70)

    fuzzy_results = evaluate_fuzzy_vs_raw()
    for r in fuzzy_results:
        print(f"  target='{r['target']}' input='{r['input']}' "
              f"lev={r['lev_distance']} freq={r['freq']} "
              f"ctx={r['has_context']} → score={r['fuzzy_score']}")

    # ═══════════════════════════════════════════════════════════════════
    #  RESUMEN GLOBAL
    # ═══════════════════════════════════════════════════════════════════
    print("\n" + "=" * 70)
    print("  📊 RESUMEN GLOBAL")
    print("=" * 70)

    for lang in ['en', 'es']:
        r = results[lang]
        total = r['tests']
        print(f"\n  {'INGLÉS' if lang == 'en' else 'ESPAÑOL'}:")
        print(f"    Tests: {total}")
        print(f"    Top-1 Accuracy: {r['top1']/total:.1%} ({int(r['top1'])}/{total})")
        print(f"    Top-3 Accuracy: {r['top3']/total:.1%} ({int(r['top3'])}/{total})")
        if r['times']:
            print(f"    Tiempo promedio: {statistics.mean(r['times']):.2f}ms")
            print(f"    Tiempo mediano:  {statistics.median(r['times']):.2f}ms")
            print(f"    Tiempo mínimo:   {min(r['times']):.2f}ms")
            print(f"    Tiempo máximo:   {max(r['times']):.2f}ms")

    # Tiempo de estrés
    print(f"\n  RENDIMIENTO (estrés):")
    for r in stress_results:
        print(f"    {r['scenario']}: avg={r['avg_ms']}ms p95={r['p95_ms']}ms")

    # Guardar resultados para referencia
    output_path = os.path.join(os.path.dirname(__file__), '..', 'test_results.json')
    with open(output_path, 'w', encoding='utf-8') as f:
        json.dump({
            'summary': {
                'en': results['en'],
                'es': results['es'],
            },
            'details': all_details,
            'stress': stress_results,
            'fuzzy_eval': fuzzy_results,
        }, f, ensure_ascii=False, indent=2)
    print(f"\n  Resultados guardados en: {output_path}")

    # ─── Limpieza ─────────────────────────────────────────────────────
    try:
        shutil.rmtree(base_dir)
    except Exception:
        pass

    print("\n" + "=" * 70)
    print("  ✅ PRUEBAS COMPLETADAS")
    print("=" * 70)


if __name__ == '__main__':
    main()
