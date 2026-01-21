package pl.mefjuu.beatgame

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.mefjuu.beatgame.AudioComponents.BeatAnalyzer
import java.awt.FileDialog
import java.awt.Frame
import java.io.File


@Composable
fun MainMenuScreen(
    initialSettings: GameSettings,
    onStartGame: (String, String, GameSettings) -> Unit
) {
    val scope = rememberCoroutineScope()
    var selectedSongName by remember { mutableStateOf("No song selected") }
    var selectedBaseName by remember { mutableStateOf<String?>(null) }
    var selectedDifficulty by remember { mutableStateOf("Hard") }
    var isStartEnabled by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }

    // Okna dialogowe
    var showSensitivityDialog by remember { mutableStateOf(false) }
    var showControlsDialog by remember { mutableStateOf(false) }

    // Lokalne ustawienia klawiszy w menu
    var currentSettings by remember { mutableStateOf(initialSettings) }

    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFFFDFCF0)).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Beat Game By Orzeł", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.Black)
        Spacer(modifier = Modifier.height(20.dp))
        Text(selectedSongName, fontSize = 16.sp, color = Color.DarkGray)
        Spacer(modifier = Modifier.height(20.dp))

        if (isLoading) {
            CircularProgressIndicator()
            Text("Analyzing song...", Modifier.padding(top = 8.dp))
        } else {
            Button(onClick = {
                val fileDialog = FileDialog(null as Frame?, "Select Song", FileDialog.LOAD)
                fileDialog.isVisible = true
                if (fileDialog.file != null) {
                    val selectedFile = File(fileDialog.directory + fileDialog.file)
                    val baseName = selectedFile.nameWithoutExtension
                    scope.launch {
                        isLoading = true
                        try {
                            withContext(Dispatchers.IO) {
                                val analyzer = BeatAnalyzer()
                                analyzer.analyzeAndExport(selectedFile.absolutePath)
                            }
                            selectedBaseName = baseName
                            selectedSongName = "Selected song: $baseName"
                            isStartEnabled = true
                        } catch (e: Exception) {
                            selectedSongName = "Error: ${e.message}"
                        } finally { isLoading = false }
                    }
                }
            }) { Text("Load Song") }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row {
            Button(onClick = { showSensitivityDialog = true }) { Text("Sensitivity") }
            Spacer(Modifier.width(8.dp))
            Button(onClick = { showControlsDialog = true }) {
                Text("Controls (${getKeyName(currentSettings.leftKey)} / ${getKeyName(currentSettings.rightKey)})")
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        DifficultySelector(selectedDifficulty) { newDifficulty: String ->
            selectedDifficulty = newDifficulty
        }
        Spacer(modifier = Modifier.height(30.dp))

        Button(
            onClick = { selectedBaseName?.let { onStartGame(it, selectedDifficulty, currentSettings) } },
            enabled = isStartEnabled,
            modifier = Modifier.width(200.dp).height(50.dp)
        ) { Text("Start Game", fontSize = 20.sp) }
    }

    if (showSensitivityDialog) SensitivityDialog(onDismiss = { showSensitivityDialog = false })
    if (showControlsDialog) {
        ControlsDialog(
            currentSettings = currentSettings,
            onSave = { currentSettings = it },
            onDismiss = { showControlsDialog = false }
        )
    }
}
