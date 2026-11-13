package com.vergis.vegs.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vergis.vegs.ui.components.GlassCard
import com.vergis.vegs.ui.components.PrimaryGradientButton
import com.vergis.vegs.ui.components.SoftSecondaryButton
import com.vergis.vegs.ui.theme.*
import com.vergis.vegs.viewmodel.AuthViewModel

@Composable
fun AuthScreen(
    viewModel: AuthViewModel,
    onSuccess: () -> Unit
) {
    var isLogin by remember { mutableStateOf(true) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }

    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()
    val loggedIn by viewModel.isLoggedIn.collectAsState()

    LaunchedEffect(loggedIn) {
        if (loggedIn) onSuccess()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF06101F), Color(0xFF0A1A33), Color(0xFF071220))
                )
            )
    ) {
        // soft blue glow
        Box(
            Modifier
                .fillMaxWidth()
                .height(320.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0x553B82F6), Color.Transparent)
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(72.dp))
            Text(
                "Vergis",
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                "Вопросы, которые сближают",
                color = TextSecondary,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 6.dp, bottom = 36.dp)
            )

            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                radius = 24.dp,
                contentPadding = PaddingValues(22.dp)
            ) {
                Column {
                    AnimatedContent(
                        targetState = isLogin,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "authMode"
                    ) { login ->
                        Text(
                            if (login) "С возвращением" else "Создать аккаунт",
                            color = TextPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(Modifier.height(18.dp))

                    if (!isLogin) {
                        AuthField(
                            value = name,
                            onValueChange = { name = it },
                            label = "Имя",
                            keyboardType = KeyboardType.Text
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                    AuthField(
                        value = email,
                        onValueChange = { email = it },
                        label = "Email",
                        keyboardType = KeyboardType.Email
                    )
                    Spacer(Modifier.height(12.dp))
                    AuthField(
                        value = password,
                        onValueChange = { password = it },
                        label = "Пароль",
                        keyboardType = KeyboardType.Password,
                        isPassword = true
                    )

                    if (error != null) {
                        Text(
                            error!!,
                            color = Error,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                    }

                    Spacer(Modifier.height(22.dp))
                    PrimaryGradientButton(
                        text = if (loading) "..." else if (isLogin) "Войти" else "Зарегистрироваться",
                        enabled = !loading && email.isNotBlank() && password.length >= 6,
                        onClick = {
                            viewModel.clearError()
                            if (isLogin) viewModel.login(email, password)
                            else viewModel.register(email, password, name)
                        }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            SoftSecondaryButton(
                text = if (isLogin) "Нет аккаунта? Создать" else "Уже есть аккаунт? Войти",
                onClick = {
                    isLogin = !isLogin
                    viewModel.clearError()
                }
            )
        }
    }
}

@Composable
private fun AuthField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType,
    isPassword: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (isPassword) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
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
    )
}
