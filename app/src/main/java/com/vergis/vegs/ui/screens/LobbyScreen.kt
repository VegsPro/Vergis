package com.vergis.vegs.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.vergis.vegs.R
import com.vergis.vegs.ui.components.GlassCard
import com.vergis.vegs.ui.theme.*
import com.vergis.vegs.viewmodel.FriendsViewModel
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
    val leaveToast by viewModel.leaveToast.collectAsState()
    val friendsVm: FriendsViewModel = viewModel()
    val friends by friendsVm.friends.collectAsState()
    var showInvite by remember { mutableStateOf(false) }
    val snack = remember { SnackbarHostState() }

    LaunchedEffect(roomId) { viewModel.observe(roomId) }
    LaunchedEffect(Unit) { friendsVm.start() }
    LaunchedEffect(room?.status) {
        if (room?.status == "playing") onGameStarted()
    }
    LaunchedEffect(leaveToast) {
        leaveToast?.let {
            snack.showSnackbar(it)
            viewModel.clearLeaveToast()
        }
    }

    val pulse = rememberInfiniteTransition(label = "pulse")
    val alpha by pulse.animateFloat(
        0.4f, 1f,
        infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "a"
    )

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            TopAppBar(
                title = { Text(room?.name ?: "Лобби", color = TextPrimary, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.leave(roomId, "left")
                        onLeave()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = TextPrimary)
                    }
                },
                actions = {
                    if (room != null && room!!.status == "waiting") {
                        IconButton(onClick = { showInvite = true }) {
                            Icon(Icons.Default.PersonAdd, null, tint = BlueBright)
                        }
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
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth()) {
                    Text("Тема: ${r.theme}", color = TextPrimary, fontWeight = FontWeight.Medium)
                    Text(
                        "Игроки ${r.players.size} / ${r.maxPlayers}",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    if (r.hidden) {
                        Text("Скрытая комната", color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                    if (r.friendsOnly) {
                        Text("Только для друзей", color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                    }
                    Spacer(Modifier.height(10.dp))
                    if (r.players.size < r.maxPlayers) {
                        Text(
                            "Ожидаем ещё ${r.maxPlayers - r.players.size}…",
                            color = BlueSoft,
                            fontSize = 13.sp,
                            modifier = Modifier.alpha(alpha)
                        )
                    } else {
                        Text("Все собрались — запускаем", color = Success, fontWeight = FontWeight.Medium)
                    }
                }
            }

            if (r.players.size < r.maxPlayers) {
                Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                    Image(
                        painter = painterResource(R.drawable.penguin_wait),
                        contentDescription = null,
                        modifier = Modifier.size(120.dp),
                        contentScale = ContentScale.Fit
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Text("Игроки", color = TextPrimary, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(r.players.values.toList(), key = { it.uid }) { p ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xCC0A1628))
                            .border(1.dp, GlassStroke, RoundedCornerShape(14.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(listOf(Color(0xFF1E3A8A), BluePrimary)))
                                .border(1.dp, GlassStroke, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (p.avatarUrl.isNotBlank()) {
                                AsyncImage(
                                    p.avatarUrl, null,
                                    Modifier.fillMaxSize().clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Text(
                                    p.name.take(1).uppercase().ifBlank { "?" },
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(p.name, color = TextPrimary, fontWeight = FontWeight.Medium)
                            if (p.uid == r.creatorId) {
                                Text("создатель", color = BlueSoft, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showInvite) {
        AlertDialog(
            onDismissRequest = { showInvite = false },
            title = { Text("Пригласить друга", color = TextPrimary) },
            text = {
                if (friends.isEmpty()) {
                    Text("Нет друзей — добавь в разделе Друзья", color = TextSecondary)
                } else {
                    Column {
                        friends.forEach { f ->
                            TextButton(
                                onClick = {
                                    friendsVm.inviteToRoom(f.uid, roomId, room?.name ?: "Комната") {
                                        showInvite = false
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(f.name.ifBlank { f.uid.take(6) }, color = BlueBright)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showInvite = false }) {
                    Text("Закрыть", color = TextMuted)
                }
            },
            containerColor = Color(0xFF0D1B2A)
        )
    }
}
