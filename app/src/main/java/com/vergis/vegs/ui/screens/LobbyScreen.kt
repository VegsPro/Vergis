package com.vergis.vegs.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vergis.vegs.ui.components.GlassCard
import com.vergis.vegs.ui.theme.*
import com.vergis.vegs.viewmodel.GameViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LobbyScreen(
    roomId: String,
    viewModel: GameViewModel,
    onGameStarted: () -> Unit,
    onLeave: () -> Unit
) {
    val room by viewModel.room.collectAsState()
    val loading by viewModel.loading.collectAsState()

    LaunchedEffect(roomId) { viewModel.observe(roomId) }
    LaunchedEffect(room?.status) {
        if (room?.status == "playing") onGameStarted()
    }

    val pulse = rememberInfiniteTransition(label = "pulse")
    val alpha by pulse.animateFloat(
        0.4f, 1f,
        infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "a"
    )

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(room?.name ?: "Лобби", color = TextPrimary, fontWeight = FontWeight.SemiBold) },
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
        val r = room
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
            GlassCard(modifier = Modifier.fillMaxWidth(), radius = 18.dp) {
                Column {
                    Text("Тема", color = TextMuted, fontSize = 12.sp)
                    Text(r.theme, color = BlueBright, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Игроков: ${r.players.size} / ${r.maxPlayers}",
                        color = TextPrimary
                    )
                    if (r.players.size < r.maxPlayers) {
                        Text(
                            "Ожидаем ещё ${r.maxPlayers - r.players.size}…",
                            color = TextSecondary,
                            fontSize = 13.sp,
                            modifier = Modifier.alpha(alpha)
                        )
                    } else {
                        Text("Все собрались — запускаем", color = Success, fontWeight = FontWeight.Medium)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Text("Игроки", color = TextPrimary, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(r.players.values.toList()) { player ->
                    GlassCard(modifier = Modifier.fillMaxWidth(), radius = 14.dp, contentPadding = PaddingValues(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(listOf(Color(0xFF1E3A8A), BluePrimary))
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    player.name.take(1).uppercase(),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(player.name, color = TextPrimary, fontWeight = FontWeight.Medium)
                            if (player.uid == r.creatorId) {
                                Spacer(Modifier.width(8.dp))
                                Text("хост", color = Warning, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            if (loading) {
                Spacer(Modifier.height(28.dp))
                CircularProgressIndicator(
                    color = BlueBright,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Text(
                    "Gemini придумывает первый вопрос…",
                    color = TextSecondary,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 10.dp)
                )
            }
        }
    }
}
