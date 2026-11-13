package com.vergis.vegs.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vergis.vegs.data.DEFAULT_THEMES
import com.vergis.vegs.ui.theme.*
import com.vergis.vegs.viewmodel.AuthViewModel
import com.vergis.vegs.viewmodel.GameViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateRoomScreen(
    onCreated: (String) -> Unit,
    onBack: () -> Unit
) {
    val gameVm: GameViewModel = viewModel()
    val authVm: AuthViewModel = viewModel()
    val displayName by authVm.displayName.collectAsState()
    val loading by gameVm.loading.collectAsState()
    val error by gameVm.error.collectAsState()

    var maxPlayers by remember { mutableIntStateOf(3) }
    var theme by remember { mutableStateOf(DEFAULT_THEMES[1]) }
    var customTheme by remember { mutableStateOf("") }
    var useCustom by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var usePassword by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = DarkBg,
        topBar = {
            TopAppBar(
                title = { Text("Новая комната", color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
                .verticalScroll(rememberScrollState())
        ) {
            Text("Имя комнаты = твоё имя", color = TextSecondary, fontSize = 13.sp)
            Text(
                displayName.ifBlank { "Игрок" },
                color = Accent,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 20.dp)
            )

            Text("Количество игроков", color = TextPrimary, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (2..5).forEach { n ->
                    FilterChip(
                        selected = maxPlayers == n,
                        onClick = { maxPlayers = n },
                        label = { Text("$n") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Accent,
                            selectedLabelColor = Color.Black
                        )
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            Text("Тема / тип", color = TextPrimary, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            DEFAULT_THEMES.forEach { t ->
                FilterChip(
                    selected = !useCustom && theme == t,
                    onClick = {
                        useCustom = false
                        theme = t
                    },
                    label = { Text(t) },
                    modifier = Modifier.padding(end = 6.dp, bottom = 6.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Accent,
                        selectedLabelColor = Color.Black
                    )
                )
            }
            FilterChip(
                selected = useCustom,
                onClick = { useCustom = true },
                label = { Text("Своя тема") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Accent,
                    selectedLabelColor = Color.Black
                )
            )
            if (useCustom) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = customTheme,
                    onValueChange = { customTheme = it },
                    label = { Text("Напиши свою тему") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Accent,
                        unfocusedBorderColor = BorderLight,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
            }

            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = usePassword,
                    onCheckedChange = { usePassword = it },
                    colors = CheckboxDefaults.colors(checkedColor = Accent)
                )
                Text("Пароль (необязательно)", color = TextPrimary)
            }
            if (usePassword) {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Пароль") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Accent,
                        unfocusedBorderColor = BorderLight,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
            }

            if (error != null) {
                Text(error!!, color = Error, modifier = Modifier.padding(top = 12.dp))
            }

            Spacer(Modifier.height(28.dp))
            Button(
                onClick = {
                    val finalTheme = if (useCustom && customTheme.isNotBlank()) customTheme else theme
                    gameVm.createRoom(
                        name = displayName.ifBlank { "Игрок" },
                        maxPlayers = maxPlayers,
                        theme = finalTheme,
                        password = if (usePassword) password else "",
                        onCreated = onCreated
                    )
                },
                enabled = !loading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent),
                shape = RoundedCornerShape(14.dp)
            ) {
                if (loading) {
                    CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    Text("Создать комнату", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
