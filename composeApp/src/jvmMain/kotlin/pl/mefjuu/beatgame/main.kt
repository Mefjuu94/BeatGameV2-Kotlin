package pl.mefjuu.beatgame

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Beat Game",
        // Usunęliśmy onKeyEvent stąd, bo obsłużymy go w GameScreen
    ) {
        // Wywołujemy App() bez żadnych argumentów (naprawia błąd "Too many arguments")
        App()
    }
}