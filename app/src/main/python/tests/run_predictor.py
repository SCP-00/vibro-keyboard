import os
import sys
# Enable debug logger early so modules pick it up at import time
os.environ['SMARTTEXT_DEBUG'] = '1'
import json
import shutil

# Add the parent directory to path so engine module can be found
sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..'))
from engine.predictor import Predictor

def main():
    # Enable debug logger
    os.environ['SMARTTEXT_DEBUG'] = '1'
    # Place user data in a temp dir under this tests folder
    user_dir = os.path.join(os.path.dirname(__file__), 'tmp_user')
    if os.path.exists(user_dir):
        shutil.rmtree(user_dir)
    os.makedirs(user_dir, exist_ok=True)

    p = Predictor(user_dir, lang='en')

    scenarios = [
        {'current': '', 'previous': 'hello'},
        {'current': 'ho', 'previous': None},
        {'current': 'th', 'previous': None},
    ]

    out = []
    for s in scenarios:
        res = p.predict(s['current'], previous_word=s['previous'], top_k=5)
        print(f"scenario={s} -> {res}")
        out.append({'scenario': s, 'result': res})

    # Print path to debug file
    dbg = os.environ.get('SMARTTEXT_DEBUG_OUTPUT')
    if not dbg:
        dbg = os.path.join(os.path.dirname(__file__), '..', 'smarttext_debug.jsonl')
    print('DEBUG_OUTPUT:', dbg)
    # Also write results summary
    with open(os.path.join(user_dir, 'summary.json'), 'w', encoding='utf-8') as f:
        json.dump(out, f, ensure_ascii=False, indent=2)

if __name__ == '__main__':
    main()
