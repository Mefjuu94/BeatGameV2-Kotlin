package pl.mefjuu.beatgame.AudioComponents

import be.tarsos.dsp.AudioDispatcher
import be.tarsos.dsp.io.jvm.AudioDispatcherFactory
import be.tarsos.dsp.onsets.PercussionOnsetDetector
import pl.mefjuu.beatgame.FfmpegHelper
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.ArrayList
import java.util.Collections
import java.util.Locale

class CustomizedBeatAnalyzer(
    private val sensitivity: Double,   // To przychodzi z UI
    private val threshold: Double,     // To przychodzi z UI
    private val mergeThreshold: Double // To przychodzi z UI
) {

    private val SAMPLE_RATE = 44100
    private val BUFFER_SIZE = 1024
    private val OVERLAP = 512
    private val KICK_GAP_THRESHOLD = 0.25

    // FIX: Usuwamy stare stałe SENSITIVITY i THRESHOLD,
    // bo teraz używamy tych z konstruktora (sensitivity, threshold)

    fun analyzeAndExport(audioPath: String): Int { // FIX: Zmieniamy na Int, żeby zwrócić liczbę uderzeń
        try {
            val audioFile = File(audioPath)
            val baseName = audioFile.nameWithoutExtension
            val beatmapsDir = Paths.get("beatmaps")
            val beatDir = beatmapsDir.resolve(baseName)

            // FIX: Skoro chcesz móc analizować na nowo tym samym analizatorem,
            // usuwamy warunek "if (!Files.exists(beatDir))" lub polegamy na tym,
            // że UI usunęło folder przed wywołaniem tej metody.
            println("sensitivity chosen $sensitivity")
            println("threshold chosen $threshold")
            println("mergeThreshold chosen $mergeThreshold")

            if (!Files.exists(beatDir)) Files.createDirectories(beatDir)

            if (!audioFile.exists()) throw FileNotFoundException(audioPath)

            val targetAudio = beatDir.resolve(audioFile.name)
            Files.copy(audioFile.toPath(), targetAudio, StandardCopyOption.REPLACE_EXISTING)

            val wavPath = convertToWav(audioFile, beatDir)
            val candidateTimes: MutableList<Double> = Collections.synchronizedList(ArrayList<Double>())

            val dispatcher: AudioDispatcher = AudioDispatcherFactory.fromPipe(
                wavPath.toString(), SAMPLE_RATE, BUFFER_SIZE, OVERLAP
            )

            val mPercussionDetector = PercussionOnsetDetector(
                SAMPLE_RATE.toFloat(),
                BUFFER_SIZE,
                { time, _ -> candidateTimes.add(time.toDouble()) },
                sensitivity.toFloat().toDouble(), // FIX: Używamy małego 'sensitivity' z konstruktora
                threshold.toDouble()              // FIX: Używamy małego 'threshold' z konstruktora
            )

            dispatcher.addAudioProcessor(mPercussionDetector)
            dispatcher.run()

            candidateTimes.sort()

            // FIX: Używamy mergeThreshold z konstruktora
            val merged = mergeCloseTimes(candidateTimes, mergeThreshold)

            val allBeats = assignTypes(merged, KICK_GAP_THRESHOLD)

            val csvPath = beatDir.resolve("beatmap.csv")
            Files.newBufferedWriter(csvPath).use { writer ->
                writer.write("Time(s),Type\n")
                for (b in allBeats) {
                    writer.write(String.format(Locale.US, "%.3f,%s\n", b.time, b.type))
                }
            }

            println("✅ Beatmap was generated: ${allBeats.size} beats")
            return allBeats.size // FIX: Zwracamy wynik dla UI

        } catch (e: Exception) {
            e.printStackTrace()
            return 0
        }
    }

    private fun mergeCloseTimes(times: List<Double>, threshold: Double): List<Double> {
        val out = ArrayList<Double>()
        if (times.isEmpty()) return out
        var prev: Double = times[0]
        for (i in 1 until times.size) {
            val cur: Double = times[i]
            if (cur - prev <= threshold) {
                prev = (prev + cur) / 2.0
            } else {
                out.add(prev)
                prev = cur
            }
        }
        out.add(prev)
        return out
    }

    private fun assignTypes(times: List<Double>, kickGap: Double): List<BeatRecord> {
        val out = ArrayList<BeatRecord>()
        var lastKick = -999.0
        val alt = arrayOf("snare", "hat")
        var altIndex = 0

        for (t in times) {
            val type: String
            if (t - lastKick < kickGap) {
                type = alt[altIndex % alt.size]!!
                altIndex++
            } else {
                type = "kick"
                lastKick = t
            }
            out.add(BeatRecord(t, type))
        }
        return out
    }

    @Throws(IOException::class, InterruptedException::class)
    private fun convertToWav(inputFile: File, outputDir: Path): Path {
        val baseName = inputFile.name.replace("\\.[^.]+$".toRegex(), "")
        val wavPath = outputDir.resolve("$baseName.wav")
        FfmpegHelper.convertToWav(inputFile, wavPath)
        println("File with '.wav' extension created!")
        return wavPath
    }

    private class BeatRecord(var time: Double, var type: String)
}
