package com.vergis.vegs.ui.screens

import android.Manifest
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import com.vergis.vegs.R
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.vergis.vegs.data.ChatMessage
import com.vergis.vegs.data.PlayerInfo
import com.vergis.vegs.ui.components.PrimaryGradientButton
import com.vergis.vegs.ui.theme.*
import com.vergis.vegs.viewmodel.AuthViewModel
import com.vergis.vegs.viewmodel.GameViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(
    roomId: String,
    viewModel: GameViewModel,
    onLeave: () -> Unit
) {
    val room by viewModel.room.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val chat by viewModel.chat.collectAsState()
    val authVm: AuthViewModel = viewModel()
    val displayName by authVm.displayName.collectAsState()
    val avatarUrl by authVm.avatarUrl.collectAsState()
    val myUid = viewModel.currentUid
    val view = LocalView.current

    var answerText by remember { mutableStateOf("") }
    var hasSubmitted by remember { mutableStateOf(false) }
    var chatOpen by remember { mutableStateOf(false) }
    var chatInput by remember { mutableStateOf("") }
    var chatSeenCount by remember { mutableIntStateOf(0) }
    val chatUnread = (chat.size - chatSeenCount).coerceAtLeast(0)
    LaunchedEffect(chatOpen, chat.size) {
        if (chatOpen) chatSeenCount = chat.size
    }

    val snackbarHostState = remember { SnackbarHostState() }
    var lastAnswerCount by remember { mutableIntStateOf(-1) }

    val notifPerm = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) {
            notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LifecycleResumeEffect(Unit) {
        viewModel.setForeground(true)
        onPauseOrDispose { viewModel.setForeground(false) }
    }

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
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            val msg = if (answerCount >= playerCount) "Все ответили · $answerCount/$playerCount"
            else "Ответили $answerCount из $playerCount"
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Short)
        } else lastAnswerCount = answerCount
    }

    val r = room
    val allAnswered = r != null && r.players.isNotEmpty() && r.answers.size >= r.players.size
    val iAmReady = r?.readyForNext?.get(myUid) == true
    val readyCount = r?.readyForNext?.size ?: 0
    
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF050B18), Color(0xFF0A1628), Color(0xFF050B18))))
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        if (r == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = BlueBright)
            }
            return@Box
        }

        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            // Top bar
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircleIconButton(onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    viewModel.leave(roomId)
                    onLeave()
                }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = TextPrimary, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Раунд ${r.currentRound}", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Text(r.theme, color = BlueSoft, fontSize = 13.sp)
                }
                Box {
                    CircleIconButton(onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        val opening = !chatOpen
                        chatOpen = opening
                        if (opening) chatSeenCount = chat.size
                    }) {
                        Icon(Icons.Default.ChatBubbleOutline, null, tint = TextPrimary, modifier = Modifier.size(20.dp))
                    }
                    if (chatUnread > 0 && !chatOpen) {
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(BluePrimary)
                        )
                    }
                }
            }

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 6.dp)
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
                modifier = Modifier.padding(bottom = 12.dp)
            )

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
                Spacer(Modifier.height(10.dp))
                Text(
                    r.currentQuestion.ifBlank { "Генерируем вопрос…" },
                    color = TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 24.sp
                )
            }

            Spacer(Modifier.height(14.dp))

            if (!allAnswered) {
                val alreadyAnswered = r.answers.containsKey(myUid)
                if (alreadyAnswered || hasSubmitted) {
                    GlowCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(40.dp).clip(CircleShape).border(2.dp, BlueBright, CircleShape),
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
                            modifier = Modifier.fillMaxWidth().padding(top = 14.dp).height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color = BluePrimary,
                            trackColor = Color(0x33FFFFFF)
                        )
                    }
                    Spacer(Modifier.weight(1f))
                } else {
                    OutlinedTextField(
                        value = answerText,
                        onValueChange = {
                            if (it.length > answerText.length) {
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            }
                            answerText = it
                        },
                        label = { Text("Твой ответ") },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 110.dp),
                        maxLines = 5,
                        shape = RoundedCornerShape(16.dp),
                        colors = fieldColors()
                    )
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.foundation.Image(
                            painter = painterResource(R.drawable.penguin_think),
                            contentDescription = null,
                            modifier = Modifier.size(140.dp)
                        )
                    }
                    PrimaryGradientButton(
                        text = "Отправить ответ",
                        enabled = answerText.isNotBlank(),
                        onClick = {
                            viewModel.submitAnswer(roomId, answerText)
                            hasSubmitted = true
                        }
                    )
                    Spacer(Modifier.height(12.dp))
                }
            } else {
                Text("Ответы", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Spacer(Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
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
                            Spacer(Modifier.height(4.dp))
                            Text(ans, color = TextPrimary, fontSize = 15.sp, lineHeight = 22.sp)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                if (iAmReady) {
                    Text(
                        "Готовы: $readyCount / ${r.players.size}",
                        color = Success,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                    if (loading) {
                        CircularProgressIndicator(
                            color = BlueBright,
                            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp).size(28.dp),
                            strokeWidth = 2.dp
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                } else {
                    PrimaryGradientButton(
                        text = "Следующий раунд",
                        onClick = { viewModel.setReady(roomId) }
                    )
                    Spacer(Modifier.height(12.dp))
                }
            }
        }

        // Chat overlay panel
        AnimatedVisibility(
            visible = chatOpen,
            enter = slideInHorizontally { it } + fadeIn(),
            exit = slideOutHorizontally { it } + fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            ChatPanel(
                messages = chat,
                input = chatInput,
                onInputChange = { chatInput = it },
                onSend = {
                    viewModel.sendChat(roomId, chatInput, displayName.ifBlank { "Игрок" }, avatarUrl)
                    chatInput = ""
                },
                onClose = { chatSeenCount = chat.size; chatOpen = false },
                myUid = myUid
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 64.dp).padding(horizontal = 16.dp)
        ) { data ->
            Snackbar(
                snackbarData = data,
                containerColor = Color(0xEE0D1B2A),
                contentColor = TextPrimary,
                shape = RoundedCornerShape(14.dp)
            )
        }
    }
}

@Composable
private fun ChatPanel(
    messages: List<ChatMessage>,
    input: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onClose: () -> Unit,
    myUid: String?
) {
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }
    Column(
        Modifier
            .fillMaxHeight()
            .width(300.dp)
            .background(Color(0xF00A1220))
            .border(1.dp, GlassStroke)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Чат", color = TextPrimary, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, null, tint = TextSecondary)
            }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages, key = { it.id }) { m ->
                Row(verticalAlignment = Alignment.Top) {
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(Color(0xFF1E3A8A), BluePrimary))),
                        contentAlignment = Alignment.Center
                    ) {
                        if (m.avatarUrl.isNotBlank()) {
                            AsyncImage(m.avatarUrl, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        } else {
                            Text(m.name.take(1).uppercase(), color = Color.White, fontSize = 12.sp)
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            m.name + if (m.uid == myUid) " · ты" else "",
                            color = BlueSoft,
                            fontSize = 11.sp
                        )
                        Text(m.text, color = TextPrimary, fontSize = 14.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = input,
            onValueChange = onInputChange,
            placeholder = { Text("сообщение…", color = TextMuted) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = fieldColors(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSend() }),
            trailingIcon = {
                IconButton(onClick = onSend, enabled = input.isNotBlank()) {
                    Icon(Icons.AutoMirrored.Filled.Send, null, tint = if (input.isNotBlank()) BlueBright else TextMuted)
                }
            }
        )
    }
}

@Composable
private fun CircleIconButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(Color(0x33FFFFFF))
            .border(1.dp, GlassStroke, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
        content = { content() }
    )
}

@Composable
private fun GlowCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xCC0A1628))
            .border(
                1.dp,
                Brush.linearGradient(listOf(Color(0x663B82F6), Color(0x22A5C8FF), Color(0x443B82F6))),
                RoundedCornerShape(18.dp)
            )
            .padding(16.dp),
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
    val pair = colors[kotlin.math.abs(player.uid.hashCode()) % colors.size]
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(pair.first, pair.second)))
            .border(2.dp, if (answered) Success else Color.White.copy(0.25f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (player.avatarUrl.isNotBlank()) {
            AsyncImage(player.avatarUrl, null, Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
        } else {
            Text(player.name.take(1).uppercase().ifBlank { "?" }, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
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
