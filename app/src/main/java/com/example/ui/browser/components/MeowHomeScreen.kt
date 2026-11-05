package com.example.ui.browser.components

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MeowAmber
import com.example.ui.theme.MeowAmberDark
import com.example.ui.theme.MeowAmberGlow
import com.example.ui.theme.MeowBlack
import com.example.ui.theme.MeowCardBg
import com.example.ui.theme.MeowCardBorder
import com.example.ui.theme.MeowCyan
import com.example.ui.theme.MeowEmerald
import com.example.ui.theme.MeowGraphite
import com.example.ui.theme.MeowTextMuted
import com.example.ui.theme.MeowTextPrimary
import com.example.ui.theme.MeowTextSecondary

data class QuickShortcut(
    val name: String,
    val url: String,
    val icon: ImageVector,
    val color: Color
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MeowHomeScreen(
    isResolvingMedia: Boolean,
    statusMessage: String?,
    onSubmit: (String) -> Unit,
    onShortcutClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var inputText by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current

    val shortcuts = listOf(
        QuickShortcut("YouTube", "https://m.youtube.com", Icons.Default.PlayArrow, Color(0xFFFF0000)),
        QuickShortcut("TikTok", "https://www.tiktok.com", Icons.Default.Videocam, MeowCyan),
        QuickShortcut("Instagram", "https://www.instagram.com", Icons.Default.OndemandVideo, Color(0xFFE1306C)),
        QuickShortcut("Twitter / X", "https://x.com", Icons.Default.Share, Color(0xFF1DA1F2)),
        QuickShortcut("Facebook", "https://m.facebook.com", Icons.Default.Language, Color(0xFF1877F2)),
        QuickShortcut("SoundCloud", "https://m.soundcloud.com", Icons.Default.MusicNote, Color(0xFFFF5500))
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MeowBlack)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // App Emblem with Glowing Halo
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(MeowAmber.copy(alpha = 0.25f), Color.Transparent)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(MeowCardBg, MeowGraphite)
                        )
                    )
                    .border(1.5.dp, MeowAmber, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "MeowSave",
                    tint = MeowAmberGlow,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "MeowSave",
            color = MeowTextPrimary,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Navega, busca o pega un enlace multimedia",
            color = MeowTextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Normal
        )

        Spacer(modifier = Modifier.height(30.dp))

        // Clean Pill-Shaped Search & URL Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .shadow(elevation = 8.dp, shape = CircleShape)
                .clip(CircleShape)
                .background(MeowCardBg)
                .border(1.5.dp, if (inputText.isNotEmpty()) MeowAmber else MeowCardBorder, CircleShape)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Magnifying glass icon in amber
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Buscar o ingresar URL",
                    tint = MeowAmber,
                    modifier = Modifier.size(22.dp)
                )

                Spacer(modifier = Modifier.width(10.dp))

                BasicTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("home_pill_input"),
                    singleLine = true,
                    textStyle = TextStyle(
                        color = MeowTextPrimary,
                        fontSize = 15.sp
                    ),
                    cursorBrush = SolidColor(MeowAmber),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Go
                    ),
                    keyboardActions = KeyboardActions(
                        onGo = {
                            if (inputText.isNotBlank()) {
                                focusManager.clearFocus()
                                onSubmit(inputText)
                            }
                        }
                    ),
                    decorationBox = { innerTextField ->
                        if (inputText.isEmpty()) {
                            Text(
                                text = "Buscar en la web o pegar enlace...",
                                color = MeowTextMuted,
                                fontSize = 14.sp
                            )
                        }
                        innerTextField()
                    }
                )

                // Quick Paste button from clipboard when empty
                if (inputText.isEmpty()) {
                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            val clipData = clipboard?.primaryClip
                            if (clipData != null && clipData.itemCount > 0) {
                                val pasted = clipData.getItemAt(0)?.text?.toString() ?: ""
                                if (pasted.isNotBlank()) {
                                    inputText = pasted
                                    onSubmit(pasted)
                                }
                            }
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("paste_clipboard_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentPaste,
                            contentDescription = "Pegar desde portapapeles",
                            tint = MeowCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else {
                    // Clear text button
                    IconButton(
                        onClick = { inputText = "" },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Limpiar texto",
                            tint = MeowTextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Action Go Button
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MeowAmber)
                            .clickable {
                                if (inputText.isNotBlank()) {
                                    focusManager.clearFocus()
                                    onSubmit(inputText)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Continuar",
                            tint = MeowBlack,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // Status or resolving loader indicator
        AnimatedVisibility(visible = isResolvingMedia) {
            Spacer(modifier = Modifier.height(18.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(
                    color = MeowAmber,
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = statusMessage ?: "Analizando enlace multimedia...",
                    color = MeowAmber,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(36.dp))

        // Quick Shortcuts Section Header
        Text(
            text = "Accesos Rápidos",
            color = MeowTextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.Start)
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Quick Shortcuts FlowRow
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            shortcuts.forEach { shortcut ->
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(MeowCardBg)
                        .border(1.dp, MeowCardBorder, RoundedCornerShape(12.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = true, color = MeowAmber),
                            onClick = { onShortcutClick(shortcut.url) }
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = shortcut.icon,
                        contentDescription = shortcut.name,
                        tint = shortcut.color,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = shortcut.name,
                        color = MeowTextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
