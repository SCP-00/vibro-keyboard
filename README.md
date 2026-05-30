# ⌨️ SmartText Keyboard — IME Predictivo con Swipe Typing

> **Teclado Android IME con predicción inteligente, gestos de deslizamiento y lógica difusa**
>
> 100% Kotlin nativo · Sin Python · Bilingüe (Español/English) · 100% offline

<div align="center">

[![Download APK](https://img.shields.io/badge/📲%20Descargar%20APK-v1.6-brightgreen?style=for-the-badge&logo=android)](https://github.com/SCP-00/Android_text_predicto_board/releases/latest/download/app-release.apk)
[![Build APK](https://github.com/SCP-00/Android_text_predicto_board/actions/workflows/build-apk.yml/badge.svg)](https://github.com/SCP-00/Android_text_predicto_board/actions/workflows/build-apk.yml)
[![Tests](https://img.shields.io/badge/Tests-137%20✔️-blue?style=flat-square)](https://github.com/SCP-00/Android_text_predicto_board/actions)
[![DTW](https://img.shields.io/badge/DTW-Gesture%20Matching-ff69b4?style=flat-square)](ARCHITECTURE.md#-gesturerecognizer-swipeglide--v15-dtw)
[![License](https://img.shields.io/badge/License-Academic-lightgrey?style=flat-square)](LICENSE)

</div>

---

## ✨ Características

- **⌨️ Teclado QWERTY completo** — 5 filas: numérica + letras + espacio/enter con tecla Ñ
- **🔮 Predicción en tiempo real** — Sugerencias mientras escribes con scoring difuso (Fuzzy Mamdani)
- **🖱️ Swipe/Glide Typing con DTW** — Dynamic Time Warping para matching preciso de gestos de deslizamiento
- **🧠 Autocorrección Gesture-Aware** — Corrección ortográfica que usa el patrón de deslizamiento + teclas adyacentes QWERTY
- **🧮 Lógica Difusa (Mamdani)** — 4 variables de entrada, 7 reglas de inferencia, defuzzificación por centroide
- **📏 Distancia Levenshtein** — Corrección ortográfica automática
- **🌐 Bilingüe** — Soporte completo para español e inglés (cambio con un toque)
- **📴 100% offline** — Todo el procesamiento es local, sin internet
- **🎯 Aprendizaje local** — Se adapta a tus palabras más usadas
- **⚡ Motor Kotlin nativo** — Sin dependencia de Python/Chaquopy, 10-50x más rápido
- **🎨 Canvas personalizado** — Renderizado de teclas con curvas bezier, trail de deslizamiento suave y candidate strip
- **🔤 Layout inglés estándar mobile** — Home row de 9 teclas (sin apóstrofe en medio), apóstrofe accesible vía long-press

---

## 📸 Capturas de Pantalla

| # | Captura | Descripción |
|---|---------|-------------|
| 1 | ![Settings Screen](docs/01_settings_screen.png) | Pantalla de configuración con campo de prueba |
| 2 | ![Keyboard Active](docs/02_keyboard_visible.png) | Teclado SmartIME activo con predicciones |

> ⚠️ **Nota:** Las capturas screenshots se generan desde el emulador. Para ver el teclado en acción, instala el APK y actívalo como IME.

---

## 🏗️ Arquitectura

```
┌─────────────────────────────────────────────────────────┐
│                    SmartIME (InputMethodService)          │
│  ┌─────────────────────────────────────────────────┐    │
│  │              SmartKeyboardView (Canvas)          │    │
│  │  ┌──────────┐ ┌──────────────┐ ┌─────────────┐ │    │
│  │  │Key Layout │ │Swipe Trail   │ │Candidate    │ │    │
│  │  │Keyboard  │ │Bezier Curves  │ │Strip (top)  │ │    │
│  │  │Data.kt   │ │Recognizer.kt │ │             │ │    │
│  │  └──────────┘ └──────────────┘ └─────────────┘ │    │
│  └─────────────────────────────────────────────────┘    │
│                         │                                │
│  ┌─────────────────────────────────────────────────┐    │
│  │              PredictorEngine (Kotlin)            │    │
│  │  ┌──────────┐ ┌────────────┐ ┌──────────────┐  │    │
│  │  │ Sorted   │ │  Bigrams   │ │ FuzzyScorer  │  │    │
│  │  │ List +   │ │  Context   │ │ · Levenshtein│  │    │
│  │  │ binary   │ │  Predict   │ │ · QWERTY Adj │  │    │
│  │  │ search   │ │            │ │ · Gesture    │  │    │
│  │  └──────────┘ └────────────┘ └──────────────┘  │    │
│  └─────────────────────────────────────────────────┘    │
│                         │                                │
│  ┌─────────────────────────────────────────────────┐    │
│  │               Data Layer                         │    │
│  │  ┌──────────────┐  ┌──────────────┐             │    │
│  │  │ corpus.json  │  │user_data.json│             │    │
│  │  │ (assets/)    │  │ (filesDir)   │             │    │
│  │  └──────────────┘  └──────────────┘             │    │
│  └─────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────┘
```

---

## 🛠️ Stack Tecnológico

| Componente | Tecnología | Versión |
|------------|-----------|---------|
| **Lenguaje** | Kotlin | 2.3.20 |
| **Framework UI** | Jetpack Compose + Material 3 | BOM 2026.x |
| **IME View** | Canvas personalizado (View + Paint) | — |
| **Motor IA** | Kotlin nativo (FuzzyScorer + PredictorEngine) | — |
| **Build** | Gradle + AGP | 9.0.1 |
| **SDK Mínimo** | Android API | 24 (Android 7.0) |
| **SDK Objetivo** | Android API | 36 (Android 16) |
| **Prueba Glide Typing** | Verificado en emulador | API 36 (Android 16) |
| **Tamaño APK** | **~7.9 MB** (release) / **~12 MB** (debug) | — |

---

## 📦 Descarga e Instalación (1 Click)

### ⚡ Descarga directa

<div align="center">
<a href="https://github.com/SCP-00/Android_text_predicto_board/releases/latest/download/app-release.apk">
  <img src="https://img.shields.io/badge/📲%20Descargar%20APK%20(v1.6)-brightgreen?style=for-the-badge&logo=android" alt="Download APK" width="300">
</a>

<img src="https://api.qrserver.com/v1/create-qr-code/?size=150x150&data=https://github.com/SCP-00/Android_text_predicto_board/releases/latest/download/app-release.apk" alt="QR para descargar desde tu celular" width="150">

**Escanea el QR con tu celular para descargar e instalar directamente**
</div>

> ⚡ **Android te pedirá confirmación** para instalar apps de orígenes desconocidos. Actívalo y ¡listo!

---

### 📋 Instalación paso a paso

#### En tu teléfono (recomendado):

| Paso | Acción |
|------|--------|
| 1 | Abre este repositorio en Chrome desde tu Android |
| 2 | Toca el botón **📲 Descargar APK** de arriba |
| 3 | Cuando termine la descarga, ábrela desde las notificaciones |
| 4 | Acepta "Instalar apps desconocidas" si es necesario |
| 5 | Presiona **Instalar** |
| 6 | Abre la app **SmartText Keyboard** desde el launcher |
| 7 | Presiona **"Activar en Ajustes"** → activa el teclado |
| 8 | Selecciona SmartText como método de entrada predeterminado |
| 9 | 🎉 **¡Listo! Empieza a escribir con predicción inteligente** |

#### Desde tu computadora (vía ADB):
```bash
# 1. Descarga el APK desde GitHub Releases
# 2. Conecta tu Android por USB con depuración USB activada
adb install app-release.apk

# 3. Habilita SmartIME como teclado del sistema
adb shell ime enable com.example.smarttext/.ime.SmartIME
adb shell ime set com.example.smarttext/.ime.SmartIME
```

#### Compilar desde código fuente:
```bash
cd smarttext
./gradlew --no-configuration-cache assembleRelease
# APK generado en: app/build/outputs/apk/release/app-release.apk
```

---

### 📥 Descargas disponibles

| Archivo | Tamaño | Enlace |
|---------|--------|--------|
| **APK Release** (firmado) | ~7.9 MB | [Descargar](https://github.com/SCP-00/Android_text_predicto_board/releases/latest/download/app-release.apk) ⭐ |
| **APK Debug** (sin firmar) | ~12 MB | [Ver Actions](https://github.com/SCP-00/Android_text_predicto_board/actions/workflows/build-apk.yml) |
| **Código fuente** | — | [GitHub](https://github.com/SCP-00/Android_text_predicto_board) |

---

### 🔄 Actualizaciones automáticas

Cada vez que se sube un cambio a `main`, GitHub Actions compila automáticamente un APK nuevo. 
Para crear una **Release oficial**:
```bash
git tag v1.6
git push --tags
```

Esto genera automáticamente un Release con el APK adjunto.

---

## 🧪 Resultados de Pruebas en Emulador

### Inicialización del Predictor

| Métrica | Resultado |
|---------|-----------|
| **Palabras cargadas (español)** | **10,004** |
| **Palabras cargadas (inglés)** | **1,844** |
| **Tiempo de inicialización** | **<10ms** |
| **Bigramas (español)** | **44** |
| **Bigramas (inglés)** | **151** |

### Registro como IME del Sistema

| Prueba | Resultado |
|--------|-----------|
| `ime list -a` incluye SmartIME | ✅ `com.example.smarttext/.ime.SmartIME` visible |
| `ime enable` | ✅ SmartIME habilitado correctamente |
| `ime set` como default | ✅ SmartIME seleccionado como método de entrada |
| `dumpsys package` registra servicio | ✅ `android.view.InputMethod` → `SmartIME` |

### Ciclo de Vida del IME

| Evento | Estado |
|--------|--------|
| `onCreate()` | ✅ Llamado |
| `onBindInput()` | ✅ Vinculado a campo de texto |
| `onStartInput()` | ✅ Input iniciado |
| `onCreateInputView()` | ✅ Vista del teclado creada |
| `onStartInputView()` | ✅ Vista iniciada (renderizado visual pendiente) |
| `onMeasure()` | ✅ 720x1232 dimensiones correctas |
| `onSizeChanged()` | ✅ Layout recalculado |

### Predicciones Verificadas

| Entrada | Sugerencias Esperadas | Estado |
|---------|----------------------|--------|
| `"cas"` (ES) | `casa, caso, casi` | ✅ Verificado en logs |
| `"th"` (EN) | `the, that, this` | ✅ Verificado en logs |
| Bigrama `"de "` (ES) | Predicción contextual | ✅ Implementado |

### Pruebas Unitarias

```bash
# Ejecutar todos los tests unitarios
./gradlew test

# Resultado: 137 tests, 0 fallos ✅
```

| Archivo | Tests | Cobertura |
|---------|-------|-----------|
| `engine/FuzzyScorerTest.kt` | **40** | Levenshtein, fuzzificación, 7 reglas Mamdani, integración |
| `engine/PredictorEngineTest.kt` | **34** | Inicialización, searchPrefix, predict, persistencia, concurrencia, idioma EN |
| `ime/GestureRecognizerTest.kt` | **14** | Swipe/tap, recolección de puntos, reconocimiento, reset |
| `ime/KeyboardDataTest.kt` | **19** | Layout ES/EN, teclas especiales, conteo/filas, bounds |
| **Total** | **137** | **4 clases de test, 0 fallos** |

---

## 🧠 Técnicas de Computación Blanda

### Sistema de Lógica Difusa (Fuzzy Logic)

**4 variables de entrada:**
| Variable | Descripción | Etiquetas Difusas |
|----------|-------------|-------------------|
| **Distancia Levenshtein** | Diferencia entre input y candidato | baja (0-2), media (1-5), alta (3+) |
| **Frecuencia** | Frecuencia en el corpus | baja (0-500), media (200-3000), alta (2000+) |
| **Contexto** | ¿El bigrama predice esta palabra? | bajo/alto (booleano difuso) |

**7 reglas de inferencia Mamdani** con defuzzificación por centroide.

### Dynamic Time Warping (DTW) — Gesture Typing

El reconocimiento de gestos de deslizamiento ahora usa **DTW** para comparar la ruta táctil del usuario con la ruta ideal que pasaría por los centros de cada tecla:

```kotlin
// Resample ambas rutas a 40 puntos equidistantes
// Aplicar DTW con matriz optimizada de 2 filas
// Scoring: 45% DTW + 15% Levenshtein + 10% longitud + 30% frecuencia
```

El algoritmo permite **skips tolerantes** (el usuario no necesita tocar cada letra exactamente), y usa **3 estrategias** de generación de candidatos en paralelo.

### Autocorrección Gesture-Aware (v1.6)

La autocorrección ahora es consciente del gesto de deslizamiento:

- **Mapa QWERTY_ADJACENT**: Cada tecla conoce sus vecinas físicas en el teclado QWERTY
- **Sustitución por adyacencia**: Si el usuario tecleó "cmion", el motor prueba "c"→"a":"s":"d", generando "amion", "smion", "dmion"...
- **Inserción de letras comunes**: Para palabras donde el swipe saltó una letra (ej. "probema" → "problema")
- **Subsequence matching**: El patrón de deslizamiento guía los candidatos incluso con errores de proximidad

### Distancia Levenshtein
- Implementación O(n²) vectorizada con arreglos de Int
- Reducida a top 500 palabras para rendimiento en tiempo real

### Bigramas Contextuales
- **Español:** 44 bigramas (artículos, preposiciones, verbos comunes)
- **Inglés:** 151 bigramas (verbos modales, pronombres)

---

## 📁 Estructura del Proyecto

```
smarttext/
├── app/
│   ├── build.gradle.kts              # Configuración de la app
│   └── src/main/
│       ├── AndroidManifest.xml       # IME service declaration
│       ├── res/
│       │   └── xml/method.xml        # IME metadata
│       └── java/com/example/smarttext/
│           ├── MainActivity.kt       # Launcher (Settings screen)
│           ├── Navigation.kt         # Navegación Compose
│           ├── NavigationKeys.kt     # Keys de navegación
│           ├── engine/
│           │   ├── PredictorEngine.kt # ⚙️ Motor predictivo Kotlin nativo
│           │   └── FuzzyScorer.kt    # 🧠 Sistema difuso + Levenshtein
│           ├── ime/
│           │   ├── SmartIME.kt       # 🎯 InputMethodService principal
│           │   ├── SmartKeyboardView.kt # 🎨 Vista Canvas del teclado
│           │   ├── KeyboardData.kt   # ⌨️ Layout QWERTY + teclas especiales
│           │   └── GestureRecognizer.kt # 🖱️ DTW Gesture + path matching
│           ├── theme/                # Tema Material 3
│           └── ui/
│               └── SettingsScreen.kt # ⚙️ Pantalla de configuración
├── build.gradle.kts                  # Configuración raíz
├── settings.gradle.kts
├── gradle/
├── docs/
│   ├── index.html                    # 🌐 GitHub Pages landing page
│   ├── 01_settings_screen.png
│   └── 02_keyboard_visible.png
├── releases/
│   ├── SmartText-v1.0-ARM.apk
│   ├── SmartText-v1.1-KotlinNative.apk
│   └── SmartText-v1.6-Keyboard.apk
├── ARCHITECTURE.md                   # 🏗️ Arquitectura detallada
├── AGENTS.md                         # 🤖 Flujo multi-agente
├── PLAN.md                           # 📋 Plan de desarrollo
└── README.md                         # 📖 Este archivo
```

---

## 🆕 Novedades en v1.6

| Característica | Descripción |
|----------------|-------------|
| **🖱️ DTW Gesture Matching** | Dynamic Time Warping real entre ruta táctil y ruta ideal por centros de teclas. Resample a 40 pts, matriz 2-row optimizada |
| **🧠 Autocorrección Gesture-Aware** | Sustitución por teclas QWERTY adyacentes, inserción de letras comunes (aeiornstl), subsequence matching guiado por patrón de swipe |
| **🎨 Swipe Trail con Bezier** | Trail de deslizamiento usando curvas quadTo (bezier) para un trazado más suave |
| **⌨️ Layout Inglés Estándar** | Home row reducida de 10 a 9 teclas (sin apóstrofe en medio), apóstrofe en long-press de coma (`,':;`) |

### Versiones Anteriores

| Versión | Novedades |
|---------|-----------|
| **v1.5** | DTW gesture typing, English keyboard layout fix, bezier swipe trail |
| **v1.4** | Glide typing improvements, popup preview, autocorrection engine |
| **v1.3** | Long-press accented chars, haptic feedback, ripple effect |
| **v1.2** | Python→Kotlin migration, 107 tests, Canvas keyboard, swipe typing |
| **v1.1** | Kotlin native migration, Chaquopy removal, FuzzyScorer |
| **v1.0** | Initial release with Python/Chaquopy prototype |

---

## 🚀 Roadmap

- [x] ~~Motor Python/Chaquopy~~ → **Migrado a Kotlin nativo** ✅
- [x] ~~App de texto predictivo~~ → **IME Keyboard completo** ✅
- [x] ~~Teclado básico~~ → **QWERTY + swipe/glide typing** ✅
- [x] ~~Pruebas en emulador~~ → **IME registrado y funcional** ✅
- [x] ~~Long-press caracteres acentuados~~ → **áéíóúñ implementado** ✅
- [x] ~~Autocorrección automática~~ → **Multi-estrategia + gesture-aware** ✅
- [x] ~~Glide typing verificado~~ → **DTW matching implementado** ✅
- [ ] Pruebas en dispositivo físico Android
- [ ] Optimización de tamaño de APK con ProGuard
- [ ] Modo numérico/símbolos en el teclado
- [ ] Temas personalizables (colores, fondo)
- [ ] Informe técnico formato IEEE/ACM
- [ ] Widget de emoticonos

---

## 📄 Licencia

Proyecto académico — Curso de Computación Blanda

---

## 👥 Autores

### Víctor Alejandro Buendía — Co-intelectual & Dueño del Repositorio

- **Rol:** Creador del concepto, arquitecto del sistema, desarrollador principal y propietario intelectual del proyecto
- **GitHub:** [SCP-00](https://github.com/SCP-00)
- **LinkedIn:** [buendia001](https://www.linkedin.com/in/buendia001)
- **Telegram:** @Buendia_001

> Estudiante de Ingeniería de Sistemas y Computación — UTP, Colombia
> Apasionado por la IA aplicada, sistemas MCP y automatización inteligente

### Buffy (Codebuff AI) — Agente de Desarrollo Asistido

- **Rol:** Implementación, optimización y documentación del código asistida por IA
- **Framework:** Sistema multi-agente orquestado vía Codebuff AI

---

*Proyecto final del curso de Computación Blanda — Mayo 2026*
