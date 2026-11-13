package com.vergis.vegs.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vergis.vegs.data.RoomInfo
import com.vergis.vegs.ui.components.GlassCard
import com.vergis.vegs.ui.theme.*
import com.vergis.vegs.viewmodel.AuthViewModel
import com.vergis.vegs.viewmodel.RoomsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomsScreen(
    viewModel: RoomsViewModel,
    authViewModel: AuthViewModel,
    onCreateRoom: () -> Unit,
    onJoinRoom: (String) -> Unit,
    onLogout: () -> Unit,
    onProfile: () -> Unit
) {
    val rooms by viewModel.rooms.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val joinError by viewModel.joinError.collectAsState()
    val displayName by authViewModel.displayName.collectAsState()

    var passwordDialogRoom by remember { mutableStateOf<RoomInfo?>(null) }

    LaunchedEffect(joinError) {
        if (joinError != null) {
            kotlinx.coroutines.delay(3500)
            viewModel.clearJoinError()
        }
    }
    var passwordInput by remember { mutableStateOf("") }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Vergis", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                        Text(
                            "Привет, ${displayName.ifBlank { "Игрок" }}",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                },
                actions = {
                    TextButton(onClick = onProfile) {
                        Text("Профиль", color = BlueBright, fontSize = 13.sp)
                    }
                    IconButton(onClick = onLogout) {
                        Icon(Icons.Default.Logout, null, tint = TextSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreateRoom,
                containerColor = BluePrimary,
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, "Создать")
            }
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                loading -> CircularProgressIndicator(
                    color = BlueBright,
                    modifier = Modifier.align(Alignment.Center)
                )
                rooms.isEmpty() -> Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Нет активных комнат", color = TextSecondary, fontSize = 16.sp)
                    Text("Создай первую →", color = TextMuted, fontSize = 13.sp)
                }
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    itemsIndexed(rooms, key = { _, r -> r.id }) { index, room ->
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn() + slideInVertically { it / 4 }
                        ) {
                            RoomCard(
                                room = room,
                                onClick = {
                                    if (room.password.isNotEmpty()) {
                                        passwordDialogRoom = room
                                        passwordInput = ""
                                    } else {
                                        viewModel.joinRoom(room.id, "", displayName) {
                                            onJoinRoom(room.id)
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }

            if (joinError != null) {
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    containerColor = BgCardSolid,
                    contentColor = TextPrimary,
                    action = {
                        TextButton(onClick = { viewModel.clearJoinError() }) {
                            Text("OK", color = BlueBright)
                        }
                    }
                ) { Text(joinError!!) }
            }
        }
    }

    if (passwordDialogRoom != null) {
        AlertDialog(
            onDismissRequest = { passwordDialogRoom = null },
            title = { Text("Пароль комнаты", color = TextPrimary) },
            text = {
                OutlinedTextField(
                    value = passwordInput,
                    onValueChange = { passwordInput = it },
                    label = { Text("Пароль") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BlueBright,
                        unfocusedBorderColor = GlassStroke,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val r = passwordDialogRoom!!
                    viewModel.joinRoom(r.id, passwordInput, displayName) {
                        passwordDialogRoom = null
                        onJoinRoom(r.id)
                    }
                }) { Text("Войти", color = BlueBright) }
            },
            dismissButton = {
                TextButton(onClick = { passwordDialogRoom = null }) {
                    Text("Отмена", color = TextSecondary)
                }
            },
            containerColor = BgCardSolid
        )
    }
}

@Composable
private fun RoomCard(room: RoomInfo, onClick: () -> Unit) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        radius = 18.dp
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF1E3A8A), Color(0xFF3B82F6))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    room.name.take(1).uppercase(),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        room.name,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                    if (room.password.isNotEmpty()) {
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Default.Lock, null, tint = Warning, modifier = Modifier.size(14.dp))
                    }
                }
                Text(room.theme, color = BlueSoft, fontSize = 13.sp)
                Text(
                    if (room.status == "playing") "Игра · раунд ${room.currentRound}" else "Ожидание",
                    color = TextMuted,
                    fontSize = 12.sp
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.People, null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    "${room.players.size}/${room.maxPlayers}",
                    color = TextPrimary,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
