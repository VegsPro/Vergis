package com.vergis.vegs.ui.screens

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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.vergis.vegs.ui.theme.*
import com.vergis.vegs.viewmodel.FriendsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsScreen(
    viewModel: FriendsViewModel,
    onBack: () -> Unit
) {
    val friends by viewModel.friends.collectAsState()
    val requests by viewModel.requests.collectAsState()
    val results by viewModel.searchResults.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val message by viewModel.message.collectAsState()
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { viewModel.start() }
    LaunchedEffect(message) {
        if (message != null) {
            kotlinx.coroutines.delay(2500)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Друзья", color = TextPrimary, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    if (it.length >= 2) viewModel.search(it) else viewModel.search("")
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                placeholder = { Text("Поиск по нику…", color = TextMuted) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = TextMuted) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BlueBright,
                    unfocusedBorderColor = GlassStroke,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = BlueBright
                )
            )

            if (message != null) {
                Text(message!!, color = BlueSoft, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
            }

            LazyColumn(
                Modifier.fillMaxSize().padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (requests.isNotEmpty()) {
                    item {
                        Text("Заявки", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                    items(requests, key = { "r" + it.uid }) { f ->
                        FriendRow(
                            name = f.name,
                            avatar = f.avatarUrl,
                            trailing = {
                                Row {
                                    TextButton(onClick = { viewModel.accept(f.uid) }) {
                                        Text("Принять", color = Success)
                                    }
                                    TextButton(onClick = { viewModel.reject(f.uid) }) {
                                        Text("Отклонить", color = Error)
                                    }
                                }
                            }
                        )
                    }
                }

                if (query.length >= 2) {
                    item {
                        Text("Поиск", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                            modifier = Modifier.padding(top = 8.dp))
                    }
                    if (loading) {
                        item { CircularProgressIndicator(color = BlueBright, modifier = Modifier.size(24.dp)) }
                    }
                    items(results, key = { "s" + it.uid }) { u ->
                        FriendRow(
                            name = u.displayName,
                            avatar = u.avatarUrl,
                            trailing = {
                                IconButton(onClick = { viewModel.addFriend(u.uid) }) {
                                    Icon(Icons.Default.PersonAdd, null, tint = BlueBright)
                                }
                            }
                        )
                    }
                    if (!loading && results.isEmpty()) {
                        item { Text("Никого не найдено", color = TextMuted, fontSize = 13.sp) }
                    }
                }

                item {
                    Text(
                        "Мои друзья (${friends.size})",
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                if (friends.isEmpty()) {
                    item { Text("Пока пусто — найди друзей по нику", color = TextMuted, fontSize = 13.sp) }
                }
                items(friends, key = { it.uid }) { f ->
                    FriendRow(
                        name = f.name.ifBlank { f.uid.take(6) },
                        avatar = f.avatarUrl,
                        trailing = {
                            TextButton(onClick = { viewModel.remove(f.uid) }) {
                                Text("Удалить", color = TextMuted, fontSize = 12.sp)
                            }
                        }
                    )
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun FriendRow(
    name: String,
    avatar: String,
    trailing: @Composable () -> Unit
) {
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
                .size(40.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Color(0xFF1E3A8A), BluePrimary))),
            contentAlignment = Alignment.Center
        ) {
            if (avatar.isNotBlank()) {
                AsyncImage(avatar, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Text(name.take(1).uppercase().ifBlank { "?" }, color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(name, color = TextPrimary, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        trailing()
    }
}
