import json
import os
from engine.trie import Trie
from ai.fuzzy_logic import FuzzyScorer
from debug_logger import log, enabled as debug_enabled

class Predictor:
    def __init__(self, user_data_dir, lang='en'):
        self.trie = Trie()
        self.bigrams = {}
        self.lang = lang
        self.corpus_path = os.path.join(os.path.dirname(__file__), "corpus.json")
        self.user_data_path = os.path.join(user_data_dir, "user_data.json")
        self.load_corpus(self.corpus_path)
        self.load_user_data()

    def load_corpus(self, corpus_path):
        if not os.path.exists(corpus_path):
            log('predictor', 'engine/predictor.py', 'load_corpus', 'corpus_load.start', {'exists': False, 'path': corpus_path}, level='ERROR')
            print(f"Error: {corpus_path} not found.")
            return

        with open(corpus_path, "r", encoding="utf-8") as f:
            data = json.load(f)
            
        lang_data = data.get(self.lang, {})
        unigrams = lang_data.get("unigrams", {})
        self.bigrams = lang_data.get("bigrams", {})

        # bulk insert but log summary
        inserted = 0
        start = time_ms = None
        for word, freq in unigrams.items():
            self.trie.insert(word, freq)
            inserted += 1
        if debug_enabled():
            sample = list(unigrams.items())[:5]
            log('predictor', 'engine/predictor.py', 'load_corpus', 'corpus_load.parse', {'lang_found': bool(lang_data), 'unigrams_count': len(unigrams), 'sample': sample})

    def load_user_data(self):
        if not os.path.exists(self.user_data_path):
            return
        try:
            with open(self.user_data_path, "r", encoding="utf-8") as f:
                user_unigrams = json.load(f)
                for word, freq in user_unigrams.items():
                    try:
                        # Insert or update words from user data into the Trie
                        self.trie.insert(word, int(freq))
                    except Exception:
                        continue
            if debug_enabled():
                log('predictor', 'engine/predictor.py', 'load_user_data', 'user_data.load.result', {'loaded_count': len(user_unigrams)})
        except Exception:
            pass

    def update_frequency(self, word):
        # Update in Trie (not fully implemented in Trie, but conceptually)
        # Update in user JSON
        user_unigrams = {}
        if os.path.exists(self.user_data_path):
            try:
                with open(self.user_data_path, "r", encoding="utf-8") as f:
                    user_unigrams = json.load(f)
            except Exception:
                pass
        
        prev = user_unigrams.get(word, 0)
        user_unigrams[word] = prev + 10 # Boost frequency
        
        # Persist user unigram frequencies and update Trie in-memory
        try:
            with open(self.user_data_path, "w", encoding="utf-8") as f:
                json.dump(user_unigrams, f)
            if debug_enabled():
                log('predictor', 'engine/predictor.py', 'update_frequency', 'update_frequency.persist', {'word': word, 'prev': prev, 'new': user_unigrams[word]})
        except Exception:
            pass

        try:
            self.trie.insert(word, user_unigrams[word])
        except Exception:
            pass

    def predict(self, current_word, previous_word=None, top_k=3):
        suggestions = []
        start_ts = None
        if debug_enabled():
            log('predictor', 'engine/predictor.py', 'predict', 'predict.call', {'current_word': current_word, 'previous_word': previous_word, 'top_k': top_k, 'trie_size_estimate': self.trie.size_estimate() if hasattr(self.trie, 'size_estimate') else None})
        
        # Si hay contexto (previous_word) y no se ha escrito nada aún en current_word
        if previous_word and not current_word:
            key = previous_word.lower()
            if key in self.bigrams:
                # Las sugerencias de bigramas ya vienen con su frecuencia
                suggestions = self.bigrams[key]
                suggestions.sort(key=lambda x: x[1], reverse=True)
                res = [word for word, freq in suggestions[:top_k]]
                if debug_enabled():
                    log('predictor', 'engine/predictor.py', 'predict', 'predict.bigram.hit', {'previous_word': previous_word, 'candidate_count': len(suggestions), 'top': res})
                return res
            else:
                # Fallback: si el bigrama no existe para esta palabra, sugerir palabras más frecuentes
                all_words = self.trie.search_prefix('')
                if all_words:
                    res = [w for w, f in all_words[:top_k]]
                    if debug_enabled():
                        log('predictor', 'engine/predictor.py', 'predict', 'predict.bigram.miss.fallback', {'previous_word': previous_word, 'top': res})
                    return res
                
        # Si se ha escrito parte de una palabra, buscar por prefijo
        if current_word:
            prefix_results = self.trie.search_prefix(current_word.lower())
            if debug_enabled():
                log('predictor', 'engine/predictor.py', 'predict', 'predict.prefix.search', {'prefix': current_word, 'results_count': len(prefix_results)})
            
            # Aplicar Computación Blanda (Fuzzy Logic) para rankear sugerencias
            scorer = FuzzyScorer()
            fuzzy_results = []
            for word, freq in prefix_results:
                score = scorer.get_score(word, current_word, freq, has_context=(previous_word is not None))
                fuzzy_results.append((word, score))
            
            # Ordenar por score difuso descendente y filtrar por umbral
            fuzzy_results.sort(key=lambda x: x[1], reverse=True)
            suggestions = [word for word, score in fuzzy_results if score >= 10][:top_k]
            # Si no hay resultados por prefijo, hacer fallback: palabras más cercanas por Levenshtein
            if not suggestions:
                all_words = self.trie.search_prefix('')
                if all_words:
                    # Tomar las palabras más frecuentes para rendimiento
                    top_candidates = all_words[:2000]
                    fuzzy_results = []
                    scorer = FuzzyScorer()
                    for word, freq in top_candidates:
                        score = scorer.get_score(word, current_word, freq, has_context=(previous_word is not None))
                        fuzzy_results.append((word, score))
                    fuzzy_results.sort(key=lambda x: x[1], reverse=True)
                    suggestions = [word for word, score in fuzzy_results if score >= 5][:top_k]
                    if debug_enabled():
                        log('predictor', 'engine/predictor.py', 'predict', 'predict.fallback.result', {'candidates_considered': len(top_candidates), 'top': suggestions[:5]})
        if debug_enabled():
            log('predictor', 'engine/predictor.py', 'predict', 'predict.result', {'final_suggestions': suggestions, 'count': len(suggestions)})
        
        # Fallback: si no hay sugerencias, devolver las palabras más frecuentes
        if not suggestions:
            all_words = self.trie.search_prefix('')
            if all_words:
                suggestions = [w for w, f in all_words[:top_k]]
        
        return suggestions
