package pl.mefjuu.beatgame

import androidx.compose.ui.input.key.Key

data class Beat(var time: Double, var isHit: Boolean = false)

data class HitWave(
    val time: Double,
    val side: String,
    val isMiss: Boolean = false
)

data class GameSettings(
    val leftKey: Key = Key.DirectionLeft,
    val rightKey: Key = Key.DirectionRight
)