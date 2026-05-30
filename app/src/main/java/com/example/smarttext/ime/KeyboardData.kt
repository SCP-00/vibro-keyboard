package com.example.smarttext.ime

import android.graphics.RectF

/**
 * Key action codes beyond regular characters.
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
 * A single key definition with its bounds and metadata.
 */
data class Key(
    val code: Int,           // Character Unicode or KeyCode constant
    val label: String,        // Display text
    val row: Int,             // Row index (0 = number, 1 = top, 2 = home, 3 = bottom)
    val col: Int,             // Column within row
    var bounds: RectF = RectF(), // Computed during layout
    var isShifted: Boolean = false,
    var isPressed: Boolean = false
) {
    /** Center X of the key. */
    val centerX: Float get() = bounds.centerX()
    /** Center Y of the key. */
    val centerY: Float get() = bounds.centerY()
    /** Width of the key. */
    val width: Float get() = bounds.width()
    /** Height of the key. */
    val height: Float get() = bounds.height()
}

/**
 * Generates the keyboard layout for a given language.
 *
 * Layout structure (landscape):
 *   Row 0 (number): 1 2 3 4 5 6 7 8 9 0
 *   Row 1 (top):    q w e r t y u i o p
 *   Row 2 (home):   a s d f g h j k l ñ
 *   Row 3 (bottom): ⇧ z x c v b n m ⌫
 *   Row 4 (space):  🌐 , _____space_____ . ↵
 */
object KeyboardData {

    private const val TAG = "SmartIME"

    /** Number of keyboard rows. */
    const val ROWS = 5

    /** Convert a Char to its Unicode Int code for Key.code. */
    private fun c(ch: Char): Int = ch.code

    /** Key row definitions. */
    private val numberRow = listOf(
        Key(c('1'), "1", 0, 0), Key(c('2'), "2", 0, 1), Key(c('3'), "3", 0, 2),
        Key(c('4'), "4", 0, 3), Key(c('5'), "5", 0, 4), Key(c('6'), "6", 0, 5),
        Key(c('7'), "7", 0, 6), Key(c('8'), "8", 0, 7), Key(c('9'), "9", 0, 8),
        Key(c('0'), "0", 0, 9)
    )

    private val topRow = listOf(
        Key(c('q'), "q", 1, 0), Key(c('w'), "w", 1, 1), Key(c('e'), "e", 1, 2),
        Key(c('r'), "r", 1, 3), Key(c('t'), "t", 1, 4), Key(c('y'), "y", 1, 5),
        Key(c('u'), "u", 1, 6), Key(c('i'), "i", 1, 7), Key(c('o'), "o", 1, 8),
        Key(c('p'), "p", 1, 9)
    )

    private val homeRow = listOf(
        Key(c('a'), "a", 2, 0), Key(c('s'), "s", 2, 1), Key(c('d'), "d", 2, 2),
        Key(c('f'), "f", 2, 3), Key(c('g'), "g", 2, 4), Key(c('h'), "h", 2, 5),
        Key(c('j'), "j", 2, 6), Key(c('k'), "k", 2, 7), Key(c('l'), "l", 2, 8),
        Key(c('ñ'), "ñ", 2, 9)
    )

    private val bottomRow = listOf(
        Key(KeyCode.SHIFT, "⇧", 3, 0),
        Key(c('z'), "z", 3, 1), Key(c('x'), "x", 3, 2), Key(c('c'), "c", 3, 3),
        Key(c('v'), "v", 3, 4), Key(c('b'), "b", 3, 5), Key(c('n'), "n", 3, 6),
        Key(c('m'), "m", 3, 7),
        Key(KeyCode.BACKSPACE, "⌫", 3, 8)
    )

    private fun spaceRow(lang: String): List<Key> = listOf(
        Key(KeyCode.SWITCH_LANG, if (lang == "es") "EN" else "ES", 4, 0),
        Key(KeyCode.COMMA, ",", 4, 1),
        Key(KeyCode.SPACE, "", 4, 2),
        Key(KeyCode.PERIOD, ".", 4, 3),
        Key(KeyCode.ENTER, "↵", 4, 4)
    )

    /** Generate full keyboard for a given language. */
    fun generate(lang: String, shiftMode: Boolean = false): List<Key> {
        val all = mutableListOf<Key>()
        all.addAll(numberRow)
        all.addAll(topRow)
        all.addAll(homeRow)
        all.addAll(bottomRow)
        all.addAll(spaceRow(lang))

        // Apply shift display for letters (actual character depends on shift state)
        return all
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

    /**
     * Compute key bounds for a given view size.
     * Each key's bounds are stored in the `bounds` property.
     */
    fun layoutKeys(
        keys: List<Key>,
        viewWidth: Float,
        viewHeight: Float,
        topPadding: Float = 0f  // Space for candidate strip
    ): List<Key> {
        val usableHeight = viewHeight - topPadding
        val rowHeight = usableHeight / ROWS
        val verticalGap = 2f
        val horizontalGap = 2f

        // Row widths (space row has different column counts)
        val rowCols = intArrayOf(10, 10, 10, 9, 5)
        // Space row column weights (comma, space gets 3x, period, enter)
        val spaceRowWeights = floatArrayOf(1.2f, 0.8f, 3.5f, 0.8f, 1.2f)

        // Group keys by row
        val rows = keys.groupBy { it.row }

        for ((rowIdx, rowKeys) in rows) {
            val rowTop = topPadding + rowIdx * rowHeight + verticalGap / 2
            val rowBottom = rowTop + rowHeight - verticalGap

            if (rowIdx == 4) {
                // Space row uses weighted widths
                val totalWeight = spaceRowWeights.sum()
                val availableWidth = viewWidth - horizontalGap * (spaceRowWeights.size)
                var xStart = horizontalGap / 2

                for ((i, key) in rowKeys.withIndex()) {
                    val keyWidth = availableWidth * spaceRowWeights[i] / totalWeight
                    key.bounds = rectF(xStart, rowTop, xStart + keyWidth, rowBottom)
                    xStart += keyWidth + horizontalGap
                }
            } else if (rowIdx == 3) {
                // Bottom row with shift and backspace wider
                val standardCols = 7 // zxcvbnm = 7 cols
                val specialWidth = 1.3f
                val totalEffective = standardCols + specialWidth * 2
                val colWidth = (viewWidth - horizontalGap * (rowKeys.size - 1)) / totalEffective

                var xStart = horizontalGap / 2
                for ((i, key) in rowKeys.withIndex()) {
                    val w = if (i == 0 || i == rowKeys.size - 1) colWidth * specialWidth else colWidth
                    key.bounds = rectF(xStart, rowTop, xStart + w, rowBottom)
                    xStart += w + horizontalGap
                }
            } else {
                // Regular rows: all keys equal width
                val totalCols = rowCols[rowIdx]
                val colWidth = (viewWidth - horizontalGap * (totalCols - 1)) / totalCols
                var xStart = horizontalGap / 2

                for (key in rowKeys) {
                    key.bounds = rectF(xStart, rowTop, xStart + colWidth, rowBottom)
                    xStart += colWidth + horizontalGap
                }
            }
        }

        return keys
    }
}
