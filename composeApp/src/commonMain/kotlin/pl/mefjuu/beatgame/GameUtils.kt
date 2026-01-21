package pl.mefjuu.beatgame

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key

fun getKeyName(key: Key): String {
    return when (key) {
        Key.DirectionLeft -> "←"
        Key.DirectionRight -> "→"
        Key.DirectionUp -> "↑"
        Key.DirectionDown -> "↓"
        Key.Spacebar -> "SPC"
        else -> key.toString().substringAfter(":")
    }
}

fun generateNeonColor(): Color = Color.hsv(
    hue = (0..360).random().toFloat(),
    saturation = 0.8f,
    value = 1.0f
)

fun randomFlashColor(): Color {
    val r = (30..150).random() / 255f
    val g = (20..150).random() / 255f
    val b = (60..150).random() / 255f
    return Color(r, g, b, 1f)
}