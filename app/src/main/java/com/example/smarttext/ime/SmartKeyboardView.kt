package com.example.smarttext.ime

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import com.example.smarttext.engine.PredictorEngine

/**
 * Custom keyboard view drawn entirely with Canvas, supporting:
 *
 * - QWERTY layout with language-adaptive rows (no Ñ in English)
 * - Tap typing with shift/caps lock
 * - Glide/swipe gesture typing
 * - **Long-press** on vowel keys for accented characters (á, é, í, ó, ú, ñ, etc.)
 * - **Long-press** on Backspace for repeat-delete with acceleration
 * - Candidate prediction strip at the top
 * - Smooth swipe trail visualization
 *
 * ## Long-press behavior
 * - Keys with `longPressChars != null` show a popup after 350ms of hold
 * - User slides finger to select a variant, then lifts to commit
 * - Backspace long-press starts deleting after 400ms, then repeats every 80ms (accelerating)
 */
class SmartKeyboardView(
    context: Context,
    private val ime: SmartIME
) : View(context) {

    companion object {
        private const val TAG = "SmartIME.Keyboard"
        private const val CANDIDATE_STRIP_HEIGHT_DP = 36f
        private const val KEY_MARGIN_DP = 2f
        private const val KEY_CORNER_RADIUS_DP = 6f
        private const val SWIPE_TRAIL_WIDTH_DP = 4f
        private const val SWIPE_TRAIL_ALPHA = 120
        /** Keyboard max height as fraction of screen height. */
        private const val MAX_KEYBOARD_HEIGHT_RATIO = 0.40f

        // ── Long-press constants ──
        /** Milliseconds before long-press popup appears. */
        private const val LONG_PRESS_TIMEOUT_MS = 350L
        /** Delay before backspace starts repeating (ms). */
        private const val BACKSPACE_INITIAL_DELAY_MS = 400L
        /** Initial repeat interval for backspace (ms). */
        private const val BACKSPACE_REPEAT_INTERVAL_MS = 80L
        /** Minimum repeat interval (fastest rate, ms). */
        private const val BACKSPACE_MIN_INTERVAL_MS = 30L
        /** How much to accelerate each repeat cycle (multiplier < 1). */
        private const val BACKSPACE_ACCELERATION = 0.85f
    }

    // ─── State ───
    private var currentLang: String = "es"
    private var shiftMode: Boolean = false
    private var shiftLocked: Boolean = false

    /** Keyboard keys with computed bounds. */
    private var keys: List<Key> = KeyboardData.generate(currentLang)

    // Candidate strip
    private val candidateWords = mutableListOf<String>()
    private var predictor: PredictorEngine? = null

    // Gesture
    private val gestureRecognizer = GestureRecognizer()
    private var isGesturing: Boolean = false

    // ─── Long-press state ───
    /** Handler for scheduling long-press actions. */
    private val handler = Handler(Looper.getMainLooper())
    /** The key being long-pressed (or null). */
    private var longPressKey: Key? = null
    /** Currently selected index in the long-press popup. */
    private var longPressSelectedIndex: Int = 0
    /** True when the long-press popup is visible. */
    private var isLongPressPopupVisible: Boolean = false
    /** Characters shown in the current long-press popup. */
    private var longPressChars: String = ""
    /** Bounding rect of the long-press popup (for touch tracking). */
    private var longPressPopupBounds: RectF = RectF()
    /** Runnable that triggers long-press popup. */
    private val longPressRunnable = Runnable {
        val key = longPressKey ?: return@Runnable
        val chars = key.longPressChars
        if (chars != null && chars.length >= 2 &&
            key.code !in longPressBlocklist) {
            longPressChars = chars
            isLongPressPopupVisible = true
            longPressSelectedIndex = 0
            invalidate()
        }
    }

    /** Keys that never show a long-press popup (even if they have longPressChars). */
    private val longPressBlocklist = setOf(
        KeyCode.BACKSPACE, KeyCode.SHIFT, KeyCode.SPACE,
        KeyCode.ENTER, KeyCode.SWITCH_LANG
    )

    // ─── Backspace repeat state ───
    /** True when backspace is being held for repeat. */
    private var isBackspaceRepeating: Boolean = false
    /** Current repeat interval (decreases with acceleration). */
    private var backspaceRepeatInterval: Long = BACKSPACE_REPEAT_INTERVAL_MS
    /** Runnable that triggers each backspace delete during repeat. */
    private val backspaceRepeatRunnable = object : Runnable {
        override fun run() {
            if (!isBackspaceRepeating) return
            ime.deleteBackward()
            // Accelerate: decrease interval but not below minimum
            backspaceRepeatInterval = maxOf(
                (backspaceRepeatInterval * BACKSPACE_ACCELERATION).toLong(),
                BACKSPACE_MIN_INTERVAL_MS
            )
            handler.postDelayed(this, backspaceRepeatInterval)
        }
    }

    // ─── Colors ───
    private val KEY_BG_NORMAL = Color.argb(35, 255, 255, 255)
    private val KEY_BG_PRESSED = Color.argb(90, 255, 255, 255)
    private val KEY_BG_SPECIAL = Color.argb(45, 180, 200, 255)
    private val KEY_TEXT_PRIMARY = Color.WHITE
    private val KEY_TEXT_SECONDARY = Color.argb(200, 200, 210, 220)
    private val KEY_TEXT_ACCENT = Color.argb(255, 120, 200, 255)
    private val CANDIDATE_BG = Color.argb(50, 100, 180, 255)
    private val CANDIDATE_BG_FIRST = Color.argb(80, 100, 180, 255)
    private val CANDIDATE_TEXT = Color.WHITE
    private val TRAIL_COLOR = Color.argb(SWIPE_TRAIL_ALPHA, 100, 180, 255)
    private val TRAIL_DOT_COLOR = Color.argb(200, 100, 180, 255)
    private val BACKGROUND_COLOR = Color.argb(235, 26, 28, 32)
    private val POPUP_BG = Color.argb(220, 50, 55, 65)
    private val POPUP_SELECTED_BG = Color.argb(255, 100, 180, 255)
    private val POPUP_TEXT = Color.WHITE

    // ─── Paints ───
    private val keyBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = KEY_BG_NORMAL
        style = Paint.Style.FILL
    }
    private val keyBgPressedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = KEY_BG_PRESSED
        style = Paint.Style.FILL
    }
    private val keyBgSpecialPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = KEY_BG_SPECIAL
        style = Paint.Style.FILL
    }
    private val keyTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = KEY_TEXT_PRIMARY
        textAlign = Paint.Align.CENTER
    }
    private val keySecondaryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = KEY_TEXT_SECONDARY
        textSize = 28f
        textAlign = Paint.Align.CENTER
    }
    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = TRAIL_COLOR
        style = Paint.Style.STROKE
        strokeWidth = SWIPE_TRAIL_WIDTH_DP * resources.displayMetrics.density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val trailDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = TRAIL_DOT_COLOR
        style = Paint.Style.FILL
    }
    private val candidateBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = CANDIDATE_BG
        style = Paint.Style.FILL
    }
    private val candidateBgFirstPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = CANDIDATE_BG_FIRST
        style = Paint.Style.FILL
    }
    private val candidateTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = CANDIDATE_TEXT
        textSize = 34f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val separatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(40, 255, 255, 255)
        strokeWidth = 1f
    }

    // ─── Long-press popup paints ───
    private val popupBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = POPUP_BG
        style = Paint.Style.FILL
    }
    private val popupSelectedBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = POPUP_SELECTED_BG
        style = Paint.Style.FILL
    }
    private val popupTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = POPUP_TEXT
        textAlign = Paint.Align.CENTER
    }

    // Metrics (cached)
    private val density = resources.displayMetrics.density
    private val keyMargin = KEY_MARGIN_DP * density
    private val keyCornerRadius = KEY_CORNER_RADIUS_DP * density
    private val candidateStripHeight = CANDIDATE_STRIP_HEIGHT_DP * density

    // ─── Touch tracking ───
    private var pressedKey: Key? = null
    private var touchStartX = 0f
    private var touchStartY = 0f
    private var hasSwiped = false

    // ─── Public API ───

    /**
     * Set the prediction engine used for word suggestions.
     * Called during IME initialization and language changes.
     */
    fun setPredictor(p: PredictorEngine?) {
        predictor = p
        updatePredictions("")
    }

    /**
     * Switch the keyboard to a different language.
     * Rebuilds the key layout to match the language (e.g. removes Ñ for English).
     */
    fun setLanguage(lang: String) {
        currentLang = lang
        shiftMode = false
        rebuildKeys()
        updatePredictions("")
    }

    /** Toggle shift mode on/off. */
    fun setShiftMode(shifted: Boolean) {
        shiftMode = shifted
        rebuildKeys()
    }

    /** Rebuild key positions after state changes (language, shift). */
    private fun rebuildKeys() {
        keys = KeyboardData.generate(currentLang, shiftMode)
        post { requestLayout() }
    }

    /**
     * Update the candidate suggestions based on current input context.
     * Fetches predictions from [PredictorEngine] and triggers a redraw.
     */
    fun updatePredictions(currentWord: String) {
        val p = predictor ?: run {
            candidateWords.clear()
            invalidate()
            return
        }
        candidateWords.clear()
        if (currentWord.isNotEmpty()) {
            try {
                val suggestions = p.predict(currentWord, null, 5)
                candidateWords.addAll(suggestions)
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Predict error", e)
            }
        }
        postInvalidate()
    }

    /** Notify that a word was selected (for frequency tracking). */
    fun notifyWordSelected(word: String) {
        predictor?.updateFrequency(word)
    }

    // ─── Layout ───

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)

        // Limit keyboard height to prevent covering the text field
        val screenHeight = resources.displayMetrics.heightPixels
        val maxHeight = (screenHeight * MAX_KEYBOARD_HEIGHT_RATIO).toInt()
        val cappedHeight = minOf(height, maxHeight).coerceAtLeast((260 * density).toInt())

        if (width > 0 && height > 0) {
            setMeasuredDimension(width, cappedHeight)
        } else {
            val defaultHeight = (300 * density).toInt()
            setMeasuredDimension(
                MeasureSpec.getSize(widthMeasureSpec).coerceAtLeast(720),
                cappedHeight.coerceAtLeast(defaultHeight)
            )
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val keyAreaHeight = h - candidateStripHeight
        keys = KeyboardData.layoutKeys(keys, w.toFloat(), keyAreaHeight, topPadding = candidateStripHeight)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Background
        canvas.drawColor(BACKGROUND_COLOR)

        // 1. Candidate strip (top)
        drawCandidateStrip(canvas)

        // 2. Swipe trail (behind keys)
        if (isGesturing) {
            drawSwipeTrail(canvas)
        }

        // 3. Keys
        for (key in keys) {
            drawKey(canvas, key)
        }

        // 4. Long-press popup (on top of everything)
        if (isLongPressPopupVisible) {
            drawLongPressPopup(canvas)
        }
    }

    // ─── Drawing helpers ───

    /** Draw the prediction candidate strip at the top of the keyboard. */
    private fun drawCandidateStrip(canvas: Canvas) {
        if (candidateWords.isEmpty()) return

        canvas.drawColor(Color.argb(10, 100, 180, 255))
        canvas.drawLine(0f, candidateStripHeight, width.toFloat(), candidateStripHeight, separatorPaint)

        val count = minOf(candidateWords.size, 5)
        val itemWidth = width.toFloat() / count

        for (i in 0 until count) {
            val word = candidateWords[i]
            val cx = itemWidth * i + itemWidth / 2
            val cy = candidateStripHeight / 2
            val pad = 6f * density
            val pillRadius = (candidateStripHeight / 2 - 3f).coerceAtMost(14f * density)

            // Pill background
            val bg = if (i == 0) candidateBgFirstPaint else candidateBgPaint
            canvas.drawRoundRect(
                itemWidth * i + pad, 3f * density,
                itemWidth * (i + 1) - pad, candidateStripHeight - 3f * density,
                pillRadius, pillRadius, bg
            )

            // Text
            candidateTextPaint.textSize = (if (i == 0) 34f else 30f) * density / resources.displayMetrics.density
            candidateTextPaint.color = CANDIDATE_TEXT
            canvas.drawText(word, cx, cy + 12f, candidateTextPaint)
        }
    }

    /** Draw a single key with its background, label, and state-dependent styling. */
    private fun drawKey(canvas: Canvas, key: Key) {
        val isSpecial = key.code < 0
        val bg = when {
            key.isPressed -> keyBgPressedPaint
            isSpecial && key.code != KeyCode.SPACE -> keyBgSpecialPaint
            else -> keyBgPaint
        }
        canvas.drawRoundRect(key.bounds, keyCornerRadius, keyCornerRadius, bg)

        val keyTextSize = 34f

        when (key.code) {
            KeyCode.SPACE -> {
                keyTextPaint.textSize = keyTextSize * 0.75f
                keyTextPaint.color = KEY_TEXT_SECONDARY
                canvas.drawText(
                    if (currentLang == "es") "Espacio" else "Space",
                    key.centerX, key.centerY + 10f, keyTextPaint
                )
            }
            KeyCode.SHIFT -> {
                val isActive = shiftMode || shiftLocked
                keySecondaryPaint.color = if (isActive) KEY_TEXT_ACCENT else KEY_TEXT_SECONDARY
                keySecondaryPaint.textSize = 28f
                canvas.drawText(key.label, key.centerX, key.centerY + 12f, keySecondaryPaint)
            }
            KeyCode.BACKSPACE -> {
                keySecondaryPaint.color = KEY_TEXT_SECONDARY
                keySecondaryPaint.textSize = 28f
                canvas.drawText(key.label, key.centerX, key.centerY + 12f, keySecondaryPaint)
            }
            KeyCode.ENTER -> {
                keySecondaryPaint.color = KEY_TEXT_ACCENT
                keySecondaryPaint.textSize = 28f
                canvas.drawText(key.label, key.centerX, key.centerY + 12f, keySecondaryPaint)
            }
            KeyCode.SWITCH_LANG -> {
                keySecondaryPaint.color = KEY_TEXT_SECONDARY
                keySecondaryPaint.textSize = 26f
                canvas.drawText(key.label, key.centerX, key.centerY + 10f, keySecondaryPaint)
            }
            KeyCode.COMMA, KeyCode.PERIOD -> {
                keyTextPaint.textSize = keyTextSize * 1.1f
                keyTextPaint.color = KEY_TEXT_PRIMARY
                canvas.drawText(key.label, key.centerX, key.centerY + 12f, keyTextPaint)
            }
            else -> {
                keyTextPaint.textSize = keyTextSize
                keyTextPaint.color = KEY_TEXT_PRIMARY
                val displayLabel = if (shiftMode || shiftLocked) key.label.uppercase() else key.label
                canvas.drawText(displayLabel, key.centerX, key.centerY + 11f, keyTextPaint)
            }
        }
    }

    /** Draw the swipe trail line with dots at gesture points. */
    private fun drawSwipeTrail(canvas: Canvas) {
        val points = gestureRecognizer.getTouchPoints()
        if (points.size < 2) return

        val path = Path()
        path.moveTo(points[0].x, points[0].y)
        for (i in 1 until points.size) {
            path.lineTo(points[i].x, points[i].y)
        }
        canvas.drawPath(path, trailPaint)

        trailDotPaint.strokeWidth = 6f * density
        for (pt in points) {
            canvas.drawCircle(pt.x, pt.y, 4f * density, trailDotPaint)
        }
    }

    /**
     * Draw the long-press character popup above the currently held key.
     *
     * Layout: horizontal bar with each variant as a rounded pill.
     * The selected variant is highlighted in blue.
     */
    private fun drawLongPressPopup(canvas: Canvas) {
        if (longPressChars.isEmpty()) return
        val key = longPressKey ?: return

        val charCount = longPressChars.length
        val itemWidth = 48f * density
        val itemHeight = 50f * density
        val popupWidth = charCount * itemWidth + 8f * density
        val popupHeight = itemHeight + 12f * density

        // Position popup centered above the key
        val keyCenterX = key.centerX
        val keyTop = key.bounds.top
        var popupLeft = keyCenterX - popupWidth / 2
        val popupTop = (keyTop - popupHeight - 4f * density).coerceAtLeast(4f)
        var popupRight = popupLeft + popupWidth
        var popupBottom = popupTop + popupHeight

        // Keep within horizontal bounds
        if (popupLeft < 2f) {
            popupLeft = 2f
            popupRight = popupLeft + popupWidth
        }
        if (popupRight > width - 2f) {
            popupRight = width - 2f
            popupLeft = popupRight - popupWidth
        }

        longPressPopupBounds = RectF(popupLeft, popupTop, popupRight, popupBottom)

        // Popup background (rounded rect)
        canvas.drawRoundRect(longPressPopupBounds, 12f * density, 12f * density, popupBgPaint)

        // Draw each character as a selectable pill
        for (i in 0 until charCount) {
            val cx = popupLeft + 4f * density + i * itemWidth + itemWidth / 2
            val cy = popupTop + popupHeight / 2
            val pillLeft = popupLeft + 4f * density + i * itemWidth + 2f * density
            val pillRight = pillLeft + itemWidth - 4f * density
            val pillTop = popupTop + 6f * density
            val pillBottom = popupTop + popupHeight - 6f * density
            val pillRadius = 8f * density

            if (i == longPressSelectedIndex) {
                canvas.drawRoundRect(pillLeft, pillTop, pillRight, pillBottom, pillRadius, pillRadius, popupSelectedBgPaint)
            }

            popupTextPaint.textSize = 32f * density / resources.displayMetrics.density
            canvas.drawText(longPressChars[i].toString(), cx, cy + 12f, popupTextPaint)
        }
    }

    // ─── Touch handling ───

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        // If long-press popup is visible, intercept touch
        if (isLongPressPopupVisible) {
            return handleLongPressTouch(event)
        }

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                touchStartX = x
                touchStartY = y
                hasSwiped = false
                isGesturing = false
                isLongPressPopupVisible = false

                pressedKey = findKeyAt(x, y)
                pressedKey?.isPressed = true

                // If on candidate strip, handle candidate tap
                if (y < candidateStripHeight) {
                    handleCandidateTap(x)
                    return true
                }

                pressedKey?.let { key ->
                    // Check for long-press candidates
                    val chars = key.longPressChars
                    if (chars != null && chars.length >= 2 &&
                        key.code !in longPressBlocklist) {
                        longPressKey = key
                        handler.postDelayed(longPressRunnable, LONG_PRESS_TIMEOUT_MS)
                    }
                    // Check for backspace long-press (repeat delete)
                    if (key.code == KeyCode.BACKSPACE) {
                        longPressKey = key
                        handler.postDelayed({
                            isBackspaceRepeating = true
                            backspaceRepeatInterval = BACKSPACE_REPEAT_INTERVAL_MS
                            handler.post(backspaceRepeatRunnable)
                        }, BACKSPACE_INITIAL_DELAY_MS)
                    }
                }

                // Start gesture tracking
                gestureRecognizer.startGesture(x, y)
                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                // Cancel long-press if finger moves too far
                if (!isLongPressPopupVisible && !isBackspaceRepeating) {
                    val dx = x - touchStartX
                    val dy = y - touchStartY
                    val distSq = dx * dx + dy * dy

                    if (distSq > 400f) { // ~20px movement cancels long-press
                        handler.removeCallbacks(longPressRunnable)
                        handler.removeCallbacks(backspaceRepeatRunnable)
                        isBackspaceRepeating = false
                        longPressKey = null
                    }

                    // Check if we should switch to gesture mode
                    if (!hasSwiped && distSq > 900f) {
                        hasSwiped = true
                        isGesturing = true
                        pressedKey?.isPressed = false
                        pressedKey = null
                        handler.removeCallbacks(longPressRunnable)
                        handler.removeCallbacks(backspaceRepeatRunnable)
                        isBackspaceRepeating = false
                    }
                }

                if (isGesturing) {
                    gestureRecognizer.addPoint(x, y)
                } else if (!isLongPressPopupVisible && !isBackspaceRepeating) {
                    pressedKey?.isPressed = false
                    pressedKey = findKeyAt(x, y)
                    pressedKey?.isPressed = true
                }
                invalidate()
                return true
            }

            MotionEvent.ACTION_UP -> {
                // Cancel all long-press handlers
                handler.removeCallbacks(longPressRunnable)
                handler.removeCallbacks(backspaceRepeatRunnable)
                isBackspaceRepeating = false

                pressedKey?.isPressed = false

                if (isLongPressPopupVisible) {
                    // Commit the selected long-press character
                    if (longPressSelectedIndex < longPressChars.length) {
                        val char = longPressChars[longPressSelectedIndex]
                        val charStr = if (shiftMode || shiftLocked) char.uppercase() else char.toString()
                        ime.commitText(charStr)
                        // Reset shift after one character
                        if (shiftMode && !shiftLocked) {
                            shiftMode = false
                            rebuildKeys()
                        }
                    }
                    isLongPressPopupVisible = false
                    longPressKey = null
                } else if (isGesturing) {
                    // Finish gesture — commit the recognized word (if any)
                    gestureRecognizer.addPoint(x, y)
                    val p = predictor
                    if (p != null) {
                        val recognized = gestureRecognizer.recognize(keys, p, 5)
                        if (recognized.isNotEmpty()) {
                            ime.commitWord(recognized.first().word)
                        }
                    }
                    gestureRecognizer.reset()
                    updatePredictions("")
                } else {
                    // Tap
                    val tappedKey = findKeyAt(x, y)
                    if (tappedKey != null && tappedKey == pressedKey) {
                        handleKeyPress(tappedKey)
                    }
                }

                isGesturing = false
                longPressKey = null
                invalidate()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPressRunnable)
                handler.removeCallbacks(backspaceRepeatRunnable)
                isBackspaceRepeating = false
                isLongPressPopupVisible = false
                pressedKey?.isPressed = false
                isGesturing = false
                gestureRecognizer.reset()
                longPressKey = null
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    /**
     * Handle touch events while the long-press popup is visible.
     * Tracks horizontal finger movement to select a variant,
     * then commits on finger lift.
     */
    private fun handleLongPressTouch(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_MOVE -> {
                // Calculate which character the finger is over
                if (longPressChars.isNotEmpty()) {
                    val itemWidth = 48f * density
                    val relativeX = x - (longPressPopupBounds.left + 4f * density)
                    val index = (relativeX / itemWidth).toInt().coerceIn(0, longPressChars.length - 1)
                    if (index != longPressSelectedIndex) {
                        longPressSelectedIndex = index
                        invalidate()
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // Commit the selected character
                if (longPressSelectedIndex < longPressChars.length) {
                    val char = longPressChars[longPressSelectedIndex]
                    val charStr = if (shiftMode || shiftLocked) char.uppercase() else char.toString()
                    ime.commitText(charStr)
                    if (shiftMode && !shiftLocked) {
                        shiftMode = false
                        rebuildKeys()
                    }
                }
                isLongPressPopupVisible = false
                longPressKey = null
                invalidate()
            }
        }
        return true
    }

    /** Find the key at a given touch coordinate. */
    private fun findKeyAt(x: Float, y: Float): Key? {
        for (key in keys) {
            if (x >= key.bounds.left && x <= key.bounds.right &&
                y >= key.bounds.top && y <= key.bounds.bottom) {
                return key
            }
        }
        return null
    }

    /** Handle a single key tap (press and release without significant movement). */
    private fun handleKeyPress(key: Key) {
        when (key.code) {
            KeyCode.SHIFT -> {
                if (shiftLocked) {
                    shiftLocked = false
                    shiftMode = false
                } else if (shiftMode) {
                    shiftLocked = true
                } else {
                    shiftMode = true
                }
                rebuildKeys()
            }
            KeyCode.BACKSPACE -> ime.deleteBackward()
            KeyCode.SPACE -> ime.performCommitSpace()
            KeyCode.ENTER -> ime.performEnter()
            KeyCode.SWITCH_LANG -> {
                val newLang = if (currentLang == "es") "en" else "es"
                setLanguage(newLang)
                ime.onLanguageChanged(newLang)
            }
            KeyCode.COMMA -> ime.commitText(",")
            KeyCode.PERIOD -> ime.commitText(".")
            else -> {
                val char = if (shiftMode || shiftLocked) key.label.uppercase() else key.label
                ime.commitText(char)
                if (shiftMode && !shiftLocked) {
                    shiftMode = false
                    rebuildKeys()
                }
            }
        }
        invalidate()
    }

    /** Handle a tap on the candidate strip to commit a predicted word. */
    private fun handleCandidateTap(x: Float) {
        if (candidateWords.isEmpty()) return
        val count = candidateWords.size.coerceAtMost(5)
        val itemWidth = width.toFloat() / count
        val index = (x / itemWidth).toInt().coerceIn(0, count - 1)
        if (index < candidateWords.size) {
            val word = candidateWords[index]
            ime.commitWord(word)
            notifyWordSelected(word)
            candidateWords.clear()
            invalidate()
        }
    }
}
