package com.example.smarttext.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Pantalla de configuración del IME SmartText.
 *
 * Muestra el estado del predictor, selector de idioma,
 * e instrucciones para activar el teclado en el sistema.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    var currentLang by remember { mutableStateOf("es") }
    var testText by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SmartText Keyboard") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // App icon / title section
            Text(
                text = "⌨️",
                style = MaterialTheme.typography.displayMedium,
                modifier = Modifier.padding(8.dp)
            )
            Text(
                text = "SmartText",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Teclado Predictivo Inteligente",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Test drive area
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Prueba el teclado",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Toca aquí abajo y escribe para probar las predicciones y el deslizamiento:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = testText,
                        onValueChange = { testText = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Escribe aquí para probar...") },
                        singleLine = false,
                        minLines = 3,
                        maxLines = 5
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Language selector
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Idioma del teclado",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = currentLang == "es",
                            onClick = { currentLang = "es" },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                        ) { Text("Español") }
                        SegmentedButton(
                            selected = currentLang == "en",
                            onClick = { currentLang = "en" },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                        ) { Text("English") }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Enable keyboard card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Activar teclado",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Para usar SmartText Keyboard:\n\n" +
                                "1. Presiona el botón \"Activar en Ajustes\"\n" +
                                "2. Ve a \"Idioma e introducción de texto\"\n" +
                                "3. Activa \"SmartText Keyboard\"\n" +
                                "4. Selecciona SmartText como método de entrada predeterminado",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { openKeyboardSettings(context) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Activar en Ajustes")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Features card
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Características",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FeatureItem("Teclado QWERTY con fila numérica")
                    FeatureItem("Predicción inteligente con lógica difusa")
                    FeatureItem("Corrección ortográfica automática")
                    FeatureItem("Deslizamiento (swipe/glide typing)")
                    FeatureItem("Soporte Español e Inglés")
                    FeatureItem("100% offline — sin conexión a internet")
                    FeatureItem("Motor Kotlin nativo — rápido y eficiente")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Info footer
            Text(
                text = "SmartText v1.2 — IME Keyboard\n" +
                        "Proyecto final de Computación Blanda\n" +
                        "Mayo 2026",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun FeatureItem(text: String) {
    Row(
        modifier = Modifier.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "✅ ",
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun openKeyboardSettings(context: Context) {
    try {
        context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
    } catch (e: Exception) {
        try {
            context.startActivity(Intent(Settings.ACTION_SETTINGS))
        } catch (e2: Exception) {
            // Ignore
        }
    }
}
