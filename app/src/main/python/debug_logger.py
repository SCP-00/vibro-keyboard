import os
import json
import time
from threading import Lock
import sys
import traceback
import os

_lock = Lock()
_file = None
_enabled = os.environ.get('SMARTTEXT_DEBUG', '0') == '1'
_sd_file = None

def _default_path():
    # Prefer Android app files dir when running on device
    android_path = '/data/data/com.example.smarttext/files/smarttext_debug.jsonl'
    if os.path.exists(os.path.dirname(android_path)):
        return android_path
    # Fallback to package-relative path
    return os.path.join(os.path.dirname(__file__), 'smarttext_debug.jsonl')

def _ensure_file():
    global _file
    if _file:
        return _file
    path = os.environ.get('SMARTTEXT_DEBUG_OUTPUT') or _default_path()
    try:
        dirp = os.path.dirname(path)
        if dirp and not os.path.exists(dirp):
            os.makedirs(dirp, exist_ok=True)
        _file = open(path, 'a', encoding='utf-8')
    except Exception:
        _file = None
    return _file


def _ensure_sd_file():
    """Try to open a writable file on /sdcard as a fallback so host can pull without run-as."""
    global _sd_file
    if _sd_file:
        return _sd_file
    try:
        sd_path = '/sdcard/smarttext_debug.jsonl'
        # ensure directory exists (sdcard root usually exists)
        _sd_file = open(sd_path, 'a', encoding='utf-8')
    except Exception:
        _sd_file = None
    return _sd_file

def enabled():
    return _enabled

def enable_runtime(path=None):
    """Enable logging at runtime (callable from Java/Kotlin via Chaquopy).
    If `path` is provided, use it as output file path. Otherwise use default device path.
    """
    global _enabled, _file
    _enabled = True
    if path:
        os.environ['SMARTTEXT_DEBUG_OUTPUT'] = path
    _ensure_file()
    # Try to also open sdcard fallback for easy adb pull
    try:
        _ensure_sd_file()
    except Exception:
        pass
    # Emit an immediate test record so we can verify runtime logging works
    try:
        log('debug_logger', 'debug_logger.py', 'enable_runtime', 'runtime_enabled', {'path': path})
    except Exception:
        pass
    return True

def log(component, file, func, event, payload=None, level='DEBUG'):
    if not _enabled:
        return
    rec = {
        'ts': time.strftime('%Y-%m-%dT%H:%M:%S', time.gmtime()) + ('.%03dZ' % (int(time.time()*1000)%1000)),
        'level': level,
        'component': component,
        'file': file,
        'func': func,
        'event': event,
        'payload': payload or {}
    }
    try:
        f = _ensure_file()
        if f:
            with _lock:
                line = json.dumps(rec, ensure_ascii=False)
                print(line)
                f.write(line + '\n')
                f.flush()
                try:
                    os.fsync(f.fileno())
                except Exception:
                    pass
        # also write to sdcard fallback if available (best-effort)
        sd = _ensure_sd_file()
        if sd:
            with _lock:
                sd.write(line + '\n')
                sd.flush()
                try:
                    os.fsync(sd.fileno())
                except Exception:
                    pass
    except Exception:
        try:
            traceback.print_exc(file=sys.stderr)
        except Exception:
            pass
