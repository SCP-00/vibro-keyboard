package com.example.smarttext

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.smarttext.ui.ExperimentationScreen
import com.example.smarttext.ui.SettingsScreen
import com.example.smarttext.ui.WritingAnalysisScreen

enum class Screen {
    Main, Experimentation, WritingAnalysis
}

@Composable
fun MainNavigation() {
    var currentScreen by remember { mutableStateOf(Screen.Main) }

    when (currentScreen) {
        Screen.Main -> SettingsScreen(
            onOpenExperimentation = { currentScreen = Screen.Experimentation },
            onOpenWritingAnalysis = { currentScreen = Screen.WritingAnalysis }
        )
        Screen.Experimentation -> ExperimentationScreen(onBack = { currentScreen = Screen.Main })
        Screen.WritingAnalysis -> WritingAnalysisScreen(onBack = { currentScreen = Screen.Main })
    }
}
