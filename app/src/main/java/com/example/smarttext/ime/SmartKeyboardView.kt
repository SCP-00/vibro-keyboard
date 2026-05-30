package com.example.smarttext.ime

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import com.example.smarttext.engine.PredictorEngine

/**
 * Custom keyboard view drawn entirely with Canvas, supporting:
 *
 * - QWERTY layout with language-adaptive rows (no Ñ in English)
 * - Tap typing with shift/caps lock
 * - **Key press popup preview** — magnified character shown above pressed key
 * - **Smooth press animation** — keys animate scale/alpha on press/release
 * - **Ripple touch feedback** — subtle expanding circle on press
 * - Glide/swipe gesture typing with smooth trail
 * - **Long-press** on vowel keys for accented characters
 * - **Long-press** on Backspace for repeat-delete with acceleration
 * - Candidate prediction strip at the top
 */
class SmartKeyboardView(
    context: Context,
    private val ime: SmartIME
) : View(context) {

    companion object {
        private const val TAG = "SmartIME.Keyboard"
        private const val CANDIDATE_STRIP_HEIGHT_DP = 40f
        private const val KEY_MARGIN_DP = 2f
        private const val KEY_CORNER_RADIUS_DP = 6f
        private const val SWIPE_TRAIL_WIDTH_DP = 5f
        private const val SWIPE_TRAIL_ALPHA = 160
        /** Keyboard max height as fraction of screen height. */
        private const val MAX_KEYBOARD_HEIGHT_RATIO = 0.42f

        // ── Long-press constants ──
        private const val LONG_PRESS_TIMEOUT_MS = 350L
        private const val BACKSPACE_INITIAL_DELAY_MS = 300L
        private const val BACKSPACE_REPEAT_INTERVAL_MS = 80L
        private const val BACKSPACE_MIN_INTERVAL_MS = 30L
        private const val BACKSPACE_ACCELERATION = 0.85f

        // ── Animation constants ──
        /** Duration of press animation (ms). */
        private const val PRESS_ANIM_DURATION_MS = 100L
        /** Duration of release animation (ms). */
        private const val RELEASE_ANIM_DURATION_MS = 120L
        /** Scale factor for pressed key. */
        private const val PRESS_SCALE = 1.08f
        /** Duration of ripple animation (ms). */
        private const val RIPPLE_DURATION_MS = 300L
        /** Starting alpha of ripple. */
        private const val RIPPLE_START_ALPHA = 120
        /** Key popup preview height multiplier (relative to key height). */
        private const val POPUP_HEIGHT_MULT = 1.8f
        /** Key popup preview width multiplier. */
        private const val POPUP_WIDTH_MULT = 1.4f
    }

    // ─── State ───
    private var currentLang: String = "es"
    private var shiftMode: Boolean = false
    private var shiftLocked: Boolean = false
    private var keys: List<Key> = KeyboardData.generate(currentLang)

    // Candidate strip
    private val candidateWords = mutableListOf<String>()
    private var predictor: PredictorEngine? = null
    private var previousWord: String? = null

    // Gesture
    private val gestureRecognizer = GestureRecognizer()
    private var isGesturing: Boolean = false

    /** Keys with active press animations: key code -> animation progress 0..1 */
    private val pressedAnimProgress = mutableMapOf<Int, Float>()
    /** Keys that are currently animating press (for ValueAnimator tracking). */
    private val activePressAnims = mutableMapOf<Int, ValueAnimator>()
    /** Current ripple state. */
    private var rippleX: Float = 0f
    private var rippleY: Float = 0f
    private var rippleRadius: Float = 0f
    private var rippleAlpha: Int = 0
    private var isRippleActive: Boolean = false
    /** Ripple animator. */
    private var rippleAnimator: ValueAnimator? = null

    // ─── Key popup preview ───
    /** Key currently showing popup preview (appears immediately on press). */
    private var popupKey: Key? = null
    /** Alpha of the popup preview (for fade-in/out). */
    private var popupAlpha: Float = 1f

    // ─── Long-press state ───
    private val handler = Handler(Looper.getMainLooper())
    private var longPressKey: Key? = null
    private var longPressSelectedIndex: Int = 0
    private var isLongPressPopupVisible: Boolean = false
    private var longPressChars: String = ""
    private var longPressPopupBounds: RectF = RectF()
    private val longPressRunnable = Runnable {
        val key = longPressKey ?: return@Runnable
        val chars = key.longPressChars
        if (chars != null && chars.length >= 2 &&
            key.code !in longPressBlocklist) {
            longPressChars = chars
            isLongPressPopupVisible = true
            longPressSelectedIndex = 0
            popupKey = null // hide the preview popup when long-press popup appears
            invalidate()
        }
    }
    private val longPressBlocklist = setOf(
        KeyCode.BACKSPACE, KeyCode.SHIFT, KeyCode.SPACE,
        KeyCode.ENTER, KeyCode.SWITCH_LANG
    )

    // ─── Backspace repeat state ───
    private var isBackspaceRepeating: Boolean = false
    private var backspaceRepeatInterval: Long = BACKSPACE_REPEAT_INTERVAL_MS
    private val backspaceRepeatRunnable = object : Runnable {
        override fun run() {
            if (!isBackspaceRepeating) return
            ime.deleteBackward()
            backspaceRepeatInterval = maxOf(
                (backspaceRepeatInterval * BACKSPACE_ACCELERATION).toLong(),
                BACKSPACE_MIN_INTERVAL_MS
            )
            handler.postDelayed(this, backspaceRepeatInterval)
        }
    }
    private val backspaceStartRunnable = Runnable {
        isBackspaceRepeating = true
        backspaceRepeatInterval = BACKSPACE_REPEAT_INTERVAL_MS
        handler.post(backspaceRepeatRunnable)
    }

    // ─── Colors ───
    private val KEY_BG_NORMAL = Color.argb(38, 255, 255, 255)
    private val KEY_BG_PRESSED = Color.argb(110, 255, 255, 255)
    private val KEY_BG_SPECIAL = Color.argb(48, 180, 200, 255)
    private val KEY_BG_POPUP = Color.argb(240, 55, 60, 70)
    private val KEY_TEXT_PRIMARY = Color.WHITE
    private val KEY_TEXT_SECONDARY = Color.argb(200, 200, 210, 220)
    private val KEY_TEXT_ACCENT = Color.argb(255, 120, 200, 255)
    private val CANDIDATE_BG = Color.argb(55, 100, 180, 255)
    private val CANDIDATE_BG_FIRST = Color.argb(90, 100, 180, 255)
    private val CANDIDATE_BG_HOVER = Color.argb(70, 100, 180, 255)
    private val CANDIDATE_TEXT = Color.WHITE
    private val CANDIDATE_TEXT_SECONDARY = Color.argb(180, 200, 210, 220)
    private val TRAIL_COLOR = Color.argb(SWIPE_TRAIL_ALPHA, 80, 170, 255)
    private val TRAIL_GLOW_COLOR = Color.argb(60, 80, 170, 255)
    private val TRAIL_DOT_COLOR = Color.argb(220, 100, 180, 255)
    private val BACKGROUND_COLOR = Color.argb(240, 24, 26, 30)
    private val CANDIDATE_STRIP_BG = Color.argb(12, 100, 180, 255)
    private val POPUP_BG = Color.argb(220, 50, 55, 65)
    private val POPUP_SELECTED_BG = Color.argb(255, 100, 180, 255)
    private val POPUP_TEXT = Color.WHITE
    private val RIPPLE_COLOR = Color.argb(RIPPLE_START_ALPHA, 180, 220, 255)

    // ─── Paints ───
    private val keyBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = KEY_BG_NORMAL; style = Paint.Style.FILL
    }
    private val keyBgPressedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = KEY_BG_PRESSED; style = Paint.Style.FILL
    }
    private val keyBgSpecialPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = KEY_BG_SPECIAL; style = Paint.Style.FILL
    }
    private val keyTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = KEY_TEXT_PRIMARY; textAlign = Paint.Align.CENTER
    }
    private val keySecondaryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = KEY_TEXT_SECONDARY; textSize = 28f; textAlign = Paint.Align.CENTER
    }
    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = TRAIL_COLOR; style = Paint.Style.STROKE
        strokeWidth = SWIPE_TRAIL_WIDTH_DP * resources.displayMetrics.density
        strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val trailGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = TRAIL_GLOW_COLOR; style = Paint.Style.STROKE
        strokeWidth = SWIPE_TRAIL_WIDTH_DP * resources.displayMetrics.density * 2.5f
        strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val trailDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = TRAIL_DOT_COLOR; style = Paint.Style.FILL
    }
    private val candidateBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = CANDIDATE_BG; style = Paint.Style.FILL
    }
    private val candidateBgFirstPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = CANDIDATE_BG_FIRST; style = Paint.Style.FILL
    }
    private val candidateTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = CANDIDATE_TEXT; textSize = 34f; textAlign = Paint.Align.CENTER; isFakeBoldText = true
    }
    private val candidateSecondaryTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = CANDIDATE_TEXT_SECONDARY; textSize = 28f; textAlign = Paint.Align.CENTER
    }
    private val separatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(40, 255, 255, 255); strokeWidth = 1f
    }
    private val ripplePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    // ─── Popup preview paints ───
    private val popupBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = KEY_BG_POPUP; style = Paint.Style.FILL
    }
    private val popupTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = POPUP_TEXT; textAlign = Paint.Align.CENTER
    }
    private val popupSelectedBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = POPUP_SELECTED_BG; style = Paint.Style.FILL
    }

    // ─── Long-press paints ───
    private val longPressBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = POPUP_BG; style = Paint.Style.FILL
    }
    private val longPressSelectedBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = POPUP_SELECTED_BG; style = Paint.Style.FILL
    }
    private val longPressTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = POPUP_TEXT; textAlign = Paint.Align.CENTER
    }

    // Metrics (cached)
    private val density = resources.displayMetrics.density
    private val keyCornerRadius = KEY_CORNER_RADIUS_DP * density
    private val candidateStripHeight = CANDIDATE_STRIP_HEIGHT_DP * density

    // ─── Touch tracking ───
    private var pressedKey: Key? = null
    private var touchStartX = 0f
    private var touchStartY = 0f
    private var hasSwiped = false

    // ─── Animators ───
    private val decelerateInterpolator = DecelerateInterpolator()
    private val linearInterpolator = LinearInterpolator()

    // ─── Public API ───

    fun setPredictor(p: PredictorEngine?) {
        predictor = p
        updatePredictions("")
        postInvalidate()
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

    private fun rebuildKeys() {
        keys = KeyboardData.generate(currentLang, shiftMode)
        post { requestLayout() }
    }

    fun updatePredictions(currentWord: String, contextWord: String? = null) {
        this.previousWord = contextWord
        val p = predictor ?: run {
            candidateWords.clear()
            invalidate()
            return
        }
        candidateWords.clear()
        if (currentWord.isNotEmpty()) {
            try {
                val suggestions = p.predict(currentWord, contextWord, 5)
                candidateWords.addAll(suggestions)
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Predict error", e)
            }
        } else if (contextWord != null) {
            try {
                val suggestions = p.predict("", contextWord, 3)
                candidateWords.addAll(suggestions)
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Predict error", e)
            }
        }
        postInvalidate()
    }

    fun notifyWordSelected(word: String) {
        predictor?.updateFrequency(word)
    }

    // ─── Layout ───

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        val screenHeight = resources.displayMetrics.heightPixels
        val maxHeight = (screenHeight * MAX_KEYBOARD_HEIGHT_RATIO).toInt()
        val cappedHeight = minOf(height, maxHeight).coerceAtLeast((280 * density).toInt())
        if (width > 0 && height > 0) {
            setMeasuredDimension(width, cappedHeight)
        } else {
            val defaultHeight = (320 * density).toInt()
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
        canvas.drawColor(BACKGROUND_COLOR)

        // 1. Candidate strip
        drawCandidateStrip(canvas)

        // 2. Swipe trail (behind keys)
        if (isGesturing) drawSwipeTrail(canvas)

        // 3. Ripple effect (behind pressed key)
        if (isRippleActive) drawRipple(canvas)

        // 4. Keys
        for (key in keys) drawKey(canvas, key)

        // 5. Key popup preview (above pressed key, NOT during long-press)
        if (popupKey != null && !isLongPressPopupVisible) {
            drawKeyPopupPreview(canvas, popupKey!!)
        }

        // 6. Long-press popup (on top of everything)
        if (isLongPressPopupVisible) drawLongPressPopup(canvas)
    }

    // ─── Drawing helpers ───

    /** Draw the prediction candidate strip. */
    private fun drawCandidateStrip(canvas: Canvas) {
        if (candidateWords.isEmpty()) return
        canvas.drawColor(CANDIDATE_STRIP_BG)
        val dividerY = candidateStripHeight
        canvas.drawLine(0f, dividerY, width.toFloat(), dividerY, separatorPaint)

        val count = minOf(candidateWords.size, 5)
        val itemWidth = width.toFloat() / count

        for (i in 0 until count) {
            val word = candidateWords[i]
            val cx = itemWidth * i + itemWidth / 2
            val cy = candidateStripHeight / 2
            val pad = 8f * density
            val pillRadius = (candidateStripHeight / 2 - 4f).coerceAtMost(14f * density)
            val left = itemWidth * i + pad
            val top = 4f * density
            val right = itemWidth * (i + 1) - pad
            val bottom = candidateStripHeight - 4f * density

            // Pill background — first suggestion is more prominent
            val bg = if (i == 0) candidateBgFirstPaint else candidateBgPaint
            canvas.drawRoundRect(left, top, right, bottom, pillRadius, pillRadius, bg)

            // Text
            val textPaint = if (i == 0) candidateTextPaint else candidateSecondaryTextPaint
            textPaint.textSize = (if (i == 0) 34f else 28f) * density / resources.displayMetrics.density
            canvas.drawText(word, cx, cy + 11f, textPaint)
        }
    }

    /**
     * Draw a single key with press animation, background, and label.
     */
    private fun drawKey(canvas: Canvas, key: Key) {
        val bounds = key.bounds
        val isSpecial = key.code < 0
        val animProgress = pressedAnimProgress[key.code] ?: 0f

        // Calculate scale transform for pressed keys
        val scale: Float
        val alphaBoost: Float
        if (animProgress > 0f) {
            // Interpolate scale: 1.0 -> PRESS_SCALE
            scale = 1f + (PRESS_SCALE - 1f) * animProgress
            // Interpolate alpha boost for background
            alphaBoost = animProgress
        } else {
            scale = 1f
            alphaBoost = 0f
        }

        // Determine background color
        val baseBgColor = when {
            key.isPressed || animProgress > 0f -> KEY_BG_PRESSED
            isSpecial && key.code != KeyCode.SPACE -> KEY_BG_SPECIAL
            else -> KEY_BG_NORMAL
        }

        // If animating, blend between pressed and normal based on progress
        val bgColor = if (alphaBoost > 0f && alphaBoost < 1f) {
            // Interpolate alpha between normal and pressed
            val normalAlpha = Color.alpha(KEY_BG_NORMAL)
            val pressedAlpha = Color.alpha(KEY_BG_PRESSED)
            val currentAlpha = (normalAlpha + (pressedAlpha - normalAlpha) * alphaBoost).toInt()
            val baseColor = if (isSpecial && key.code != KeyCode.SPACE) KEY_BG_SPECIAL else KEY_BG_NORMAL
            val r = Color.red(baseColor); val g = Color.green(baseColor); val b = Color.blue(baseColor)
            Color.argb(currentAlpha, r, g, b)
        } else {
            baseBgColor
        }

        keyBgPaint.color = bgColor

        // Apply scale transform: draw centered on the key
        if (scale > 1f) {
            val cx = bounds.centerX()
            val cy = bounds.centerY()
            val hw = bounds.width() * scale / 2f
            val hh = bounds.height() * scale / 2f
            canvas.drawRoundRect(
                cx - hw, cy - hh, cx + hw, cy + hh,
                keyCornerRadius, keyCornerRadius, keyBgPaint
            )
        } else {
            canvas.drawRoundRect(bounds, keyCornerRadius, keyCornerRadius, keyBgPaint)
        }

        // Draw key label
        val keyTextSize = 36f
        when (key.code) {
            KeyCode.SPACE -> {
                keyTextPaint.textSize = keyTextSize * 0.7f
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
                canvas.drawText(displayLabel, key.centerX, key.centerY + 12f, keyTextPaint)
            }
        }
    }

    /**
     * Draw the key popup preview — a magnified character above the pressed key.
     * Appears immediately on press (no delay).
     */
    private fun drawKeyPopupPreview(canvas: Canvas, key: Key) {
        val keyBounds = key.bounds
        val char = (if (shiftMode || shiftLocked) key.label.uppercase() else key.label)
        val previewWidth = keyBounds.width() * POPUP_WIDTH_MULT
        val previewHeight = keyBounds.height() * POPUP_HEIGHT_MULT
        val cornerRadius = 12f * density

        val previewLeft = key.centerX - previewWidth / 2
        val previewBottom = keyBounds.top - 4f * density
        val previewTop = previewBottom - previewHeight
        val previewRight = previewLeft + previewWidth

        // Keep within horizontal bounds
        val adjustedLeft = previewLeft.coerceAtLeast(2f)
        val adjustedRight = previewRight.coerceAtMost(width.toFloat() - 2f)

        // Popup background with rounded corners
        popupBgPaint.alpha = (popupAlpha * 240).toInt()
        canvas.drawRoundRect(
            adjustedLeft, previewTop, adjustedRight, previewBottom,
            cornerRadius, cornerRadius, popupBgPaint
        )

        // Arrow/pointer connecting popup to key
        val pointerPaint = Paint(popupBgPaint).apply {
            style = Paint.Style.FILL
        }
        pointerPaint.alpha = (popupAlpha * 240).toInt()
        val pointerW = 14f * density
        val pointerH = 8f * density
        val pointerPath = Path().apply {
            moveTo(key.centerX - pointerW / 2, previewBottom)
            lineTo(key.centerX, previewBottom + pointerH)
            lineTo(key.centerX + pointerW / 2, previewBottom)
            close()
        }
        canvas.drawPath(pointerPath, pointerPaint)

        // Character text — large and centered
        popupTextPaint.textSize = 56f * density / resources.displayMetrics.density
        popupTextPaint.alpha = (popupAlpha * 255).toInt()
        val textY = previewTop + previewHeight / 2 + 18f
        canvas.drawText(char, key.centerX, textY, popupTextPaint)
    }

    /** Draw the ripple touch feedback. */
    private fun drawRipple(canvas: Canvas) {
        ripplePaint.color = Color.argb(rippleAlpha, 180, 220, 255)
        canvas.drawCircle(rippleX, rippleY, rippleRadius, ripplePaint)
    }

    /** Draw the swipe trail with glow effect and smooth bezier curves. */
    private fun drawSwipeTrail(canvas: Canvas) {
        val points = gestureRecognizer.getTouchPoints()
        if (points.size < 2) return

        // Build smooth path using quadratic bezier curves
        val path = Path()
        path.moveTo(points[0].x, points[0].y)
        if (points.size == 2) {
            path.lineTo(points[1].x, points[1].y)
        } else {
            for (i in 1 until points.size - 1) {
                val midX = (points[i].x + points[i + 1].x) / 2f
                val midY = (points[i].y + points[i + 1].y) / 2f
                path.quadTo(points[i].x, points[i].y, midX, midY)
            }
            // Last segment
            val last = points.last()
            path.lineTo(last.x, last.y)
        }

        // Glow layer (wider, more transparent)
        canvas.drawPath(path, trailGlowPaint)

        // Main trail
        canvas.drawPath(path, trailPaint)

        // Dots at key inflection points
        if (points.size >= 3) {
            trailDotPaint.strokeWidth = 6f * density
            val step = maxOf(1, points.size / 12)
            for (i in 0 until points.size step step) {
                val pt = points[i]
                val dotRadius = when (i) {
                    0, points.size - 1 -> 5f * density
                    else -> 3.5f * density
                }
                canvas.drawCircle(pt.x, pt.y, dotRadius, trailDotPaint)
            }
        }
    }

    /** Draw the long-press character popup. */
    private fun drawLongPressPopup(canvas: Canvas) {
        if (longPressChars.isEmpty()) return
        val key = longPressKey ?: return

        val charCount = longPressChars.length
        val itemWidth = 50f * density
        val itemHeight = 52f * density
        val popupWidth = charCount * itemWidth + 10f * density
        val popupHeight = itemHeight + 14f * density

        val keyCenterX = key.centerX
        val keyTop = key.bounds.top
        var popupLeft = keyCenterX - popupWidth / 2
        val popupTop = (keyTop - popupHeight - 4f * density).coerceAtLeast(4f)
        var popupRight = popupLeft + popupWidth
        val popupBottom = popupTop + popupHeight

        if (popupLeft < 2f) { popupLeft = 2f; popupRight = popupLeft + popupWidth }
        if (popupRight > width - 2f) { popupRight = width - 2f; popupLeft = popupRight - popupWidth }

        longPressPopupBounds = RectF(popupLeft, popupTop, popupRight, popupBottom)
        canvas.drawRoundRect(longPressPopupBounds, 14f * density, 14f * density, longPressBgPaint)

        for (i in 0 until charCount) {
            val pillLeft = popupLeft + 5f * density + i * itemWidth + 2f * density
            val pillRight = pillLeft + itemWidth - 4f * density
            val pillTop = popupTop + 7f * density
            val pillBottom = popupTop + popupHeight - 7f * density
            val pillRadius = 8f * density
            val cx = pillLeft + (pillRight - pillLeft) / 2

            if (i == longPressSelectedIndex) {
                canvas.drawRoundRect(pillLeft, pillTop, pillRight, pillBottom, pillRadius, pillRadius, longPressSelectedBgPaint)
            }

            longPressTextPaint.textSize = 32f * density / resources.displayMetrics.density
            canvas.drawText(longPressChars[i].toString(), cx, pillBottom - 10f, longPressTextPaint)
        }
    }

    // ─── Touch handling ───

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        if (isLongPressPopupVisible) return handleLongPressTouch(event)

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                touchStartX = x; touchStartY = y
                hasSwiped = false; isGesturing = false
                isLongPressPopupVisible = false

                pressedKey = findKeyAt(x, y)
                pressedKey?.isPressed = true

                // Start popup preview immediately for letter keys
                if (y >= candidateStripHeight) {
                    val key = pressedKey
                    if (key != null && key.code > 0) {
                        showKeyPopup(key)
                    }
                }

                // Start press animation
                pressedKey?.let { animateKeyPress(it) }

                // If on candidate strip, handle candidate tap
                if (y < candidateStripHeight) {
                    handleCandidateTap(x); return true
                }

                pressedKey?.let { key ->
                    val chars = key.longPressChars
                    if (chars != null && chars.length >= 2 && key.code !in longPressBlocklist) {
                        longPressKey = key
                        handler.postDelayed(longPressRunnable, LONG_PRESS_TIMEOUT_MS)
                    }
                    if (key.code == KeyCode.BACKSPACE) {
                        longPressKey = key
                        handler.postDelayed(backspaceStartRunnable, BACKSPACE_INITIAL_DELAY_MS)
                    }
                }

                // Start ripple at touch point
                startRipple(x, y)

                gestureRecognizer.startGesture(x, y)
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                invalidate(); return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = x - touchStartX; val dy = y - touchStartY
                val distSq = dx * dx + dy * dy

                if (distSq > 400f) {
                    handler.removeCallbacks(longPressRunnable)
                    handler.removeCallbacks(backspaceStartRunnable)
                    handler.removeCallbacks(backspaceRepeatRunnable)
                    isBackspaceRepeating = false
                    longPressKey = null
                }

                if (!hasSwiped && !isBackspaceRepeating && distSq > 900f) {
                    hasSwiped = true; isGesturing = true
                    pressedKey?.isPressed = false
                    hideKeyPopup()
                    cancelPressAnimation(pressedKey?.code ?: return true)
                    pressedKey = null
                    handler.removeCallbacks(longPressRunnable)
                    handler.removeCallbacks(backspaceStartRunnable)
                    handler.removeCallbacks(backspaceRepeatRunnable)
                    isBackspaceRepeating = false
                }

                if (isGesturing) {
                    gestureRecognizer.addPoint(x, y)
                    // Hide popup when swiping
                    popupKey = null
                } else if (!isLongPressPopupVisible && !isBackspaceRepeating) {
                    pressedKey?.isPressed = false
                    val newKey = findKeyAt(x, y)
                    if (newKey != pressedKey) {
                        cancelPressAnimation(pressedKey?.code ?: 0)
                        pressedKey = newKey
                        pressedKey?.isPressed = true
                        if (newKey != null && newKey.code > 0) {
                            showKeyPopup(newKey)
                            animateKeyPress(newKey)
                        } else {
                            hideKeyPopup()
                        }
                    }
                }
                invalidate(); return true
            }

            MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(longPressRunnable)
                handler.removeCallbacks(backspaceStartRunnable)
                handler.removeCallbacks(backspaceRepeatRunnable)
                isBackspaceRepeating = false

                pressedKey?.isPressed = false

                if (isLongPressPopupVisible) {
                    if (longPressSelectedIndex < longPressChars.length) {
                        val char = longPressChars[longPressSelectedIndex]
                        val charStr = if (shiftMode || shiftLocked) char.uppercase() else char.toString()
                        ime.commitText(charStr)
                        if (shiftMode && !shiftLocked) { shiftMode = false; rebuildKeys() }
                    }
                    isLongPressPopupVisible = false; longPressKey = null
                } else if (isGesturing) {
                    gestureRecognizer.forceAddPoint(x, y)
                    val p = predictor
                    if (p != null) {
                        val recognized = gestureRecognizer.recognize(keys, p, 5, previousWord)
                        if (recognized.isNotEmpty()) {
                            val best = recognized.first()
                            // Pass gesture pattern and DTW score for gesture-aware autocorrection
                            val gesturePattern = gestureRecognizer.getLastKeySequence().joinToString("")
                            ime.commitWord(best.word, gesturePattern, best.score)
                        }
                    }
                    gestureRecognizer.reset()
                    updatePredictions("")
                } else {
                    val tappedKey = findKeyAt(x, y)
                    if (tappedKey != null && tappedKey == pressedKey) {
                        handleKeyPress(tappedKey)
                    }
                }

                // Animate release and hide popup
                pressedKey?.let { animateKeyRelease(it) }
                hideKeyPopup()
                isRippleActive = false

                isGesturing = false; longPressKey = null
                invalidate(); return true
            }

            MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPressRunnable)
                handler.removeCallbacks(backspaceRepeatRunnable)
                isBackspaceRepeating = false
                isLongPressPopupVisible = false
                pressedKey?.isPressed = false
                hideKeyPopup()
                isRippleActive = false
                isGesturing = false
                gestureRecognizer.reset()
                longPressKey = null
                invalidate(); return true
            }
        }
        return super.onTouchEvent(event)
    }

    // ─── Animation helpers ───

    /** Start press animation for a key using ValueAnimator. */
    private fun animateKeyPress(key: Key) {
        val code = key.code
        cancelPressAnimation(code)

        val anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = PRESS_ANIM_DURATION_MS
            interpolator = decelerateInterpolator
            addUpdateListener { animator ->
                pressedAnimProgress[code] = animator.animatedFraction
                invalidate()
            }
            start()
        }
        activePressAnims[code] = anim
        pressedAnimProgress[code] = 0f
    }

    /** Animate key release (scale back to normal). */
    private fun animateKeyRelease(key: Key) {
        val code = key.code
        cancelPressAnimation(code)

        val anim = ValueAnimator.ofFloat(pressedAnimProgress[code] ?: 1f, 0f).apply {
            duration = RELEASE_ANIM_DURATION_MS
            interpolator = decelerateInterpolator
            addUpdateListener { animator ->
                val value = animator.animatedValue as Float
                pressedAnimProgress[code] = value
                if (value <= 0.01f) {
                    pressedAnimProgress.remove(code)
                }
                invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    pressedAnimProgress.remove(code)
                    activePressAnims.remove(code)
                }
            })
            start()
        }
        activePressAnims[code] = anim
    }

    /** Cancel any running press animation for a key. */
    private fun cancelPressAnimation(code: Int) {
        activePressAnims[code]?.cancel()
        activePressAnims.remove(code)
        pressedAnimProgress.remove(code)
    }

    /** Show the key popup preview immediately. */
    private fun showKeyPopup(key: Key) {
        if (key.code <= 0) return // Don't show popup for special keys
        popupKey = key
        popupAlpha = 1f
    }

    /** Hide the key popup preview. */
    private fun hideKeyPopup() {
        popupKey = null
        popupAlpha = 0f
    }

    /** Start ripple animation at touch coordinates. */
    private fun startRipple(x: Float, y: Float) {
        rippleX = x; rippleY = y
        isRippleActive = true
        rippleAnimator?.cancel()

        val maxRadius = 80f * density
        rippleAnimator = ValueAnimator.ofFloat(0f, maxRadius).apply {
            duration = RIPPLE_DURATION_MS
            interpolator = linearInterpolator
            addUpdateListener { animator ->
                rippleRadius = animator.animatedValue as Float
                val progress = animator.animatedFraction
                rippleAlpha = (RIPPLE_START_ALPHA * (1f - progress)).toInt()
                if (rippleAlpha < 5) rippleAlpha = 5
                invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    isRippleActive = false
                    invalidate()
                }
            })
            start()
        }
    }

    // ─── Touch helpers ───

    private fun handleLongPressTouch(event: MotionEvent): Boolean {
        val x = event.x
        when (event.action) {
            MotionEvent.ACTION_MOVE -> {
                if (longPressChars.isNotEmpty()) {
                    val itemWidth = 50f * density
                    val relativeX = x - (longPressPopupBounds.left + 5f * density)
                    val index = (relativeX / itemWidth).toInt().coerceIn(0, longPressChars.length - 1)
                    if (index != longPressSelectedIndex) {
                        longPressSelectedIndex = index; invalidate()
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (longPressSelectedIndex < longPressChars.length) {
                    val char = longPressChars[longPressSelectedIndex]
                    val charStr = if (shiftMode || shiftLocked) char.uppercase() else char.toString()
                    ime.commitText(charStr)
                    if (shiftMode && !shiftLocked) { shiftMode = false; rebuildKeys() }
                }
                isLongPressPopupVisible = false; longPressKey = null; invalidate()
            }
        }
        return true
    }

    private fun findKeyAt(x: Float, y: Float): Key? {
        for (key in keys) {
            if (x >= key.bounds.left && x <= key.bounds.right &&
                y >= key.bounds.top && y <= key.bounds.bottom) return key
        }
        return null
    }

    private fun handleKeyPress(key: Key) {
        when (key.code) {
            KeyCode.SHIFT -> {
                if (shiftLocked) { shiftLocked = false; shiftMode = false }
                else if (shiftMode) { shiftLocked = true }
                else { shiftMode = true }
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
                if (shiftMode && !shiftLocked) { shiftMode = false; rebuildKeys() }
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
