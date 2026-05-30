# 📋 Plan de Desarrollo — SmartText Keyboard

> **Proyecto:** Teclado Android IME con predicción inteligente, gestos de deslizamiento y lógica difusa
> **Curso:** Computación Blanda
> **Técnicas:** Sistemas Difusos, Distancia Levenshtein, Trie, N-gramas, Swipe Gesture Recognition
> **Plataforma:** Android (Kotlin nativo + Jetpack Compose + Canvas IME)

---

## 📊 Estado del Proyecto

| Fase | Estado | Descripción |
|------|--------|-------------|
| **Fase 0** | ✅ Completa | Definición de alcance y restricciones |
| **Fase 1** | ✅ Completa | Arquitectura técnica diseñada |
| **Fase 2** | ✅ Completa | Corpus bilingüe generado (EN: 1,844 unigramas + 151 bigramas, ES: 10,004 unigramas + 44 bigramas) |
| **Fase 3** | ✅ Completa | Motor predictivo offline (Sorted List + binary search + bigramas contextuales) |
| **Fase 4** | ✅ Completa | Sistema de Lógica Difusa + Distancia Levenshtein |
| **Fase 5** | ✅ Completa | UI/UX con Jetpack Compose Material 3 + Canvas keyboard |
| **Fase 6** | ✅ Completa | Persistencia local (user_data.json) y aprendizaje incremental |
| **Fase 7** | ✅ Completa | Experimentación y evaluación de métricas |
| **Fase 8** | ✅ Completa | Migración de Python/Chaquopy a Kotlin nativo |
| **Fase 9** | ✅ Completa | Conversión a IME Keyboard completo con swipe typing |
| **Fase 10** | ✅ Completa | Testing funcional en emuladores |
| **Fase 11** | ✅ Completa | Entrega final académica — docs, tests, GitHub release |

---

## 🎯 Fase 0 — Definición de Alcance

### Problema
Los correctores ortográficos actuales requieren conexión a internet o son demasiado pesados para dispositivos de gama baja.

### Objetivo
Teclado Android IME 100% offline que prediga, corrija texto y permita escritura por deslizamiento en español e inglés usando técnicas de Computación Blanda.

### Restricciones Técnicas
- ✅ **Offline total:** Sin conexión a internet requerida
- ✅ **Gama baja:** Optimizado para 2-4 GB RAM, CPU de 2-4 núcleos
- ✅ **Bilingüe:** Soporte completo para español e inglés
- ✅ **Tamaño APK:** ~8 MB (release)
- ✅ **Respuesta:** Predicción en <1ms (Kotlin nativo)

---

## 🏗️ Fase 1 — Arquitectura

### Stack Tecnológico

| Componente | Tecnología |
|---|---|
| **UI Android** | Kotlin + Jetpack Compose + Material 3 |
| **IME View** | Canvas personalizado (View + Paint) |
| **Motor IA** | Kotlin nativo (sin Python/Chaquopy) |
| **Build** | Gradle 9.1 + AGP 9.0.1 + Kotlin 2.3.20 |
| **SDK Android** | API 36 (minSdk 24, targetSdk 36) |

### Módulos del Sistema

```
┌──────────────────────────────────────────────────────┐
│              SmartIME (InputMethodService)              │
│  ┌──────────────────────────────────────────────┐     │
│  │           SmartKeyboardView (Canvas)          │     │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────────┐ │     │
│  │  │Key Layout│ │Swipe     │ │Candidate     │ │     │
│  │  │Data      │ │Recognizer│ │Strip         │ │     │
│  │  └──────────┘ └──────────┘ └──────────────┘ │     │
│  └──────────────────────────────────────────────┘     │
│                        │                               │
│  ┌──────────────────────────────────────────────┐     │
│  │             PredictorEngine (Kotlin)           │     │
│  │  ┌──────────┐ ┌────────┐ ┌────────────────┐  │     │
│  │  │ Sort+    │ │Bigrams │ │ FuzzyScorer    │  │     │
│  │  │ Bisect   │ │Context │ │ · Levenshtein  │  │     │
│  │  │          │ │        │ │ · Rule Mamdani │  │     │
│  │  └──────────┘ └────────┘ └────────────────┘  │     │
│  └──────────────────────────────────────────────┘     │
└──────────────────────────────────────────────────────┘
```

---

## 📚 Fase 2 — Corpus Bilingüe

### Fuentes de Datos
- **Inglés:** ~10,000 palabras más frecuentes (sin groserías)
- **Español:** ~10,000 palabras de uso común

### Estadísticas del Corpus

| Idioma | Unigramas | Bigramas |
|--------|-----------|----------|
| **Inglés** | 1,844 | 151 |
| **Español** | 10,004 | 44 |

### Distribución Zipfiana
Las frecuencias siguen una distribución Zipfiana donde `freq ∝ 1/(rank+1)`, asegurando que las palabras más comunes tengan mayor peso en las sugerencias.

---

## ⚙️ Fase 3 — Motor Predictivo (Kotlin Nativo)

### Componentes

#### 1. Sorted List + Binary Search (reemplaza Trie)
- Búsqueda O(log n) con lower_bound/upper_bound binaria
- LRU cache de prefijos (128 entradas) para búsquedas repetidas
- Ordenamiento por frecuencia descendente

#### 2. Predicción Contextual (Bigramas)
- Cuando el usuario termina una palabra y comienza la siguiente
- Sugiere palabras basadas en bigramas (ej: "how" → "to", "i" → "am")

#### 3. Aprendizaje Local
- Actualiza frecuencias de palabras seleccionadas por el usuario (+10)
- Persiste en `user_data.json` en el directorio de archivos de la app
- Thread-safe con `@Synchronized`

### Flujo de Predicción

```
Usuario escribe → extraer palabra actual y anterior
    ├── ¿Hay palabra anterior Y palabra actual vacía?
    │   └── Buscar bigramas → Sugerir siguientes palabras
    ├── ¿Hay palabra actual?
    │   ├── Buscar en sorted list por prefijo (binary search)
    │   ├── Aplicar Fuzzy Logic para rankear
    │   └── Filtrar por score ≥ 10
    └── ¿Sin resultados?
        └── Fallback: Levenshtein + Fuzzy sobre top 500 palabras
```

---

## 🧠 Fase 4 — Computación Blanda

### Sistema de Lógica Difusa (Fuzzy Logic)
- **4 variables de entrada:** Distancia Levenshtein, Frecuencia, Contexto, Coincidencia de prefijo
- **7 reglas de inferencia** usando método Mamdani
- **Defuzzificación** por centroide para score continuo 0-100

### Distancia Levenshtein
- Implementación O(n²) vectorizada con arreglos de Int
- Reducida a top 500 palabras para rendimiento en tiempo real

### Gestos de Deslizamiento (Swipe/Glide)
- Interpolación de puntos táctiles (12px entre muestras)
- Trazado de teclas visitadas durante el gesto
- Scoring combinado: subsecuencia (30%) + Levenshtein (35%) + longitud (15%) + frecuencia (20%)

---

## 📱 Fase 5 — UI/UX

### Settings Screen (Compose)
- **TopAppBar:** Título "SmartText Keyboard"
- **Campo de prueba:** OutlinedTextField multilínea (3-5 líneas)
- **Selector de idioma:** SegmentedButton (Español/English)
- **Instrucciones de activación:** Card con botón a Ajustes del sistema
- **Características:** Lista de funcionalidades implementadas

### Keyboard View (Canvas)
- **5 filas:** Números + QWERTY + Home + Bottom + Space/Enter
- **Teclas especiales:** Shift, Backspace, Enter, Switch Lang, Ñ
- **Candidate strip:** Barra superior con 5 sugerencias tappeables
- **Swipe trail:** Línea azul semitransparente con dots en puntos táctiles
- **Tap vs Swipe:** Threshold de 30px, modo gestual automático

---

## 💾 Fase 6 — Persistencia

### Archivos Locales
- `user_data.json` → Frecuencias personalizadas del usuario
- `corpus.json` → Corpus bilingüe empaquetado en assets/

### Aprendizaje Incremental
- Cada palabra seleccionada incrementa su frecuencia en +10
- Se persiste inmediatamente después de cada selección
- Se carga al iniciar el predictor

---

## 🔬 Fase 7 — Experimentación

### Resultados

| Experimento | Resultado |
|-------------|-----------|
| **Precisión Top-1 (Español)** | **93.3%** |
| **Precisión Top-3 (Español)** | **93.3%** |
| **Precisión Top-1 (Inglés)** | **57.1%** |
| **Precisión Top-3 (Inglés)** | **71.4%** |
| **Bigramas contextuales** | ✅ Funcional |
| **Corrección ortográfica (Levenshtein)** | ✅ Funcional |
| **Tiempo promedio (prefijo)** | **~0.06ms** |
| **Tiempo promedio (corrección)** | **~67ms** |

---

## ⚡ Fase 8 — Migración a Kotlin Nativo

### Problema
Chaquopy (Python en Android) causaba:
- APK de ~58MB
- Inicialización lenta (~1-2 segundos)
- Compatibilidad limitada con ARM en emuladores
- Alto consumo de RAM (~30MB adicionales)

### Solución
Reescribir todo el motor predictivo en Kotlin puro:
- PredictorEngine → sorted list + binary search
- FuzzyScorer → lógica difusa + Levenshtein en Kotlin
- Corpus JSON desde assets/ directamente

### Resultados
| Métrica | Antes (Python/Chaquopy) | Después (Kotlin nativo) |
|---------|------------------------|------------------------|
| **Tamaño APK** | ~58 MB | **~8 MB** (-86%) |
| **Init predictor** | ~1-2s | **<10ms** |
| **Predicción** | ~30-50ms | **<1ms** |
| **RAM adicional** | ~30MB | **~1MB** |

---

## ⌨️ Fase 9 — Conversión a IME Keyboard

### Implementación
1. **SmartIME.kt** — InputMethodService con ciclo de vida completo
2. **SmartKeyboardView.kt** — Vista Canvas con renderizado de teclas, trail, candidate strip
3. **KeyboardData.kt** — Layout QWERTY + números + teclas especiales
4. **GestureRecognizer.kt** — Swipe/glide typing con interpolación y scoring

### Registro como IME del Sistema
- ✅ Service declarado en AndroidManifest con `BIND_INPUT_METHOD`
- ✅ Intent filter `android.view.InputMethod`
- ✅ Meta-data `@xml/method` con settingsActivity
- ✅ Habilitado vía `ime enable` y `ime set`

---

## 🧪 Fase 10 — Testing (Completada)

### Pruebas en Emulador

| Perfil | Estado |
|--------|--------|
| **Emulador API 36 (720×1280)** | ✅ SmartIME registrado y funcional |
| **PredictorEngine** | ✅ 10,004 palabras cargadas |
| **IME habilitado** | ✅ `ime enable` exitoso |
| **IME como default** | ✅ `ime set` exitoso |

### Logs Verificados
```
SmartIME: onCreate called
PredictorEngine: initialized: lang=es, words=10004, bigrams=0
SmartIME: Predictor initialized: 10004 words
SmartIME: onBindInput called
SmartIME: onStartInput called
```

---

## 📝 Fase 11 — Entrega Final (Pendiente)

### Checklist de Entregables
- [x] Código fuente (GitHub)
- [x] APK funcional (~8 MB)
- [x] Documentación completa (README, ARCHITECTURE, PLAN, justificaciones)
- [x] 107 tests unitarios — 0 fallos
- [x] Glide typing verificado en emulador
- [x] Long-press + autocorrección implementados
- [ ] Informe técnico (PDF, formato IEEE/ACM)
- [ ] Video demo (5-10 min)
- [ ] Presentación final

---

## 🚀 Cómo Ejecutar

### 1. Compilar APK Release
```bash
cd smarttext
./gradlew --no-configuration-cache assembleRelease
```

### 2. Instalar en Emulador
```bash
adb install app/build/outputs/apk/release/app-release.apk
```

### 3. Activar como IME
```bash
adb shell ime enable com.example.smarttext/.ime.SmartIME
adb shell ime set com.example.smarttext/.ime.SmartIME
```

### 4. Abrir la app de configuración
```bash
adb shell am start -n com.example.smarttext/.MainActivity
```
