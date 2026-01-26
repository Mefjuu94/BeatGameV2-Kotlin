package pl.mefjuu.beatgame.Efects

data class WarpStar(
    var x: Float, // Pozycja względem środka
    var y: Float,
    var z: Float, // Głębia (1000 = daleko, 0 = tuż przed ekranem)
    var prevZ: Float = z
) {
    fun update(speed: Float) {
        prevZ = z
        z -= speed
        if (z <= 1) { // Reset gwiazdy, gdy przeleci za nas
            z = 1000f
            prevZ = z
        }
    }
}