# 📋 Plan de Desarrollo — SmartText

> **Proyecto:** Corrector ortográfico inteligente con predicción de texto offline
> **Curso:** Computación Blanda
> **Técnicas:** Sistemas Difusos, Distancia Levenshtein, Trie, N-gramas
> **Plataforma:** Android (Kotlin + Jetpack Compose + Chaquopy Python)

---

## 📊 Estado del Proyecto

| Fase | Estado | Descripción |
|------|--------|-------------|
| **Fase 0** | ✅ Completa | Definición de alcance y restricciones |
| **Fase 1** | ✅ Completa | Arquitectura técnica diseñada |
| **Fase 2** | ✅ Completa | Corpus bilingüe generado (EN: 9,894 unigramas + 759 bigramas, ES: 10,131 unigramas + 295 bigramas) |
| **Fase 3** | ✅ Completa | Motor predictivo offline (Trie + búsqueda por prefijo + bigramas contextuales) |
| **Fase 4** | ✅ Completa | Sistema de Lógica Difusa + Distancia Levenshtein |
| **Fase 5** | ✅ Completa | UI/UX con Jetpack Compose Material 3 |
| **Fase 6** | ✅ Completa | Persistencia local (user_data.json) y aprendizaje incremental |
| **Fase 7** | 🔄 Pendiente | Experimentación y evaluación de métricas |
| **Fase 8** | 🔄 Pendiente | Optimización para Android gama baja |
| **Fase 9** | 🔄 Pendiente | Testing funcional en emuladores |
| **Fase 10** | 🔄 Pendiente | Entrega final académica |

---

## 🎯 Fase 0 — Definición de Alcance

### Problema
Los correctores ortográficos actuales requieren conexión a internet o son demasiado pesados para dispositivos de gama baja.

### Objetivo
Aplicación Android 100% offline que prediga y corrija texto en español e inglés usando técnicas de Computación Blanda.

### Restricciones Técnicas
- ✅ **Offline total:** Sin conexión a internet requerida
- ✅ **Gama baja:** Optimizado para 2-4 GB RAM, CPU de 2-4 núcleos
- ✅ **Bilingüe:** Soporte completo para español e inglés
- ✅ **Tamaño APK:** Actualmente 58MB (optimizable)
- ✅ **Respuesta:** Predicción en <50ms

---

## 🏗️ Fase 1 — Arquitectura

### Stack Tecnológico

| Componente | Tecnología |
|---|---|
| **UI Android** | Kotlin + Jetpack Compose + Material 3 |
| **Motor IA** | Python 3.10 vía Chaquopy 17.0.0 |
| **Build** | Gradle 9.1 + AGP 9.0.1 + Kotlin 2.3.20 |
| **SDK Android** | API 36 (minSdk 24, targetSdk 36) |

### Módulos del Sistema

```
┌─────────────────────────────────────────────┐
│            UI Layer (Jetpack Compose)        │
│  PredictorScreen · Selector Idioma · Chips   │
├─────────────────────────────────────────────┤
│         Python Bridge (Chaquopy)             │
├─────────────────────────────────────────────┤
│       Prediction Engine (Python)             │
│  ┌─────────┐ ┌──────────┐ ┌──────────────┐  │
│  │  Trie   │ │ Bigramas │ │ Fuzzy Logic   │  │
│  └─────────┘ └──────────┘ └──────────────┘  │
├─────────────────────────────────────────────┤
│           Persistencia Local                 │
│  user_data.json · corpus.json                │
└─────────────────────────────────────────────┘
```

---

## 📚 Fase 2 — Corpus Bilingüe

### Fuentes de Datos
- **Inglés:** Google 10,000 palabras más frecuentes (sin groserías)
- **Español:** Listado general de ~80,000 palabras

### Estadísticas del Corpus

| Idioma | Unigramas | Bigramas |
|--------|-----------|----------|
| **Inglés** | 9,894 | 759 |
| **Español** | 10,131 | 295 |

### Distribución Zipfiana
Las frecuencias siguen una distribución Zipfiana donde `freq ∝ 1/(rank+1)`, asegurando que las palabras más comunes tengan mayor peso en las sugerencias.

### Bigramas Contextuales
Los bigramas se generaron a partir de patrones gramaticales reales:
- **Inglés:** Verbos modales + infinitivos, pronombres + verbos, artículos + sustantivos
- **Español:** Artículos + sustantivos, preposiciones + artículos, verbos + complementos

---

## ⚙️ Fase 3 — Motor Predictivo

### Componentes

#### 1. Trie (Árbol de Prefijos)
- Búsqueda O(k) donde k es la longitud del prefijo
- Almacena palabras con sus frecuencias
- Búsqueda por prefijo con ordenamiento por frecuencia descendente

#### 2. Búsqueda por Prefijo
- Encuentra todas las palabras que comienzan con el texto ingresado
- Retorna resultados ordenados por frecuencia

#### 3. Predicción Contextual (Bigramas)
- Cuando el usuario termina una palabra y comienza la siguiente
- Sugiere palabras basadas en bigramas (ej: "how" → "to", "i" → "am")

#### 4. Aprendizaje Local
- Actualiza frecuencias de palabras seleccionadas por el usuario
- Persiste en `user_data.json` en el directorio de archivos de la app

### Flujo de Predicción

```
Usuario escribe → Extraer palabra actual y anterior
    ├── ¿Hay palabra anterior Y palabra actual vacía?
    │   └── Buscar bigramas → Sugerir siguientes palabras
    ├── ¿Hay palabra actual?
    │   ├── Buscar en Trie por prefijo
    │   ├── Aplicar Fuzzy Logic para rankear
    │   └── Filtrar por score ≥ 10
    └── ¿Sin resultados?
        └── Fallback: Levenshtein + Fuzzy sobre top 500 palabras
```

---

## 🧠 Fase 4 — Computación Blanda (Sistema Difuso)

### Variables de Entrada

| Variable | Descripción | Etiquetas Difusas |
|----------|-------------|-------------------|
| **Distancia Levenshtein** | Diferencia entre input y candidato | baja (0-2), media (1-5), alta (3+) |
| **Frecuencia** | Frecuencia en el corpus | baja (0-500), media (200-3000), alta (2000+) |
| **Contexto** | ¿El bigrama predice esta palabra? | bajo/alto (booleano difuso) |

### Variable de Salida
- **Score de Sugerencia:** 0-100 (malo → aceptable → bueno → excelente)

### Reglas Difusas (Inferencia Mamdani)

| Regla | Antecedente | Consecuente |
|-------|-------------|-------------|
| R1 | Lev IS baja AND Frec IS alta | Excelente |
| R2 | Lev IS baja AND Ctx IS alto | Excelente |
| R3 | Lev IS baja AND Frec IS media | Buena |
| R4 | Lev IS media AND Frec IS alta | Buena |
| R5 | Lev IS media AND Frec IS media | Aceptable |
| R6 | Lev IS alta | Malo |
| R7 | Default (basado en frecuencia) | Aceptable |

### Defuzzificación
Método del centroide con pesos: malo=25, aceptable=50, buena=75, excelente=100.

---

## 📱 Fase 5 — UI/UX

### Pantalla Principal (PredictorScreen)
- **TopAppBar:** Título "SmartText"
- **Selector de idioma:** SegmentedButton (Español/English)
- **Campo de texto:** OutlinedTextField multilínea (3-8 líneas)
- **Sugerencias:** FlowRow con AssistChips cliqueables
- **Footer:** Estado del predictor, información del sistema

### Interacciones
- **Autocompletar:** Click en sugerencia reemplaza la palabra actual
- **Aprendizaje:** Cada selección incrementa la frecuencia de la palabra
- **Cambio de idioma:** Recarga el predictor con el nuevo corpus
- **Feedback visual:** Card de error si el predictor falla

---

## 💾 Fase 6 — Persistencia

### Archivos Locales
- `user_data.json` → Frecuencias personalizadas del usuario
- `corpus.json` → Corpus bilingüe empaquetado en la APK

### Formato user_data.json
```json
{
  "palabra_usada": 45,
  "otra_palabra": 12
}
```

### Aprendizaje Incremental
- Cada palabra seleccionada incrementa su frecuencia en +10
- Se persiste inmediatamente después de cada selección
- Se carga al iniciar el predictor

---

## 🔬 Fase 7 — Experimentación (Pendiente)

### Experimentos Planeados

| Experimento | Descripción | Métrica |
|-------------|-------------|---------|
| Sin Levenshtein vs Con Levenshtein | Impacto de la distancia de edición | Precisión top-3 |
| Sin Fuzzy vs Con Fuzzy | Impacto del sistema difuso | Precisión top-3 |
| top-3 vs top-5 | Diferentes tamaños de sugerencias | Tasa de acierto |
| Unigramas vs Bigramas | Impacto del contexto | Precisión predictiva |
| Español vs Inglés | Comparación entre idiomas | Precisión por idioma |

### Métricas a Recolectar
- **Precisión top-1:** ¿La primera sugerencia es la correcta?
- **Precisión top-3:** ¿La correcta está entre las 3 primeras?
- **Tiempo de respuesta:** Latencia por predicción
- **Uso de RAM:** Memoria consumida por el predictor
- **Tamaño del modelo:** Corpus + datos de usuario

---

## 📈 Fase 8 — Optimización (Pendiente)

### Áreas de Optimización
- **Tamaño del APK:** Reducir corpus.json (actualmente ~395KB)
- **Velocidad de inicio:** Carga lazy del corpus
- **Consumo de RAM:** Limitar vocabulario en memoria
- **Trie:** Implementar compresión de nodos

---

## 🧪 Fase 9 — Testing (Pendiente)

### Escenarios de Prueba

| Perfil | CPU | RAM | SO |
|--------|-----|-----|----|
| **Gama Baja** | 2 núcleos | 1.5 GB | Android 11 (API 30) |
| **Gama Media** | 4 núcleos | 4 GB | Android 15 (API 36) |

### Pruebas Unitarias (Python)
- `tests/run_predictor.py` — Pruebas de predicción local

### Pruebas Android
- `MainScreenViewModelTest.kt` — Pruebas de ViewModel
- `MainScreenTest.kt` — Pruebas de UI instrumentadas

---

## 📝 Fase 10 — Entrega Final (Pendiente)

### Checklist de Entregables
- [x] Código fuente (GitHub)
- [ ] APK funcional
- [ ] Informe técnico (PDF)
- [ ] Video demo (5-10 min)
- [ ] Presentación final
- [ ] Documentación completa

---

## 📁 Estructura del Proyecto

```
smarttext/
├── app/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/example/smarttext/
│       │   │   ├── MainActivity.kt
│       │   │   ├── Navigation.kt
│       │   │   ├── NavigationKeys.kt
│       │   │   ├── data/DataRepository.kt
│       │   │   ├── theme/{Color,Theme,Type}.kt
│       │   │   └── ui/
│       │   │       ├── PredictorScreen.kt
│       │   │       └── main/{MainScreen,MainScreenViewModel}.kt
│       │   └── python/
│       │       ├── ai/fuzzy_logic.py
│       │       ├── engine/{predictor.py,trie.py,corpus.json}
│       │       ├── nlp/build_corpus.py
│       │       └── tests/run_predictor.py
│       ├── test/   (unit tests)
│       └── androidTest/  (instrumented tests)
├── build.gradle.kts
├── settings.gradle.kts
└── gradle/
```

---

## 🚀 Cómo Ejecutar

### 1. Generar Corpus
```bash
cd app/src/main/python
python nlp/build_corpus.py
```

### 2. Probar Predictor Local
```bash
cd app/src/main/python
python tests/run_predictor.py
```

### 3. Compilar APK
```bash
cd smarttext
./gradlew --no-configuration-cache assembleDebug
```

### 4. Instalar en Emulador
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```
