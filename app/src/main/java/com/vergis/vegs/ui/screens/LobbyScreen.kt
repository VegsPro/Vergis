package com.vergis.vegs.ui.screens

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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    LaunchedEffect(roomId) {
        viewModel.observe(roomId)
    }

    LaunchedEffect(room?.status) {
        if (room?.status == "playing") {
            onGameStarted()
        }
    }

    Scaffold(
        containerColor = DarkBg,
        topBar = {
            TopAppBar(
                title = { Text(room?.name ?: "Лобби", color = TextPrimary) },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            val r = room
            if (r == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Accent)
                }
                return@Column
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceGlass),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Тема: ${r.theme}", color = Accent, fontWeight = FontWeight.Medium)
                    Text(
                        "Игроков: ${r.players.size} / ${r.maxPlayers}",
                        color = TextPrimary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    if (r.players.size < r.maxPlayers) {
                        Text(
                            "Ожидаем ещё ${r.maxPlayers - r.players.size}...",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                    } else {
                        Text(
                            "Все собрались! Запускаем игру...",
                            color = Success,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Text("Игроки", color = TextPrimary, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(r.players.values.toList()) { player ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SurfaceGlass, RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Accent.copy(alpha = 0.3f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                player.name.take(1).uppercase(),
                                color = Accent,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(player.name, color = TextPrimary)
                        if (player.uid == r.creatorId) {
                            Spacer(Modifier.width(8.dp))
                            Text("хост", color = Warning, fontSize = 11.sp)
                        }
                    }
                }
            }

            if (loading) {
                Spacer(Modifier.height(24.dp))
                CircularProgressIndicator(
                    color = Accent,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Text(
                    "Gemini придумывает первый вопрос...",
                    color = TextSecondary,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp)
                )
            }
        }
    }
}
