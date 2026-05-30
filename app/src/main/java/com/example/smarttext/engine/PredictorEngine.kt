package com.example.smarttext.engine

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.util.LinkedHashMap

/**
 * SmartText PredictorEngine — motor de predicción de palabras nativo Kotlin.
 *
 * ## Arquitectura
 *
 * Reemplaza el predictor Python/Chaquopy con una implementación Kotlin pura,
 * optimizada para dispositivos de gama baja (sin dependencia de Python).
 *
 * ## Algoritmos
 *
 * 1. **Búsqueda por prefijo** — lista ordenada + bisección O(log n)
 * 2. **FuzzyScorer (Lógica Difusa Mamdani)** — Levenshtein + frecuencia + contexto
 * 3. **LRU cache** para prefijos comunes (128 entradas)
 * 4. **Bigramas** para predicción por contexto
 * 5. **Frecuencia de usuario persistente**
 * 6. **Autocorrección** con detección de errores comunes (doble letra, omisiones,
 *    teclas adyacentes QWERTY, inserciones)
 * 7. **Gesture-aware correction** — usa el patrón de deslizamiento (key sequence)
 *    para guiar la búsqueda de candidatos de autocorrección
 *
 * ## Optimizaciones
 * - Sorted word list + bisect para búsqueda de prefijo O(log n)
 * - LRU cache para prefijos comunes
 * - Levenshtein reducido a top 500 palabras
 * - Sin dependencia de Python/Chaquopy (~10-50× más rápido en gama baja)
 * - Thread-safe con @Synchronized en métodos compartidos
 */
class PredictorEngine(private val context: Context, private val lang: String) {

    companion object {
        private const val TAG = "SmartText"
        private const val CACHE_SIZE = 128
        private const val MIN_LENGTH = 3
        private const val LEVENSHTEIN_CANDIDATES = 500
        private const val USER_BOOST = 10
        /** High surrogate for binary search upper bound. */
        private const val END_CHAR = '\uffff'

        /**
         * QWERTY adjacency map: each key → set of physically adjacent keys.
         * Used for keyboard-proximity-aware autocorrection (most common gesture error).
         * Includes both English and Spanish keyboard layouts.
         */
        private val QWERTY_ADJACENT = mapOf(
            // Top row
            'q' to setOf('w', 'a', 's'),
            'w' to setOf('q', 'e', 'a', 's', 'd'),
            'e' to setOf('w', 'r', 's', 'd', 'f'),
            'r' to setOf('e', 't', 'd', 'f', 'g'),
            't' to setOf('r', 'y', 'f', 'g', 'h'),
            'y' to setOf('t', 'u', 'g', 'h', 'j'),
            'u' to setOf('y', 'i', 'h', 'j', 'k'),
            'i' to setOf('u', 'o', 'j', 'k', 'l'),
            'o' to setOf('i', 'p', 'k', 'l'),
            'p' to setOf('o', 'l'),
            // Home row
            'a' to setOf('q', 'w', 's', 'z'),
            's' to setOf('q', 'w', 'e', 'a', 'd', 'z', 'x'),
            'd' to setOf('w', 'e', 'r', 's', 'f', 'x', 'c'),
            'f' to setOf('e', 'r', 't', 'd', 'g', 'c', 'v'),
            'g' to setOf('r', 't', 'y', 'f', 'h', 'v', 'b'),
            'h' to setOf('t', 'y', 'u', 'g', 'j', 'b', 'n'),
            'j' to setOf('y', 'u', 'i', 'h', 'k', 'n', 'm'),
            'k' to setOf('u', 'i', 'o', 'j', 'l', 'm'),
            'l' to setOf('i', 'o', 'p', 'k'),
            'ñ' to setOf('l', 'o', 'p'),
            // Bottom row
            'z' to setOf('a', 's', 'x'),
            'x' to setOf('z', 's', 'd', 'c'),
            'c' to setOf('x', 'd', 'f', 'v'),
            'v' to setOf('c', 'f', 'g', 'b'),
            'b' to setOf('v', 'g', 'h', 'n'),
            'n' to setOf('b', 'h', 'j', 'm'),
            'm' to setOf('n', 'j', 'k')
        )

        /**
         * Common insertion letters that gesture typing might skip (short/small keys).
         * Ordered by frequency of being accidentally skipped in a swipe.
         */
        private val COMMON_INSERTIONS = "aeiornstl"
    }

    /**
     * Una entrada de palabra con su frecuencia combinada (corpus + usuario).
     *
     * @property word La palabra en minúsculas
     * @property frequency Frecuencia combinada (corpus + boost de usuario)
     */
    data class WordEntry(val word: String, val frequency: Int)

    // ─── Corpus data ───
    private var sortedWords: List<WordEntry> = emptyList()   // sorted by word for bisect
    private var bigrams: Map<String, List<WordEntry>> = emptyMap()

    // ─── Freq-sorted cache ───
    private var byFreqCache: List<WordEntry>? = null

    // ─── User personalisation ───
    private val userFreqs: MutableMap<String, Int> = mutableMapOf()
    private val userDataFile: File

    // ─── LRU prefix cache (synchronized for thread safety) ───
    private val prefixCache = object : LinkedHashMap<String, List<WordEntry>>(CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<WordEntry>>?): Boolean =
            size > CACHE_SIZE
    }

    init {
        userDataFile = File(context.filesDir, "user_data.json")
        loadCorpus()
        loadUserData()
        Log.d(TAG, "PredictorEngine initialized: lang=$lang, words=${sortedWords.size}, bigrams=${bigrams.size}")
    }

    // ──────────────────────────  Loading  ──────────────────────────

    /** Load the corpus JSON from assets and parse unigrams + bigrams. */
    private fun loadCorpus() {
        try {
            val jsonStr = context.assets.open("corpus.json").bufferedReader().use { it.readText() }
            val root = JSONObject(jsonStr)
            val langData = root.optJSONObject(lang)
            if (langData == null) {
                Log.e(TAG, "Language '$lang' not found in corpus")
                return
            }

            val unigramsObj = langData.optJSONObject("unigrams")
            val bigramsObj = langData.optJSONObject("bigrams")

            val unigrams = mutableMapOf<String, Int>()
            if (unigramsObj != null) {
                val keys: MutableList<String> = mutableListOf()
                unigramsObj.keys().forEach { keys.add(it) }
                for (key in keys) {
                    unigrams[key] = unigramsObj.optInt(key, 1)
                }
            }

            val bigramsMap = mutableMapOf<String, List<WordEntry>>()
            if (bigramsObj != null) {
                val keys: MutableList<String> = mutableListOf()
                bigramsObj.keys().forEach { keys.add(it) }
                for (key in keys) {
                    val followerObj = bigramsObj.optJSONObject(key)
                    if (followerObj != null) {
                        val entries = mutableListOf<WordEntry>()
                        val followerKeys: MutableList<String> = mutableListOf()
                        followerObj.keys().forEach { followerKeys.add(it) }
                        for (followerKey in followerKeys) {
                            val freq = followerObj.optInt(followerKey, 1)
                            entries.add(WordEntry(followerKey, freq))
                        }
                        entries.sortByDescending { it.frequency }
                        bigramsMap[key] = entries
                    }
                }
            }

            sortedWords = unigrams.entries.map { WordEntry(it.key, it.value) }.sortedBy { it.word }
            bigrams = bigramsMap
            byFreqCache = null  // invalidate

            Log.d(TAG, "Corpus loaded: ${unigrams.size} words, ${bigramsMap.size} bigram heads")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load corpus", e)
        }
    }

    /** Load user frequency data from disk (user_data.json). */
    private fun loadUserData() {
        if (!userDataFile.exists()) return
        try {
            val jsonStr = userDataFile.readText()
            val parsed = JSONObject(jsonStr)
            userFreqs.clear()
            val keys: MutableList<String> = mutableListOf()
            parsed.keys().forEach { keys.add(it) }
            for (key in keys) {
                userFreqs[key] = parsed.optInt(key, 1)
            }
            Log.d(TAG, "User data loaded: ${userFreqs.size} words")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load user data", e)
        }
    }

    /** Save user frequency data to disk. */
    private fun saveUserData() {
        try {
            userDataFile.writeText(JSONObject(userFreqs).toString())
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save user data", e)
        }
    }

    // ────────────────────────  Public API  ────────────────────────

    /**
     * Boost a word's frequency after the user selects/types it.
     *
     * Invalidates both the frequency-sorted cache and the prefix LRU cache
     * so that future predictions reflect the boost immediately.
     *
     * Thread-safe via @Synchronized.
     *
     * @param word The word to boost (case-insensitive).
     */
    @Synchronized
    fun updateFrequency(word: String) {
        val prev = userFreqs[word] ?: 0
        userFreqs[word] = prev + USER_BOOST
        byFreqCache = null
        prefixCache.clear()
        saveUserData()
    }

    /**
     * Search for words matching a given prefix using binary search.
     *
     * Results are sorted by frequency descending and filtered by minimum length.
     * User frequency boosts are included.
     *
     * Thread-safe via @Synchronized.
     *
     * @param prefix The string prefix to search for (case-insensitive).
     * @param minLength Minimum word length to include (default 3).
     * @return List of [WordEntry] matching the prefix, sorted by frequency descending.
     */
    @Synchronized
    fun searchPrefix(prefix: String, minLength: Int = MIN_LENGTH): List<WordEntry> {
        if (sortedWords.isEmpty()) return emptyList()

        val prefixLower = prefix.lowercase()
        val startPos = lowerBound(prefixLower)
        val endPos = upperBound(prefixLower)

        if (startPos >= endPos || startPos >= sortedWords.size) return emptyList()

        val results = mutableListOf<WordEntry>()
        for (i in startPos until minOf(endPos, sortedWords.size)) {
            val entry = sortedWords[i]
            if (entry.word.length < minLength) continue
            val boost = userFreqs[entry.word] ?: 0
            results.add(WordEntry(entry.word, entry.frequency + boost))
        }

        results.sortByDescending { it.frequency }
        return results
    }

    /**
     * Get all words sorted by combined frequency descending.
     *
     * Results are cached in [byFreqCache] and invalidated on [updateFrequency].
     *
     * Thread-safe via @Synchronized getter.
     */
    val allWords: List<WordEntry>
        @Synchronized
        get() {
            if (byFreqCache == null) {
                byFreqCache = sortedWords.map { entry ->
                    val boost = userFreqs[entry.word] ?: 0
                    if (boost > 0) WordEntry(entry.word, entry.frequency + boost) else entry
                }.sortedByDescending { it.frequency }
            }
            return byFreqCache!!
        }

    /**
     * Get top-K word suggestions using context + prefix + fuzzy logic.
     *
     * ## Estrategias (en orden de prioridad)
     *
     * 1. **Bigrama** — si hay `previousWord` y `currentWord` está vacío, usa bigramas
     * 2. **Prefijo + Fuzzy** — si hay `currentWord`, busca por prefijo y scurea con FuzzyScorer
     * 3. **Levenshtein fallback** — si el prefijo no da buenos resultados, prueba Levenshtein
     *    en las top 500 palabras más frecuentes
     * 4. **Ultimate fallback** — palabras más frecuentes del corpus
     *
     * @param currentWord The word currently being typed (may be empty).
     * @param previousWord The previous word for bigram context (may be null).
     * @param topK Number of suggestions to return.
     * @return List of suggested words, best first.
     */
    fun predict(currentWord: String, previousWord: String?, topK: Int = 3): List<String> {
        val suggestions = mutableListOf<String>()
        val word = currentWord.trim()

        // ── 1) Bigram / context prediction ──
        if (previousWord != null && word.isEmpty()) {
            val key = previousWord.lowercase().trim()
            val bg = bigrams[key]
            if (bg != null) {
                val sorted = bg.sortedByDescending { it.frequency }
                suggestions.addAll(sorted.take(topK).map { it.word })
                return suggestions
            }

            // Fallback: top-N most frequent words
            val all = allWords
            if (all.isNotEmpty()) {
                suggestions.addAll(all.take(topK).map { it.word })
                return suggestions
            }
        }

        // ── 2) Prefix prediction (with fuzzy scoring) ──
        if (word.isNotEmpty()) {
            val prefixResults = getCachedPrefix(word.lowercase())

            val fuzzyResults = mutableListOf<Pair<String, Double>>()
            val hasContext = previousWord != null
            for (we in prefixResults) {
                val score = FuzzyScorer.getScore(we.word, word, we.frequency, hasContext)
                fuzzyResults.add(we.word to score)
            }

            fuzzyResults.sortByDescending { it.second }
            suggestions.addAll(fuzzyResults.filter { it.second >= 10.0 }.take(topK).map { it.first })

            // ── 3) Fallback: Levenshtein / spelling correction ──
            if (suggestions.isEmpty()) {
                val topCandidates = allWords.take(LEVENSHTEIN_CANDIDATES)
                val levResults = mutableListOf<Pair<String, Double>>()
                for (we in topCandidates) {
                    val score = FuzzyScorer.getScore(we.word, word, we.frequency, hasContext)
                    levResults.add(we.word to score)
                }
                levResults.sortByDescending { it.second }
                suggestions.addAll(levResults.filter { it.second >= 5.0 }.take(topK).map { it.first })
            }
        }

        // ── 4) Ultimate fallback: most frequent words ──
        if (suggestions.isEmpty()) {
            val all = allWords
            if (all.isNotEmpty()) {
                suggestions.addAll(all.take(topK).map { it.word })
            }
        }

        return suggestions
    }

    // ────────────────────────  Autocorrection  ────────────────────────

    /**
     * Check if a word exists in the dictionary (corpus or user data).
     *
     * Words of length ≤ 2 are always considered "known" to avoid
     * over-aggressive correction of short words like "he", "la", "un".
     *
     * @param word The word to check (case-insensitive).
     * @return True if the word exists in the dictionary.
     */
    private fun isKnownWord(word: String): Boolean {
        val lower = word.lowercase()
        if (lower.length <= 2) return true
        val results = searchPrefix(lower, minLength = 1)
        return results.any { it.word == lower }
    }

    /**
     * Compute Levenshtein (edit) distance between two strings.
     * Uses the optimized two-row DP array approach.
     */
    private fun levenshtein(s1: String, s2: String): Int {
        val a = s1.lowercase()
        val b = s2.lowercase()
        if (a.length < b.length) return levenshtein(s2, s1)
        if (b.isEmpty()) return a.length

        var prev = IntArray(b.length + 1) { it }
        for (i in a.indices) {
            val curr = IntArray(b.length + 1)
            curr[0] = i + 1
            for (j in b.indices) {
                val insert = prev[j + 1] + 1
                val delete = curr[j] + 1
                val subst = prev[j] + if (a[i] == b[j]) 0 else 1
                curr[j + 1] = minOf(insert, delete, subst)
            }
            prev = curr
        }
        return prev[b.length]
    }

    /**
     * Detect if the word has a common double-letter typo pattern.
     * E.g. "teest" → has double "e", "commputer" → has double "m".
     * This heuristic helps prioritize corrections that remove double letters.
     *
     * @return True if the word contains consecutive repeated characters.
     */
    private fun hasDoubleLetter(word: String): Boolean {
        for (i in 0 until word.length - 1) {
            if (word[i] == word[i + 1]) return true
        }
        return false
    }

    /**
     * Generate correction candidates for a misspelled word.
     *
     * Uses multiple strategies:
     * 1. **Direct predict** — FuzzyScorer prefix + Levenshtein
     * 2. **Double-letter reduction** — if the word has double letters, try removing them
     * 3. **Single-letter addition** — try inserting missing letters
     * 4. **Character swap** — try swapping adjacent characters
     * 5. **Adjacent-key (QWERTY) substitution** — replace each character with nearby keys
     * 6. **Gesture pattern matching** — if a gesture key sequence is provided,
     *    prioritize candidates that match the pattern via subsequence matching
     *
     * Results are scored by FuzzyScorer and sorted by score descending.
     *
     * @param word The misspelled word.
     * @param topK Maximum number of candidates to return.
     * @param gesturePattern Optional key sequence from gesture typing (e.g., "helo" from
     *   a swipe that passed through h→e→l→o). Used to guide candidate generation.
     * @param gestureDtwScore Optional DTW spatial score (0-100) from gesture recognizer.
     *   Higher scores = more spatial confidence, lower thresholds.
     * @return List of (candidate, score) pairs, best first.
     */
    fun suggestCorrections(
        word: String,
        topK: Int = 5,
        gesturePattern: String? = null,
        gestureDtwScore: Float? = null
    ): List<Pair<String, Double>> {
        val lower = word.lowercase().trim()
        if (lower.length < 2) return emptyList()

        val candidates = mutableMapOf<String, Double>()
        val patternLower = gesturePattern?.lowercase()

        // Adjust thresholds based on gesture confidence
        val hasGestureInfo = gesturePattern != null && gestureDtwScore != null
        val gestureConfidence = if (hasGestureInfo) gestureDtwScore / 100f else 0f
        val isHighConfidenceGesture = gestureConfidence >= 0.5f

        // Strategy 1: PredictorEngine predict (FuzzyScorer)
        val predictions = predict(lower, null, topK * 2)
        for (pred in predictions) {
            if (pred != lower) {
                val score = FuzzyScorer.getScore(pred, lower,
                    userFreqs[pred] ?: 0, false)
                candidates[pred] = score.coerceAtLeast(0.0)
            }
        }

        // Strategy 2: Double-letter reduction
        if (hasDoubleLetter(lower)) {
            val deduped = StringBuilder()
            var i = 0
            while (i < lower.length) {
                val ch = lower[i]
                deduped.append(ch)
                while (i + 1 < lower.length && lower[i + 1] == ch) i++
                i++
            }
            val candidate = deduped.toString()
            if (candidate != lower && candidate.length >= 2) {
                scoreAndAdd(candidate, lower, 2, candidates)
            }
        }

        // Strategy 3: Single adjacent character swap (transposition errors)
        if (lower.length >= 2) {
            for (i in 0 until lower.length - 1) {
                val swapped = lower.toCharArray()
                val tmp = swapped[i]
                swapped[i] = swapped[i + 1]
                swapped[i + 1] = tmp
                val candidate = String(swapped)
                scoreAndAdd(candidate, candidate, 1, candidates)
            }
        }

        // Strategy 4: Adjacent-key (QWERTY) substitution
        // For each character, try replacing it with physically adjacent keys
        val adjCandidates = mutableSetOf<String>()
        for (i in lower.indices) {
            val ch = lower[i]
            val adjacent = QWERTY_ADJACENT[ch] ?: continue
            for (adj in adjacent) {
                val modified = lower.substring(0, i) + adj + lower.substring(i + 1)
                adjCandidates.add(modified)
            }
        }
        for (candidate in adjCandidates) {
            scoreAndAdd(candidate, candidate, 2, candidates)
        }

        // Strategy 5: Single-letter insertion (for missing keys in swipe)
        // Try inserting common letters at each position
        if (isHighConfidenceGesture || lower.length <= 10) {
            val insertCandidates = mutableSetOf<String>()
            for (i in 0..lower.length) {
                for (ch in COMMON_INSERTIONS) {
                    val modified = lower.substring(0, i) + ch + lower.substring(i)
                    insertCandidates.add(modified)
                }
            }
            for (candidate in insertCandidates) {
                scoreAndAdd(candidate, candidate, 1, candidates)
            }
        }

        // Strategy 6: Gesture pattern subsequence matching
        // If we have a gesture pattern, prioritize candidates whose letters
        // appear in order within the pattern (subsequence match)
        if (patternLower != null && patternLower.length >= 2) {
            // Also look for candidates starting with the pattern prefix
            val prefix = patternLower.first().toString()
            val prefixCandidates = searchPrefix(prefix)
            for (we in prefixCandidates.take(200)) {
                if (we.word in candidates || we.word == lower) continue
                if (we.word.length < 2) continue

                // Check if the word is a subsequence of the pattern (swipe passed through these keys)
                if (isSubsequenceMatch(we.word, patternLower)) {
                    val score = FuzzyScorer.getScore(we.word, lower, we.frequency, false)
                    val gestureBoost = if (isHighConfidenceGesture) 15.0 else 5.0
                    candidates[we.word] = maxOf(candidates[we.word] ?: 0.0, score + gestureBoost)
                }
            }
        }

        // Sort by score descending and return topK
        return candidates.entries
            .sortedByDescending { it.value }
            .take(topK)
            .map { it.key to it.value }
    }

    /**
     * Score a candidate word and add to the candidates map if it's a valid
     * known word with reasonable edit distance.
     */
    /**
     * Score a candidate word and add to the candidates map if valid.
     * Uses exact prefix match (candidate as prefix) for O(log n) lookup.
     */
    private fun scoreAndAdd(
        candidate: String,
        searchInput: String,
        maxEditDist: Int,
        candidates: MutableMap<String, Double>
    ) {
        if (candidate.length < 2) return

        // Exact prefix search (candidate as prefix) — binary search finds it instantly
        val matched = searchPrefix(candidate, minLength = 1).firstOrNull { it.word == candidate }
        if (matched == null) return

        val levDist = levenshtein(matched.word, searchInput)
        if (levDist <= maxEditDist) {
            val score = FuzzyScorer.getScore(matched.word, searchInput,
                matched.frequency, false)
            if (score > (candidates[matched.word] ?: 0.0)) {
                candidates[matched.word] = score.coerceAtLeast(0.0)
            }
        }
    }

    /**
     * Check if the word's letters appear as a subsequence of the pattern
     * with tolerance for extra characters between matches.
     * Used for gesture pattern matching.
     */
    private fun isSubsequenceMatch(word: String, pattern: String): Boolean {
        val wl = word.lowercase()
        val pl = pattern.lowercase()
        var wi = 0
        var skips = 0
        val maxSkipsPerChar = maxOf(3, pl.length / 3)

        if (wl[0] != pl[0]) return false  // First char must match (swipe starts at first key)

        for (ch in pl) {
            if (wi < wl.length) {
                if (ch == wl[wi]) {
                    wi++
                    skips = 0
                } else {
                    skips++
                    if (skips > maxSkipsPerChar) return false
                }
            }
        }

        val ratio = wi.toFloat() / wl.length
        return ratio >= 0.30f
    }

    /**
     * Find the best autocorrection for a potentially misspelled word,
     * optionally using gesture typing information to guide the correction.
     *
     * ## Reglas de autocorrección (gesture-aware)
     *
     * 1. Ignora palabras < 3 caracteres
     * 2. Si la palabra ya existe en el diccionario, no corrige
     * 3. Usa [suggestCorrections] para obtener candidatos (con gesture info si está disponible)
     * 4. Aplica filtros de seguridad (umbrales más permisivos si hay alta confianza gesture):
     *    - La primera letra debe coincidir
     *    - El edit distance debe ser razonable (1-4 según longitud)
     *    - El score FuzzyScorer debe superar un umbral (más bajo para gesture)
     *
     * @param word The potentially misspelled word.
     * @param gesturePattern Optional key sequence from gesture typing.
     * @param gestureDtwScore Optional DTW spatial score (0-100).
     * @return The best corrected word, or null if no correction is needed/possible.
     */
    fun autocorrect(word: String, gesturePattern: String? = null, gestureDtwScore: Float? = null): String? {
        val lower = word.lowercase().trim()
        if (lower.length < 3) return null  // Too short for reliable correction

        // Determine gesture confidence (lowers thresholds)
        val isHighConfidenceGesture = gesturePattern != null && gestureDtwScore != null && gestureDtwScore >= 45f

        // Already in dictionary — no correction needed (unless it's a high-confidence gesture word
        // that might benefit from a smooth/clean version)
        if (isKnownWord(lower)) {
            // For gesture-typed words that are known, still check if there's a
            // significantly better match (e.g., swipe passed through "w"+"s"+"a"+"s"+"h" for "wash"
            // but typed "was" — don't correct since "was" is known)
            return null
        }

        // Get correction candidates using multi-strategy approach with gesture info
        val candidates = suggestCorrections(lower, 5, gesturePattern, gestureDtwScore)
        if (candidates.isEmpty()) return null

        val best = candidates.first()
        val bestWord = best.first
        val bestScore = best.second

        if (bestWord == lower) return null

        // Safety checks before suggesting a correction
        val editDist = levenshtein(bestWord, lower)

        // More permissive edit distance for gesture-typed words
        val maxEdit = when {
            isHighConfidenceGesture -> when {
                lower.length <= 4 -> 2
                lower.length <= 7 -> 3
                else -> 4
            }
            else -> when {
                lower.length <= 4 -> 1
                lower.length <= 7 -> 2
                else -> 3
            }
        }

        // Lower score threshold for gesture-typed words (gesture spatial score adds confidence)
        val minScore = if (isHighConfidenceGesture) 10.0 else 15.0

        // Must share first letter, reasonable edit distance, and minimum score
        if (bestWord.first() != lower.first() || editDist > maxEdit || bestScore < minScore) {
            return null
        }

        Log.d(TAG, "Autocorrect: '$lower' -> '$bestWord' (editDist=$editDist, score=${
            "%.1f".format(bestScore)
        }, gestureConfidence=${if (isHighConfidenceGesture) "high" else "normal"})")
        return bestWord
    }

    // ────────────────────────  Internal helpers  ────────────────────────

    /** Binary search lower bound (first index >= prefix). */
    private fun lowerBound(prefix: String): Int {
        var lo = 0
        var hi = sortedWords.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (sortedWords[mid].word < prefix) lo = mid + 1
            else hi = mid
        }
        return lo
    }

    /** Binary search upper bound (first index > prefix). */
    private fun upperBound(prefix: String): Int {
        val endKey = prefix + END_CHAR
        var lo = 0
        var hi = sortedWords.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (sortedWords[mid].word < endKey) lo = mid + 1
            else hi = mid
        }
        return lo
    }

    /** Get prefix search results with LRU cache (synchronized for thread safety). */
    @Synchronized
    private fun getCachedPrefix(prefix: String): List<WordEntry> {
        return prefixCache.getOrPut(prefix) { searchPrefix(prefix) }
    }
}
