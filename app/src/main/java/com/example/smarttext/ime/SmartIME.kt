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
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile
    private var predictor: PredictorEngine? = null
    private var keyboardView: SmartKeyboardView? = null
    private var currentLang: String = "es"
    private var currentInputWord: String = ""

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
        if (attribute != null) {
            Log.d(TAG, "onStartInput: inputType=${attribute.inputType}, imeOptions=${attribute.imeOptions}, packageName=${attribute.packageName}, fieldId=${attribute.fieldId}")
            val cls = attribute.inputType and android.text.InputType.TYPE_MASK_CLASS
            val variation = attribute.inputType and android.text.InputType.TYPE_MASK_VARIATION
            Log.d(TAG, "onStartInput: class=$cls, variation=$variation, TYPE_CLASS_TEXT=${android.text.InputType.TYPE_CLASS_TEXT}")
        } else {
            Log.d(TAG, "onStartInput: attribute is NULL")
        }
        Log.d(TAG, "onStartInput: isInputViewShown=${isInputViewShown}, isFullscreenMode=${isFullscreenMode}")
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

    // ─── InputConnection helpers ───

    /**
     * Commit a single character (from a key press).
     */
    fun commitText(text: String) {
        val ic = currentInputConnection ?: return

        // Track current word for predictions
        if (text.length == 1 && text[0].isLetter()) {
            currentInputWord += text
            keyboardView?.updatePredictions(currentInputWord)
        } else {
            // Non-letter character (space, punctuation) — reset current word
            val p = predictor
            if (currentInputWord.isNotEmpty() && p != null) {
                scope.launch {
                    p.updateFrequency(currentInputWord.lowercase())
                }
            }
            currentInputWord = ""
            if (text == " " || text == "." || text == ",") {
                keyboardView?.updatePredictions("")
            }
        }

        ic.commitText(text, 1)
    }

    /**
     * Commit a full word (from predictions or swipe).
     * Replaces the current composing text.
     */
    fun commitWord(word: String) {
        val ic = currentInputConnection ?: return

        // Delete the current partial input
        if (currentInputWord.isNotEmpty()) {
            ic.deleteSurroundingText(currentInputWord.length, 0)
        }

        ic.commitText(word + " ", 1)
        currentInputWord = ""

        // Update frequency on background
        scope.launch {
            predictor?.updateFrequency(word.lowercase())
        }

        keyboardView?.updatePredictions("")
        keyboardView?.notifyWordSelected(word)
    }

    /**
     * Delete one character backward (backspace).
     */
    fun deleteBackward() {
        val ic = currentInputConnection ?: return
        if (currentInputWord.isNotEmpty()) {
            currentInputWord = currentInputWord.dropLast(1)
            keyboardView?.updatePredictions(currentInputWord)
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
