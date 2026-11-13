package com.vergis.vegs.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vergis.vegs.ui.theme.*
import com.vergis.vegs.viewmodel.GameViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(
    roomId: String,
    viewModel: GameViewModel,
    onLeave: () -> Unit
) {
    val room by viewModel.room.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val myUid = viewModel.currentUid

    var answerText by remember { mutableStateOf("") }
    var hasSubmitted by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    var lastAnswerCount by remember { mutableIntStateOf(-1) }

    LaunchedEffect(roomId) {
        viewModel.observe(roomId)
    }

    LaunchedEffect(room?.currentRound) {
        answerText = ""
        hasSubmitted = false
        lastAnswerCount = -1
    }

    // In-app уведомление: N из M ответили — при каждом новом ответе
    val answerCount = room?.answers?.size ?: 0
    val playerCount = room?.players?.size ?: 0
    LaunchedEffect(answerCount, playerCount, room?.currentRound) {
        if (playerCount <= 0) return@LaunchedEffect
        if (lastAnswerCount < 0) {
            lastAnswerCount = answerCount
            return@LaunchedEffect
        }
        if (answerCount > lastAnswerCount && answerCount > 0) {
            lastAnswerCount = answerCount
            val msg = if (answerCount >= playerCount) {
                "Все ответили! $answerCount из $playerCount"
            } else {
                "Ответили $answerCount из $playerCount"
            }
            snackbarHostState.showSnackbar(
                message = msg,
                duration = SnackbarDuration.Short
            )
        } else {
            lastAnswerCount = answerCount
        }
    }

    val r = room
    val allAnswered = r != null && r.players.isNotEmpty() && r.answers.size >= r.players.size
    val iAmReady = r?.readyForNext?.get(myUid) == true
    val readyCount = r?.readyForNext?.size ?: 0

    Scaffold(
        containerColor = DarkBg,
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = SurfaceGlass,
                    contentColor = TextPrimary,
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Раунд ${r?.currentRound ?: 0}", color = TextPrimary, fontWeight = FontWeight.Bold)
                        Text(r?.theme ?: "", color = TextSecondary, fontSize = 12.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.leave(roomId)
                        onLeave()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg)
            )
        }
    ) { padding ->
        if (r == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Accent)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // Живой счётчик ответов
            if (!allAnswered && r.status == "playing") {
                Text(
                    text = "Ответили ${r.answers.size} из ${r.players.size}",
                    color = Accent,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceGlass),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text("Вопрос", color = TextSecondary, fontSize = 12.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        r.currentQuestion.ifBlank { "Генерируем вопрос..." },
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            if (!allAnswered) {
                val alreadyAnswered = r.answers.containsKey(myUid)
                if (alreadyAnswered || hasSubmitted) {
                    Text(
                        "Ответ отправлен. Ждём остальных (${r.answers.size}/${r.players.size})...",
                        color = TextSecondary,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                    LinearProgressIndicator(
                        progress = { r.answers.size.toFloat() / r.players.size.coerceAtLeast(1) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                            .height(6.dp),
                        color = Accent,
                        trackColor = BorderLight
                    )
                } else {
                    OutlinedTextField(
                        value = answerText,
                        onValueChange = { answerText = it },
                        label = { Text("Твой ответ") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 100.dp),
                        maxLines = 5,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Accent,
                            unfocusedBorderColor = BorderLight,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            if (answerText.isNotBlank()) {
                                viewModel.submitAnswer(roomId, answerText)
                                hasSubmitted = true
                            }
                        },
                        enabled = answerText.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Accent),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Отправить ответ", color = Color.Black, fontWeight = FontWeight.SemiBold)
                    }
                }
            } else {
                Text(
                    "Ответы всех игроков",
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
                Spacer(Modifier.height(10.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(r.players.values.toList()) { player ->
                        val ans = r.answers[player.uid] ?: "—"
                        Card(
                            colors = CardDefaults.cardColors(containerColor = SurfaceGlass),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(14.dp)) {
                                Text(
                                    player.name + if (player.uid == myUid) " (ты)" else "",
                                    color = Accent,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 13.sp
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(ans, color = TextPrimary, fontSize = 15.sp)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                if (iAmReady) {
                    Text(
                        "Готовы к следующему: $readyCount / ${r.players.size}",
                        color = Success,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                    if (loading) {
                        CircularProgressIndicator(
                            color = Accent,
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(top = 8.dp)
                                .size(28.dp),
                            strokeWidth = 2.dp
                        )
                        Text(
                            "Генерируем следующий вопрос...",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                } else {
                    Button(
                        onClick = { viewModel.setReady(roomId) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Accent),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(
                            "Следующий раунд",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
