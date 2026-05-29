# 🤖 AGENTS.md — Flujo de Trabajo Multi-Agente

> Documentación del sistema de agentes de IA utilizados para el desarrollo de SmartText.
> Este archivo describe cómo los diferentes agentes especializados colaboran para
> diagnosticar, implementar, probar y documentar el proyecto.

---

## 🧠 Filosofía del Sistema Multi-Agente

El desarrollo de SmartText utiliza un enfoque de **orquestación multi-agente** donde
agentes especializados colaboran secuencial y paralelamente para maximizar la calidad
del código y la eficiencia del desarrollo.

### Principios Clave
1. **Entender antes de actuar** — Cada agente recopila contexto antes de modificar archivos
2. **Calidad sobre velocidad** — Pocos agentes bien informados > muchos agentes apresurados
3. **Validación continua** — Cada cambio es revisado por un agente especializado
4. **Paralelización inteligente** — Agentes independientes se ejecutan simultáneamente

---

## 👤 Roles de Agentes

### 1. Orchestrator (Buffy)
**Rol:** Agente principal que coordina todo el flujo de trabajo.

**Responsabilidades:**
- Interpretar los requisitos del usuario
- Descomponer tareas complejas en subtareas
- Spawnear agentes especializados en el orden correcto
- Sintetizar resultados y presentarlos al usuario
- Mantener el plan de ejecución (write_todos)

**Patrón de uso:**
```
Usuario → Buffy → [Agentes especializados] → Buffy → Usuario
```

### 2. File Picker (Selector de Archivos)
**Rol:** Encuentra archivos relevantes en el código fuente.

**Cuándo usarlo:**
- Al iniciar una nueva tarea
- Cuando se necesita entender el contexto de un módulo
- Para encontrar archivos relacionados con un tema

**Ejemplo:**
```
Prompt: "Encuentra archivos relacionados con la navegación en Android"
Resultado: Navigation.kt, NavigationKeys.kt, MainActivity.kt
```

### 3. Code Searcher (Buscador de Código)
**Rol:** Busca patrones específicos en el código fuente usando ripgrep.

**Cuándo usarlo:**
- Para encontrar todas las referencias a una función/clase
- Para entender patrones de uso de una API
- Para buscar imports y dependencias

**Ejemplo:**
```
Búsqueda: "PredictorScreen" en *.kt
Resultado: Usos en PredictorScreen.kt, Navigation.kt
```

### 4. Basher (Ejecutor de Terminal)
**Rol:** Ejecuta comandos en la terminal y analiza la salida.

**Cuándo usarlo:**
- Compilar el proyecto (Gradle)
- Ejecutar scripts Python
- Probar el predictor local
- Verificar estructura de archivos
- Ejecutar emuladores Android

**Ejemplo:**
```
Comando: python tests/run_predictor.py
Análisis: Verificar predicciones, scores difusos, errores
```

### 5. Code Reviewer (Revisor de Código)
**Rol:** Revisa cambios en el código y proporciona feedback crítico.

**Cuándo usarlo:**
- Después de cualquier modificación significativa
- Antes de compilar
- Para identificar problemas potenciales en runtime

**Ejemplo de feedback:**
```
- ❌ Imports no utilizados
- ⚠️ Manejo de excepciones silencioso
- ✅ Lógica de autocompletado correcta
```

### 6. Researcher Web / Docs
**Rol:** Investiga documentación técnica y librerías externas.

**Cuándo usarlo:**
- Para verificar APIs de librerías
- Para encontrar soluciones a problemas técnicos
- Para investigar configuraciones óptimas

---

## 🔄 Flujo de Trabajo Típico

### Fase 1: Diagnóstico
```
Usuario: "Hay un bug en el predictor"
         │
         ▼
[File Picker] ─── Encuentra archivos relevantes
[Code Searcher] ─ Busca patrones relacionados
[Basher] ──────── Ejecuta pruebas para reproducir el bug
         │
         ▼
[Orchestrator] ── Sintetiza hallazgos y planifica
```

### Fase 2: Implementación
```
[Orchestrator] ── Divide la tarea en subtareas
         │
    ┌────┼────┐
    ▼    ▼    ▼
[Edit 1] [Edit 2] [Edit 3]
    │    │    │
    └────┼────┘
         ▼
[Code Reviewer] ── Revisa todos los cambios
         │
         ▼
[Basher] ──────── Compila y ejecuta pruebas
```

### Fase 3: Validación
```
[Basher] ──────── Compila el proyecto
[Basher] ──────── Ejecuta pruebas unitarias
[Code Reviewer] ─ Revisión final
         │
         ▼
[Orchestrator] ── Resume resultados al usuario
```

---

## 📋 Ejemplo: Corrección de Bug en Fuzzy Logic

### Paso 1: Diagnóstico
```
1. Basher → Ejecutar tests → "the" no aparece para prefijo "th"
2. File Picker → Encontrar fuzzy_logic.py y predictor.py
3. Code Searcher → Buscar funciones de membresía
```

### Paso 2: Implementación
```
1. Leer fuzzy_logic.py → Identificar bug en funciones de membresía
2. Reescribir fuzzy_logic.py → Scores continuos 0-100
3. Ajustar predictor.py → Bajar umbral de score
```

### Paso 3: Validación
```
1. Basher → Ejecutar tests → "the" aparece para prefijo "th" ✅
2. Code Reviewer → Revisar cambios
3. Basher → Compilar APK → BUILD SUCCESSFUL ✅
```

---

## ⚙️ Comandos Útiles para Agentes

### Python Local
```bash
# Probar predictor
cd app/src/main/python && python tests/run_predictor.py

# Generar corpus
cd app/src/main/python && python nlp/build_corpus.py

# Probar fuzzy logic
cd app/src/main/python && python -c "from ai.fuzzy_logic import FuzzyScorer; ..."
```

### Android Build
```bash
# Compilar APK (con --no-configuration-cache para evitar bugs de Chaquopy)
cd smarttext && ./gradlew --no-configuration-cache assembleDebug

# Limpiar build
cd smarttext && ./gradlew clean
```

### Git
```bash
# Estado
git status

# Commit
git add . && git commit -m "mensaje"

# Push
git push origin main
```

---

## 🎯 Mejores Prácticas

### Para el Orchestrator (Buffy)
1. **Siempre leer archivos antes de editarlos** — No asumas el contenido
2. **Usar write_todos para tareas complejas** — Mantén el plan visible
3. **Parallelizar agentes independientes** — Maximiza eficiencia
4. **Spawnear code-reviewer después de cambios** — Atrapa errores temprano
5. **Preguntar al usuario en decisiones críticas** — Usa ask_user

### Para Agentes Especializados
1. **set_output es obligatorio** — Sin esto, el resultado se pierde
2. **Sé específico en lo que buscas** — Prompts claros = resultados precisos
3. **Reporta problemas encontrados** — No solo lo que funciona

---

## 📊 Métricas de Eficiencia del Sistema Multi-Agente

| Operación | Tiempo Típico | Agentes Involucrados |
|-----------|---------------|---------------------|
| Diagnóstico de bug | 2-5 min | File Picker + Code Searcher + Basher |
| Corrección simple | 1-3 min | Editor + Code Reviewer + Basher |
| Feature compleja | 5-15 min | Múltiples agentes en paralelo |
| Compilación APK | 10-60 seg | 1 Basher |
| Documentación | 3-10 min | File Picker + Writer |
