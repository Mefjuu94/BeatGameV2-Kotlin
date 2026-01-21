package pl.mefjuu.beatgame.AudioComponents

import pl.mefjuu.beatgame.Beat
import java.io.File
import java.util.ArrayList

class BeatMap(path: String) {
    private val beats: MutableList<Beat> = ArrayList()

    init {
        try {
            val file = File(path)
            if (file.exists()) {
                file.useLines { lines ->
                    // skip(1) omija nagłówek CSV
                    lines.drop(1).forEach { line ->
                        val parts = line.split(",")
                        if (parts.isNotEmpty()) {
                            val time = parts[0].toDoubleOrNull()
                            if (time != null) {
                                beats.add(Beat(time))
                            }
                        }
                    }
                }
            } else {
                println("Beatmap file not found: $path")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getBeats(): MutableList<Beat> {
        return beats
    }
}