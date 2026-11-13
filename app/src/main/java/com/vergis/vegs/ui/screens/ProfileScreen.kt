package com.vergis.vegs.ui.screens

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.os.Build
import android.provider.MediaStore
import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.vergis.vegs.ui.components.PrimaryGradientButton
import com.vergis.vegs.ui.theme.*
import com.vergis.vegs.viewmodel.AuthViewModel
import java.io.ByteArrayOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: AuthViewModel,
    onBack: () -> Unit
) {
    val displayName by viewModel.displayName.collectAsState()
    val avatarUrl by viewModel.avatarUrl.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()
    val view = LocalView.current
    val context = LocalContext.current

    var nameInput by remember { mutableStateOf("") }
    LaunchedEffect(displayName) {
        if (nameInput.isBlank() && displayName.isNotBlank()) nameInput = displayName
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            var bitmap = if (Build.VERSION.SDK_INT >= 28) {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.isMutableRequired = true
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            }
            // force software ARGB for compress
            if (bitmap.config != Bitmap.Config.ARGB_8888) {
                bitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
            }
            val maxSide = 512
            val scale = maxSide.toFloat() / maxOf(bitmap.width, bitmap.height)
            val w = (bitmap.width * scale).toInt().coerceAtLeast(1)
            val h = (bitmap.height * scale).toInt().coerceAtLeast(1)
            val scaled = Bitmap.createScaledBitmap(bitmap, w, h, true)
            val baos = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 82, baos)
            val bytes = baos.toByteArray()
            if (bytes.isEmpty()) throw Exception("Не удалось сжать фото")
            viewModel.uploadAvatar(bytes)
        } catch (e: Exception) {
            viewModel.setError(e.message ?: "Не удалось обработать фото")
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Профиль", color = TextPrimary, fontWeight = FontWeight.SemiBold) },
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
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(28.dp))

            // Avatar with camera badge OUTSIDE the clip
            Box(
                modifier = Modifier.size(120.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(Color(0xFF1E3A8A), BluePrimary)))
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            picker.launch("image/*")
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (avatarUrl.isNotBlank()) {
                        AsyncImage(
                            model = avatarUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(
                            (displayName.ifBlank { "?" }).take(1).uppercase(),
                            color = Color.White,
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                // camera badge — not clipped by avatar
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = (-6).dp, y = (-6).dp)
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(BluePrimary)
                        .border(2.dp, BgDeep, CircleShape)
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            picker.launch("image/*")
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.CameraAlt,
                        contentDescription = "Сменить фото",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Text(
                if (loading) "Загружаем…" else "Нажми, чтобы сменить фото",
                color = TextMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 12.dp, bottom = 28.dp)
            )

            Text("Имя", color = TextMuted, fontSize = 12.sp, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = nameInput,
                onValueChange = { nameInput = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BlueBright,
                    unfocusedBorderColor = Color(0x33FFFFFF),
                    cursorColor = BlueBright,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent
                )
            )

            if (error != null) {
                Text(
                    error!!,
                    color = Error,
                    modifier = Modifier.padding(top = 12.dp).fillMaxWidth(),
                    fontSize = 13.sp
                )
            }

            Spacer(Modifier.height(24.dp))
            PrimaryGradientButton(
                text = if (loading) "Сохраняем..." else "Сохранить",
                enabled = !loading && nameInput.isNotBlank(),
                onClick = { viewModel.updateName(nameInput) { onBack() } }
            )
        }
    }
}
