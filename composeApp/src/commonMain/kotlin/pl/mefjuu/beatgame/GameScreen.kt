package pl.mefjuu.beatgame

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import pl.mefjuu.beatgame.AudioComponents.AudioPlayer
import pl.mefjuu.beatgame.AudioComponents.BeatMap
import kotlin.math.*
import kotlin.random.Random

@Composable
fun GameScreen(
    baseName: String,
    difficulty: String,
    settings: GameSettings,
    drawBackground: Boolean,
    onBackToMenu: () -> Unit
) {
    // --- STAN GRY ---
    var isGameOver by remember { mutableStateOf(false) }
    val pathToWav = "beatmaps/$baseName/$baseName.wav"
    val pathToCsv = "beatmaps/$baseName/beatmap.csv"

    var currentTime by remember { mutableStateOf(0.0) }
    var score by remember { mutableStateOf(0) }
    var combo by remember { mutableStateOf(0) }
    var isPaused by remember { mutableStateOf(false) }

    val leftBeats = remember { mutableStateListOf<Beat>() }
    val rightBeats = remember { mutableStateListOf<Beat>() }
    val hitWaves = remember { mutableStateListOf<HitWave>() }

    val focusRequester = remember { FocusRequester() }
    val audioPlayer = remember { AudioPlayer(pathToWav) }
    val beatMap = remember { BeatMap(pathToCsv) }
    var beatenBeats by remember { mutableIntStateOf(0) }
    var beatsLeft by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }

    val flashAlpha = remember { Animatable(0f) }
    val moveGameToRight = 100f
    val textMeasurer = rememberTextMeasurer()

    // --- KOLORY I ANIMACJE ---
    val baseColor1 = Color(0xFF0A0A1F)
    val baseColor2 = Color(0xFF1A0A3A)

    var currentTargetColor1 by remember { mutableStateOf(baseColor1) }
    var currentTargetColor2 by remember { mutableStateOf(baseColor2) }

    var currentLeftColor by remember { mutableStateOf(Color(0xFF00FBFF)) }
    var currentRightColor by remember { mutableStateOf(Color(0xFFFF00FF)) }
    var lastColorChangeCombo by remember { mutableIntStateOf(0) }

    val animLeft by animateColorAsState(targetValue = currentLeftColor, animationSpec = tween(200))
    val animRight by animateColorAsState(targetValue = currentRightColor, animationSpec = tween(200))

    val scope = rememberCoroutineScope()
    val audioVisualOffset = -0.1

    val drawDynamicBackground = drawBackground

    val dynamicHue by remember(currentTime) {
        derivedStateOf { (currentTime * 20f % 360f).toFloat() }
    }

    LaunchedEffect(dynamicHue) {
        currentLeftColor = Color.hsv(hue = dynamicHue, saturation = 0.8f, value = 1f)
        currentRightColor = Color.hsv(hue = (dynamicHue + 180f) % 360f, saturation = 0.8f, value = 1f)
    }

    // Funkcja pomocnicza do błysków tła
    fun randomFlashColor(): Color {
        // Losujemy pełny odcień, ale z niską przezroczystością, żeby nie oślepiało
        return Color.hsv(
            hue = (0..360).random().toFloat(),
            saturation = 0.6f,
            value = 0.4f // Niższa wartość = ciemniejszy, głębszy błysk
        )
    }

    // --- LOGIKA TRAFIENIA (OKNO 0.2s + MISS) ---
    fun checkHit(beats: MutableList<Beat>, side: String) {
        val visualTime = currentTime + audioVisualOffset
        val iterator = beats.iterator()

        while (iterator.hasNext()) {
            val beat = iterator.next()
            val delta = abs(beat.time - visualTime)

            // 0.1s w każdą stronę od idealnego punktu = 0.2s okna
            if (delta < 0.1) {
                currentTargetColor1 = randomFlashColor()
                currentTargetColor2 = randomFlashColor()

                scope.launch {
                    flashAlpha.snapTo(0f)
                    flashAlpha.animateTo(0.2f, tween(50))
                    flashAlpha.animateTo(0f, tween(400))
                }

                hitWaves.add(HitWave(currentTime, side, isMiss = false))
                beat.isHit = true
                iterator.remove()
                score = score + 10 + combo
                combo++
                beatenBeats += 1
                return
            }
        }

        combo = 0
        hitWaves.add(HitWave(currentTime, side, isMiss = true)) // Czerwona fala pudła
    }

    // Tworzymy listę gwiazd raz przy starcie ekranu
    val stars = remember {
        List(250) {
            WarpStar(
                x = (Random.nextFloat() * 2000 - 1000),
                y = (Random.nextFloat() * 2000 - 1000),
                z = Random.nextFloat() * 1000
            )
        }
    }

    // Dynamiczna prędkość zależna od combo lub błysku
    val warpSpeed = 10f + (flashAlpha.value * 40f)

    // --- ŁADOWANIE I PĘTLA GRY ---
    LaunchedEffect(baseName) {

        val minInterval = when (difficulty.lowercase()) {
            "easy" -> 0.5    //  2nuty
            "medium" -> 0.25  // Maksymalnie 4 nut na sekundę
            "hard" -> 0.1   // Maksymalnie ~10 nut na sekundę (bardzo gęsto)
            else -> 0.2
        }

        isLoading = true
        val rawBeats = beatMap.getBeats()
        leftBeats.clear()
        rightBeats.clear()
        var lastTime = -1.0
        rawBeats.forEach { beat ->
            // Sprawdzamy, czy nuta nie jest za blisko poprzedniej dla danego poziomu
            if (beat.time - lastTime >= minInterval) {
                beatsLeft ++;

                // Tutaj decydujesz o podziale na strony
                if (leftBeats.size <= rightBeats.size) {
                    leftBeats.add(beat)
                } else {
                    rightBeats.add(beat)
                }

                lastTime = beat.time
            }
        }
        audioPlayer.play()
        isLoading = false
        while (true) {
            withFrameMillis {
                if (!isPaused) {
                    currentTime = audioPlayer.timeSeconds
                    hitWaves.removeAll { currentTime - it.time > 0.5 }
                    if (currentTime >= audioPlayer.durationSeconds && audioPlayer.durationSeconds > 0) {
                        isGameOver = true
                    }
                }
            }
            if (isGameOver) break
        }
    }

    // --- LOGIKA ZMIANY KOLORÓW PRZY COMBO ---
    LaunchedEffect(combo) {
        if (combo > 0 && combo % 4 == 0 && combo != lastColorChangeCombo) {
            val randomHue = (0..360).random().toFloat()
            currentLeftColor = Color.hsv(
                hue = dynamicHue,
                saturation = 0.7f + (sin(currentTime).toFloat() * 0.2f), // Saturation pulsuje
                value = 1f
            )
            currentRightColor = Color.hsv(hue = (randomHue + 180f) % 360f, saturation = 0.8f, value = 1f)
            lastColorChangeCombo = combo
        }
    }

    if (isLoading) {
        Box(Modifier.fillMaxSize().background(Color(0xFF0F0F0F)), Alignment.Center) {
            CircularProgressIndicator(color = Color.Cyan)
        }
    } else if (isGameOver) {
        EndScreen(score, beatenBeats, leftBeats.size + rightBeats.size + beatenBeats, onBackToMenu)
    } else {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            Canvas(Modifier.fillMaxSize()) {
                val canvasWidth = size.width
                val canvasHeight = size.height
                val centerX = canvasWidth / 2f
                val centerY = canvasHeight / 2f
                val spawnDuration = 2.2f
                val visualFloatTime = (currentTime + audioVisualOffset).toFloat()

                // --- 1. TŁO DYNAMICZNE ---
                val currentColor1 = lerp(baseColor1, currentTargetColor1, flashAlpha.value)
                val currentColor2 = lerp(baseColor2, currentTargetColor2, flashAlpha.value)
                drawRect(brush = Brush.verticalGradient(listOf(currentColor1, currentColor2)), size = size)


                val warpCenterX = centerX + moveGameToRight

                // --- 1. TŁO GRADIENTOWE (POŚWIATA ŚRODKA) ---
                // Używamy kolorów animowanych (animLeft/animRight) dla poświaty
                drawCircle(
                    brush = Brush.radialGradient(
                        0f to Color.White.copy(alpha = 0.05f + flashAlpha.value * 0.1f),
                        0.5f to animLeft.copy(alpha = 0.1f),
                        1f to Color.Transparent,
                        center = Offset(warpCenterX, centerY)
                    ),
                    radius = canvasWidth * 0.8f,
                    center = Offset(warpCenterX, centerY)
                )

                // --- 2. TUNEL WARP ---
                if(drawDynamicBackground) {
                    stars.forEach { star ->
                        if (!isPaused) star.update(warpSpeed)

                        // Projekcja 3D na 2D: (pos / z) * skala + offset
                        val px = (star.x / star.prevZ) * centerX + warpCenterX
                        val py = (star.y / star.prevZ) * centerY + centerY
                        val sx = (star.x / star.z) * centerX + warpCenterX
                        val sy = (star.y / star.z) * centerY + centerY

                        val starBaseColor = if (star.x < 0) animLeft else animRight

                        // Przezroczystość rośnie, im bliżej nas jest gwiazda
                        val alpha = (1f - star.z / 1000f).coerceIn(0f, 1f)

                        drawLine(
                            color = starBaseColor.copy(alpha = alpha),
                            start = Offset(px, py),
                            end = Offset(sx, sy),
                            strokeWidth = (1f + (1f - star.z / 1000f) * 6f), // Linie grubieją przy krawędziach
                            cap = StrokeCap.Round
                        )
                    }
                }

                // --- 3. NUTY
                val spreadX = canvasWidth * 0.2f
                val startSpread = canvasWidth * 0.05f
                val targetY = canvasHeight * 0.85f
                val centerYPos = canvasHeight * 0.3f
                val leftStart = centerX - startSpread + moveGameToRight
                val leftTarget = centerX - spreadX + moveGameToRight
                val rightStart = centerX + startSpread + moveGameToRight
                val rightTarget = centerX + spreadX + moveGameToRight

                listOf("left" to leftBeats, "right" to rightBeats).forEach { (side, beats) ->
                    beats.forEach { beat ->
                        val timeToHit = beat.time.toFloat() - visualFloatTime
                        val progress = (1.0f - (timeToHit / spawnDuration)).coerceIn(0f, 1.5f)

                        if (progress > 0f && !beat.isHit && progress < 1.2f) {
                            val easedProgress = progress.toDouble().pow(4).toFloat()
                            val startX = if (side == "left") leftStart else rightStart
                            val targetX = if (side == "left") leftTarget else rightTarget
                            val currentX = startX + (targetX - startX) * easedProgress
                            val currentY = centerYPos + (targetY - centerYPos) * easedProgress
                            val alphaFade = if (progress > 1.0f) (1.2f - progress) * 5f else 1.0f
                            val baseColor = if (side == "left") animLeft else animRight
                            val radius = (5f + 100f * easedProgress) * alphaFade

                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(baseColor.copy(alpha = 0.6f * progress * alphaFade), Color.Transparent),
                                    center = Offset(currentX, currentY), radius = radius * 2.5f
                                ),
                                radius = radius * 2.5f, center = Offset(currentX, currentY)
                            )
                            drawCircle(Color.White.copy(alpha = alphaFade), radius * 0.4f, Offset(currentX, currentY))
                        }
                    }
                }

                // --- 4. CELE I ETYKIETY ---
                val targets = listOf("left" to leftTarget, "right" to rightTarget)

                targets.forEach { (side, targetX) ->
                    val color = if (side == "left") animLeft else animRight
                    val flash = flashAlpha.value
                    val label = if (side == "left") getKeyName(settings.leftKey) else getKeyName(settings.rightKey)

                    drawCircle(
                        color = color.copy(alpha = 0.2f + flash * 0.4f),
                        radius = 65f + flash * 20f,
                        center = Offset(targetX, targetY),
                        style = Stroke(width = 4f)
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.3f + flash * 0.5f),
                        radius = 55f,
                        center = Offset(targetX, targetY),
                        style = Stroke(width = 2f)
                    )

                    val tLayout = textMeasurer.measure(
                        text = label,
                        style = TextStyle(color = Color.White.copy(0.7f + flash * 0.3f), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                    )
                    drawText(
                        textLayoutResult = tLayout,
                        topLeft = Offset(targetX - tLayout.size.width / 2f, targetY - tLayout.size.height / 2f)
                    )
                }

                // --- 5. FALE (BIAŁE HIT / CZERWONE MISS) ---
                hitWaves.forEach { wave ->
                    val duration = if (wave.isMiss) 0.3f else 0.45f
                    val waveProgress = (currentTime.toFloat() - wave.time.toFloat()) / duration

                    if (waveProgress < 1.0f) {
                        val waveX = if (wave.side.lowercase() == "left") leftTarget else rightTarget
                        val waveColor = if (wave.isMiss) Color.Red else Color.White

                        drawCircle(
                            color = waveColor.copy(alpha = 1.0f - waveProgress),
                            radius = 60f + (waveProgress * (if (wave.isMiss) 100f else 180f)),
                            center = Offset(waveX, targetY),
                            style = Stroke(width = (if (wave.isMiss) 15f else 12f) * (1.0f - waveProgress))
                        )
                    }
                }
            }

            // --- UI STATYSTYK ---
            Column(Modifier.width(220.dp).fillMaxHeight().padding(24.dp), Arrangement.Center) {
                Text("SCORE", color = Color.Cyan.copy(0.7f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("$score", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(16.dp))
                Text("COMBO", color = Color.Magenta.copy(0.7f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("x$combo", color = Color(0xFFFF00FF), fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(32.dp))
                Text("beats: $beatenBeats / ${leftBeats.size}", color = Color(0xFFFF00FF), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                ///naprawić!!
                Spacer(Modifier.height(32.dp))
                GameButton(if (isPaused) "RESUME" else "PAUSE", Color.Cyan) {
                    isPaused = !isPaused
                    if (isPaused) audioPlayer.pause() else audioPlayer.resume()
                }
                Spacer(Modifier.height(12.dp))
                GameButton("BACK", Color.Red) { audioPlayer.stop(); onBackToMenu() }
            }

            // --- OBSŁUGA KLAWIATURY ---
            Box(Modifier.fillMaxSize().focusRequester(focusRequester).focusable().onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        settings.leftKey -> { checkHit(leftBeats, "left"); true }
                        settings.rightKey -> { checkHit(rightBeats, "right"); true }
                        Key.Spacebar -> { isPaused = !isPaused; if (isPaused) audioPlayer.pause() else audioPlayer.resume(); true }
                        else -> false
                    }
                } else false
            })
            LaunchedEffect(Unit) { focusRequester.requestFocus() }
        }
    }
}

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