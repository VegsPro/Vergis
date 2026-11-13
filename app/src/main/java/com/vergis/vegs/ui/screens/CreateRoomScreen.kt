package com.vergis.vegs.ui.screens

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
import com.vergis.vegs.ui.components.GlassCard
import com.vergis.vegs.ui.components.PrimaryGradientButton
import com.vergis.vegs.ui.theme.*
import com.vergis.vegs.viewmodel.AuthViewModel
import com.vergis.vegs.viewmodel.GameViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Новая комната", color = TextPrimary, fontWeight = FontWeight.SemiBold) },
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            GlassCard(modifier = Modifier.fillMaxWidth(), radius = 16.dp, contentPadding = PaddingValues(14.dp)) {
                Text("Имя комнаты", color = TextMuted, fontSize = 11.sp)
                Text(
                    displayName.ifBlank { "Игрок" },
                    color = BlueBright,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(14.dp))
            Text("Игроки", color = TextPrimary, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (2..5).forEach { n ->
                    FilterChip(
                        selected = maxPlayers == n,
                        onClick = { maxPlayers = n },
                        label = { Text("$n") },
                        colors = chipColors(maxPlayers == n),
                        border = chipBorder(maxPlayers == n)
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            Text("Тема", color = TextPrimary, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            Spacer(Modifier.height(6.dp))
            // Компактный FlowRow вместо вертикального списка
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                DEFAULT_THEMES.forEach { t ->
                    FilterChip(
                        selected = !useCustom && theme == t,
                        onClick = { useCustom = false; theme = t },
                        label = { Text(t, fontSize = 12.sp) },
                        colors = chipColors(!useCustom && theme == t),
                        border = chipBorder(!useCustom && theme == t)
                    )
                }
                FilterChip(
                    selected = useCustom,
                    onClick = { useCustom = true },
                    label = { Text("Своя", fontSize = 12.sp) },
                    colors = chipColors(useCustom),
                    border = chipBorder(useCustom)
                )
            }
            if (useCustom) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = customTheme,
                    onValueChange = { customTheme = it },
                    label = { Text("Своя тема") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = fieldColors()
                )
            }

            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = usePassword,
                    onCheckedChange = { usePassword = it },
                    colors = CheckboxDefaults.colors(checkedColor = BluePrimary)
                )
                Text("Пароль (необязательно)", color = TextPrimary, fontSize = 14.sp)
            }
            if (usePassword) {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Пароль") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = fieldColors()
                )
            }

            if (error != null) {
                Text(error!!, color = Error, modifier = Modifier.padding(top = 10.dp), fontSize = 13.sp)
            }

            Spacer(Modifier.height(20.dp))
            PrimaryGradientButton(
                text = if (loading) "Создаём..." else "Создать комнату",
                enabled = !loading,
                onClick = {
                    val finalTheme = if (useCustom && customTheme.isNotBlank()) customTheme else theme
                    gameVm.createRoom(
                        name = displayName.ifBlank { "Игрок" },
                        maxPlayers = maxPlayers,
                        theme = finalTheme,
                        password = if (usePassword) password else "",
                        onCreated = onCreated
                    )
                }
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun chipColors(selected: Boolean) = FilterChipDefaults.filterChipColors(
    selectedContainerColor = BluePrimary,
    selectedLabelColor = Color.White,
    containerColor = BgCardSolid,
    labelColor = TextSecondary
)

@Composable
private fun chipBorder(selected: Boolean) = FilterChipDefaults.filterChipBorder(
    borderColor = GlassStroke,
    selectedBorderColor = BlueBright,
    enabled = true,
    selected = selected
)

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = BlueBright,
    unfocusedBorderColor = GlassStroke,
    focusedLabelColor = BlueSoft,
    unfocusedLabelColor = TextMuted,
    cursorColor = BlueBright,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary
)
