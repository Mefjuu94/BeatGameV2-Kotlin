package pl.mefjuu.beatgame

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