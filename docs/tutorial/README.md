# 📖 Tutorial de SmartText Keyboard — IME Predictivo con Swipe

> **Teclado Android IME con predicción inteligente y escritura por deslizamiento**
>
> 100% Kotlin nativo · Sin Python · Bilingüe (Español / English) · 100% offline

---

## 📋 Índice

1. [¿Qué es SmartText Keyboard?](#-qué-es-smarttext-keyboard)
2. [Instalación](#-instalación)
3. [Cómo activar el teclado](#-cómo-activar-el-teclado)
4. [Cómo usar el teclado](#-cómo-usar-el-teclado)
5. [Predicción de texto](#-predicción-de-texto)
6. [Escritura por deslizamiento (Swipe)](#-escritura-por-deslizamiento-swipe)
7. [Preguntas frecuentes](#-preguntas-frecuentes)

---

## 🧠 ¿Qué es SmartText Keyboard?

SmartText Keyboard es un **teclado Android IME** (Input Method Editor) que reemplaza el teclado estándar de tu dispositivo con funciones inteligentes:

### Técnicas implementadas:

| Técnica | Propósito |
|---------|-----------|
| **🔍 Sorted List + Binary Search** | Búsqueda rápida de palabras por prefijo (O(log n)) |
| **📊 Bigramas** | Predicción contextual basada en la palabra anterior |
| **🧮 Lógica Difusa (Fuzzy Logic)** | Scoring continuo con 4 variables y 7 reglas Mamdani |
| **📏 Distancia Levenshtein** | Corrección ortográfica automática |
| **🖱️ Reconocimiento de gestos** | Escritura por deslizamiento (swipe/glide typing) |
| **🎯 Aprendizaje local** | Se adapta a las palabras que más usas |

---

## 📲 Instalación

### Requisitos
- Dispositivo Android con API 24+ (Android 7.0 o superior)
- 2-4 GB RAM recomendados

### Opción 1: Desde ADB
```bash
# Instalar el APK
adb install app/build/outputs/apk/release/app-release.apk
```

### Opción 2: Compilar desde código fuente
```bash
cd smarttext
./gradlew --no-configuration-cache assembleRelease
adb install app/build/outputs/apk/release/app-release.apk
```

---

## ⚙️ Cómo activar el teclado

### Método 1: Desde la app (recomendado)

1. Abre **SmartText Keyboard** desde el launcher de apps
2. Presiona el botón **"Activar en Ajustes"**
3. En Ajustes → Idioma e introducción de texto → Teclado virtual
4. Activa **"SmartText Keyboard"**
5. Selecciona SmartText como método de entrada predeterminado

### Método 2: Desde ADB
```bash
# Habilitar SmartIME como método de entrada
adb shell ime enable com.example.smarttext/.ime.SmartIME

# Establecer como teclado predeterminado
adb shell ime set com.example.smarttext/.ime.SmartIME
```

### Verificar activación
```bash
# Ver que SmartIME está en la lista
adb shell ime list -a

# Verificar que es el método activo
adb shell settings get secure default_input_method
# → com.example.smarttext/.ime.SmartIME
```

---

## ⌨️ Cómo usar el teclado

### Disposición del teclado

```
┌──────────────────────────────────────┐
│  casa     caso     casi     cosa     │ ← Candidate strip
├──────────────────────────────────────┤
│ 1   2   3   4   5   6   7   8   9   0 │ ← Números
├──────────────────────────────────────┤
│ q   w   e   r   t   y   u   i   o  p │ ← QWERTY
├──────────────────────────────────────┤
│ a   s   d   f   g   h   j   k   l  ñ │ ← Home row + Ñ
├──────────────────────────────────────┤
│ ⇧   z   x   c   v   b   n   m     ⌫ │ ← Shift + letras + Backspace
├──────────────────────────────────────┤
│ EN    ,   ___________   .          ↵ │ ← Lang, coma, espacio, punto, enter
└──────────────────────────────────────┘
```

### Teclas especiales

| Tecla | Acción |
|-------|--------|
| **⇧** | Shift: un toque → mayúscula, dos toques → bloqueo |
| **⌫** | Backspace: borra carácter anterior |
| **🌐 / EN / ES** | Cambia idioma entre Español e Inglés |
| **Espacio** | Inserta espacio |
| **.** | Punto (cierra palabra actual y sugiere) |
| **,** | Coma |
| **↵** | Enter (salto de línea) |
| **Ñ** | Letra Ñ (español) |

---

## 🔮 Predicción de texto

Mientras escribes, la **candidate strip** (barra superior) muestra hasta 5 sugerencias:

1. **Escribe** — Por ejemplo, "cas"
2. **Mira las sugerencias** — Aparecen en la barra gris arriba del teclado
3. **Toca una sugerencia** — La palabra se autocompleta

### Ejemplo: Español
```
Escribes: "cas"
Sugerencias: [casa] [caso] [casi] [cosa] [casos]
    ↑ Toca "casa" → se completa automáticamente
```

### Ejemplo: Inglés
```
Escribes: "th"
Sugerencias: [the] [that] [this] [there] [they]
    ↑ Toca "the" → se completa automáticamente
```

### ¿Cómo funciona?
El sistema de predicción usa 4 etapas:

```
Input: "cas" ──→ ① Buscar palabras por prefijo (binary search)
                 ② Aplicar Lógica Difusa (Levenshtein + frecuencia + contexto)
                 ③ Ordenar por score (0-100)
                 ④ Mostrar Top-5 sugerencias
```

---

## 🖱️ Escritura por deslizamiento (Swipe/Glide)

Puedes escribir palabras completas **deslizando el dedo** sobre las letras sin levantar:

### Cómo hacerlo

1. Coloca el dedo en la **primera letra** de la palabra
2. **Desliza** sobre las letras siguientes
3. **Levanta** el dedo al terminar
4. La palabra aparece automáticamente

### Ejemplo

```
Para escribir "casa":
  ① Toca 'c'
  ② Desliza → 'a' → 's' → 'a'
  ③ Levanta el dedo
  ④ ¡"casa" aparece en el texto!
```

### Trail visual
Mientras deslizas, verás una **línea azul semitransparente** que sigue tu dedo, con puntos en cada letra detectada. Esto te ayuda a ver qué letras estás recorriendo.

### Consejos
- No necesitas ser preciso — el gesto se interpola automáticamente
- Palabras de 3-10 letras funcionan mejor
- El sistema usa subsecuencia + Levenshtein para encontrar la palabra correcta

---

## 🎯 Aprendizaje local

Cada vez que seleccionas una sugerencia:
1. La frecuencia de esa palabra **aumenta en +10**
2. Se guarda en `user_data.json`
3. La próxima vez que escribas, esa palabra **aparecerá primero**

Este aprendizaje es **persistente** — incluso si cierras la app.

---

## 📱 Capturas de Pantalla

| # | Captura | Descripción |
|---|---------|-------------|
| 1 | ![Settings](01_settings_screen.png) | Pantalla de configuración con campo de prueba |
| 2 | ![Keyboard](02_keyboard_visible.png) | Teclado SmartIME activo |

---

## ❓ Preguntas frecuentes

### ¿Funciona sin internet?
Sí. **100% offline**. Todo el procesamiento es local en el dispositivo.

### ¿Cuánto espacio ocupa?
El APK release es de **~8 MB**. En el dispositivo ocupa ~15 MB tras instalar.

### ¿Aprende de mis palabras?
Sí. Cada vez que tocas una sugerencia, su frecuencia aumenta localmente.

### ¿Funciona en cualquier app?
Sí. Como es un IME del sistema, funciona en **cualquier app** donde puedas escribir: WhatsApp, Chrome, Notas, Telegram, etc.

### ¿Cómo cambio de idioma?
Toca la tecla **"ES"** o **"EN"** en la fila inferior del teclado. El predictor se recarga automáticamente.

### ¿Tiene corrector ortográfico?
Sí. Si escribes una palabra mal, el sistema activa la **distancia Levenshtein** para encontrar la palabra más cercana.

### ¿Puedo escribir con mayúsculas?
Sí. Toca **⇧** una vez para mayúscula inicial, dos veces para BLOQUEO MAYÚSCULAS.

### ¿Por qué no veo el teclado?
El teclado aparece automáticamente cuando tocas un campo de texto con SmartIME configurado como método de entrada predeterminado. Verifica que:
1. SmartIME esté habilitado en Ajustes → Idioma e introducción de texto
2. SmartIME esté seleccionado como método predeterminado

---

## 👥 Autores

### Víctor Alejandro Buendía — Co-intelectual & Dueño del Repositorio

- **GitHub:** [SCP-00](https://github.com/SCP-00)
- **LinkedIn:** [buendia001](https://www.linkedin.com/in/buendia001)

### Buffy (Codebuff AI) — Agente de Desarrollo Asistido

- **Rol:** Implementación, optimización y documentación del código asistida por IA

---

*Tutorial generado para el proyecto académico de Computación Blanda — Mayo 2026*
