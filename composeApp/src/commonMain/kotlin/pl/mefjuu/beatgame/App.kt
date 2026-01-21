package pl.mefjuu.beatgame

import androidx.compose.runtime.*

enum class Screen { Menu, Game }

@Composable
fun App() {
    var currentScreen by remember { mutableStateOf(Screen.Menu) }

    // Główne stany przekazywane między ekranami
    var selectedSong by remember { mutableStateOf("") }
    var difficulty by remember { mutableStateOf("Hard") }

    // Tu trzymamy nasze klawisze, aby gra wiedziała, jakich użyć
    var gameSettings by remember { mutableStateOf(GameSettings()) }

    when (currentScreen) {
        Screen.Menu -> {
            MainMenuScreen(
                initialSettings = gameSettings,
                onStartGame = { song, diff, finalSettings ->
                    selectedSong = song
                    difficulty = diff
                    gameSettings = finalSettings // Zapisujemy klawisze przed startem
                    currentScreen = Screen.Game
                }
            )
        }
        Screen.Game -> {
            GameScreen(
                baseName = selectedSong,
                difficulty = difficulty,
                settings = gameSettings, // PRZEKAZUJEMY SETTINGS - Błąd naprawiony!
                onBackToMenu = { currentScreen = Screen.Menu }
            )
        }
    }
}