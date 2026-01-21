package pl.mefjuu.beatgame

import java.io.*
import java.nio.file.*

object FfmpegHelper {

    /**
     * Kopiuje ffmpeg z zasobów do folderu tymczasowego.
     */
    @Throws(IOException::class)
    fun extractFfmpegBinary(): Path {
        // 1. Sprawdź, czy ffmpeg.exe leży bezpośrednio w folderze projektu
        val localPath = Paths.get("ffmpeg.exe")
        if (Files.exists(localPath)) {
            println("Używam ffmpeg znalezionego w folderze projektu.")
            return localPath.toAbsolutePath()
        }

        // 2. Jeśli nie ma lokalnie, spróbuj z zasobów (standardowa procedura)
        val resourcePath = "ffmpeg/ffmpeg.exe"
        val inputStream = Thread.currentThread().contextClassLoader.getResourceAsStream(resourcePath)
            ?: throw IOException("Nie znaleziono ffmpeg ani lokalnie, ani w zasobach.")

        val tempFile = Files.createTempFile("ffmpeg_temp", ".exe")
        tempFile.toFile().deleteOnExit()
        Files.newOutputStream(tempFile).use { inputStream.copyTo(it) }

        return tempFile.toAbsolutePath()
    }

    /**
     * Uruchamia ffmpeg do konwersji MP3 → WAV
     */
    @Throws(IOException::class, InterruptedException::class)
    fun convertToWav(inputFile: File, outputWavPath: Path) {
        val ffmpegPath = extractFfmpegBinary()

        val pb = ProcessBuilder(
            ffmpegPath.toAbsolutePath().toString(), "-y",
            "-i", inputFile.absolutePath,
            outputWavPath.toAbsolutePath().toString()
        )
        pb.redirectErrorStream(true)
        val process = pb.start()

        // Odczyt wyjścia, aby proces się nie zablokował
        process.inputStream.bufferedReader().use { reader ->
            while (reader.readLine() != null) { /* konsumujemy logi */ }
        }

        val exitCode = process.waitFor()
        if (exitCode != 0) {
            throw IOException("Błąd ffmpeg (exit code $exitCode)")
        }
    }
}