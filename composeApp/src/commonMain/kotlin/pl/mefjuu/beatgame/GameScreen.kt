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
import androidx.compose.ui.graphics.Shadow
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
import pl.mefjuu.beatgame.AudioComponents.AudioPlayer
import pl.mefjuu.beatgame.AudioComponents.BeatMap
import pl.mefjuu.beatgame.Efects.Spark
import pl.mefjuu.beatgame.Efects.WarpStar
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
    val pathToMp3 = "beatmaps/$baseName/$baseName.mp3"
    val pathToCsv = "beatmaps/$baseName/beatmap.csv"

    var currentTime by remember { mutableStateOf(0.0) }
    var score by remember { mutableStateOf(0) }
    var combo by remember { mutableStateOf(0) }
    var isPaused by remember { mutableStateOf(false) }

    val leftBeats = remember { mutableStateListOf<Beat>() }
    val rightBeats = remember { mutableStateListOf<Beat>() }
    val hitWaves = remember { mutableStateListOf<HitWave>() }

    val focusRequester = remember { FocusRequester() }
    val audioPlayer = remember { AudioPlayer(pathToMp3) }
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

    val audioVisualOffset = -0.1

    val drawDynamicBackground = drawBackground
    val sparks = remember { mutableStateListOf<Spark>() }

    val dynamicHue by remember(currentTime) {
        derivedStateOf { (currentTime * 20f % 360f).toFloat() }
    }

    LaunchedEffect(dynamicHue) {
        // Zapewniamy, że hue zawsze jest w zakresie 0..360
        val leftHue = ((dynamicHue % 360f) + 360f) % 360f
        val rightHue = (((dynamicHue + 180f) % 360f) + 360f) % 360f

        currentLeftColor = Color.hsv(hue = leftHue, saturation = 0.8f, value = 1f)
        currentRightColor = Color.hsv(hue = rightHue, saturation = 0.8f, value = 1f)
    }

    var beatsToHit by remember { mutableStateOf(0) }

    // --- LOGIKA TRAFIENIA (OKNO 0.2s + MISS) ---
    fun checkHit(beats: MutableList<Beat>, side: String, targetX: Float, targetY: Float) {
        val visualTime = currentTime + audioVisualOffset
        val iterator = beats.iterator()

        while (iterator.hasNext()) {
            val beat = iterator.next()
            val delta = abs(beat.time - visualTime)

            if (delta < 0.1) {
                // --- GENEROWANIE ISKIER (SPAWANIE) ---
                val weldingColors = listOf(
                    Color(0xFFFFFFFF), // Czysty biały (najgorętszy)
                    Color(0xFFFFFAED), // Bardzo jasny żółty
                    Color(0xFFFFD700), // Złoty
                    Color(0xFFFF8C00)  // Pomarańczowy (chłodniejszy odłamek)
                )

                repeat(20) {
                    val angle = (Random.nextFloat() * PI.toFloat()) + PI.toFloat()
                    val speed = Random.nextFloat() * 12f + 4f

                    sparks.add(Spark(
                        x = targetX,
                        y = targetY,
                        vx = cos(angle) * speed,
                        vy = sin(angle) * speed,
                        // Losuj kolor z palety spawania
                        color = weldingColors.random()
                    ))
                }

                hitWaves.add(HitWave(currentTime, side, isMiss = false))
                beat.isHit = true
                iterator.remove()
                score += 10 + combo
                combo++
                beatenBeats++
                return
            }
        }
        combo = 0
        hitWaves.add(HitWave(currentTime, side, isMiss = true))
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
            "easy" -> 0.5    //  2 nuty/s
            "medium" -> 0.25  // Maksymalnie 4 nut na sekundę
            "hard" -> 0.1   // Maksymalnie ~10 nut na sekundę (bardzo gęsto)
            else -> 0.2
        }

        isLoading = true
        val rawBeats = beatMap.getBeats()
        leftBeats.clear()
        rightBeats.clear()
        var lastTime = -1.0

        val songRandom = Random(baseName.hashCode()) // randomowo dobiera nuty

        rawBeats.forEach { beat ->
            if (beat.time - lastTime >= minInterval) {
                beatsLeft++

                val lane = songRandom.nextInt(2)

                if (lane == 0) {
                    leftBeats.add(beat)
                } else {
                    rightBeats.add(beat)
                }

                lastTime = beat.time
            }
        }

        // do obliczenia ile całości (aby nie zmieniało w czasie)
        beatsToHit = beatsLeft


        val startDelaySeconds = 3.0
        var musicStarted = false
        var actualPlayStarted = false
        audioPlayer.prepare() // "Rozgrzewamy" player tutaj
        var startTimeMillis = System.currentTimeMillis() // Upewnij się, że to jest zainicjowane przed pętlą

        isLoading = false

        while (!isGameOver) { // Przenosimy warunek końca gry tutaj
            withFrameMillis { frameTime ->
                if (!isPaused) {
                    val now = System.currentTimeMillis()

                    if (!musicStarted) {
                        // --- FAZA 1: ODLICZANIE ---
                        val elapsedSinceStart = (now - startTimeMillis) / 1000.0
                        if (elapsedSinceStart < startDelaySeconds) {
                            currentTime = (elapsedSinceStart - startDelaySeconds).toDouble()
                        } else {
                            Thread {
                                audioPlayer.play()
                            }.start()
                            musicStarted = true
                        }

                    } else {
                        // --- FAZA 2: ODTWARZANIE ---
                        // Sprawdzamy, czy player faktycznie zaczął wydawać dźwięk
                        if (!actualPlayStarted && audioPlayer.isActive()) {
                            actualPlayStarted = true
                        }

                        if (actualPlayStarted) {
                            // Po fizycznym starcie czas bierzemy tylko z AudioPlayer
                            currentTime = audioPlayer.timeSeconds.toDouble()
                        } else {
                            // Czekamy na buforowanie (nuta stoi na zerze)
                            currentTime = 0.0
                        }
                    }

                    // --- AKTUALIZACJA ISKIER ---
                    val delta = 0.016f
                    val sparkIterator = sparks.iterator()
                    while (sparkIterator.hasNext()) {
                        val s = sparkIterator.next()
                        s.update(delta)
                        if (s.life <= 0) sparkIterator.remove()
                    }

                    // --- LOGIKA TRAFIEŃ I KOŃCA GRY ---
                    hitWaves.removeAll { currentTime - it.time > 0.5 }

                    if (musicStarted && currentTime >= audioPlayer.durationSeconds && audioPlayer.durationSeconds > 0) {
                        isGameOver = true
                    }
                } else {
                    // --- LOGIKA PAUZY ---
                    // Przesuwamy startTimeMillis o czas trwania pauzy,
                    // aby elapsedSinceStart stało w miejscu względem currentTime
                    startTimeMillis = System.currentTimeMillis() - ((currentTime + startDelaySeconds) * 1000).toLong()
                }
            }
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
            if (currentTime < 0 && !isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    val countdown = ceil(abs(currentTime)).toInt()
                    Text(
                        text = if (countdown > 0) "$countdown" else "GO!",
                        style = TextStyle(
                            color = Color.White,
                            fontSize = 120.sp,
                            fontWeight = FontWeight.Black,
                            shadow = Shadow(Color.Cyan, blurRadius = 20f)
                        )
                    )
                }
            }
        }
    } else if (isGameOver) {
        EndScreen(score, beatenBeats, leftBeats.size + rightBeats.size + beatenBeats, onBackToMenu)
    } else {
        BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
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
                // --- 6. RYSOWANIE ISKIER ---
                sparks.forEach { spark ->
                    val trailLength = 0.6f
                    val start = Offset(spark.x, spark.y)
                    val end = Offset(spark.x - spark.vx * trailLength, spark.y - spark.vy * trailLength)

                    // 1. Warstwa "Glow" (szeroka, kolorowa poświata)
                    drawLine(
                        color = spark.color.copy(alpha = spark.life * 0.5f),
                        start = start,
                        end = end,
                        strokeWidth = 8f * spark.life, // Grubsza
                        cap = StrokeCap.Round
                    )

                    // 2. Warstwa "Core" (cienki, biały środek-efekt gorąca)
                    drawLine(
                        color = Color.White.copy(alpha = spark.life),
                        start = start,
                        end = end,
                        strokeWidth = 2f * spark.life, // Cienka
                        cap = StrokeCap.Round
                    )
                }
            }

            // --- WARSTWA ODLICZANIA (NA WIERZCHU) ---
            if (!isLoading && currentTime < 0) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    val secondsLeft = ceil(abs(currentTime)).toInt()

                    // Wyświetlamy 3, 2, 1, a potem "GO!" na ułamek sekundy
                    val displayText = if (secondsLeft > 0) "$secondsLeft" else "READY?"

                    Text(
                        text = displayText,
                        modifier = Modifier.offset(x = (+100).dp),
                        style = TextStyle(
                            color = Color.White,
                            fontSize = 120.sp,
                            fontWeight = FontWeight.Black,
                            shadow = androidx.compose.ui.graphics.Shadow(
                                color = Color.Cyan.copy(alpha = 0.8f),
                                blurRadius = 40f
                            )
                        )
                    )
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
                Text("beats: $beatenBeats / $beatsToHit", color = Color(0xFFFF00FF), fontSize = 18.sp, fontWeight = FontWeight.Bold)
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
            Box(
                Modifier
                    .fillMaxSize()
                    .focusRequester(focusRequester)
                    .focusable()
                    .onKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown) {

                            val w = constraints.maxWidth.toFloat()
                            val h = constraints.maxHeight.toFloat()

                            val spreadX = w * 0.2f
                            val targetYPos = h * 0.85f
                            val leftT = (w / 2f) - spreadX + moveGameToRight
                            val rightT = (w / 2f) + spreadX + moveGameToRight

                            when (event.key) {
                                settings.leftKey -> {
                                    checkHit(leftBeats, "left", leftT, targetYPos)
                                    true
                                }
                                settings.rightKey -> {
                                    checkHit(rightBeats, "right", rightT, targetYPos)
                                    true
                                }
                                Key.Spacebar -> {
                                    isPaused = !isPaused
                                    if (isPaused) audioPlayer.pause() else audioPlayer.resume()
                                    true
                                }
                                else -> false
                            }
                        } else false
            })
            LaunchedEffect(Unit) { focusRequester.requestFocus() }
        }
    }
}