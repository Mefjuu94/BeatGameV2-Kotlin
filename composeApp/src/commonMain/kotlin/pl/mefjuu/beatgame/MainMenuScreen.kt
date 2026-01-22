package pl.mefjuu.beatgame

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.mefjuu.beatgame.AudioComponents.BeatAnalyzer
import pl.mefjuu.beatgame.AudioComponents.CustomizedBeatAnalyzer
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

    // --- NOWE STANY DLA ANALIZY ---
    var useAdvanced by remember { mutableStateOf(false) }
    var sensitivity by remember { mutableStateOf(20f) }
    var threshold by remember { mutableStateOf(-40f) }
    var mergeGap by remember { mutableStateOf(0.1f) }
    // ------------------------------

    var beatmapExists by remember(selectedBaseName) {
        mutableStateOf(selectedBaseName?.let { File("beatmaps/$it").exists() } ?: false)
    }
    var beatsDetected by remember { mutableStateOf(0) }

    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFFFDFCF0)).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Beat Game By Orzeł", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.Black)
        Spacer(modifier = Modifier.height(20.dp))
        Text(selectedSongName, fontSize = 16.sp, color = Color.DarkGray)

        Text(
            "Detected beats: $beatsDetected",
            fontSize = 14.sp,
            color = Color(0xFF388E3C), // Ładny zielony
            fontWeight = FontWeight.Bold
        )

        if (beatmapExists || !isLoading) {
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    selectedBaseName?.let {
                        if (deleteBeatmapFolder(it)) {
                            beatmapExists = false
                            isStartEnabled = false
                            selectedSongName = "Beatmap deleted. Ready to re-analyze. Select Song."
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE57373)) // Czerwony kolor
            ) {
                Text("Delete & Re-analyze", color = Color.White)
            }
        }
        Spacer(modifier = Modifier.height(20.dp))

        // --- SEKCJA WYBORU ANALIZATORA ---
        Text("Analyzer Mode:", fontWeight = FontWeight.SemiBold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = !useAdvanced, onClick = { useAdvanced = false })
            Text("Default (RMS)", Modifier.clickable { useAdvanced = false })
            Spacer(Modifier.width(20.dp))
            RadioButton(selected = useAdvanced, onClick = { useAdvanced = true })
            Text("Advanced (Spectral) *EXPERIMENTAL", Modifier.clickable { useAdvanced = true })
        }

        // --- ROZWIJANE MENU ZAAWANSOWANE ---
        AnimatedVisibility(visible = useAdvanced) {
            Column(
                modifier = Modifier
                    .padding(10.dp)
                    .background(Color.Black.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Advanced Settings", fontWeight = FontWeight.Bold, fontSize = 14.sp)

                SettingsSlider(
                    label = "Sensitivity",
                    value = sensitivity,
                    hint = "Im wyższa czułość, tym więcej cichych uderzeń zostanie wykrytych jako nuty.",
                    range = 5f..80f
                ) { sensitivity = it }
                SettingsSlider(
                    label = "Threshold (dB)",
                    value = threshold,
                    hint = "Próg głośności. Ustaw niżej (np. -60) dla cichych piosenek, wyżej dla głośnych.",
                    range = -60f..-10f
                ) { threshold = it }
                SettingsSlider(
                    label = "Merge Gap (s)",
                    value = mergeGap,
                    hint = "Minimalny czas między nutami. Zapobiega zlewaniu się dźwięków i 'double-tapom'.",
                    range = 0.02f..0.3f
                ) { mergeGap = it }
            }
        }

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

                    // --- KLUCZOWA POPRAWKA: Przechwycenie aktualnych stanów UI ---
                    val currentDifficulty = selectedDifficulty
                    val currentSensitivity = sensitivity.toDouble()
                    val currentThreshold = threshold.toDouble()
                    val currentMergeGap = mergeGap.toDouble()
                    val isAdvancedMode = useAdvanced
                    // -----------------------------------------------------------

                    scope.launch {
                        isLoading = true
                        try {
                            withContext(Dispatchers.IO) {
                                if (isAdvancedMode) {
                                    // Używamy przechwyconych wartości
                                    val analyzer = CustomizedBeatAnalyzer(
                                        currentSensitivity,
                                        currentThreshold,
                                        currentMergeGap
                                    )
                                    analyzer.analyzeAndExport(selectedFile.absolutePath)
                                } else {
                                    // Używamy przechwyconego poziomu trudności
                                    val analyzer = BeatAnalyzer()
                                    println("You are selected difficulty: $currentDifficulty")
                                    analyzer.analyzeAndExport(selectedFile.absolutePath)
                                }
                            }

                            selectedBaseName = baseName
                            selectedSongName = "Selected song: $baseName"
                            isStartEnabled = true

                            // Aktualizacja licznika uderzeń
                            if (File("beatmaps/$baseName").exists()) {
                                beatsDetected = countBeatsInCsv(baseName)
                            }

                        } catch (e: Exception) {
                            selectedSongName = "Error: ${e.message}"
                        } finally {
                            isLoading = false
                        }
                    }
                }
            }) { Text("Load Song") }
        }

        Spacer(modifier = Modifier.height(20.dp))
        DifficultySelector(selectedDifficulty) { newDifficulty: String ->
            selectedDifficulty = newDifficulty
            println(selectedDifficulty)
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SettingsSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    hint: String,
    onValueChange: (Float) -> Unit
) {
    Column(
        horizontalAlignment = Alignment.Start,
        modifier = Modifier.fillMaxWidth(0.8f).padding(vertical = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$label: ${String.format("%.2f", value)}", fontSize = 12.sp, color = Color.Black)

            Spacer(Modifier.width(8.dp))

            // Tooltip tylko nad ikoną pytajnika, nie nad suwakiem!
            TooltipArea(
                tooltip = {
                    Surface(
                        modifier = Modifier.shadow(4.dp),
                        color = Color(0xFF333333),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = hint,
                            modifier = Modifier.padding(8.dp),
                            color = Color.White,
                            fontSize = 12.sp
                        )
                    }
                },
                delayMillis = 200,
                tooltipPlacement = TooltipPlacement.CursorPoint(alignment = Alignment.BottomEnd)
            ) {
                // Mały wskaźnik podpowiedzi
                Text(
                    "(?)",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(2.dp)
                )
            }
        }

        // Suwak jest teraz poza TooltipArea, więc działa idealnie
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier.fillMaxWidth().height(30.dp)
        )
    }
}

fun deleteBeatmapFolder(baseName: String): Boolean {
    val folder = File("beatmaps/$baseName")
    return if (folder.exists()) {
        folder.deleteRecursively() // Usuwa folder wraz z całą zawartością (wav, csv, audio)
    } else {
        false
    }
}

fun countBeatsInCsv(baseName: String): Int {
    val csvFile = File("beatmaps/$baseName/beatmap.csv")
    if (!csvFile.exists()) return 0
    // Odczytujemy wszystkie linie i odejmujemy nagłówek
    return csvFile.readLines().size - 1
}