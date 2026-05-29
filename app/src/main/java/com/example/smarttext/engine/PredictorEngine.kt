package com.example.smarttext.engine

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.util.LinkedHashMap

/**
 * SmartText Predictor — Kotlin nativo.
 *
 * Reemplaza el predictor Python/Chaquopy con una implementación Kotlin pura.
 *
 * Optimizaciones:
 * - Sorted word list + bisect para búsqueda de prefijo O(log n)
 * - LRU cache para prefijos comunes
 * - Levenshtein reducido a top 500 palabras
 * - Bigramas para predicción por contexto
 * - Frecuencia de usuario persistente
 * - Sin dependencia de Python/Chaquopy (~10-50x más rápido en gama baja)
 */
class PredictorEngine(private val context: Context, private val lang: String) {

    companion object {
        private const val TAG = "SmartText"
        private const val CACHE_SIZE = 128
        private const val MIN_LENGTH = 3
        private const val LEVENSHTEIN_CANDIDATES = 500
        private const val USER_BOOST = 10

        /** High surrogate for binary search upper bound */
        private const val END_CHAR = '\uffff'
    }

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

    private fun saveUserData() {
        try {
            val sb = StringBuilder()
            sb.append("{")
            var first = true
            for ((word, freq) in userFreqs) {
                if (!first) sb.append(",")
                first = false
                sb.append("\"${word.replace("\"", "\\\"")}\":$freq")
            }
            sb.append("}")
            userDataFile.writeText(sb.toString())
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save user data", e)
        }
    }

    // ────────────────────────  Public API  ────────────────────────

    /**
     * Boost a word's frequency after user selects it.
     * Invalidates both caches so future predictions reflect the boost.
     * Thread-safe (synchronized on instance).
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
     * Return [(word, combined_freq), …] sorted by freq desc.
     *
     * Combines corpus frequency with any user boost.
     * Filters out words shorter than minLength (default 3) to avoid
     * single/double-letter artifacts from subtitle/corpus data.
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
     * Return all words sorted by frequency descending (cached).
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
     * Return top-k word suggestions using context + prefix + fuzzy logic.
     *
     * @param currentWord The word currently being typed (may be empty)
     * @param previousWord The previous word for bigram context (may be null)
     * @param topK Number of suggestions to return
     * @return List of suggested words
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
                Log.d(TAG, "Bigram predict: ctx='$key' -> $suggestions")
                return suggestions
            }

            // Fallback: top-N most frequent words
            val all = allWords
            if (all.isNotEmpty()) {
                suggestions.addAll(all.take(topK).map { it.word })
                Log.d(TAG, "Bigram miss: ctx='$key' fallback -> $suggestions")
                return suggestions
            }
        }

        // ── 2) Prefix prediction (with fuzzy scoring) ──
        if (word.isNotEmpty()) {
            val prefixResults = getCachedPrefix(word.lowercase())
            Log.d(TAG, "Prefix search: '$word' -> ${prefixResults.size} candidates")

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
                Log.d(TAG, "Levenshtein fallback: ${topCandidates.size} candidates -> $suggestions")
            }
        }

        // ── 4) Ultimate fallback: most frequent words ──
        if (suggestions.isEmpty()) {
            val all = allWords
            if (all.isNotEmpty()) {
                suggestions.addAll(all.take(topK).map { it.word })
            }
        }

        Log.d(TAG, "Predict result: '$word' ctx='${previousWord ?: ""}' -> $suggestions")
        return suggestions
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
