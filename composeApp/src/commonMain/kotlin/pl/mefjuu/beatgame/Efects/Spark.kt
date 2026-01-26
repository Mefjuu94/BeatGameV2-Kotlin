package pl.mefjuu.beatgame.Efects

import androidx.compose.ui.graphics.Color

data class Spark(
    var x: Float,
    var y: Float,
    val vx: Float,
    val vy: Float,
    var life: Float = 1.0f, // 1.0 to pełne życie, 0.0 to zniknięcie
    val color: Color
) {
    fun update(delta: Float) {
        x += vx
        y += vy
        life -= delta * 2.0f // czas trwania iskry (ok. 0.5s)
    }
}