"""
Sistema de Lógica Difusa para ranking de sugerencias de texto.

Variables de entrada:
  - Distancia Levenshtein (qué tan similar es el input a la palabra candidata)
  - Frecuencia de la palabra en el corpus
  - Contexto (si el bigrama predice esta palabra)

Variable de salida:
  - Score de sugerencia (0-100)

Reglas difusas:
  R1: IF lev IS baja AND freq IS alta  → excelente
  R2: IF lev IS baja AND ctx IS alto   → excelente
  R3: IF lev IS baja AND freq IS media → buena
  R4: IF lev IS media AND freq IS alta → buena
  R5: IF lev IS media AND freq IS media → aceptable
  R6: IF lev IS alta → malo
  R7: (default) → aceptable (basado en freq si nada más aplica)
"""


def levenshtein_distance(s1, s2):
    """Calcula la distancia de Levenshtein entre dos strings."""
    if len(s1) < len(s2):
        return levenshtein_distance(s2, s1)
    if len(s2) == 0:
        return len(s1)

    previous_row = range(len(s2) + 1)
    for i, c1 in enumerate(s1):
        current_row = [i + 1]
        for j, c2 in enumerate(s2):
            insertions = previous_row[j + 1] + 1
            deletions = current_row[j] + 1
            substitutions = previous_row[j] + (c1 != c2)
            current_row.append(min(insertions, deletions, substitutions))
        previous_row = current_row
    return previous_row[-1]


def fuzzify_frequency(freq):
    """
    Fuzzificación de la frecuencia de la palabra.
    Los rangos están ajustados para el corpus con frecuencias 1-10000.
    """
    # Baja (rara): 0 a 500
    if freq <= 100:
        baja = 1.0
    elif freq <= 500:
        baja = (500 - freq) / 400.0
    else:
        baja = 0.0

    # Media (moderada): 200 a 3000
    if freq <= 200:
        media = 0.0
    elif freq <= 1000:
        media = (freq - 200) / 800.0
    elif freq <= 2000:
        media = 1.0
    elif freq <= 3000:
        media = (3000 - freq) / 1000.0
    else:
        media = 0.0

    # Alta (común): 2000+
    if freq <= 2000:
        alta = 0.0
    elif freq <= 5000:
        alta = (freq - 2000) / 3000.0
    else:
        alta = 1.0

    return {'baja': baja, 'media': media, 'alta': alta}


def fuzzify_levenshtein(dist):
    """
    Fuzzificación de la distancia Levenshtein.
    Determina qué tan cerca está el input de la palabra candidata.
    """
    # Baja (cerca): 0 a 2
    if dist == 0:
        baja = 1.0
    elif dist <= 2:
        baja = (2 - dist) / 2.0
    else:
        baja = 0.0

    # Media (parcial): 1 a 5
    if dist <= 1:
        media = 0.0
    elif dist <= 3:
        media = (dist - 1) / 2.0
    elif dist <= 5:
        media = (5 - dist) / 2.0
    else:
        media = 0.0

    # Alta (lejos): 3+
    if dist <= 3:
        alta = 0.0
    elif dist <= 6:
        alta = (dist - 3) / 3.0
    else:
        alta = 1.0

    return {'baja': baja, 'media': media, 'alta': alta}


def fuzzify_context(has_context):
    """Fuzzificación booleana del contexto."""
    if has_context:
        return {'bajo': 0, 'alto': 1}
    return {'bajo': 1, 'alto': 0}


def evaluate_rules(freq_fuz, lev_fuz, ctx_fuz):
    """
    Evaluación de reglas difusas usando inferencia Mamdani.
    Returns: score defuzzificado (0-100)
    """
    # R1: IF lev IS baja AND freq IS alta → excelente
    r1 = min(lev_fuz['baja'], freq_fuz['alta'])

    # R2: IF lev IS baja AND ctx IS alto → excelente
    r2 = min(lev_fuz['baja'], ctx_fuz['alto'])

    # R3: IF lev IS baja AND freq IS media → buena
    r3 = min(lev_fuz['baja'], freq_fuz['media'])

    # R4: IF lev IS media AND freq IS alta → buena
    r4 = min(lev_fuz['media'], freq_fuz['alta'])

    # R5: IF lev IS media AND freq IS media → aceptable
    r5 = min(lev_fuz['media'], freq_fuz['media'])

    # R6: IF lev IS alta → malo
    r6 = lev_fuz['alta']

    # R7: default - si ninguna regla aplica, usar freq como baseline
    r7_default = max(freq_fuz['media'] * 0.5, freq_fuz['alta'] * 0.3)

    # Agregación: combinar reglas (máximo para cada categoría)
    score_excelente = max(r1, r2)
    score_buena = max(r3, r4)
    score_aceptable = max(r5, r7_default)
    score_malo = r6

    # Defuzzificación: método del centroide
    # Centroides: malo=25, aceptable=50, buena=75, excelente=100
    numerator = (score_malo * 25.0) + (score_aceptable * 50.0) + (score_buena * 75.0) + (score_excelente * 100.0)
    denominator = score_malo + score_aceptable + score_buena + score_excelente

    if denominator == 0:
        # Fallback: score basado solo en frecuencia
        return freq_fuz['alta'] * 60 + freq_fuz['media'] * 40 + freq_fuz['baja'] * 10

    return numerator / denominator


class FuzzyScorer:
    """Scorer que usa lógica difusa para rankear sugerencias de texto."""

    def __init__(self):
        from debug_logger import log, enabled as debug_enabled
        self._log = log
        self._debug_enabled = debug_enabled

    def get_score(self, target_word, current_input, frequency, has_context):
        """
        Calcula el score difuso para una palabra candidata.
        
        Args:
            target_word: La palabra candidata del diccionario
            current_input: El texto parcial escrito por el usuario
            frequency: Frecuencia de la palabra en el corpus
            has_context: Si hay contexto (bigrama previo)
            
        Returns:
            Score entre 0 y 100
        """
        dist = levenshtein_distance(current_input.lower(), target_word.lower())
        freq_fuz = fuzzify_frequency(frequency)
        lev_fuz = fuzzify_levenshtein(dist)
        ctx_fuz = fuzzify_context(has_context)
        score = evaluate_rules(freq_fuz, lev_fuz, ctx_fuz)

        # Logging para debug
        try:
            if self._debug_enabled():
                self._log('fuzzy', 'ai/fuzzy_logic.py', 'get_score', 'fuzzy.internal',
                          {'target': target_word, 'input': current_input,
                           'lev': dist, 'freq': frequency,
                           'lev_baja': round(lev_fuz['baja'], 3),
                           'lev_media': round(lev_fuz['media'], 3),
                           'freq_alta': round(freq_fuz['alta'], 3),
                           'freq_media': round(freq_fuz['media'], 3),
                           'score': round(score, 1)})
        except Exception:
            pass
        return score
