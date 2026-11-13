package com.vergis.vegs.ui.screens

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vergis.vegs.data.PlayerInfo
import com.vergis.vegs.ui.components.PrimaryGradientButton
import com.vergis.vegs.ui.theme.*
import com.vergis.vegs.viewmodel.GameViewModel
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale

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
    val view = LocalView.current
    val keyboard = LocalSoftwareKeyboardController.current

    var answerText by remember { mutableStateOf("") }
    var hasSubmitted by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    var lastAnswerCount by remember { mutableIntStateOf(-1) }

    LaunchedEffect(roomId) { viewModel.observe(roomId) }
    LaunchedEffect(room?.currentRound) {
        answerText = ""
        hasSubmitted = false
        lastAnswerCount = -1
        keyboard?.hide()
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
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            val msg = if (answerCount >= playerCount) {
                "Все ответили · $answerCount/$playerCount"
            } else {
                "Ответили $answerCount из $playerCount"
            }
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(
                message = msg,
                duration = SnackbarDuration.Short
            )
        } else {
            lastAnswerCount = answerCount
        }
    }

    // комната удалена / таймаут
    LaunchedEffect(room) {
        if (room == null) {
            // после leave или delete
        }
    }

    val r = room
    val allAnswered = r != null && r.players.isNotEmpty() && r.answers.size >= r.players.size
    val iAmReady = r?.readyForNext?.get(myUid) == true
    val readyCount = r?.readyForNext?.size ?: 0

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF050B18), Color(0xFF0A1628), Color(0xFF050B18))
                )
            )
            .imePadding() // snackbar/кнопки над клавиатурой
    ) {
        if (r == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = BlueBright)
            }
            return@Box
        }

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 16.dp)
        ) {
            // Top bar: circular back + title
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color(0x33FFFFFF))
                        .border(1.dp, GlassStroke, CircleShape)
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            viewModel.leave(roomId)
                            onLeave()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        null,
                        tint = TextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        "Раунд ${r.currentRound}",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp
                    )
                    Text(r.theme, color = BlueSoft, fontSize = 13.sp)
                }
            }

            // Player avatars
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                items(r.players.values.toList()) { player ->
                    PlayerAvatar(player, answered = r.answers.containsKey(player.uid))
                }
            }

            Text(
                "Ответили ${r.answers.size} из ${r.players.size}",
                color = BlueBright,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 14.dp)
            )

            // Question card with glow border
            GlowCard {
                Text(
                    "Вопрос",
                    color = TextMuted,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x22FFFFFF))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    r.currentQuestion.ifBlank { "Генерируем вопрос…" },
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 26.sp
                )
            }

            Spacer(Modifier.height(16.dp))

            if (!allAnswered) {
                val alreadyAnswered = r.answers.containsKey(myUid)
                if (alreadyAnswered || hasSubmitted) {
                    GlowCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .border(2.dp, BlueBright, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Check, null, tint = BlueBright)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("Ответ отправлен", color = Success, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "Ждём остальных (${r.answers.size}/${r.players.size})",
                                    color = TextSecondary,
                                    fontSize = 13.sp
                                )
                            }
                        }
                        LinearProgressIndicator(
                            progress = { r.answers.size.toFloat() / r.players.size.coerceAtLeast(1) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 14.dp)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = BluePrimary,
                            trackColor = Color(0x33FFFFFF)
                        )
                    }
                } else {
                    OutlinedTextField(
                        value = answerText,
                        onValueChange = { new ->
                            if (new.length > answerText.length) {
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            }
                            answerText = new
                        },
                        label = { Text("Твой ответ") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp),
                        maxLines = 6,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        shape = RoundedCornerShape(18.dp),
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
                            keyboard?.hide()
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
                        GlowCard {
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

        // Snackbar ABOVE content / keyboard, not covering bottom buttons permanently
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 72.dp)
                .padding(horizontal = 16.dp)
        ) { data ->
            Snackbar(
                snackbarData = data,
                containerColor = Color(0xEE0D1B2A),
                contentColor = TextPrimary,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.border(1.dp, GlassStroke, RoundedCornerShape(14.dp))
            )
        }
    }
}

@Composable
private fun GlowCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xCC0A1628))
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    listOf(Color(0x663B82F6), Color(0x22A5C8FF), Color(0x443B82F6))
                ),
                shape = RoundedCornerShape(20.dp)
            )
            .padding(18.dp),
        content = content
    )
}

@Composable
private fun PlayerAvatar(player: PlayerInfo, answered: Boolean) {
    val colors = listOf(
        Color(0xFF1E3A8A) to Color(0xFF3B82F6),
        Color(0xFF0E7490) to Color(0xFF22D3EE),
        Color(0xFF5B21B6) to Color(0xFFA78BFA),
        Color(0xFF9D174D) to Color(0xFFF472B6)
    )
    val pair = colors[player.uid.hashCode().mod(colors.size).let { if (it < 0) it + colors.size else it }]
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(pair.first, pair.second)))
            .border(
                width = 2.dp,
                color = if (answered) Success else Color.White.copy(alpha = 0.25f),
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        if (player.avatarUrl.isNotBlank()) {
            AsyncImage(
                model = player.avatarUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(
                player.name.take(1).uppercase().ifBlank { "?" },
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }
    }
}
