package com.vergis.vegs.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import com.vergis.vegs.ui.components.GlassCard
import com.vergis.vegs.ui.components.PrimaryGradientButton
import com.vergis.vegs.ui.theme.*
import com.vergis.vegs.viewmodel.GameViewModel

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

    LaunchedEffect(roomId) { viewModel.observe(roomId) }
    LaunchedEffect(room?.currentRound) {
        answerText = ""
        hasSubmitted = false
        lastAnswerCount = -1
    }

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
            snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Short)
        } else {
            lastAnswerCount = answerCount
        }
    }

    val r = room
    val allAnswered = r != null && r.players.isNotEmpty() && r.answers.size >= r.players.size
    val iAmReady = r?.readyForNext?.get(myUid) == true
    val readyCount = r?.readyForNext?.size ?: 0

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = BgCardSolid,
                    contentColor = TextPrimary,
                    shape = RoundedCornerShape(14.dp)
                )
            }
        },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Раунд ${r?.currentRound ?: 0}", color = TextPrimary, fontWeight = FontWeight.Bold)
                        Text(r?.theme ?: "", color = BlueSoft, fontSize = 12.sp)
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        if (r == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = BlueBright)
            }
            return@Scaffold
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            AnimatedVisibility(visible = !allAnswered && r.status == "playing", enter = fadeIn(), exit = fadeOut()) {
                Text(
                    "Ответили ${r.answers.size} из ${r.players.size}",
                    color = BlueBright,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 10.dp)
                )
            }

            GlassCard(modifier = Modifier.fillMaxWidth(), radius = 20.dp) {
                Column {
                    Text("Вопрос", color = TextMuted, fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        r.currentQuestion.ifBlank { "Генерируем вопрос…" },
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 26.sp
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            if (!allAnswered) {
                val alreadyAnswered = r.answers.containsKey(myUid)
                if (alreadyAnswered || hasSubmitted) {
                    GlassCard(modifier = Modifier.fillMaxWidth(), radius = 16.dp) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                "Ответ отправлен",
                                color = Success,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "Ждём остальных (${r.answers.size}/${r.players.size})",
                                color = TextSecondary,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                            LinearProgressIndicator(
                                progress = { r.answers.size.toFloat() / r.players.size.coerceAtLeast(1) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 14.dp)
                                    .height(6.dp),
                                color = BluePrimary,
                                trackColor = GlassStroke
                            )
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = answerText,
                        onValueChange = { answerText = it },
                        label = { Text("Твой ответ") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp),
                        maxLines = 6,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BlueBright,
                            unfocusedBorderColor = GlassStroke,
                            focusedLabelColor = BlueSoft,
                            unfocusedLabelColor = TextMuted,
                            cursorColor = BlueBright,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedContainerColor = Color(0x220B1730),
                            unfocusedContainerColor = Color(0x180B1730)
                        )
                    )
                    Spacer(Modifier.height(14.dp))
                    PrimaryGradientButton(
                        text = "Отправить ответ",
                        enabled = answerText.isNotBlank(),
                        onClick = {
                            viewModel.submitAnswer(roomId, answerText)
                            hasSubmitted = true
                        }
                    )
                }
            } else {
                Text("Ответы", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Spacer(Modifier.height(10.dp))
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(r.players.values.toList()) { player ->
                        val ans = r.answers[player.uid] ?: "—"
                        GlassCard(modifier = Modifier.fillMaxWidth(), radius = 14.dp) {
                            Column {
                                Text(
                                    player.name + if (player.uid == myUid) " · ты" else "",
                                    color = BlueSoft,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 12.sp
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(ans, color = TextPrimary, fontSize = 15.sp, lineHeight = 22.sp)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                if (iAmReady) {
                    Text(
                        "Готовы: $readyCount / ${r.players.size}",
                        color = Success,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                    if (loading) {
                        CircularProgressIndicator(
                            color = BlueBright,
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(top = 10.dp)
                                .size(28.dp),
                            strokeWidth = 2.dp
                        )
                    }
                } else {
                    PrimaryGradientButton(
                        text = "Следующий раунд",
                        onClick = { viewModel.setReady(roomId) }
                    )
                }
            }
        }
    }
}
