package com.example.smarttext.ime

import android.inputmethodservice.InputMethodService
import android.text.InputType
import android.util.Log
import android.view.View
import kotlin.jvm.Volatile
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import com.example.smarttext.engine.PredictorEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext

/**
 * SmartText Input Method Editor (IME) — teclado virtual completo.
 *
 * Características:
 * - Teclado QWERTY con fila numérica y tecla Ñ
 * - Predicción de palabras con FuzzyScorer (Mamdani)
 * - Corrección ortográfica vía Levenshtein
 * - Gestos de deslizamiento (swipe/glide typing)
 * - Soporte español/inglés
 * - Persistencia de frecuencia de usuario
 * - Thread-safe con @Synchronized
 */
class SmartIME : InputMethodService() {

    companion object {
        private const val TAG = "SmartIME"
        private const val DEBUG = true
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile
    private var predictor: PredictorEngine? = null
    private var keyboardView: SmartKeyboardView? = null
    private var currentLang: String = "es"
    private var currentInputWord: String = ""
    private var previousWord: String? = null  // For bigram context & autocorrect

    // Gesture info for gesture-aware autocorrection
    private var lastGesturePattern: String? = null
    private var lastGestureDtwScore: Float? = null

    // ─── Lifecycle ───

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate called")
        initPredictor()
    }

    override fun onBindInput() {
        super.onBindInput()
        Log.d(TAG, "onBindInput called")
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        Log.d(TAG, "onStartInput called, restarting=$restarting")
        if (DEBUG && attribute != null) {
            Log.d(TAG, "onStartInput: inputType=${attribute.inputType}, imeOptions=${attribute.imeOptions}")
        }
    }

    private fun initPredictor() {
        scope.launch {
            try {
                val pred = withContext(Dispatchers.IO) {
                    PredictorEngine(this@SmartIME, currentLang)
                }
                predictor = pred
                Log.d(TAG, "Predictor initialized: ${pred.allWords.size} words")
                // Update keyboard view if already created
                post {
                    keyboardView?.setPredictor(pred)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize predictor", e)
            }
        }
    }

    override fun onCreateInputView(): View {
        Log.d(TAG, "onCreateInputView called — creating keyboard view")
        val kbView = SmartKeyboardView(this, this).also { keyboardView = it }
        // Set predictor if already initialized
        predictor?.let { kbView.setPredictor(it) }
        Log.d(TAG, "onCreateInputView returning view: ${kbView.width}x${kbView.height}")
        return kbView
    }

    override fun onStartInputView(editorInfo: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(editorInfo, restarting)
        Log.d(TAG, "onStartInputView called, inputType=${editorInfo?.inputType}, restarting=$restarting")
        currentInputWord = ""

        // Adjust for different input types
        if (editorInfo != null) {
            when (editorInfo.inputType and InputType.TYPE_MASK_CLASS) {
                InputType.TYPE_CLASS_NUMBER,
                InputType.TYPE_CLASS_DATETIME -> {
                    // Could switch to numeric layout
                }
                InputType.TYPE_CLASS_TEXT -> {
                    // Standard text input
                }
            }
        }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        Log.d(TAG, "onFinishInputView called, finishing=$finishingInput")
        currentInputWord = ""
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy called")
        scope.cancel()
        predictor = null
        keyboardView = null
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onEvaluateInputViewShown(): Boolean = true

    // ─── InputConnection helpers ───

    /**
     * Commit a single character (from a key press).
     *
     * For **letters**, appends to [currentInputWord] and triggers prediction updates.
     * For **word-ending punctuation** (.,!?;:), finalizes and autocorrects the word
     * before committing the punctuation.
     * For **other characters**, simply commits without affecting word tracking.
     */
    fun commitText(text: String) {
        val ic = currentInputConnection ?: return

        if (text.length == 1 && text[0].isLetter()) {
            // ── Letter — build current word ──
            // Clear gesture info since user is now typing manually
            if (currentInputWord.isEmpty()) {
                lastGesturePattern = null
                lastGestureDtwScore = null
            }
            currentInputWord += text
            keyboardView?.updatePredictions(currentInputWord, previousWord)
            ic.commitText(text, 1)
            return
        }

        // ── Non-letter character ──
        val isWordEndingPunctuation = text in listOf(".", ",", "!", "?", ";", ":")

        if (currentInputWord.isNotEmpty() && isWordEndingPunctuation) {
            // ── Autocorrect + finalize word before punctuation ──
            val prevWordLength = currentInputWord.length
            val wasUpperCase = currentInputWord.first().isUpperCase()
            val correction = autocorrectCurrentWord()

            ic.beginBatchEdit()
            if (correction != null) {
                // Replace the typed word with the corrected word
                ic.deleteSurroundingText(prevWordLength, 0)
                val correctedCased = if (wasUpperCase)
                    correction.replaceFirstChar { it.uppercase() } else correction
                ic.commitText(correctedCased, 1)
            }
            ic.commitText(text, 1)
            ic.endBatchEdit()
            return
        }

        // ── Other non-letter (not word-ending punctuation) — finalize without autocorrect ──
        if (currentInputWord.isNotEmpty()) {
            val word = currentInputWord.lowercase()
            scope.launch {
                predictor?.updateFrequency(word)
            }
            previousWord = word
        }
        currentInputWord = ""
        ic.commitText(text, 1)
        if (text == " " || text == "." || text == ",") {
            keyboardView?.updatePredictions("", previousWord)
        }
    }

    /**
     * Press the space bar with autocorrection.
     *
     * If the current word is not in the dictionary and a good correction
     * is found, replaces the word before committing the space.
     */
    fun performCommitSpace() {
        val ic = currentInputConnection ?: return

        // Save word info for field manipulation before autocorrectCurrentWord clears it
        val prevWordLength = currentInputWord.length
        val wasUpperCase = currentInputWord.isNotEmpty() && currentInputWord.first().isUpperCase()

        val correction = autocorrectCurrentWord()

        ic.beginBatchEdit()
        if (correction != null) {
            // Autocorrect: delete original word, commit corrected + space
            ic.deleteSurroundingText(prevWordLength, 0)
            val correctedCased = if (wasUpperCase)
                correction.replaceFirstChar { it.uppercase() } else correction
            ic.commitText(correctedCased, 1)
        }
        ic.commitText(" ", 1)
        ic.endBatchEdit()
    }

    /**
     * Autocorrect the current word (if needed) and update all internal state.
     *
     * This method consolidates the word-finalization logic used by both
     * [performCommitSpace] and the punctuation path in [commitText].
     *
     * ## What it does:
     * 1. Checks if [currentInputWord] needs autocorrection via [PredictorEngine.autocorrect]
     * 2. If a correction is found (and differs), returns the corrected word
     * 3. Updates word frequency (corrected word if autocorrected, original otherwise)
     * 4. Sets [previousWord] for next-word prediction context
     * 5. Clears [currentInputWord] and refreshes prediction strip
     *
     * @return The corrected word if autocorrection was applied, or null if no correction.
     */
    private fun autocorrectCurrentWord(): String? {
        if (currentInputWord.isEmpty()) return null

        val typedWord = currentInputWord.lowercase()
        val p = predictor
        var correction: String? = null

        if (p != null) {
            // Use gesture-aware autocorrect if gesture info is available
            val cand = if (lastGesturePattern != null) {
                p.autocorrect(typedWord, lastGesturePattern, lastGestureDtwScore)
            } else {
                p.autocorrect(typedWord)
            }
            if (cand != null && cand != typedWord) {
                correction = cand
                Log.d(TAG, "Autocorrect: '$typedWord' -> '$cand'")
                scope.launch {
                    p.updateFrequency(cand)
                }
            } else {
                scope.launch {
                    p.updateFrequency(typedWord)
                }
            }
        }

        // Clear gesture info after autocorrection
        lastGesturePattern = null
        lastGestureDtwScore = null

        previousWord = correction ?: typedWord
        currentInputWord = ""
        keyboardView?.updatePredictions("", previousWord)

        return correction
    }

    /**
     * Commit a full word (from predictions or swipe).
     * Replaces the current composing text.
     *
     * @param word The word to commit.
     * @param gesturePattern Optional key sequence from gesture typing.
     * @param gestureDtwScore Optional DTW spatial score from gesture recognizer.
     */
    fun commitWord(word: String, gesturePattern: String? = null, gestureDtwScore: Float? = null) {
        val ic = currentInputConnection ?: return

        // Store gesture info for potential autocorrection of subsequent words
        if (gesturePattern != null) {
            lastGesturePattern = gesturePattern
            lastGestureDtwScore = gestureDtwScore
            Log.d(TAG, "Gesture word stored: '$word' pattern='$gesturePattern' dtwScore=$gestureDtwScore")
        } else {
            lastGesturePattern = null
            lastGestureDtwScore = null
        }

        // Delete the current partial input
        if (currentInputWord.isNotEmpty()) {
            ic.deleteSurroundingText(currentInputWord.length, 0)
        }

        ic.commitText(word + " ", 1)
        previousWord = word.lowercase()
        currentInputWord = ""

        // Update frequency on background
        scope.launch {
            predictor?.updateFrequency(word.lowercase())
        }

        keyboardView?.updatePredictions("", previousWord)
        keyboardView?.notifyWordSelected(word)
    }

    /**
     * Delete one character backward (backspace).
     */
    fun deleteBackward() {
        val ic = currentInputConnection ?: return
        if (currentInputWord.isNotEmpty()) {
            currentInputWord = currentInputWord.dropLast(1)
            keyboardView?.updatePredictions(currentInputWord, previousWord)
        }
        ic.deleteSurroundingText(1, 0)
    }

    /**
     * Send enter key.
     */
    fun performEnter() {
        val ic = currentInputConnection ?: return
        ic.commitText("\n", 1)
        currentInputWord = ""
        previousWord = null
        lastGesturePattern = null
        lastGestureDtwScore = null
        keyboardView?.updatePredictions("")
    }

    /**
     * Called when language is switched from the keyboard.
     */
    fun onLanguageChanged(newLang: String) {
        currentLang = newLang
        scope.launch {
            try {
                val pred = withContext(Dispatchers.IO) {
                    PredictorEngine(this@SmartIME, currentLang)
                }
                predictor = pred
                post {
                    keyboardView?.setPredictor(pred)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to reinitialize predictor", e)
            }
        }
    }

    // ─── UI thread helpers ───

    private fun post(action: () -> Unit) {
        keyboardView?.post(action)
    }
}
