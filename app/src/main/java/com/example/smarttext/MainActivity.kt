package com.example.smarttext

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.smarttext.theme.SmartTextTheme
import android.util.Log
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
    if (!Python.isStarted()) {
        Python.start(AndroidPlatform(this))
    }

    // Enable Python-side debug logger to write JSONL into app files dir
    try {
      val py = Python.getInstance()
      val dbg = py.getModule("debug_logger")
      // Use app files dir path to ensure Chaquopy can write there
      val outPath = filesDir.absolutePath + "/smarttext_debug.jsonl"
      val enabled = try { dbg.callAttr("enable_runtime", outPath).toString() } catch (ex: Exception) { "error" }
      Log.d("MainActivity", "debug_logger enable_runtime result=$enabled")
      try {
        Log.d("MainActivity", "debug_logger module: ${dbg.toString()}")
      } catch (ex: Exception) {
        Log.e("MainActivity", "debug_logger toString failed", ex)
      }
      val isEnabled = try { dbg.callAttr("enabled").toString() } catch (ex: Exception) { "error" }
      Log.d("MainActivity", "debug_logger.enabled() => $isEnabled")
      try {
        dbg.callAttr("log", "MainActivity", "MainActivity.kt", "onCreate", "startup", null)
        Log.d("MainActivity", "debug_logger.log startup call succeeded")
      } catch (e: Exception) {
        Log.e("MainActivity", "debug_logger.log failed", e)
      }
    } catch (e: Exception) {
      Log.e("MainActivity", "Failed to enable debug_logger", e)
    }

    enableEdgeToEdge()
    setContent {
      SmartTextTheme { Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { MainNavigation() } }
    }
  }
}
