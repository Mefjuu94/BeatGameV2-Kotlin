package pl.mefjuu.beatgame.AudioComponents

import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip

class AudioPlayer(filePath: String) {
    private var clip: Clip? = null

    init {
        try {
            val file = java.io.File(filePath)
            val baseStream = AudioSystem.getAudioInputStream(file)
            val baseFormat = baseStream.format

            // Jeśli to MP3, musimy zdefiniować format wyjściowy (PCM)
            val decodedFormat = AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                baseFormat.sampleRate,
                16,
                baseFormat.channels,
                baseFormat.channels * 2,
                baseFormat.sampleRate,
                false
            )

            // Tworzymy strumień dekodujący
            val decodedStream = AudioSystem.getAudioInputStream(decodedFormat, baseStream)

            clip = AudioSystem.getClip()
            clip?.open(decodedStream)
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

    // Clip.getMicrosecondPosition() po starcie bywa niedokładny.
    // Lepiej użyć Frame Position dla większej precyzji:
    val timeSeconds: Float
        get() = (clip?.framePosition?.toFloat() ?: 0f) / (clip?.format?.frameRate ?: 44100f)

    val durationSeconds: Double
        get() = (clip?.microsecondLength ?: 0L) / 1000000.0

    val isFinished: Boolean
        get() = clip?.let { !it.isRunning && it.framePosition >= it.frameLength } ?: true

    fun isActive(): Boolean = clip?.isActive ?: false
    // W AudioPlayer.kt
    fun prepare() {
        clip?.let {
            it.framePosition = 0
            // Niektóre systemy potrzebują "pchnięcia", by zbuforować dane
            it.start()
            it.stop()
        }
    }

}
