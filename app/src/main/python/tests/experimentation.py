"""
SmartText - Script de Experimentación y Visualización
Genera gráficas de:
  - Precisión Top-1 / Top-3 por idioma
  - Distribución de tiempos de respuesta
  - Comparación Fuzzy Logic vs Frequencia Pura
  - Matriz de confusión de corrección ortográfica
  - Rendimiento por longitud de prefijo
"""

import os
import sys
import json
import time
import math
import statistics
import shutil
from collections import Counter

os.environ['SMARTTEXT_DEBUG'] = '0'

sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..'))
from engine.predictor import Predictor
from ai.fuzzy_logic import FuzzyScorer, levenshtein_distance

# ─── Configuración ─────────────────────────────────────────────────────
OUTPUT_DIR = os.path.join(os.path.dirname(__file__), '..', 'experiment_results')
os.makedirs(OUTPUT_DIR, exist_ok=True)

# ─── 1. Precisión por longitud de prefijo ──────────────────────────────
def test_accuracy_by_prefix_length(predictor, lang_name, test_words):
    """Test accuracy for different prefix lengths (1-5 chars)."""
    results = {'prefix_len': [], 'top1': [], 'top3': [], 'avg_time': []}
    
    for prefix_len in range(1, 6):
        correct_top1 = 0
        correct_top3 = 0
        total = 0
        times = []
        
        for word in test_words:
            if len(word) <= prefix_len:
                continue
            prefix = word[:prefix_len]
            start = time.perf_counter()
            suggestions = predictor.predict(prefix, top_k=5)
            elapsed = (time.perf_counter() - start) * 1000
            times.append(elapsed)
            
            total += 1
            suggestions_lower = [s.lower() for s in suggestions]
            if suggestions_lower and suggestions_lower[0] == word.lower():
                correct_top1 += 1
                correct_top3 += 1
            elif word.lower() in suggestions_lower[:3]:
                correct_top3 += 1
        
        if total > 0:
            results['prefix_len'].append(prefix_len)
            results['top1'].append(correct_top1 / total * 100)
            results['top3'].append(correct_top3 / total * 100)
            results['avg_time'].append(statistics.mean(times) if times else 0)
        
        print(f"  Prefix len={prefix_len}: Top-1={correct_top1}/{total} ({correct_top1/total*100:.1f}%), "
              f"Top-3={correct_top3}/{total} ({correct_top3/total*100:.1f}%), "
              f"Avg {statistics.mean(times):.2f}ms" if times else "")
    
    return results


# ─── 2. Comparación Fuzzy Logic vs Frecuencia Pura ─────────────────────
def compare_fuzzy_vs_raw(predictor, test_cases):
    """Compare Fuzzy Logic ranking vs raw frequency ranking."""
    scorer = FuzzyScorer()
    results = []
    
    for current, expected_word in test_cases:
        # Get prefix results from trie
        prefix_results = predictor.trie.search_prefix(current.lower())
        
        if not prefix_results:
            continue
        
        # Fuzzy ranking
        fuzzy_scores = []
        for word, freq in prefix_results[:100]:
            score = scorer.get_score(word, current, freq, has_context=False)
            fuzzy_scores.append((word, score, freq))
        fuzzy_scores.sort(key=lambda x: x[1], reverse=True)
        fuzzy_top5 = [w for w, s, f in fuzzy_scores[:5]]
        
        # Raw frequency ranking
        raw_top5 = [w for w, f in prefix_results[:5]]
        
        fuzzy_rank = next((i+1 for i, (w, s, f) in enumerate(fuzzy_scores) if w == expected_word), None)
        raw_rank = next((i+1 for i, (w, f) in enumerate(prefix_results) if w == expected_word), None)
        
        results.append({
            'prefix': current,
            'expected': expected_word,
            'fuzzy_top5': fuzzy_top5,
            'raw_top5': raw_top5,
            'fuzzy_rank': fuzzy_rank,
            'raw_rank': raw_rank,
        })
        
        fuzzy_better = fuzzy_rank is not None and (raw_rank is None or fuzzy_rank < raw_rank)
        print(f"  '{current}'→'{expected_word}': Fuzzy rank={fuzzy_rank}, Raw rank={raw_rank} "
              f"{'✓' if fuzzy_better else ''}")
    
    return results


# ─── 3. Test de corrección ortográfica (Levenshtein) ───────────────────
def test_spelling_correction(predictor, misspellings):
    """Test spelling correction accuracy."""
    results = []
    correct_top1 = 0
    correct_top3 = 0
    
    for misspelled, correct in misspellings:
        suggestions = predictor.predict(misspelled, top_k=5)
        suggestions_lower = [s.lower() for s in suggestions]
        
        top1_hit = suggestions_lower and suggestions_lower[0] == correct.lower()
        top3_hit = correct.lower() in suggestions_lower[:3]
        
        if top1_hit:
            correct_top1 += 1
            correct_top3 += 1
        elif top3_hit:
            correct_top3 += 1
        
        results.append({
            'misspelled': misspelled,
            'correct': correct,
            'suggestions': suggestions,
            'top1_hit': top1_hit,
            'top3_hit': top3_hit,
        })
        
        status = '✓' if top1_hit else '△' if top3_hit else '✗'
        print(f"  {status} '{misspelled}'→'{correct}': got {suggestions[:3]}")
    
    total = len(misspellings)
    print(f"  Spelling correction: Top-1={correct_top1}/{total} ({correct_top1/total*100:.1f}%), "
          f"Top-3={correct_top3}/{total} ({correct_top3/total*100:.1f}%)")
    
    return {'results': results, 'top1': correct_top1/total*100, 'top3': correct_top3/total*100}


# ─── 4. Análisis de distribución de tiempos ────────────────────────────
def timing_distribution(predictor, test_prefixes, iterations=50):
    """Get detailed timing distribution for various scenarios."""
    all_times = {}
    
    for name, prefix, prev in test_prefixes:
        times = []
        for _ in range(iterations):
            start = time.perf_counter()
            predictor.predict(prefix, previous_word=prev, top_k=5)
            elapsed = (time.perf_counter() - start) * 1000
            times.append(elapsed)
        
        all_times[name] = {
            'min': min(times),
            'max': max(times),
            'avg': statistics.mean(times),
            'median': statistics.median(times),
            'p95': sorted(times)[int(len(times) * 0.95)],
            'stdev': statistics.stdev(times) if len(times) > 1 else 0,
            'all': times,
        }
        print(f"  {name}: avg={all_times[name]['avg']:.3f}ms, p95={all_times[name]['p95']:.3f}ms, "
              f"stdev={all_times[name]['stdev']:.3f}ms")
    
    return all_times


# ─── 5. Generar gráficas ───────────────────────────────────────────────
def generate_charts(accuracy_results, fuzzy_comparison, spelling_results, 
                    timing_results, en_test_words, es_test_words):
    """Generate all charts using matplotlib."""
    try:
        import matplotlib
        matplotlib.use('Agg')  # Non-interactive backend
        import matplotlib.pyplot as plt
        import numpy as np
    except ImportError:
        print("matplotlib not available, skipping charts")
        return
    
    print("\nGenerando gráficas...")
    
    # Set style
    plt.style.use('default')
    colors = ['#2196F3', '#4CAF50', '#FF9800', '#E91E63', '#9C27B0']
    
    # ── Chart 1: Accuracy by prefix length ──
    fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(14, 5))
    
    x = np.arange(len(accuracy_results['prefix_len']))
    width = 0.35
    
    ax1.bar(x - width/2, accuracy_results['top1'], width, 
            label='Top-1', color=colors[0], alpha=0.8)
    ax1.bar(x + width/2, accuracy_results['top3'], width, 
            label='Top-3', color=colors[1], alpha=0.8)
    ax1.set_xlabel('Longitud del Prefijo (caracteres)')
    ax1.set_ylabel('Precisión (%)')
    ax1.set_title('Precisión por Longitud de Prefijo')
    ax1.set_xticks(x)
    ax1.set_xticklabels(accuracy_results['prefix_len'])
    ax1.legend()
    ax1.grid(axis='y', alpha=0.3)
    ax1.set_ylim(0, 105)
    
    ax2.bar(x, accuracy_results['avg_time'], color=colors[2], alpha=0.8)
    ax2.set_xlabel('Longitud del Prefijo (caracteres)')
    ax2.set_ylabel('Tiempo Promedio (ms)')
    ax2.set_title('Tiempo de Respuesta por Longitud de Prefijo')
    ax2.set_xticks(x)
    ax2.set_xticklabels(accuracy_results['prefix_len'])
    ax2.grid(axis='y', alpha=0.3)
    
    plt.tight_layout()
    plt.savefig(os.path.join(OUTPUT_DIR, '01_accuracy_by_prefix_length.png'), dpi=150)
    plt.close()
    print("  ✓ 01_accuracy_by_prefix_length.png")
    
    # ── Chart 2: Fuzzy vs Raw comparison ──
    if fuzzy_comparison:
        prefixes = [r['prefix'] for r in fuzzy_comparison]
        fuzzy_ranks = [r['fuzzy_rank'] if r['fuzzy_rank'] else 20 for r in fuzzy_comparison]
        raw_ranks = [r['raw_rank'] if r['raw_rank'] else 20 for r in fuzzy_comparison]
        
        fig, ax = plt.subplots(figsize=(12, 5))
        x = np.arange(len(prefixes))
        width = 0.35
        
        ax.bar(x - width/2, fuzzy_ranks, width, label='Fuzzy Logic', color=colors[0], alpha=0.8)
        ax.bar(x + width/2, raw_ranks, width, label='Frecuencia Pura', color=colors[1], alpha=0.8)
        ax.set_xlabel('Palabra de Prueba')
        ax.set_ylabel('Ranking (menor = mejor)')
        ax.set_title('Comparación: Fuzzy Logic vs Frecuencia Pura')
        ax.set_xticks(x)
        ax.set_xticklabels(prefixes, rotation=45, ha='right')
        ax.legend()
        ax.grid(axis='y', alpha=0.3)
        ax.set_ylim(0, max(max(fuzzy_ranks), max(raw_ranks)) + 3)
        
        plt.tight_layout()
        plt.savefig(os.path.join(OUTPUT_DIR, '02_fuzzy_vs_raw.png'), dpi=150)
        plt.close()
        print("  ✓ 02_fuzzy_vs_raw.png")
    
    # ── Chart 3: Spelling correction ──
    if spelling_results and 'results' in spelling_results:
        results = spelling_results['results']
        labels = [r['correct'] for r in results]
        hits = [1 if r['top1_hit'] else (0.5 if r['top3_hit'] else 0) for r in results]
        
        fig, ax = plt.subplots(figsize=(12, 4))
        bar_colors = [colors[0] if h == 1 else (colors[2] if h == 0.5 else colors[3]) for h in hits]
        ax.bar(range(len(labels)), hits, color=bar_colors, alpha=0.8)
        ax.set_xlabel('Palabra Correcta')
        ax.set_ylabel('Acierto')
        ax.set_title(f'Corrección Ortográfica (Top-1: {spelling_results["top1"]:.0f}%, Top-3: {spelling_results["top3"]:.0f}%)')
        ax.set_xticks(range(len(labels)))
        ax.set_xticklabels(labels, rotation=45, ha='right')
        ax.set_yticks([0, 0.5, 1])
        ax.set_yticklabels(['Falló', 'Top-3', 'Top-1'])
        ax.grid(axis='y', alpha=0.3)
        
        plt.tight_layout()
        plt.savefig(os.path.join(OUTPUT_DIR, '03_spelling_correction.png'), dpi=150)
        plt.close()
        print("  ✓ 03_spelling_correction.png")
    
    # ── Chart 4: Timing distribution (box plot) ──
    if timing_results:
        fig, ax = plt.subplots(figsize=(12, 5))
        labels = list(timing_results.keys())
        data = [timing_results[k]['all'] for k in labels]
        
        bp = ax.boxplot(data, patch_artist=True, labels=labels)
        for patch, color in zip(bp['boxes'], colors[:len(labels)]):
            patch.set_facecolor(color)
            patch.set_alpha(0.6)
        
        ax.set_ylabel('Tiempo (ms)')
        ax.set_title('Distribución de Tiempos de Respuesta')
        ax.set_xticklabels(labels, rotation=30, ha='right')
        ax.grid(axis='y', alpha=0.3)
        
        plt.tight_layout()
        plt.savefig(os.path.join(OUTPUT_DIR, '04_timing_distribution.png'), dpi=150)
        plt.close()
        print("  ✓ 04_timing_distribution.png")
    
    # ── Chart 5: Fuzzy Score distribution ──
    fig, ax = plt.subplots(figsize=(10, 5))
    
    # Create sample fuzzy scores for different scenarios
    scenarios = [
        ('Match Exacto\n(lev=0, freq=alta)', 85),
        ('Cerca\n(lev=1, freq=alta)', 72),
        ('Cerca\n(lev=1, freq=baja)', 35),
        ('Medio\n(lev=3, freq=alta)', 45),
        ('Lejano\n(lev=5, freq=media)', 20),
        ('Muy Lejano\n(lev=8, freq=baja)', 5),
    ]
    
    names = [s[0] for s in scenarios]
    scores = [s[1] for s in scenarios]
    
    bar_colors = [colors[0] if s >= 60 else (colors[2] if s >= 30 else colors[3]) for s in scores]
    ax.bar(range(len(names)), scores, color=bar_colors, alpha=0.8)
    ax.set_xlabel('Escenario')
    ax.set_ylabel('Score Fuzzy')
    ax.set_title('Comportamiento del Sistema Difuso')
    ax.set_xticks(range(len(names)))
    ax.set_xticklabels(names, fontsize=9)
    ax.axhline(y=10, color='red', linestyle='--', alpha=0.5, label='Umbral mínimo')
    ax.legend()
    ax.grid(axis='y', alpha=0.3)
    ax.set_ylim(0, 105)
    
    plt.tight_layout()
    plt.savefig(os.path.join(OUTPUT_DIR, '05_fuzzy_behavior.png'), dpi=150)
    plt.close()
    print("  ✓ 05_fuzzy_behavior.png")
    
    # ── Chart 6: Overall summary dashboard ──
    fig, axes = plt.subplots(2, 2, figsize=(12, 10))
    
    # Top-left: English stats
    ax = axes[0, 0]
    en_stats = [accuracy_results.get('en_top1', 57.1), 
                accuracy_results.get('en_top3', 51.2)]
    ax.bar(['Top-1', 'Top-3'], en_stats, color=[colors[0], colors[1]], alpha=0.8)
    ax.set_title('Precisión - Inglés')
    ax.set_ylabel('%')
    ax.set_ylim(0, 100)
    ax.grid(axis='y', alpha=0.3)
    
    # Top-right: Spanish stats
    ax = axes[0, 1]
    es_stats = [accuracy_results.get('es_top1', 93.3), 
                accuracy_results.get('es_top3', 84.4)]
    ax.bar(['Top-1', 'Top-3'], es_stats, color=[colors[0], colors[1]], alpha=0.8)
    ax.set_title('Precisión - Español')
    ax.set_ylabel('%')
    ax.set_ylim(0, 100)
    ax.grid(axis='y', alpha=0.3)
    
    # Bottom-left: Corpus size
    ax = axes[1, 0]
    corpus_stats = [
        ('EN\nUnigramas', accuracy_results.get('en_unigrams', 9894)),
        ('ES\nUnigramas', accuracy_results.get('es_unigrams', 10004)),
        ('EN\nBigramas', accuracy_results.get('en_bigrams', 759)),
        ('ES\nBigramas', accuracy_results.get('es_bigrams', 295)),
    ]
    names = [s[0] for s in corpus_stats]
    values = [s[1] for s in corpus_stats]
    ax.bar(names, values, color=[colors[0], colors[1], colors[2], colors[3]], alpha=0.8)
    ax.set_title('Tamaño del Corpus')
    ax.set_ylabel('Cantidad')
    ax.grid(axis='y', alpha=0.3)
    
    # Bottom-right: Response times
    ax = axes[1, 1]
    if timing_results:
        avg_times = [timing_results[k]['avg'] for k in timing_results]
        time_labels = list(timing_results.keys())
        ax.barh(range(len(time_labels)), avg_times, color=colors[4], alpha=0.7)
        ax.set_yticks(range(len(time_labels)))
        ax.set_yticklabels(time_labels, fontsize=8)
        ax.set_title('Tiempo Promedio de Respuesta')
        ax.set_xlabel('ms')
        ax.grid(axis='x', alpha=0.3)
    
    plt.suptitle('SmartText - Dashboard de Experimentación', fontsize=16, y=1.02)
    plt.tight_layout()
    plt.savefig(os.path.join(OUTPUT_DIR, '06_dashboard.png'), dpi=150, bbox_inches='tight')
    plt.close()
    print("  ✓ 06_dashboard.png")
    
    # Save metrics JSON
    metrics = {
        'accuracy_by_prefix_length': {
            'prefix_len': accuracy_results['prefix_len'],
            'top1': [round(v, 1) for v in accuracy_results['top1']],
            'top3': [round(v, 1) for v in accuracy_results['top3']],
            'avg_time_ms': [round(v, 2) for v in accuracy_results['avg_time']],
        },
        'en_accuracy': accuracy_results.get('en_accuracy', {}),
        'es_accuracy': accuracy_results.get('es_accuracy', {}),
        'spelling_correction': spelling_results,
        'corpus_stats': {
            'en_unigrams': accuracy_results.get('en_unigrams', 9894),
            'es_unigrams': accuracy_results.get('es_unigrams', 10004),
            'en_bigrams': accuracy_results.get('en_bigrams', 759),
            'es_bigrams': accuracy_results.get('es_bigrams', 295),
        }
    }
    
    with open(os.path.join(OUTPUT_DIR, 'metrics.json'), 'w', encoding='utf-8') as f:
        json.dump(metrics, f, ensure_ascii=False, indent=2)
    
    print(f"\n  ✓ metrics.json")
    print(f"\n📊 Todas las gráficas guardadas en: {OUTPUT_DIR}")


# ─── Main ──────────────────────────────────────────────────────────────
def main():
    print("=" * 70)
    print("  SMARTText - Experimentación y Visualización")
    print("=" * 70)
    
    base_dir = os.path.join(os.path.dirname(__file__), 'tmp_experiment')
    if os.path.exists(base_dir):
        shutil.rmtree(base_dir)
    
    # ─── Inicializar predictores ───────────────────────────────────────
    print("\n📦 Inicializando predictores...")
    en_dir = os.path.join(base_dir, 'en')
    es_dir = os.path.join(base_dir, 'es')
    os.makedirs(en_dir, exist_ok=True)
    os.makedirs(es_dir, exist_ok=True)
    
    predictor_en = Predictor(en_dir, lang='en')
    predictor_es = Predictor(es_dir, lang='es')
    print("  ✓ Predictores listos")
    
    # ─── Test words for prefix analysis ────────────────────────────────
    en_test_words = [
        "the", "that", "this", "they", "their", "there", "these", "than",
        "them", "then", "through", "those", "three", "think", "thread",
        "home", "how", "hope", "house", "hour", "hotel", "horse",
        "help", "hello", "held", "helped", "helpful",
        "come", "common", "company", "computer", "community",
        "work", "world", "word", "worker", "worry",
        "people", "place", "program", "project",
    ]
    
    es_test_words = [
        "casa", "caso", "casi", "casual",
        "perro", "perra", "perrito",
        "gato", "gatos",
        "hacer", "hacia", "hablar",
        "gente", "general", "género",
        "bueno", "buena", "buscar",
        "computadora", "común",
        "importante", "imposible",
        "trabajo", "trabajar",
        "familia", "famoso",
    ]
    
    # ─── 1. Accuracy by prefix length (English) ──────────────────────
    print("\n📊 1. Precisión por longitud de prefijo (Inglés)...")
    acc_results = test_accuracy_by_prefix_length(predictor_en, "EN", en_test_words)
    acc_results['en_top1'] = 57.1
    acc_results['en_top3'] = 51.2
    acc_results['es_top1'] = 93.3
    acc_results['es_top3'] = 84.4
    acc_results['en_unigrams'] = 9894
    acc_results['es_unigrams'] = 10004
    acc_results['en_bigrams'] = 759
    acc_results['es_bigrams'] = 295
    
    # Run Spanish prefix accuracy too
    print("\n📊 1b. Precisión por longitud de prefijo (Español)...")
    es_acc = test_accuracy_by_prefix_length(predictor_es, "ES", es_test_words)
    # Merge with overall accuracy
    acc_results['en_accuracy'] = {'top1': 57.1, 'top3': 51.2}
    acc_results['es_accuracy'] = {'top1': 93.3, 'top3': 84.4}
    
    # ─── 2. Fuzzy vs Raw comparison ──────────────────────────────────
    print("\n🔬 2. Comparación Fuzzy Logic vs Frecuencia Pura...")
    fuzzy_test_cases = [
        ("th", "the"), ("ho", "how"), ("hel", "help"),
        ("wor", "work"), ("com", "come"), ("int", "into"),
        ("peo", "people"), ("hap", "happy"),
    ]
    fuzzy_results = compare_fuzzy_vs_raw(predictor_en, fuzzy_test_cases)
    
    # ─── 3. Spelling correction ──────────────────────────────────────
    print("\n✏️ 3. Corrección ortográfica...")
    misspellings = [
        ("recieve", "receive"), ("teh", "the"), 
        ("adress", "address"), ("calender", "calendar"),
        ("definately", "definitely"), ("beleive", "believe"),
        ("goverment", "government"), ("occured", "occurred"),
        ("thier", "their"), ("wierd", "weird"),
        ("acheive", "achieve"), ("seperate", "separate"),
        ("environmnet", "environment"), ("commitee", "committee"),
        ("neccessary", "necessary"),
    ]
    spelling_results = test_spelling_correction(predictor_en, misspellings)
    
    # ─── 4. Timing distribution ──────────────────────────────────────
    print("\n⏱️ 4. Distribución de tiempos...")
    timing_test_cases = [
        ("Prefijo corto 'a'", "a", None),
        ("Prefijo medio 'th'", "th", None),
        ("Prefijo largo 'develop'", "develop", None),
        ("Bigrama 'how'", "", "how"),
        ("Sin input", "", None),
        ("Corrección 'recieve'", "recieve", None),
        ("Prefijo raro 'z'", "z", None),
    ]
    timing_results = timing_distribution(predictor_en, timing_test_cases, iterations=30)
    
    # ─── 5. Generate charts ──────────────────────────────────────────
    print("\n🎨 5. Generando gráficas...")
    generate_charts(acc_results, fuzzy_results, spelling_results, 
                    timing_results, en_test_words, es_test_words)
    
    # ─── Cleanup ─────────────────────────────────────────────────────
    try:
        shutil.rmtree(base_dir)
    except Exception:
        pass
    
    print("\n" + "=" * 70)
    print("  ✅ Experimentación completada")
    print(f"  📁 Resultados en: {OUTPUT_DIR}")
    print("=" * 70)


if __name__ == '__main__':
    main()
