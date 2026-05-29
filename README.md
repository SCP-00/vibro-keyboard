# SmartText — Corrector Ortográfico Inteligente Offline

> **Aplicación Android de texto predictivo basada en Computación Blanda**
> 
> Funciona 100% offline · Bilingüe (Español/English) · Optimizada para gama baja

---

## ✨ Características

- **🔮 Predicción de texto en tiempo real** — Mientras escribes, SmartText sugiere palabras
- **🌐 Bilingüe** — Soporte completo para español e inglés
- **📴 100% offline** — Todo el procesamiento es local, sin necesidad de internet
- **🧠 Lógica Difusa** — Sistema experto con 4 variables de entrada y 7 reglas de inferencia
- **📏 Distancia Levenshtein** — Corrección ortográfica con fuzzy matching
- **🌳 Trie** — Búsqueda eficiente por prefijo en O(k)
- **📊 Bigramas contextuales** — Predicción basada en la palabra anterior
- **🎯 Aprendizaje local** — Se adapta a tus palabras más usadas
- **📱 Material 3** — UI moderna con Jetpack Compose

---

## 📸 Capturas de Pantalla

*(pendiente)*

---

## 🏗️ Arquitectura

```
┌─────────────────────────────────────┐
│       Jetpack Compose (Material 3)   │
│   PredictorScreen · Chips · Selector │
├─────────────────────────────────────┤
│        Chaquopy (Python Bridge)      │
├─────────────────────────────────────┤
│      Prediction Engine (Python)      │
│  ┌────────┐ ┌────────┐ ┌──────────┐ │
│  │  Trie  │ │Bigrams │ │Fuzzy Logic│ │
│  └────────┘ └────────┘ └──────────┘ │
├─────────────────────────────────────┤
│       Persistencia (JSON local)       │
└─────────────────────────────────────┘
```

---

## 🛠️ Stack Tecnológico

| Componente | Tecnología | Versión |
|------------|-----------|---------|
| **Lenguaje UI** | Kotlin | 2.3.20 |
| **Framework UI** | Jetpack Compose + Material 3 | BOM 2024.x |
| **Motor IA** | Python (vía Chaquopy) | 3.10 |
| **Build** | Gradle + AGP | 9.1.0 / 9.0.1 |
| **SDK Mínimo** | Android API | 24 (Android 7.0) |
| **SDK Objetivo** | Android API | 36 (Android 16) |
| **Navegación** | Navigation3 (experimental) | — |

---

## 📦 Instalación

### Requisitos
- Android Studio Ladybug Feature Drop (2024.2.2+) o superior
- Android SDK 24-36
- Python 3.10 en `C:\Users\andyh\AppData\Local\Programs\Python\Python310\`
- JDK 17

### Compilar APK
```bash
cd smarttext
./gradlew --no-configuration-cache assembleDebug
```

O desde Android Studio:
1. Abre la carpeta `smarttext/`
2. Espera a que Gradle sincronice
3. Build → Build Bundle(s) / APK(s) → Build APK(s)

### Instalar en Dispositivo
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

---

## 🧪 Pruebas Locales (Python)

### 1. Generar Corpus
```bash
cd app/src/main/python
python nlp/build_corpus.py
```

### 2. Probar Predictor
```bash
cd app/src/main/python
python tests/run_predictor.py
```

### Salida Esperada
```
scenario={'current': '', 'previous': 'hello'} -> []
scenario={'current': 'ho', 'previous': None} -> ['how', 'home', 'hot', ...]
scenario={'current': 'th', 'previous': None} -> ['the', 'thy', 'thu', ...]
```

---

## 🧠 Técnicas de Computación Blanda

### Sistema de Lógica Difusa (Fuzzy Logic)
- **4 variables de entrada:** Distancia Levenshtein, Frecuencia, Contexto, Coincidencia de prefijo
- **7 reglas de inferencia** usando método Mamdani
- **Defuzzificación** por centroide para score continuo 0-100

### Distancia Levenshtein
- Implementación estándar O(n²) vectorizada (optimizada a O(n·m))
- Umbrales difusos: baja (0-2), media (1-5), alta (3+)

### Trie (Árbol de Prefijos)
- Búsqueda O(k) para prefijos
- Implementación con diccionarios anidados
- Ordenamiento por frecuencia descendente

### Bigramas
- 759 bigramas en inglés (verbos modales, pronombres, preposiciones)
- 295 bigramas en español (artículos, preposiciones, verbos comunes)

---

## 📁 Estructura del Proyecto

```
smarttext/
├── app/
│   ├── build.gradle.kts          # Configuración de la app
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/example/smarttext/
│       │   ├── MainActivity.kt       # Entry point
│       │   ├── Navigation.kt         # Navegación
│       │   ├── NavigationKeys.kt     # Keys de navegación
│       │   ├── data/DataRepository.kt
│       │   ├── theme/                # Tema Material 3
│       │   └── ui/
│       │       ├── PredictorScreen.kt    # 🎯 Pantalla principal
│       │       └── main/                 # (legacy)
│       └── python/
│           ├── ai/fuzzy_logic.py      # 🧠 Sistema difuso
│           ├── engine/
│           │   ├── predictor.py       # ⚙️ Motor predictivo
│           │   ├── trie.py            # 🌳 Árbol de prefijos
│           │   └── corpus.json        # 📚 Datos del corpus
│           ├── nlp/build_corpus.py    # 🔨 Generador de corpus
│           └── tests/run_predictor.py # 🧪 Tests locales
├── build.gradle.kts               # Configuración raíz
├── settings.gradle.kts
├── gradle/
├── PLAN.md                        # 📋 Plan de desarrollo
├── AGENTS.md                      # 🤖 Flujo multi-agente
├── ARCHITECTURE.md                # 🏗️ Arquitectura detallada
└── README.md                      # 📖 Este archivo
```

---

## 📊 Resultados de Pruebas

### 🧪 Suite de Tests (Python)

| Escenario | Resultado |
|-----------|-----------|
| **Inglés — Precisión Top-1** | **57.1%** |
| **Inglés — Precisión Top-3** | **71.4%** |
| **Español — Precisión Top-1** | **93.3%** |
| **Español — Precisión Top-3** | **93.3%** |
| **Bigramas contextuales** | ✅ Funcional (`how → to, you, many`) |
| **Corrección ortográfica (Levenshtein)** | ✅ Funcional (fallback top 2000 palabras) |
| **Tiempo promedio (prefijo)** | ~0.06ms |
| **Tiempo promedio (corrección)** | ~67ms |
| **Stress test (100 consultas)** | ✅ Completo sin errores |

### 📱 Pruebas en Emuladores

| Emulador | Estado | Resultado |
|----------|--------|-----------|
| `mid_range` (API 36, 720×1280) | ✅ **2 emuladores** | App instalada, predicción funcional, sin crashes |
| `small_phone` (API 36, 720×1280) | ✅ Conectado | App instalada y funcional |
| `high_end` (ARM) | ❌ No compatible | CPU ARM no soportada por QEMU2 en este equipo |

**Predicciones verificadas en emulador:**
- `"th"` → `["the" (88.5), "there" (69.2), "that" (65.6)]` ✅
- `"the"` → `["the" (88.5), "they" (81.2), "theo" (81.2)]` ✅
- `"cas"` (ES) → `["casa", "caso", "casi"]` ✅
- `"de "` (ES bigram) → Predicción contextual detectada ✅

### 📈 6 Gráficas de Experimentación

| Gráfica | Descripción |
|---------|-------------|
| `01_accuracy_by_prefix_length.png` | Precisión vs longitud del prefijo |
| `02_fuzzy_vs_raw.png` | Comparativa fuzzy scoring vs raw |
| `03_spelling_correction.png` | Rendimiento de corrección ortográfica |
| `04_timing_distribution.png` | Distribución de tiempos de respuesta |
| `05_fuzzy_behavior.png` | Comportamiento del sistema difuso |
| `06_dashboard.png` | Dashboard consolidado de métricas |

---

## 🔮 Próximos Pasos

- [x] ~~Experimentación con métricas de precisión~~ ✅
- [x] ~~Pruebas en emuladores~~ ✅ (2 emuladores)
- [x] ~~Visualización de datos experimentales~~ ✅ (6 gráficas)
- [ ] Optimización de tamaño de APK (58MB → reducir vía ProGuard)
- [ ] Pruebas en dispositivo físico
- [ ] Informe técnico (formato IEEE/ACM)
- [ ] SOM para clustering de palabras (técnica adicional de Computación Blanda)

---

## 📄 Licencia

Proyecto académico — Curso de Computación Blanda

---

## 👥 Autor

Desarrollado como proyecto final del curso de Computación Blanda.
Sistema multi-agente orquestado por Codebuff AI.
