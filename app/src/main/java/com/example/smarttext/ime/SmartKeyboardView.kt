package com.example.smarttext.ime

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import com.example.smarttext.engine.PredictorEngine
import com.example.smarttext.engine.PredictorEngine.WordEntry

/**
 * Custom keyboard view that draws the QWERTY keyboard with Canvas,
 * handles touch (tap, swipe), draws swipe trails, and shows a candidate strip.
 *
 * Uses PredictorEngine for both tap-based predictions and swipe gesture
 * recognition.
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
        /** Keyboard max height as fraction of screen height */
        private const val MAX_KEYBOARD_HEIGHT_RATIO = 0.40f
    }

    // ─── State ───
    private var currentLang: String = "es"
    private var shiftMode: Boolean = false
    private var shiftLocked: Boolean = false
    private var symbolMode: Boolean = false

    // Keys with computed bounds
    private var keys: List<Key> = KeyboardData.generate(currentLang)

    // Candidate strip
    private val candidateWords = mutableListOf<String>()
    private var predictor: PredictorEngine? = null

    // Gesture
    private val gestureRecognizer = GestureRecognizer()
    private var isGesturing: Boolean = false

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

    // Metrics
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

    fun setPredictor(p: PredictorEngine?) {
        predictor = p
        updatePredictions("")
    }

    fun setLanguage(lang: String) {
        currentLang = lang
        shiftMode = false
        rebuildKeys()
        updatePredictions("")
    }

    fun setShiftMode(shifted: Boolean) {
        shiftMode = shifted
        rebuildKeys()
    }

    /** Rebuild key positions after state changes. */
    private fun rebuildKeys() {
        keys = KeyboardData.generate(currentLang, shiftMode)
        post { requestLayout() }
    }

    /** Update the candidate suggestions based on current input context. */
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

    /** Notify the keyboard that a word was selected (update frequency). */
    fun notifyWordSelected(word: String) {
        predictor?.updateFrequency(word)
    }

    // ─── Layout ───

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        android.util.Log.d(TAG, "onMeasure: ${MeasureSpec.getMode(widthMeasureSpec)}x${MeasureSpec.getMode(heightMeasureSpec)} $width x $height")

        // Limit keyboard height to a fraction of screen height so the user can see the text
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
        android.util.Log.d(TAG, "onSizeChanged: $w x $h (old: $oldw x $oldh)")
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
    }

    private fun drawCandidateStrip(canvas: Canvas) {
        if (candidateWords.isEmpty()) return

        // Strip background
        canvas.drawColor(Color.argb(10, 100, 180, 255))
        canvas.drawLine(0f, candidateStripHeight, width.toFloat(), candidateStripHeight, separatorPaint)

        val count = minOf(candidateWords.size, 5)
        val itemWidth = width.toFloat() / count

        for (i in 0 until count) {
            val word = candidateWords[i]
            val cx = itemWidth * i + itemWidth / 2
            val cy = candidateStripHeight / 2

            // Pill background — first candidate highlighted
            val bg = if (i == 0) candidateBgFirstPaint else candidateBgPaint
            val pad = 6f * density
            val pillRadius = (candidateStripHeight / 2 - 3f).coerceAtMost(14f * density)
            canvas.drawRoundRect(
                itemWidth * i + pad, 3f * density,
                itemWidth * (i + 1) - pad, candidateStripHeight - 3f * density,
                pillRadius, pillRadius, bg
            )

            // Text
            val textSize = if (i == 0) 34f else 30f
            candidateTextPaint.textSize = textSize * density / resources.displayMetrics.density
            candidateTextPaint.color = CANDIDATE_TEXT
            canvas.drawText(word, cx, cy + 12f, candidateTextPaint)
        }
    }

    private fun drawKey(canvas: Canvas, key: Key) {
        // Pick background based on key type and pressed state
        val isSpecial = key.code < 0 // Special key (shift, backspace, space, enter, etc.)
        val bg = when {
            key.isPressed -> keyBgPressedPaint
            isSpecial && key.code != KeyCode.SPACE -> keyBgSpecialPaint
            else -> keyBgPaint
        }
        canvas.drawRoundRect(key.bounds, keyCornerRadius, keyCornerRadius, bg)

        // Font size scaling for compact layout
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
                // Regular character key
                keyTextPaint.textSize = keyTextSize
                keyTextPaint.color = KEY_TEXT_PRIMARY
                val displayLabel = if (shiftMode || shiftLocked) {
                    key.label.uppercase()
                } else {
                    key.label
                }
                canvas.drawText(displayLabel, key.centerX, key.centerY + 11f, keyTextPaint)
            }
        }
    }

    private fun drawSwipeTrail(canvas: Canvas) {
        val points = gestureRecognizer.getTouchPoints()
        if (points.size < 2) return

        // Draw the trail line
        val path = Path()
        path.moveTo(points[0].x, points[0].y)
        for (i in 1 until points.size) {
            path.lineTo(points[i].x, points[i].y)
        }
        canvas.drawPath(path, trailPaint)

        // Draw dots at gesture points
        trailDotPaint.strokeWidth = 6f * density
        for (pt in points) {
            canvas.drawCircle(pt.x, pt.y, 4f * density, trailDotPaint)
        }
    }

    // ─── Touch handling ───

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                touchStartX = x
                touchStartY = y
                hasSwiped = false
                isGesturing = false
                pressedKey = findKeyAt(x, y)
                pressedKey?.isPressed = true

                // If on candidate strip, handle candidate tap
                if (y < candidateStripHeight) {
                    handleCandidateTap(x)
                    return true
                }

                // Start gesture tracking
                gestureRecognizer.startGesture(x, y)
                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                // Check if we should switch to gesture mode
                val dx = x - touchStartX
                val dy = y - touchStartY
                if (!hasSwiped && (dx * dx + dy * dy) > 900f) {
                    hasSwiped = true
                    isGesturing = true
                    pressedKey?.isPressed = false
                    pressedKey = null
                }

                if (isGesturing) {
                    gestureRecognizer.addPoint(x, y)
                } else {
                    // Update pressed key highlight
                    pressedKey?.isPressed = false
                    pressedKey = findKeyAt(x, y)
                    pressedKey?.isPressed = true
                }
                invalidate()
                return true
            }

            MotionEvent.ACTION_UP -> {
                pressedKey?.isPressed = false

                if (isGesturing) {
                    // Finish gesture — commit the recognized word (if any)
                    gestureRecognizer.addPoint(x, y)
                    val recognized = gestureRecognizer.recognize(keys, predictor ?: return true, 5)

                    if (recognized.isNotEmpty()) {
                        val best = recognized.first().word
                        ime.commitWord(best)
                    }
                    // IMPORTANT: Do NOT fall back to single character on failed gesture!
                    // The user intended a swipe — writing only the last key would be confusing.
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
                invalidate()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                pressedKey?.isPressed = false
                isGesturing = false
                gestureRecognizer.reset()
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun findKeyAt(x: Float, y: Float): Key? {
        for (key in keys) {
            if (x >= key.bounds.left && x <= key.bounds.right &&
                y >= key.bounds.top && y <= key.bounds.bottom) {
                return key
            }
        }
        return null
    }

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
                // Regular character
                val char = if (shiftMode || shiftLocked) {
                    key.label.uppercase()
                } else {
                    key.label
                }
                ime.commitText(char)
                // Reset shift after typing one character (not locked)
                if (shiftMode && !shiftLocked) {
                    shiftMode = false
                    rebuildKeys()
                }
            }
        }
        invalidate()
    }

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
