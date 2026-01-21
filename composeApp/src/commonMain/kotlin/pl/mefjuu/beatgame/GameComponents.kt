package pl.mefjuu.beatgame

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.* // To daje dostęp do remember, mutableStateOf
import androidx.compose.runtime.getValue // KLUCZOWE
import androidx.compose.runtime.setValue // KLUCZOWE
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pl.mefjuu.beatgame.AudioComponents.BeatAnalyzer

@Composable
fun GameButton(text: String, color: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = Color.White
        ),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth().height(45.dp)
    ) {
        Text(text = text, fontWeight = FontWeight.Bold, style = TextStyle(letterSpacing = 2.sp))
    }
}

@Composable
fun EndScreen(score: Int, hits: Int, totalBeats: Int, onBackToMenu: () -> Unit) {
    val efficiency = if (totalBeats > 0) (hits * 100) / totalBeats else 0
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFF121212)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Text("🎉 End of song!", style = MaterialTheme.typography.headlineLarge, color = Color.White)
            Spacer(modifier = Modifier.height(24.dp))
            Text("Score: $score", color = Color.White, style = MaterialTheme.typography.bodyLarge)
            Text("Hits: $hits / $totalBeats", color = Color.White)
            Text(
                "Efficiency: $efficiency%",
                color = if (efficiency > 70) Color.Green else Color.Yellow,
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                "Thanks for playing my little game,\nKisses from Mefjuu 94 - Orzeł! 🦅",
                textAlign = TextAlign.Center, color = Color.LightGray
            )
            Spacer(modifier = Modifier.height(48.dp))
            Button(onClick = onBackToMenu, modifier = Modifier.height(50.dp).width(200.dp)) {
                Text("Back to menu")
            }
        }
    }
}

@Composable
fun SensitivityDialog(onDismiss: () -> Unit) {
    var sliderValue by remember { mutableStateOf(BeatAnalyzer.RMS_THRESHOLD.toFloat()) }

    // Używamy Dialog z biblioteki Compose (androidx.compose.ui.window.Dialog)
    androidx.compose.ui.window.Dialog(onCloseRequest = onDismiss, title = "Set Impact Sensitivity") {
        Surface(
            modifier = Modifier.padding(16.dp),
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFF2D2D2D)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("RMS Sensitivity (0.05 – 0.30):", color = Color.White, fontSize = 16.sp)
                Spacer(Modifier.height(8.dp))
                Slider(
                    value = sliderValue,
                    onValueChange = { sliderValue = it },
                    valueRange = 0.05f..0.30f
                )
                Text("Current: ${"%.2f".format(sliderValue)}", color = Color.Cyan)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = {
                    BeatAnalyzer.RMS_THRESHOLD = sliderValue.toDouble()
                    onDismiss()
                }) {
                    Text("Apply")
                }
            }
        }
    }
}

@Composable
fun DifficultySelector(current: String, onSelected: (String) -> Unit) {
    val options = listOf("Easy", "Medium", "Hard")
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("MODE", fontWeight = FontWeight.Bold, color = Color.Black)
            Row {
                options.forEach { text ->
                    Row(
                        modifier = Modifier
                            .selectable(selected = (text == current), onClick = { onSelected(text) })
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = (text == current), onClick = null)
                        Text(text, Modifier.padding(start = 4.dp), color = Color.Black)
                    }
                }
            }
        }
    }
}