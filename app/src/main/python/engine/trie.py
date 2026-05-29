from debug_logger import log, enabled as debug_enabled


class TrieNode:
    def __init__(self):
        self.children = {}
        self.is_end_of_word = False
        self.frequency = 0

class Trie:
    def __init__(self):
        self.root = TrieNode()
        self._count = 0

    def insert(self, word, frequency):
        node = self.root
        for char in word:
            if char not in node.children:
                node.children[char] = TrieNode()
            node = node.children[char]
        node.is_end_of_word = True
        node.frequency = frequency
        self._count += 1
        if debug_enabled() and self._count % 500 == 0:
            log('trie', 'engine/trie.py', 'insert', 'trie.insert.summary', {'inserted_total': self._count})

    def search_prefix(self, prefix):
        node = self.root
        for char in prefix:
            if char not in node.children:
                if debug_enabled():
                    log('trie', 'engine/trie.py', 'search_prefix', 'trie.search_prefix.miss', {'prefix': prefix})
                return []
            node = node.children[char]
        
        # Traverse children to find all words with this prefix
        results = []
        self._dfs(node, prefix, results)
        # Sort by frequency descending
        results.sort(key=lambda x: x[1], reverse=True)
        if debug_enabled():
            log('trie', 'engine/trie.py', 'search_prefix', 'trie.search_prefix.result', {'prefix': prefix, 'results_count': len(results)})
        return results

    def _dfs(self, node, current_word, results):
        if node.is_end_of_word:
            results.append((current_word, node.frequency))
        for char, child_node in node.children.items():
            self._dfs(child_node, current_word + char, results)

    def size_estimate(self):
        return getattr(self, '_count', None)
