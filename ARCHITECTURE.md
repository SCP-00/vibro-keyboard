# 🏗️ Arquitectura Técnica — SmartText Keyboard

> Documento de arquitectura detallada del teclado IME con predicción inteligente y gestos de deslizamiento.
> Cubre desde el servicio IME en Android hasta el motor de inferencia difusa en Kotlin nativo.

---

## 📐 Diagrama de Arquitectura

```
┌──────────────────────────────────────────────────────────────────────┐
│                     ANDROID SYSTEM (InputMethodFramework)              │
│                                                                       │
│  ┌────────────────────────────────────────────────────────────┐      │
│  │                    SmartIME (InputMethodService)             │      │
│  │                                                             │      │
│  │  ┌──────────────────────────────────────────────────────┐  │      │
│  │  │              SmartKeyboardView (Canvas View)          │  │      │
│  │  │                                                      │  │      │
│  │  │  ┌──────────────┐  ┌──────────────┐  ┌──────────┐  │  │      │
│  │  │  │  Key Drawing  │  │ Swipe Trail  │  │Candidate │  │  │      │
│  │  │  │  · 47 teclas  │  │ · Bezier     │  │ Strip    │  │  │      │
│  │  │  │  · 5 filas    │  │   quadTo     │  │ (5 chips)│  │  │      │
│  │  │  │  · Press anim │  │ · Dot points │  │          │  │  │      │
│  │  │  └──────┬───────┘  └──────┬───────┘  └─────┬────┘  │  │      │
│  │  └─────────┼─────────────────┼─────────────────┼───────┘  │      │
│  │            │                 │                 │           │      │
│  │  ┌─────────▼─────────────────▼─────────────────▼───────┐  │      │
│  │  │                 Touch Handler                        │  │      │
│  │  │  · ACTION_DOWN → key press / gesture start          │  │      │
│  │  │  · ACTION_MOVE → track swipe / key highlight        │  │      │
│  │  │  · ACTION_UP → commit tap / recognize gesture       │  │      │
│  │  │  · Threshold 30px: tap vs swipe detection           │  │      │
│  │  └──────────────────────┬──────────────────────────────┘  │      │
│  │                         │                                  │      │
│  │  ┌──────────────────────▼──────────────────────────────┐  │      │
│  │  │              SmartIME (Service Layer)                │  │      │
│  │  │                                                      │  │      │
│  │  │  · commitText(char)    → escribe carácter           │  │      │
│  │  │  · commitWord(word)    → autocompleta palabra       │  │      │
│  │  │  · autocorrectCurrentWord() → gesture-aware         │  │      │
│  │  │  · deleteBackward()    → borra un carácter          │  │      │
│  │  │  · performEnter()      → salto de línea             │  │      │
│  │  │  · lastGesturePattern  → guía autocorrección        │  │      │
│  │  │  · Thread safety: @Volatile + @Synchronized + scope │  │      │
│  │  └──────────────────────┬──────────────────────────────┘  │      │
│  │                         │                                  │      │
│  │  ┌──────────────────────▼──────────────────────────────┐  │      │
│  │  │              PredictorEngine (Kotlin)                │  │      │
│  │  │                                                      │  │      │
│  │  │  ┌──────────────┐  ┌──────────────┐  ┌──────────┐  │  │      │
│  │  │  │ Sorted List  │  │   Bigrams    │  │FuzzyScore│  │  │      │
│  │  │  │ · 10K words  │  │ · 44 (ES)    │  │·Levensht.│  │  │      │
│  │  │  │ · binary srch│  │ · 151 (EN)   │  │·Mamdani  │  │  │      │
│  │  │  │ · LRU cache  │  │ · context    │  │·Centroid │  │  │      │
│  │  │  │              │  │              │  │·QWERTY   │  │  │      │
│  │  │  │              │  │              │  │ Adjacent │  │  │      │
│  │  │  └──────────────┘  └──────────────┘  └──────────┘  │  │      │
│  │  └──────────────────────┬──────────────────────────────┘  │      │
│  │                         │                                  │      │
│  │  ┌──────────────────────▼──────────────────────────────┐  │      │
│  │  │                   Data Layer                         │  │      │
│  │  │  ┌────────────────┐  ┌────────────────────────┐    │  │      │
│  │  │  │  corpus.json   │  │   user_data.json        │    │  │      │
│  │  │  │  (assets/)     │  │   (context.filesDir)    │    │  │      │
│  │  │  │  · read-only   │  │   · read-write          │    │  │      │
│  │  │  │  · 395KB       │  │   · frecuencias usuario │    │  │      │
│  │  │  └────────────────┘  └────────────────────────┘    │  │      │
│  │  └────────────────────────────────────────────────────┘  │      │
│  └────────────────────────────────────────────────────────────┘      │
└──────────────────────────────────────────────────────────────────────┘
```

---

## 🔄 Flujo de Datos

### 1. Inicialización del IME

```
Usuario enfoca un campo de texto
    │
    ▼
Sistema Android: InputMethodManager
    │
    ├── onCreate() → SmartIME
    │       ├── initPredictor() [Dispatchers.IO]
    │       │       ├── PredictorEngine(context, lang)
    │       │       │       ├── loadCorpus() → leer corpus.json de assets
    │       │       │       ├── loadUserData() → leer user_data.json
    │       │       │       └── sortedWords = sorted by word
    │       │       └── @Volatile predictor = pred
    │       │
    │       └── SmartIME listo para recibir input
    │
    ├── onBindInput() → enlazado a la conexión de input
    │
    ├── onStartInput(info) → recibe EditorInfo del campo
    │
    ├── onCreateInputView() → SmartKeyboardView
    │       ├── KeyboardData.generate(lang) → 47 teclas, 5 filas
    │       ├── KeyboardData.layoutKeys(w, h) → bounds de cada tecla
    │       └── setPredictor(predictor) → candidate strip ready
    │
    └── onStartInputView() → teclado visible en pantalla
```

### 2. Tap en una Tecla

```
Usuario toca tecla "c"
    │
    ▼
SmartKeyboardView.onTouchEvent(ACTION_DOWN)
    │
    ├── findKeyAt(360, 500) → Key('c')
    ├── pressedKey = Key('c')
    │
    ▼
SmartKeyboardView.onTouchEvent(ACTION_UP)
    │
    ├── findKeyAt(360, 500) === pressedKey
    │   └── handleKeyPress(Key('c'))
    │       └── ime.commitText("c")
    │
    ▼
SmartIME.commitText("c")
    │
    ├── currentInputWord = "c"
    ├── keyboardView.updatePredictions("c")
    │       └── predictor.predict("c", null, 5)
    │           ├── searchPrefix("c") → binary search O(log n)
    │           │   → ["casa", "como", "cosa", ...]
    │           ├── FuzzyScorer.getScore(word, "c", freq, ctx)
    │           │   ├── levenshteinDistance("c", word)
    │           │   ├── fuzzifyFrequency(freq)
    │           │   ├── fuzzifyLevenshtein(dist)
    │           │   ├── fuzzifyContext(hasContext)
    │           │   └── evaluateRules → score 0-100
    │           └── filter score ≥ 10 → top 5
    │
    └── currentInputConnection.commitText("c", 1)
```

### 3. Swipe/Glide Typing

```
Usuario desliza: c → a → s → a (sin levantar el dedo)
    │
    ▼
SmartKeyboardView.onTouchEvent(ACTION_MOVE)
    │
    ├── dx²+dy² > 900 (30px threshold)
    │   └── isGesturing = true
    │
    ├── gestureRecognizer.addPoint(x, y)
    │
    ▼ (continúa hasta ACTION_UP)

SmartKeyboardView.onTouchEvent(ACTION_UP)
    │
    ├── gestureRecognizer.endGesture(x, y)
    │
    ▼
GestureRecognizer.recognize(keys, predictor, 5)
    │
    ├── resamplePath(points) → 40 puntos equidistantes
    ├── computeDTWScore(touchPath, idealPath) → DTW matrix 2-row
    ├── isSubsequenceMatch(pattern, word) → skip tolerance
    ├── generateCandidates(sequence, predictor)
    │   ├── Estrategia 1: predictor.predict() → fuzzy score
    │   ├── Estrategia 2: DTW spatial matching
    │   └── Estrategia 3: prefix fallback
    │
    └── best = recognized.first().word
        └── ime.commitWord("casa", gesturePattern, dtwScore)
            ├── SmartIME.lastGesturePattern = "casa"
            ├── deleteSurroundingText(currentInputWord.length)
            ├── commitText("casa ", 1)
            └── updateFrequency("casa") [scope.launch]
```

### 4. Autocorrección Gesture-Aware

```
Usuario termina palabra con espacio/puntuación
    │
    ▼
SmartIME.autocorrectCurrentWord()
    │
    ├── ¿Hay lastGesturePattern?
    │   └── Sí → autocorrect(typedWord, gesturePattern, dtwScore)
    │       ├── PredictorEngine.suggestCorrections(word)
    │       │   ├── Strategy 1: Prefix + FuzzyScorer
    │       │   ├── Strategy 2: Levenshtein fallback
    │       │   ├── Strategy 3: QWERTY adjacent substitution
    │       │   │   └── Para cada char, probar teclas vecinas
    │       │   │       ej: "comio" → "comio","domio","fomio","cpmio"...
    │       │   ├── Strategy 4: Single-letter insertion
    │       │   │   └── Insertar letras comunes (aeiornstl)
    │       │   │       ej: "probema" → "problema"
    │       │   └── Strategy 5: Gesture subsequence guide
    │       │       └── Filtrar por isSubsequenceMatch(gesture, word)
    │       └── Score combinado: menor threshold si gesture es confiable
    │
    └── No → autocorrect(typedWord)
        └── Solo prefijo + Levenshtein + fuzzy
```

---

## 🧠 Sistema de Lógica Difusa (Detalle)

### FuzzyScorer (Kotlin Object)

#### Funciones de Membresía

##### Distancia Levenshtein
```
baja:  [0, 2]   → triangular: μ=1.0 en d=0, μ=0.0 en d=2
media: [1, 5]   → trapezoidal: μ=0 en 1, μ=1 en 3, μ=0 en 5
alta:  [3, ∞)   → sigmoidal: μ=0 en 3, μ=1 en 6+
```

##### Frecuencia
```
baja:  [0, 500]    → lineal descendente: μ=1 en 0, μ=0 en 500
media: [200, 3000] → trapezoidal: μ=0 en 200, μ=1 en 1000-2000, μ=0 en 3000
alta:  [2000, ∞)   → sigmoidal: μ=0 en 2000, μ=1 en 5000+
```

##### Contexto
```
bajo/alto → booleano difuso (μ=0 o μ=1)
```

#### Reglas de Inferencia (Mamdani)

| Regla | Antecedente | σ (min) | Consecuente | Centroide |
|-------|-------------|---------|-------------|-----------|
| R1 | lev.baja ∧ freq.alta | min | Excelente | 100 |
| R2 | lev.baja ∧ ctx.alto | min | Excelente | 100 |
| R3 | lev.baja ∧ freq.media | min | Buena | 75 |
| R4 | lev.media ∧ freq.alta | min | Buena | 75 |
| R5 | lev.media ∧ freq.media | min | Aceptable | 50 |
| R6 | lev.alta | — | Malo | 25 |
| R7 | default (freq baseline) | max | Aceptable | 50 |

#### Defuzzificación
```
Score(σ) = Σ(σ_i · centroide_i) / Σ(σ_i)

Donde σ_i = activación de la regla i
      centroide_i = {malo=25, aceptable=50, buena=75, excelente=100}
```

---

## ⌨️ Keyboard Layout

### 5 Filas

```
Fila 0: 1 2 3 4 5 6 7 8 9 0          [Números]
Fila 1: q w e r t y u i o p          [QWERTY top]
Fila 2: a s d f g h j k l ñ          [Home row + Ñ]
Fila 3: ⇧ z x c v b n m ⌫            [Bottom + Shift/Backspace]
Fila 4: 🌐 , ______Espacio______ . ↵  [Space row]
```

> **Nota:** En el layout Inglés (EN), la home row tiene **9 teclas** (a s d f g h j k l, sin apóstrofe). La apóstrofe se accede mediante long-press en la tecla coma (`,` → `,':;`).

### Tipos de Teclas

| Tipo | Código | Comportamiento |
|------|--------|----------------|
| **Letra** | 'a'-'z', 'ñ' | Carácter normal, reset shift |
| **Shift** | -1 | Toggle: normal → mayús → bloqueo → normal |
| **Backspace** | -2 | deleteBackward() |
| **Espacio** | -3 | commitText(" ") |
| **Enter** | -4 | performEnter() → \n |
| **Switch Lang** | -5 | Cambia idioma, recarga predictor |
| **Coma** | -7 | commitText(",") — long-press: `,':;` |
| **Punto** | -8 | commitText(".") |

---

## 🖱️ GestureRecognizer (Swipe/Glide) — v1.5+ DTW

### Algoritmo

1. **Recolección de puntos** — Durante ACTION_MOVE, saltar puntos muy cercanos (<25px²)
2. **Resample** — Reducir/expandir a exactamente **40 puntos equidistantes** para normalizar rutas de cualquier longitud
3. **Key centers cache** — Centros precomputados de todas las teclas (a-z, ñ, ', à-ÿ) para lookup O(1)
4. **DTW Path Matching** — Dynamic Time Warping entre la ruta táctil y la ruta ideal (key centers del patrón)
5. **Generación de candidatos** — 3 estrategias paralelas:
   - Predictor predict (con prefix)
   - DTW spatial matching (palabras con ruta similar)
   - Prefix fallback (primer carácter)
6. **Scoring combinado v2**:
   ```
   Score = DTW_score × 0.45 + levenshtein × 0.15 + length × 0.10 + frequency × 0.30
   ```

### DTW Implementation

```kotlin
fun dynamicTimeWarping(seq1: List<PointF>, seq2: List<PointF>): Float {
    val (n, m) = seq1.size to seq2.size
    var prev = FloatArray(m) { Float.MAX_VALUE }
    var curr = FloatArray(m)

    for (i in 0 until n) {
        curr[0] = pointDist(seq1[i], seq2[0])
        if (i > 0) curr[0] += prev[0]

        for (j in 1 until m) {
            val cost = pointDist(seq1[i], seq2[j])
            val minPrev = min(prev[j], prev[j-1], curr[j-1])
            curr[j] = cost + minPrev
        }
        val temp = prev; prev = curr; curr = temp
    }
    return prev[m - 1]
}
```

#### Ventajas del DTW sobre el enfoque anterior:
- **Tolerante a velocidad variable** — El usuario puede deslizar rápido o lento
- **Sin threshold de subsecuencia** — Ya no depende de un ratio arbitrario
- **Mejor precisión** — 45% de peso en matching espacial vs 30% de subsecuencia antes

---

## 🤖 Autocorrección Gesture-Aware — v1.6

### Mapa de Adyacencia QWERTY (QWERTY_ADJACENT)

Cada tecla conoce las teclas físicamente adyacentes en el teclado QWERTY:

```
'q' → [w, a, s]         'a' → [q, w, s, z]
'w' → [q, e, a, s, d]   's' → [q, w, e, a, d, z, x]
...
'ñ' → [l, o, p]          (tecla Ñ en posición española)
```

### Estrategias de Corrección

| Estrategia | Descripción | Ejemplo |
|------------|-------------|---------|
| **Prefijo + Fuzzy** | Binary search + FuzzyScorer | `"cas"` → `casa` |
| **Levenshtein** | Distancia de edición sobre top-500 | `"kasa"` → `casa` |
| **QWERTY Adjacent** | Sustituir cada char por teclas vecinas | `"cmion"` → `camión` (c→a) |
| **Inserción** | Insertar letras comunes (aeiornstl) | `"probema"` → `problema` |
| **Gesture Subsequence** | Guiar por patrón de deslizamiento | `"casa"` pattern → filtrar candidatos |

### Scoring para Autocorrección

```kotlin
// Sin gesture: threshold minScore = 15
// Con gesture de alta confianza: threshold minScore = 10
// Edit distance máxima: 2 (normal) o 4 (gesture + palabra larga)
```

---

## 🔒 Thread Safety

| Componente | Mecanismo | Propósito |
|------------|-----------|-----------|
| `predictor` field | `@Volatile` | Visibilidad entre threads IO y UI |
| `searchPrefix()` | `@Synchronized` | Proteger LRU cache de accesos concurrentes |
| `updateFrequency()` | `@Synchronized` | Proteger `byFreqCache` y `prefixCache` |
| `allWords` getter | `@Synchronized` | Proteger `byFreqCache` |
| `getCachedPrefix()` | `@Synchronized` | Proteger `prefixCache` |
| `initPredictor()` | `CoroutineScope(IO)` | Inicialización en background |
| `commitWord()` → `updateFrequency` | `scope.launch` | Escribir user_data.json en IO |
| `scope.cancel()` | `onDestroy()` | Prevenir leaks de corrutinas |

---

## 💾 Persistencia

### corpus.json (assets/)
```json
{
  "en": {
    "unigrams": { "the": 99899, "be": 99898, ... },
    "bigrams": { "i": { "am": 2500, "have": 2000, ... }, ... }
  },
  "es": {
    "unigrams": { "de": 50000, "la": 48000, ... },
    "bigrams": { "de": { "la": 3000, "los": 2500, ... }, ... }
  }
}
```

### user_data.json (context.filesDir)
```json
{ "android": 35, "predictor": 22, "difusa": 15 }
```

---

## 📊 Dependencias Principales

| Dependencia | Propósito |
|-------------|-----------|
| `androidx.compose.material3` | UI Settings Screen (Material 3) |
| `androidx.navigation3` | Navegación experimental |
| `androidx.lifecycle` | ViewModel + Compose lifecycle |
| `kotlinx.serialization` | Serialización de NavigationKeys |
| **Sin Chaquopy** | **Motor Kotlin nativo puro** (-86% APK size) |

---

## 🔍 Métricas de Rendimiento

| Operación | Tiempo | Complejidad |
|-----------|--------|-------------|
| Búsqueda por prefijo (binary) | **<0.1ms** | O(log n) |
| Fuzzy scoring (1 palabra) | **<0.01ms** | O(1) |
| Levenshtein (1 par) | **~0.1ms** | O(n·m) |
| Predicción completa (top-5) | **<1ms** | O(log n + k) |
| DTW path matching (40×40) | **~0.05ms** | O(n·m) con 2-row |
| Corrección ortográfica (500 cand.) | **~67ms** | O(500·n·m) |
| Inicialización del predictor | **<10ms** | O(n·log n) |
| **Tamaño APK Release** | **~7.9 MB** | -86% vs Chaquopy |
