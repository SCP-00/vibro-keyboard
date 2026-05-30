package com.example.smarttext.ime

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests unitarios para KeyboardData y KeyCode.
 *
 * Cobertura nueva:
 * - Cambio de idioma (ES ↔ EN): etiquetas SWITCH_LANG
 * - Teclas especiales (shift, backspace, enter, espacio, coma, punto)
 * - Estructura del teclado (filas, columnas, letra ñ)
 * - Layout de teclas (bounds, distribución proporcional)
 */
class KeyboardDataTest {

    // ═══════════════════════════════════════
    // 1. Cambio de idioma (ES ↔ EN)
    // ═══════════════════════════════════════

    @Test
    fun `generate - idioma espanol tiene EN como etiqueta de cambio de idioma`() {
        val keys = KeyboardData.generate("es")
        val langKey = keys.find { it.code == KeyCode.SWITCH_LANG }
        assertNotNull("Spanish keyboard should have SWITCH_LANG key", langKey)
        assertEquals("Spanish keyboard should show 'EN' as lang label", "EN", langKey!!.label)
    }

    @Test
    fun `generate - idioma ingles tiene ES como etiqueta de cambio de idioma`() {
        val keys = KeyboardData.generate("en")
        val langKey = keys.find { it.code == KeyCode.SWITCH_LANG }
        assertNotNull("English keyboard should have SWITCH_LANG key", langKey)
        assertEquals("English keyboard should show 'ES' as lang label", "ES", langKey!!.label)
    }

    @Test
    fun `generate - idioma espanol contiene letra enie en home row`() {
        val keys = KeyboardData.generate("es")
        val enieKey = keys.find { it.code == 'ñ'.code }
        assertNotNull("Spanish keyboard should include 'ñ'", enieKey)
        assertEquals("ñ should be in home row (row 1)", 1, enieKey!!.row)
        assertEquals("ñ should be at column 9 (last in home row)", 9, enieKey.col)
    }

    @Test
    fun `generate - teclado contiene 10 teclas alfabeticas en top row (fila 0)`() {
        val keys = KeyboardData.generate("es")
        val topRowKeys = keys.filter { it.row == 0 }
        assertEquals("Row 0 should have 10 letter keys", 10, topRowKeys.size)
        assertTrue("Row 0 should include 'q'", topRowKeys.any { it.label == "q" })
        assertTrue("Row 0 should include 'p'", topRowKeys.any { it.label == "p" })
    }

    @Test
    fun `generate - teclado contiene 10 teclas alfabeticas en home row (fila 1)`() {
        val keys = KeyboardData.generate("es")
        val homeRowKeys = keys.filter { it.row == 1 }
        assertEquals("Row 1 should have 10 letter keys", 10, homeRowKeys.size)
        assertTrue("Row 1 should include 'a'", homeRowKeys.any { it.label == "a" })
        assertTrue("Row 1 should include 'ñ'", homeRowKeys.any { it.label == "ñ" })
    }

    @Test
    fun `generate - teclado tiene 4 filas`() {
        val keys = KeyboardData.generate("es")
        val rows = keys.map { it.row }.distinct().sorted()
        assertEquals("Should have 4 rows (0-3)", listOf(0, 1, 2, 3), rows)
    }

    // ═══════════════════════════════════════
    // 2. Teclas especiales
    // ═══════════════════════════════════════

    @Test
    fun `generate - incluye tecla shift en fila 2`() {
        val keys = KeyboardData.generate("es")
        val shiftKey = keys.find { it.code == KeyCode.SHIFT }
        assertNotNull("Keyboard should have SHIFT key", shiftKey)
        assertEquals("SHIFT should be in row 2 (bottom)", 2, shiftKey!!.row)
        assertEquals("SHIFT should be at column 0", 0, shiftKey.col)
        assertEquals("SHIFT label should be '⇧'", "⇧", shiftKey.label)
    }

    @Test
    fun `generate - incluye tecla backspace al final de fila 2`() {
        val keys = KeyboardData.generate("es")
        val backspaceKey = keys.find { it.code == KeyCode.BACKSPACE }
        assertNotNull("Keyboard should have BACKSPACE key", backspaceKey)
        assertEquals("BACKSPACE should be in row 2", 2, backspaceKey!!.row)
        assertEquals("BACKSPACE label should be '⌫'", "⌫", backspaceKey.label)
    }

    @Test
    fun `generate - incluye tecla enter en fila 3`() {
        val keys = KeyboardData.generate("es")
        val enterKey = keys.find { it.code == KeyCode.ENTER }
        assertNotNull("Keyboard should have ENTER key", enterKey)
        assertEquals("ENTER should be in row 3 (space)", 3, enterKey!!.row)
        assertEquals("ENTER label should be '↵'", "↵", enterKey.label)
    }

    @Test
    fun `generate - incluye tecla espacio en fila 3`() {
        val keys = KeyboardData.generate("es")
        val spaceKey = keys.find { it.code == KeyCode.SPACE }
        assertNotNull("Keyboard should have SPACE key", spaceKey)
        assertEquals("SPACE should be in row 3", 3, spaceKey!!.row)
        assertTrue("SPACE label should be empty string", spaceKey!!.label.isEmpty())
    }

    @Test
    fun `generate - incluye teclas de coma y punto en fila 3`() {
        val keys = KeyboardData.generate("es")
        assertNotNull("Keyboard should have COMMA key", keys.find { it.code == KeyCode.COMMA })
        assertNotNull("Keyboard should have PERIOD key", keys.find { it.code == KeyCode.PERIOD })
        val comma = keys.first { it.code == KeyCode.COMMA }
        val period = keys.first { it.code == KeyCode.PERIOD }
        assertEquals("COMMA label should be ','", ",", comma.label)
        assertEquals("PERIOD label should be '.'", ".", period.label)
    }

    // ═══════════════════════════════════════
    // 3. Conteo total de teclas
    // ═══════════════════════════════════════

    @Test
    fun `generate - numero total de teclas es 34`() {
        // Row 0: 10 + Row 1: 10 + Row 2: 9 + Row 3: 5 = 34
        val keys = KeyboardData.generate("es")
        assertEquals("Total keys should be 34 (10+10+9+5)", 34, keys.size)
    }

    @Test
    fun `generate - distribucion de teclas por fila`() {
        val keys = KeyboardData.generate("es")
        val rowCounts = keys.groupBy { it.row }.mapValues { it.value.size }
        assertEquals("Row 0 should have 10 keys", 10, rowCounts[0])
        assertEquals("Row 1 should have 10 keys", 10, rowCounts[1])
        assertEquals("Row 2 should have 9 keys", 9, rowCounts[2])
        assertEquals("Row 3 should have 5 keys", 5, rowCounts[3])
    }

    @Test
    fun `generate - todas las teclas tienen codigo y etiqueta no nula`() {
        val keys = KeyboardData.generate("es")
        for (key in keys) {
            assertNotNull("Key at row ${key.row} col ${key.col} should have non-null label", key.label)
        }
    }

    // ═══════════════════════════════════════
    // 4. Layout de teclas (validación básica)
    // ═══════════════════════════════════════

    @Test
    fun `layoutKeys - asigna bounds dentro del area visible`() {
        val keys = KeyboardData.generate("es")
        val viewWidth = 720f
        val viewHeight = 400f
        val tolerance = 1.5f  // tolerancia por redondeo de punto flotante

        val laidOut = KeyboardData.layoutKeys(keys, viewWidth, viewHeight)

        for (key in laidOut) {
            assertTrue("Key '${key.label}' left should be >= 0, got ${key.bounds.left}", key.bounds.left >= -tolerance)
            assertTrue("Key '${key.label}' right (${key.bounds.right}) should be <= $viewWidth",
                key.bounds.right <= viewWidth + tolerance)
            assertTrue("Key '${key.label}' top should be >= 0, got ${key.bounds.top}", key.bounds.top >= -tolerance)
            assertTrue("Key '${key.label}' bottom (${key.bounds.bottom}) should be <= $viewHeight",
                key.bounds.bottom <= viewHeight + tolerance)
        }
    }

    @Test
    fun `layoutKeys - teclas no se superponen en misma fila`() {
        val keys = KeyboardData.generate("es")
        val laidOut = KeyboardData.layoutKeys(keys, 720f, 400f)

        val rows = laidOut.groupBy { it.row }
        for ((rowIdx, rowKeys) in rows) {
            val sorted = rowKeys.sortedBy { it.bounds.left }
            for (i in 0 until sorted.size - 1) {
                assertTrue(
                    "Row $rowIdx: key '${sorted[i].label}' right (${sorted[i].bounds.right}) " +
                        "should be <= next key '${sorted[i+1].label}' left (${sorted[i+1].bounds.left})",
                    sorted[i].bounds.right <= sorted[i + 1].bounds.left + 0.01f
                )
            }
        }
    }

    @Test
    fun `layoutKeys - filas no se superponen verticalmente`() {
        val keys = KeyboardData.generate("es")
        val laidOut = KeyboardData.layoutKeys(keys, 720f, 400f)

        val rows = laidOut.groupBy { it.row }.entries.sortedBy { it.key }
        for (i in 0 until rows.size - 1) {
            val currentRow = rows.elementAt(i)
            val nextRow = rows.elementAt(i + 1)
            val maxBottom = currentRow.value.maxOf { it.bounds.bottom }
            val minTop = nextRow.value.minOf { it.bounds.top }
            assertTrue(
                "Row ${currentRow.key} bottom ($maxBottom) should be <= row ${nextRow.key} top ($minTop)",
                maxBottom <= minTop + 0.01f
            )
        }
    }

    @Test
    fun `layoutKeys - tecla espaciadora es mas ancha que las otras en fila 3`() {
        val keys = KeyboardData.generate("es")
        val laidOut = KeyboardData.layoutKeys(keys, 720f, 400f)

        val spaceRow = laidOut.filter { it.row == 3 }
        val spaceKey = spaceRow.first { it.code == KeyCode.SPACE }
        val langKey = spaceRow.first { it.code == KeyCode.SWITCH_LANG }

        val spaceWidth = spaceKey.bounds.right - spaceKey.bounds.left
        val langWidth = langKey.bounds.right - langKey.bounds.left

        assertTrue(
            "Space key width ($spaceWidth) should be larger than lang key width ($langWidth)",
            spaceWidth > langWidth * 2
        )
    }

    @Test
    fun `layoutKeys - teclas shift y backspace mas anchas en fila 2`() {
        val keys = KeyboardData.generate("es")
        val laidOut = KeyboardData.layoutKeys(keys, 720f, 400f)

        val bottomRow = laidOut.filter { it.row == 2 }
        val shiftKey = bottomRow.first { it.code == KeyCode.SHIFT }
        val backspaceKey = bottomRow.first { it.code == KeyCode.BACKSPACE }
        val letterKey = bottomRow.first { it.code == 'c'.code }

        val shiftWidth = shiftKey.bounds.right - shiftKey.bounds.left
        val backspaceWidth = backspaceKey.bounds.right - backspaceKey.bounds.left
        val letterWidth = letterKey.bounds.right - letterKey.bounds.left

        assertTrue(
            "SHIFT width ($shiftWidth) should be > letter key width ($letterWidth)",
            shiftWidth > letterWidth
        )
        assertTrue(
            "BACKSPACE width ($backspaceWidth) should be > letter key width ($letterWidth)",
            backspaceWidth > letterWidth
        )
    }

    // ═══════════════════════════════════════
    // 5. Key data class
    // ═══════════════════════════════════════

    // Key.centerX/centerY/width/height son delegados a RectF que requiere
    // instrumentación Android completa (Robolectric) para funcionar.
    // Se testean indirectamente mediante layoutKeys que asigna bounds.
}
