package com.example.smarttext.ime

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests unitarios para KeyboardData V3.
 *
 * ## Cobertura V3
 * - Layout español: 34 teclas (10+10+9+5), incluye ñ
 * - Layout inglés: 33 teclas (10+9+9+5), sin ñ, con apóstrofe
 * - SPECIAL_CHARS map: caracteres acentuados para long-press
 * - Key.longPressChars: asignación correcta por tecla
 * - Teclas especiales (shift, backspace, enter, espacio, coma, punto)
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
    fun `generate - espanol contiene letra enie en home row`() {
        val keys = KeyboardData.generate("es")
        val enieKey = keys.find { it.code == 'ñ'.code }
        assertNotNull("Spanish keyboard should include 'ñ'", enieKey)
        assertEquals("ñ should be in home row (row 1)", 1, enieKey!!.row)
        assertEquals("ñ should be at column 9 (last in home row)", 9, enieKey.col)
    }

    @Test
    fun `generate - ingles NO contiene letra enie`() {
        val keys = KeyboardData.generate("en")
        val enieKey = keys.find { it.code == 'ñ'.code }
        assertNull("English keyboard should NOT include 'ñ'", enieKey)
    }

    @Test
    fun `generate - ingles tiene home row de 9 teclas (sin ñ)`() {
        val keys = KeyboardData.generate("en")
        val homeRowKeys = keys.filter { it.row == 1 }
        assertEquals("English home row should have 9 keys (no ñ)", 9, homeRowKeys.size)
    }

    @Test
    fun `generate - espanol tiene home row de 10 teclas (con ñ)`() {
        val keys = KeyboardData.generate("es")
        val homeRowKeys = keys.filter { it.row == 1 }
        assertEquals("Spanish home row should have 10 keys (with ñ)", 10, homeRowKeys.size)
    }

    @Test
    fun `generate - teclado contiene 10 teclas en top row (fila 0) para ambos idiomas`() {
        val esKeys = KeyboardData.generate("es")
        val enKeys = KeyboardData.generate("en")
        assertEquals("Spanish Row 0 should have 10 keys", 10, esKeys.filter { it.row == 0 }.size)
        assertEquals("English Row 0 should have 10 keys", 10, enKeys.filter { it.row == 0 }.size)
        assertTrue("Row 0 should include 'q'", esKeys.any { it.label == "q" })
        assertTrue("Row 0 should include 'p'", esKeys.any { it.label == "p" })
    }

    @Test
    fun `generate - espanol tiene 4 filas`() {
        val keys = KeyboardData.generate("es")
        val rows = keys.map { it.row }.distinct().sorted()
        assertEquals("Spanish should have 4 rows (0-3)", listOf(0, 1, 2, 3), rows)
    }

    @Test
    fun `generate - ingles tiene 4 filas`() {
        val keys = KeyboardData.generate("en")
        val rows = keys.map { it.row }.distinct().sorted()
        assertEquals("English should have 4 rows (0-3)", listOf(0, 1, 2, 3), rows)
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
    fun `generate - espanol tiene 34 teclas (10+10+9+5)`() {
        val keys = KeyboardData.generate("es")
        assertEquals("Spanish should have 34 keys (10+10+9+5)", 34, keys.size)
    }

    @Test
    fun `generate - ingles tiene 33 teclas (10+9+9+5)`() {
        val keys = KeyboardData.generate("en")
        assertEquals("English should have 33 keys (10+9+9+5)", 33, keys.size)
    }

    @Test
    fun `generate - distribucion de teclas por fila`() {
        val esKeys = KeyboardData.generate("es")
        val enKeys = KeyboardData.generate("en")
        val esRowCounts = esKeys.groupBy { it.row }.mapValues { it.value.size }
        val enRowCounts = enKeys.groupBy { it.row }.mapValues { it.value.size }

        // Row counts (row 1 differs)
        assertEquals("Spanish Row 0", 10, esRowCounts[0]!!)
        assertEquals("Spanish Row 1 (home)", 10, esRowCounts[1]!!)
        assertEquals("Spanish Row 2 (bottom)", 9, esRowCounts[2]!!)
        assertEquals("Spanish Row 3 (space)", 5, esRowCounts[3]!!)
        assertEquals("English Row 0", 10, enRowCounts[0]!!)
        assertEquals("English Row 1 (home)", 9, enRowCounts[1]!!)
        assertEquals("English Row 2 (bottom)", 9, enRowCounts[2]!!)
        assertEquals("English Row 3 (space)", 5, enRowCounts[3]!!)
    }

    // ═══════════════════════════════════════
    // 4. SPECIAL_CHARS y long-press
    // ═══════════════════════════════════════

    @Test
    fun `SPECIAL_CHARS contiene vocales acentuadas`() {
        assertNotNull("a should have accented variants", KeyboardData.SPECIAL_CHARS['a'])
        assertNotNull("e should have accented variants", KeyboardData.SPECIAL_CHARS['e'])
        assertNotNull("i should have accented variants", KeyboardData.SPECIAL_CHARS['i'])
        assertNotNull("o should have accented variants", KeyboardData.SPECIAL_CHARS['o'])
        assertNotNull("u should have accented variants", KeyboardData.SPECIAL_CHARS['u'])
        assertNotNull("n should have ñ", KeyboardData.SPECIAL_CHARS['n'])
    }

    @Test
    fun `SPECIAL_CHARS - 'a' incluye 'a', 'a', 'a', 'a'`() {
        val chars = KeyboardData.SPECIAL_CHARS['a'] ?: ""
        assertTrue("'a' variants should start with 'a'", chars.startsWith("a"))
        assertTrue("'a' variants should include 'á'", chars.contains("á"))
        assertTrue("'a' variants should include 'à'", chars.contains("à"))
    }

    @Test
    fun `SPECIAL_CHARS - 'n' incluye 'n' y 'n'`() {
        val chars = KeyboardData.SPECIAL_CHARS['n'] ?: ""
        assertTrue("'n' variants should start with 'n'", chars.startsWith("n"))
        assertTrue("'n' variants should include 'ñ'", chars.contains("ñ"))
    }

    @Test
    fun `SPECIAL_CHARS - 'c' incluye cedilla`() {
        val chars = KeyboardData.SPECIAL_CHARS['c'] ?: ""
        assertTrue("'c' variants should include 'ç'", chars.contains("ç"))
    }

    @Test
    fun `teclas con longPressChars asignados correctamente`() {
        val keys = KeyboardData.generate("es")

        // Vowels should have long-press
        val aKey = keys.find { it.code == 'a'.code }
        assertNotNull("a key should have longPressChars", aKey?.longPressChars)
        assertTrue("a key longPress should include 'á'", aKey!!.longPressChars!!.contains("á"))

        val eKey = keys.find { it.code == 'e'.code }
        assertNotNull("e key should have longPressChars", eKey?.longPressChars)

        // Non-vowel letters should not have long-press
        val pKey = keys.find { it.code == 'p'.code }
        assertNull("p key should NOT have longPressChars", pKey?.longPressChars)

        val rKey = keys.find { it.code == 'r'.code }
        assertNull("r key should NOT have longPressChars", rKey?.longPressChars)
    }

    @Test
    fun `teclas especiales no tienen longPressChars`() {
        val keys = KeyboardData.generate("es")

        val shiftKey = keys.find { it.code == KeyCode.SHIFT }
        assertNull("SHIFT should not have longPressChars", shiftKey?.longPressChars)

        val spaceKey = keys.find { it.code == KeyCode.SPACE }
        assertNull("SPACE should not have longPressChars", spaceKey?.longPressChars)

        val enterKey = keys.find { it.code == KeyCode.ENTER }
        assertNull("ENTER should not have longPressChars", enterKey?.longPressChars)
    }

    // ═══════════════════════════════════════
    // 5. Layout de teclas (validación básica)
    // ═══════════════════════════════════════

    @Test
    fun `layoutKeys - asigna bounds dentro del area visible`() {
        val keys = KeyboardData.generate("es")
        val viewWidth = 720f
        val viewHeight = 400f
        val tolerance = 1.5f

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
    fun `layoutKeys - layout en ingles no lanza excepcion`() {
        val keys = KeyboardData.generate("en")
        // No debe lanzar excepción al hacer layout con 33 teclas (row 1 con 9 teclas)
        val laidOut = KeyboardData.layoutKeys(keys, 720f, 400f)
        assertEquals("English layout should have 33 keys", 33, laidOut.size)
    }

}
