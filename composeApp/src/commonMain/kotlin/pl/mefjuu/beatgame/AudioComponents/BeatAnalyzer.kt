package pl.mefjuu.beatgame.AudioComponents

import be.tarsos.dsp.AudioDispatcher
import be.tarsos.dsp.AudioEvent
import be.tarsos.dsp.AudioProcessor
import be.tarsos.dsp.io.jvm.AudioDispatcherFactory
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

class BeatAnalyzer() {
    // Parametry analizy
    private val SAMPLE_RATE = 44100
    private val BUFFER_SIZE = 256
    private val OVERLAP = 128
    private val MERGE_THRESHOLD = 0.1 // sekundy — scalanie bliskich beatów
    private var KICK_GAP_THRESHOLD = 0.1 // sekundy — odstęp między kickami

    fun analyzeAndExport(audioPath: String): Int {
        var countBeats = 0

        try {
            val audioFile = File(audioPath)
            val baseName = audioFile.getName().replace("\\.[^.]+$".toRegex(), "")
            println("basena z analizera $baseName")
            if (!Files.exists(Path.of("beatmaps\\" + baseName))) {
                println("Analyzing and creating new beatmap.")
                if (!audioFile.exists()) throw FileNotFoundException(audioPath)

                val beatDir = Paths.get("beatmaps", baseName)
                Files.createDirectories(beatDir)

                // Skopiuj plik audio
                val targetAudio = beatDir.resolve(audioFile.getName())
                Files.copy(audioFile.toPath(), targetAudio, StandardCopyOption.REPLACE_EXISTING)

                // Konwersja do WAV
                val wavPath = convertToWav(audioFile, beatDir)

                // Dispatcher TarsosDSP
                val dispatcher: AudioDispatcher = AudioDispatcherFactory.fromPipe(
                    wavPath.toString(), SAMPLE_RATE, BUFFER_SIZE, OVERLAP
                )

                val candidateTimes: MutableList<Double> = Collections.synchronizedList(ArrayList<Double>())

                dispatcher.addAudioProcessor(object : AudioProcessor {
                    override fun process(audioEvent: AudioEvent): Boolean {
                        val rms = audioEvent.rms
                        val time = audioEvent.timeStamp
                        if (rms >= RMS_THRESHOLD) candidateTimes.add(time)
                        return true
                    }

                    override fun processingFinished() {}
                })

                dispatcher.run()

                // Sortuj i scal bliskie beaty
                Collections.sort<Double?>(candidateTimes)
                val merged = mergeCloseTimes(candidateTimes.filterNotNull(), MERGE_THRESHOLD)

                // Przypisz typy beatów
                val allBeats = assignTypes(merged, KICK_GAP_THRESHOLD)

                // Zapis CSV
                val csvPath = beatDir.resolve("beatmap.csv")
                Files.newBufferedWriter(csvPath).use { writer ->
                    writer.write("Time(s),Type\n")
                    for (b in allBeats) {
                        writer.write(String.Companion.format(Locale.US, "%.3f,%s\n", b.time, b.type))
                        countBeats++;
                    }
                }
                println("✅ Beatmap was generated: " + csvPath.toAbsolutePath() + " detected $countBeats beats")

                val fileWavToClean: File = File(wavPath.toString())
                cleanWav(fileWavToClean)

            } else if (Files.exists(Path.of("beatmaps\\" + baseName))) {
                println("No need to analyzing, beat map was already created.")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return countBeats
    }

    private fun mergeCloseTimes(times: List<Double>, threshold: Double): List<Double> {
        val out = ArrayList<Double>()
        if (times.isEmpty()) return out
        var prev: Double = times.get(0)!!
        for (i in 1..<times.size) {
            val cur: Double = times.get(i)!!
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
        val alt: Array<String?> = arrayOf("snare", "hat") // POPRAWKA TABLICY
        var altIndex = 0

        for (t in times) {
            val type: String?
            if (t - lastKick < kickGap) {
                type = alt[altIndex % alt.size]
                altIndex++
            } else {
                type = "kick"
                lastKick = t
            }
            out.add(BeatRecord(t, type!!))
        }
        return out
    }

    @Throws(IOException::class, InterruptedException::class)
    private fun convertToWav(inputFile: File, outputDir: Path): Path {
        val baseName = inputFile.getName().replace("\\.[^.]+$".toRegex(), "")
        val wavPath = outputDir.resolve(baseName + ".wav")
        FfmpegHelper.convertToWav(inputFile, wavPath)
        println("File with '.wav' extension created!")
        return wavPath
    }

    private class BeatRecord(var time: Double, var type: String?)

    companion object {
        var RMS_THRESHOLD: Double = 0.4
    }

    private fun cleanWav(wavFile: File) {
        if (wavFile.exists()) {
            val deleted = wavFile.delete()
            if (deleted) {
                println("Sukces: Plik .wav został usunięty po analizie.")
            } else {
                println("Ostrzeżenie: Nie udało się usunąć pliku (może być nadal otwarty).")
            }
        }
    }

}