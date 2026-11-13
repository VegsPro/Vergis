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
            GlassCard(modifier = Modifier.fillMaxWidth(), radius = 18.dp) {
                Column {
                    Text("Имя комнаты", color = TextMuted, fontSize = 12.sp)
                    Text(
                        displayName.ifBlank { "Игрок" },
                        color = BlueBright,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            Text("Игроки", color = TextPrimary, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (2..5).forEach { n ->
                    FilterChip(
                        selected = maxPlayers == n,
                        onClick = { maxPlayers = n },
                        label = { Text("$n") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = BluePrimary,
                            selectedLabelColor = Color.White,
                            containerColor = BgCardSolid,
                            labelColor = TextSecondary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = GlassStroke,
                            selectedBorderColor = BlueBright,
                            enabled = true,
                            selected = maxPlayers == n
                        )
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            Text("Тема", color = TextPrimary, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            DEFAULT_THEMES.forEach { t ->
                FilterChip(
                    selected = !useCustom && theme == t,
                    onClick = { useCustom = false; theme = t },
                    label = { Text(t) },
                    modifier = Modifier.padding(end = 6.dp, bottom = 6.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = BluePrimary,
                        selectedLabelColor = Color.White,
                        containerColor = BgCardSolid,
                        labelColor = TextSecondary
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        borderColor = GlassStroke,
                        selectedBorderColor = BlueBright,
                        enabled = true,
                        selected = !useCustom && theme == t
                    )
                )
            }
            FilterChip(
                selected = useCustom,
                onClick = { useCustom = true },
                label = { Text("Своя тема") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = BluePrimary,
                    selectedLabelColor = Color.White,
                    containerColor = BgCardSolid,
                    labelColor = TextSecondary
                ),
                border = FilterChipDefaults.filterChipBorder(
                    borderColor = GlassStroke,
                    selectedBorderColor = BlueBright,
                    enabled = true,
                    selected = useCustom
                )
            )
            if (useCustom) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = customTheme,
                    onValueChange = { customTheme = it },
                    label = { Text("Напиши тему") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = fieldColors()
                )
            }

            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = usePassword,
                    onCheckedChange = { usePassword = it },
                    colors = CheckboxDefaults.colors(checkedColor = BluePrimary)
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
                    shape = RoundedCornerShape(14.dp),
                    colors = fieldColors()
                )
            }

            if (error != null) {
                Text(error!!, color = Error, modifier = Modifier.padding(top = 12.dp))
            }

            Spacer(Modifier.height(28.dp))
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
            Spacer(Modifier.height(24.dp))
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
    unfocusedTextColor = TextPrimary
)
