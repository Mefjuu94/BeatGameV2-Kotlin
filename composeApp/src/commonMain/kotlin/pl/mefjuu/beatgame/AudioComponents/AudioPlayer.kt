package pl.mefjuu.beatgame.AudioComponents

import java.io.File
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip

class AudioPlayer(path: String) {
    // 1. Zmieniamy na var, aby móc przypisać wartość w init
    private var clip: Clip? = null

    init {
        try {
            val file = File(path)
            println("odtwarzanie $path")
            if (file.exists()) {
                val audioStream = AudioSystem.getAudioInputStream(file)
                val audioClip = AudioSystem.getClip()
                audioClip.open(audioStream)
                clip = audioClip // Przypisanie do var
            } else {
                println("Plik audio nie istnieje: $path")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun play() {
        clip?.let {
            it.framePosition = 0
            it.start()
        }
    }

    fun pause() {
        clip?.stop()
    }

    fun resume() {
        clip?.start()
    }

    fun stop() {
        clip?.let {
            it.stop()
            it.framePosition = 0
        }
    }

    val timeSeconds: Double
        get() = (clip?.microsecondPosition ?: 0L) / 1000000.0

    val durationSeconds: Double
        get() = (clip?.microsecondLength ?: 0L) / 1000000.0

    val isFinished: Boolean
        get() = clip?.let { !it.isRunning && it.framePosition >= it.frameLength } ?: true
}