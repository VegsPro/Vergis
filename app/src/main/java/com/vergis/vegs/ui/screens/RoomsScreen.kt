package com.vergis.vegs.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vergis.vegs.data.RoomInfo
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
    onLogout: () -> Unit
) {
    val rooms by viewModel.rooms.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val joinError by viewModel.joinError.collectAsState()
    val displayName by authViewModel.displayName.collectAsState()

    var passwordDialogRoom by remember { mutableStateOf<RoomInfo?>(null) }
    var passwordInput by remember { mutableStateOf("") }

    Scaffold(
        containerColor = DarkBg,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Vergis", color = Accent, fontWeight = FontWeight.Bold)
                        Text(
                            "Привет, ${displayName.ifBlank { "Игрок" }}",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onLogout) {
                        Icon(Icons.Default.Logout, contentDescription = "Выйти", tint = TextSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreateRoom,
                containerColor = Accent,
                contentColor = Color.Black
            ) {
                Icon(Icons.Default.Add, contentDescription = "Создать комнату")
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (loading) {
                CircularProgressIndicator(
                    color = Accent,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else if (rooms.isEmpty()) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Нет активных комнат", color = TextSecondary)
                    Text("Создай первую!", color = TextSecondary, fontSize = 13.sp)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(rooms, key = { it.id }) { room ->
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

            if (joinError != null) {
                LaunchedEffect(joinError) {
                    // show briefly
                }
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    action = {
                        TextButton(onClick = { viewModel.clearJoinError() }) {
                            Text("OK", color = Accent)
                        }
                    }
                ) {
                    Text(joinError!!)
                }
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
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Accent,
                        unfocusedBorderColor = BorderLight,
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
                }) {
                    Text("Войти", color = Accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { passwordDialogRoom = null }) {
                    Text("Отмена", color = TextSecondary)
                }
            },
            containerColor = SurfaceGlass
        )
    }
}

@Composable
private fun RoomCard(room: RoomInfo, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = SurfaceGlass),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = room.name,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 17.sp
                    )
                    if (room.password.isNotEmpty()) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            tint = Warning,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Text(
                    text = room.theme,
                    color = Accent,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
                Text(
                    text = if (room.status == "playing") "Идёт игра • раунд ${room.currentRound}" else "Ожидание",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.People, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
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
