# 🏗️ Arquitectura Técnica — SmartText

> Documento de arquitectura detallada del sistema de texto predictivo offline.
> Cubre desde la UI en Android hasta el motor de inferencia difusa en Python.

---

## 📐 Diagrama de Arquitectura

```
┌──────────────────────────────────────────────────────────────────┐
│                       ANDROID (Kotlin)                            │
│                                                                   │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │                    UI Layer (Compose)                     │   │
│  │                                                          │   │
│  │  ┌────────────────────┐  ┌──────────────────────┐       │   │
│  │  │  PredictorScreen   │  │    MainNavigation    │       │   │
│  │  │  · TextField        │  │  · NavDisplay        │       │   │
│  │  │  · SuggestionChips  │  │  · entryProvider     │       │   │
│  │  │  · LangSelector     │  │                      │       │   │
│  │  └────────┬───────────┘  └──────────────────────┘       │   │
│  └───────────┼──────────────────────────────────────────────┘   │
│              │ Chaquopy (Python Bridge)                          │
│  ┌───────────▼──────────────────────────────────────────────┐   │
│  │               Python Predictor Engine                      │   │
│  │                                                          │   │
│  │  ┌──────────┐  ┌────────────┐  ┌──────────────────┐    │   │
│  │  │   Trie   │  │  Bigrams   │  │  FuzzyScorer     │    │   │
│  │  │ prefix   │  │  context   │  │  · lev distance  │    │   │
│  │  │ search   │  │  predict   │  │  · freq fuzzify  │    │   │
│  │  │ O(k)     │  │            │  │  · rule eval     │    │   │
│  │  └────┬─────┘  └─────┬──────┘  └────────┬─────────┘    │   │
│  └───────┼──────────────┼──────────────────┼──────────────┘   │
│          │              │                  │                    │
│  ┌───────▼──────────────▼──────────────────▼──────────────┐   │
│  │                   Data Layer                             │   │
│  │  ┌──────────────┐  ┌──────────────┐                     │   │
│  │  │ corpus.json  │  │user_data.json│                     │   │
│  │  │ (read-only)  │  │ (read-write) │                     │   │
│  │  └──────────────┘  └──────────────┘                     │   │
│  └──────────────────────────────────────────────────────────┘   │
└──────────────────────────────────────────────────────────────────┘
```

---

## 🔄 Flujo de Datos

### 1. Inicialización
```
MainActivity.onCreate()
    │
    ├── Python.start(AndroidPlatform(this))
    │
    ├── debug_logger.enable_runtime(path)
    │
    └── setContent { SmartTextTheme { MainNavigation() } }
                            │
                            ▼
                    PredictorScreen()
                            │
                            ├── py.getModule("engine.predictor")
                            ├── Predictor(user_data_dir, lang)
                            │       ├── load_corpus(corpus.json)
                            │       │       ├── Parse JSON
                            │       │       ├── Insert unigrams → Trie
                            │       │       └── Store bigrams
                            │       └── load_user_data()
                            │               └── Insert user words → Trie
                            └── LaunchedEffect(Unit) { focusRequester.requestFocus() }
```

### 2. Predicción en Tiempo Real

```
Usuario escribe → onValueChange(newText)
    │
    ├── Extraer palabras: words = newText.split(" ")
    ├── currentWord = words.last()
    └── previousWord = words[last-1] (si existe)
        │
        ├── ¿previousWord AND NOT currentWord?
        │   └── predictor.predict("", previousWord, 3)
        │       └── Buscar bigrams[previousWord] → top 3
        │
        ├── ¿currentWord?
        │   └── predictor.predict(currentWord, previousWord, 3)
        │       ├── trie.search_prefix(currentWord)
        │       ├── FuzzyScorer.get_score(word, input, freq, context)
        │       │       ├── levenshtein_distance(input, word)
        │       │       ├── fuzzify_frequency(freq)
        │       │       ├── fuzzify_levenshtein(dist)
        │       │       ├── fuzzify_context(has_context)
        │       │       └── evaluate_rules(...)
        │       └── Filtrar score ≥ 10 → top 3
        │
        └── Mostrar suggestions en chips
```

### 3. Aprendizaje (Feedback Loop)

```
Usuario hace clic en sugerencia
    │
    ├── Reemplazar última palabra con sugerencia
    ├── text = words[:-1] + suggestion + " "
    ├── suggestions = []
    └── predictor.update_frequency(suggestion)
        ├── Leer user_data.json
        ├── Incrementar frecuencia en +10
        ├── Escribir user_data.json
        └── Actualizar Trie in-memory
```

---

## 🧠 Sistema de Lógica Difusa (Detalle)

### Funciones de Membresía

#### Distancia Levenshtein
```
baja:  [0, 2]   → triangular: 1.0 en 0, 0.0 en 2
media: [1, 5]   → trapezoidal: 0 en 1, 1 en 3, 0 en 5
alta:  [3, ∞)   → sigmoidal: 0 en 3, 1 en 6+
```

#### Frecuencia
```
baja:  [0, 500]    → lineal descendente: 1 en 0, 0 en 500
media: [200, 3000] → trapezoidal: 0 en 200, 1 en 1000-2000, 0 en 3000
alta:  [2000, ∞)   → sigmoidal: 0 en 2000, 1 en 5000+
```

#### Contexto
```
bajo/alto → booleano difuso (0 o 1)
```

### Reglas de Inferencia (Mamdani)

| Regla | Antecedente | σ | Consecuente | Centroide |
|-------|-------------|---|-------------|-----------|
| R1 | lev.baja ∧ freq.alta | min | Excelente | 100 |
| R2 | lev.baja ∧ ctx.alto | min | Excelente | 100 |
| R3 | lev.baja ∧ freq.media | min | Buena | 75 |
| R4 | lev.media ∧ freq.alta | min | Buena | 75 |
| R5 | lev.media ∧ freq.media | min | Aceptable | 50 |
| R6 | lev.alta | — | Malo | 25 |
| R7 | default (freq baseline) | max | Aceptable | 50 |

### Defuzzificación
```
Score = Σ(σ_i · centroide_i) / Σ(σ_i)
```

---

## 🌳 Trie (Árbol de Prefijos)

### Estructura
```python
class TrieNode:
    children: dict      # {char: TrieNode}
    is_end_of_word: bool
    frequency: int      # Frecuencia acumulada
```

### Operaciones
- **Insert:** O(k) — Recorrer/crear nodos para cada caracter
- **Search Prefix:** O(k + m) — k: prefijo, m: palabras bajo ese prefijo
- **DFS Traversal:** Recursivo, recolecta todas las palabras con sus frecuencias

### Ordenamiento
Los resultados se ordenan por frecuencia descendente para presentar las palabras más comunes primero.

---

## 💾 Persistencia

### corpus.json (Read-only)
```json
{
  "en": {
    "unigrams": { "the": 99899, "be": 99898, "to": 99897, ... },
    "bigrams": {
      "i": [["am", 2500], ["have", 2000], ...],
      "you": [["are", 2000], ["have", 1500], ...]
    }
  },
  "es": { ... }
}
```

### user_data.json (Read-Write)
```json
{
  "android": 35,
  "predictor": 22,
  "difusa": 15
}
```

### Rutas en Android
- **corpus.json:** Empaquetado en APK vía Chaquopy (`app/src/main/python/engine/corpus.json`)
- **user_data.json:** Creado en `context.filesDir.absolutePath + "/user_data.json"`

---

## 🔧 Configuración de Build

### Gradle (app/build.gradle.kts)
```kotlin
chaquopy {
    defaultConfig {
        buildPython("C:\\Users\\andyh\\AppData\\Local\\Programs\\Python\\Python310\\python.exe")
    }
}

android {
    defaultConfig {
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        }
    }
}
```

### Build Command
```bash
# Necesita --no-configuration-cache por incompatibilidad con Chaquopy
./gradlew --no-configuration-cache assembleDebug
```

---

## 📊 Dependencias Principales

| Dependencia | Propósito |
|-------------|-----------|
| `androidx.compose.material3` | UI Material 3 |
| `androidx.navigation3` | Navegación experimental |
| `com.chaquo.python` | Bridge Python-Android |
| `androidx.lifecycle.viewmodel.compose` | ViewModel + Compose |
| `kotlinx.serialization` | Serialización de NavigationKeys |

---

## 🔍 Consideraciones de Rendimiento

### Trie
- Inserción masiva de ~20,000 palabras al inicio
- Búsqueda por prefijo: O(k) donde k ≤ 20 caracteres
- Memoria: ~2-5 MB para 20,000 palabras

### Fuzzy Logic
- Cálculo de Levenshtein: O(n·m) por palabra candidata
- Para prefijos con pocos resultados (<50): rápido
- Para fallback (500 palabras): ~5-10ms adicionales

### Chaquopy
- Overhead de llamada Python: ~1-2ms por llamada
- Inicialización: ~500ms-2s (primera vez)
- Memoria Python: ~10-30MB adicionales

---

## 🔐 Seguridad y Offline

- ✅ **100% offline:** No se hacen requests de red
- ✅ **Datos locales:** user_data.json solo se almacena en el directorio privado de la app
- ✅ **Sin permisos especiales:** Solo los necesarios para la app base
- ✅ **Sin analytics:** No se recolectan datos de uso
