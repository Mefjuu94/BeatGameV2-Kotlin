package pl.mefjuu.beatgame

import androidx.compose.runtime.*

enum class Screen { Menu, Game }

@Composable
fun App() {
    var currentScreen by remember { mutableStateOf(Screen.Menu) }

    var selectedSong by remember { mutableStateOf("") }
    var difficulty by remember { mutableStateOf("Hard") }

    var gameSettings by remember { mutableStateOf(GameSettings()) }
    var drawBackground by remember { mutableStateOf(true) }

    when (currentScreen) {
        Screen.Menu -> {
            MainMenuScreen(
                initialSettings = gameSettings,
                onStartGame = { song, diff, finalSettings, background ->
                    selectedSong = song
                    difficulty = diff
                    gameSettings = finalSettings
                    currentScreen = Screen.Game
                    drawBackground = background
                }
            )
        }
        Screen.Game -> {
            GameScreen(
                baseName = selectedSong,
                difficulty = difficulty,
                settings = gameSettings,
                drawBackground = drawBackground,
                onBackToMenu = { currentScreen = Screen.Menu }
            )
        }
    }
}