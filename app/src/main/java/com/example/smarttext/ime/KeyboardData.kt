package com.example.smarttext.ime

import android.graphics.RectF

/**
 * Key action codes beyond regular characters.
 * All codes are negative to avoid conflicts with Unicode character codes.
 */
object KeyCode {
    const val SHIFT = -1
    const val BACKSPACE = -2
    const val SPACE = -3
    const val ENTER = -4
    const val SWITCH_LANG = -5
    const val SWITCH_SYMBOL = -6
    const val COMMA = -7
    const val PERIOD = -8
    const val SWITCH_ABC = -9
}

/**
 * A single key definition with its bounds and metadata on the keyboard.
 *
 * @property code Character Unicode code point or [KeyCode] constant for special keys
 * @property label Display text shown on the key
 * @property row Row index (0 = top QWERTY row, 1 = home row, 2 = bottom row, 3 = space row)
 * @property col Column within row
 * @property bounds Computed bounding rectangle during layout
 * @property isShifted Whether this key is currently rendered in shifted/caps state
 * @property isPressed Whether this key is currently being pressed (for visual feedback)
 * @property longPressChars Characters to show on long-press (e.g. "áàäâ" for 'a')
 */
data class Key(
    val code: Int,
    val label: String,
    val row: Int,
    val col: Int,
    var bounds: RectF = RectF(),
    var isShifted: Boolean = false,
    var isPressed: Boolean = false,
    /** Characters available on long-press (null = no long-press popup). */
    val longPressChars: String? = null
) {
    /** Center X of the key in the keyboard layout. */
    val centerX: Float get() = bounds.centerX()
    /** Center Y of the key in the keyboard layout. */
    val centerY: Float get() = bounds.centerY()
    /** Width of the key in pixels. */
    val width: Float get() = bounds.width()
    /** Height of the key in pixels. */
    val height: Float get() = bounds.height()
}

/**
 * Generates the keyboard layout for a given language and manages key data.
 *
 * ## Layout (4 rows, language-adaptive):
 *
 * **Spanish (es) — Row 0: 10 keys**
 * ```
 * q w e r t y u i o p
 * ```
 *
 * **Spanish — Row 1: 10 keys (with Ñ)**
 * ```
 * a s d f g h j k l ñ
 * ```
 *
 * **English (en) — Row 1: 9 keys (no Ñ, adds apostrophe)**
 * ```
 * a s d f g h j k l
 * ```
 *
 * **Row 2 (bottom): 9 keys (all languages)**
 * ```
 * ⇧ z x c v b n m ⌫
 * ```
 *
 * **Row 3 (space): 5 keys (all languages)**
 * ```
 * 🌐 , _____space_____ . ↵
 * ```
 *
 * ## Long-press special characters
 * Keys like a, e, i, o, u, n provide accented variants on long-press:
 * - a → á à ä â
 * - e → é è ë ê
 * - i → í ì ï î
 * - o → ó ò ö ô
 * - u → ú ù ü û
 * - n → ñ
 */
object KeyboardData {

    private const val TAG = "SmartIME"

    /** Number of keyboard rows (4). */
    const val ROWS = 4

    /**
     * Map of base character → available accented/special variants for long-press.
     * The base character itself is always included as the first option.
     */
    val SPECIAL_CHARS: Map<Char, String> = mapOf(
        'a' to "aáàäâãå",
        'e' to "eéèëê",
        'i' to "iíìïî",
        'o' to "oóòöôõ",
        'u' to "uúùüû",
        'n' to "nñ",
        's' to "sß",
        'c' to "cç",
        'y' to "yýÿ",
        'z' to "zž",
        ',' to ",;:",
        '.' to ".!?…"
    )

    /** Convert a Char to its Unicode Int code for Key.code. */
    private fun c(ch: Char): Int = ch.code

    // ── Row definitions ──

    /** Top QWERTY row: row 0 — same for all languages. */
    private val topRow = listOf(
        Key(c('q'), "q", 0, 0, longPressChars = null),
        Key(c('w'), "w", 0, 1, longPressChars = null),
        Key(c('e'), "e", 0, 2, longPressChars = SPECIAL_CHARS['e']),
        Key(c('r'), "r", 0, 3, longPressChars = null),
        Key(c('t'), "t", 0, 4, longPressChars = null),
        Key(c('y'), "y", 0, 5, longPressChars = SPECIAL_CHARS['y']),
        Key(c('u'), "u", 0, 6, longPressChars = SPECIAL_CHARS['u']),
        Key(c('i'), "i", 0, 7, longPressChars = SPECIAL_CHARS['i']),
        Key(c('o'), "o", 0, 8, longPressChars = SPECIAL_CHARS['o']),
        Key(c('p'), "p", 0, 9, longPressChars = null)
    )

    /** Home row: row 1 — adapted per language (Spanish has Ñ, English has apostrophe). */
    private val homeRowSpanish = listOf(
        Key(c('a'), "a", 1, 0, longPressChars = SPECIAL_CHARS['a']),
        Key(c('s'), "s", 1, 1, longPressChars = SPECIAL_CHARS['s']),
        Key(c('d'), "d", 1, 2, longPressChars = null),
        Key(c('f'), "f", 1, 3, longPressChars = null),
        Key(c('g'), "g", 1, 4, longPressChars = null),
        Key(c('h'), "h", 1, 5, longPressChars = null),
        Key(c('j'), "j", 1, 6, longPressChars = null),
        Key(c('k'), "k", 1, 7, longPressChars = null),
        Key(c('l'), "l", 1, 8, longPressChars = null),
        Key(c('ñ'), "ñ", 1, 9, longPressChars = null)
    )

    /** English home row — 10 keys including apostrophe after L.
     *  Standard EN QWERTY: a s d f g h j k l '
     */
    private val homeRowEnglish = listOf(
        Key(c('a'), "a", 1, 0, longPressChars = SPECIAL_CHARS['a']),
        Key(c('s'), "s", 1, 1, longPressChars = SPECIAL_CHARS['s']),
        Key(c('d'), "d", 1, 2, longPressChars = null),
        Key(c('f'), "f", 1, 3, longPressChars = null),
        Key(c('g'), "g", 1, 4, longPressChars = null),
        Key(c('h'), "h", 1, 5, longPressChars = null),
        Key(c('j'), "j", 1, 6, longPressChars = null),
        Key(c('k'), "k", 1, 7, longPressChars = null),
        Key(c('l'), "l", 1, 8, longPressChars = null),
        Key(c('\''), "'", 1, 9, longPressChars = "'\"`")
    )

    /** Bottom row: row 2 — same for all languages. */
    private val bottomRow = listOf(
        Key(KeyCode.SHIFT, "⇧", 2, 0),
        Key(c('z'), "z", 2, 1, longPressChars = SPECIAL_CHARS['z']),
        Key(c('x'), "x", 2, 2, longPressChars = null),
        Key(c('c'), "c", 2, 3, longPressChars = SPECIAL_CHARS['c']),
        Key(c('v'), "v", 2, 4, longPressChars = null),
        Key(c('b'), "b", 2, 5, longPressChars = null),
        Key(c('n'), "n", 2, 6, longPressChars = SPECIAL_CHARS['n']),
        Key(c('m'), "m", 2, 7, longPressChars = null),
        Key(KeyCode.BACKSPACE, "⌫", 2, 8)
    )

    /** Space row: row 3 — same for all languages. */
    private fun spaceRow(lang: String): List<Key> = listOf(
        Key(KeyCode.SWITCH_LANG, if (lang == "es") "EN" else "ES", 3, 0),
        Key(KeyCode.COMMA, ",", 3, 1, longPressChars = SPECIAL_CHARS[',']),
        Key(KeyCode.SPACE, "", 3, 2),
        Key(KeyCode.PERIOD, ".", 3, 3, longPressChars = SPECIAL_CHARS['.']),
        Key(KeyCode.ENTER, "↵", 3, 4)
    )

    // ── Layout dimensions ──

    /** Columns per row in the 4-row layout. */
    private val rowCols = mapOf(
        0 to 10,   // top row
        1 to 10,   // home row (Spanish); overridden to 9 for English
        2 to 9,    // bottom row
        3 to 5     // space row
    )

    /** Space row column weights for proportional sizing. */
    private val spaceRowWeights = floatArrayOf(1.0f, 0.8f, 4.0f, 0.8f, 1.2f)

    // ── Public API ──

    /**
     * Generate a full keyboard key list for the given language.
     *
     * @param lang Language code: "es" (Spanish) or "en" (English)
     * @param shiftMode Whether to render labels in uppercase (handled at draw-time)
     * @return Flat list of [Key] objects with labels, rows, and columns
     */
    fun generate(lang: String, shiftMode: Boolean = false): List<Key> {
        val all = mutableListOf<Key>()
        all.addAll(topRow)
        all.addAll(if (lang == "en") homeRowEnglish else homeRowSpanish)
        all.addAll(bottomRow)
        all.addAll(spaceRow(lang))
        return all
    }

    /** Total key count for a given language (Spanish: 34, English: 33). */
    fun keyCount(lang: String): Int = generate(lang).size

    /**
     * Compute key bounds for a given view size.
     *
     * Layout uses proportional column widths with special handling for:
     * - Row 2 (bottom): wider Shift and Backspace keys (1.3× normal)
     * - Row 3 (space): weighted proportional widths
     *
     * @param keys Keys to lay out (must have row/col assigned)
     * @param viewWidth Total available width in pixels
     * @param viewHeight Total available height in pixels
     * @param topPadding Vertical space reserved above keys (e.g. candidate strip)
     * @return The same [keys] list with bounds computed
     */
    fun layoutKeys(
        keys: List<Key>,
        viewWidth: Float,
        viewHeight: Float,
        topPadding: Float = 0f
    ): List<Key> {
        val usableHeight = viewHeight - topPadding
        val rowHeight = usableHeight / ROWS
        val verticalGap = 2f
        val horizontalGap = 2f

        // Determine rows and their column counts per language
        val colsByRow = mutableMapOf<Int, Int>()
        val rows = keys.groupBy { it.row }
        for ((rowIdx, rowKeys) in rows) {
            colsByRow[rowIdx] = rowKeys.size
        }

        for ((rowIdx, rowKeys) in rows) {
            val rowTop = topPadding + rowIdx * rowHeight + verticalGap / 2
            val rowBottom = rowTop + rowHeight - verticalGap

            when (rowIdx) {
                3 -> layoutSpaceRow(rowKeys, viewWidth, rowTop, rowBottom, horizontalGap)
                2 -> layoutBottomRow(rowKeys, viewWidth, rowTop, rowBottom, horizontalGap)
                else -> layoutUniformRow(rowKeys, viewWidth, rowTop, rowBottom, horizontalGap)
            }
        }

        return keys
    }

    /** Layout a row with equal-width columns. */
    private fun layoutUniformRow(
        rowKeys: List<Key>,
        viewWidth: Float,
        rowTop: Float,
        rowBottom: Float,
        gap: Float
    ) {
        if (rowKeys.isEmpty()) return
        val totalCols = rowKeys.size
        val colWidth = (viewWidth - gap * (totalCols - 1)) / totalCols
        var xStart = gap / 2
        for (key in rowKeys) {
            key.bounds = rectF(xStart, rowTop, xStart + colWidth, rowBottom)
            xStart += colWidth + gap
        }
    }

    /** Layout bottom row with wider Shift and Backspace keys (1.3×). */
    private fun layoutBottomRow(
        rowKeys: List<Key>,
        viewWidth: Float,
        rowTop: Float,
        rowBottom: Float,
        gap: Float
    ) {
        if (rowKeys.isEmpty()) return
        val standardCols = rowKeys.size - 2
        val specialWidth = 1.3f
        val totalEffective = standardCols + specialWidth * 2
        val colWidth = (viewWidth - gap * (rowKeys.size - 1)) / totalEffective
        var xStart = gap / 2
        for ((i, key) in rowKeys.withIndex()) {
            val w = if (i == 0 || i == rowKeys.size - 1) colWidth * specialWidth else colWidth
            key.bounds = rectF(xStart, rowTop, xStart + w, rowBottom)
            xStart += w + gap
        }
    }

    /** Layout space row with weighted proportional widths. */
    private fun layoutSpaceRow(
        rowKeys: List<Key>,
        viewWidth: Float,
        rowTop: Float,
        rowBottom: Float,
        gap: Float
    ) {
        if (rowKeys.isEmpty()) return
        val totalWeight = spaceRowWeights.sum()
        val availableWidth = viewWidth - gap * spaceRowWeights.size
        var xStart = gap / 2
        for ((i, key) in rowKeys.withIndex()) {
            val keyWidth = availableWidth * spaceRowWeights[i] / totalWeight
            key.bounds = rectF(xStart, rowTop, xStart + keyWidth, rowBottom)
            xStart += keyWidth + gap
        }
    }

    /** Create a RectF with field-by-field assignment (compatible with Android unit test stubs). */
    private fun rectF(left: Float, top: Float, right: Float, bottom: Float): RectF {
        val r = RectF()
        r.left = left
        r.top = top
        r.right = right
        r.bottom = bottom
        return r
    }
}
