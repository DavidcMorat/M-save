package com.example.ui.browser.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MeowAmber
import com.example.ui.theme.MeowCardBg
import com.example.ui.theme.MeowCardBorder
import com.example.ui.theme.MeowGraphite
import com.example.ui.theme.MeowTextMuted
import com.example.ui.theme.MeowTextPrimary
import com.example.ui.theme.MeowTextSecondary

@Composable
fun BrowserAddressBar(
    currentUrl: String,
    isLoading: Boolean,
    loadingProgress: Int,
    canGoBack: Boolean,
    canGoForward: Boolean,
    isBookmarked: Boolean,
    onNavigate: (String) -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onReload: () -> Unit,
    onHome: () -> Unit,
    onToggleBookmark: () -> Unit,
    modifier: Modifier = Modifier
) {
    var textFieldValue by remember(currentUrl) { mutableStateOf(currentUrl) }
    val focusManager = LocalFocusManager.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MeowGraphite)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Home Screen Button
            IconButton(
                onClick = onHome,
                modifier = Modifier
                    .size(38.dp)
                    .testTag("browser_home_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Home,
                    contentDescription = "Pantalla de inicio limpia",
                    tint = MeowAmber,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Navigation Back Button
            IconButton(
                onClick = onBack,
                enabled = canGoBack,
                modifier = Modifier
                    .size(38.dp)
                    .testTag("browser_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Retroceder",
                    tint = if (canGoBack) MeowTextPrimary else MeowTextMuted,
                    modifier = Modifier.size(19.dp)
                )
            }

            // Navigation Forward Button
            IconButton(
                onClick = onForward,
                enabled = canGoForward,
                modifier = Modifier
                    .size(38.dp)
                    .testTag("browser_forward_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Avanzar",
                    tint = if (canGoForward) MeowTextPrimary else MeowTextMuted,
                    modifier = Modifier.size(19.dp)
                )
            }

            // Address / Search Input Field
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp)
                    .clip(RoundedCornerShape(21.dp))
                    .background(MeowCardBg)
                    .border(1.dp, MeowCardBorder, RoundedCornerShape(21.dp))
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = if (textFieldValue.startsWith("https")) Icons.Default.Lock else Icons.Default.Search,
                        contentDescription = "Seguridad",
                        tint = if (textFieldValue.startsWith("https")) MeowAmber else MeowTextMuted,
                        modifier = Modifier.size(15.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    BasicTextField(
                        value = textFieldValue,
                        onValueChange = { textFieldValue = it },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("browser_address_input"),
                        singleLine = true,
                        textStyle = TextStyle(
                            color = MeowTextPrimary,
                            fontSize = 13.5.sp
                        ),
                        cursorBrush = SolidColor(MeowAmber),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Go
                        ),
                        keyboardActions = KeyboardActions(
                            onGo = {
                                focusManager.clearFocus()
                                onNavigate(textFieldValue)
                            }
                        ),
                        decorationBox = { innerTextField ->
                            if (textFieldValue.isEmpty()) {
                                Text(
                                    text = "Buscar o escribir URL...",
                                    color = MeowTextMuted,
                                    fontSize = 13.5.sp
                                )
                            }
                            innerTextField()
                        }
                    )

                    if (textFieldValue.isNotEmpty()) {
                        IconButton(
                            onClick = { textFieldValue = "" },
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Limpiar texto",
                                tint = MeowTextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(2.dp))

            // Bookmark Button
            IconButton(
                onClick = onToggleBookmark,
                modifier = Modifier
                    .size(38.dp)
                    .testTag("browser_bookmark_button")
            ) {
                Icon(
                    imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                    contentDescription = "Guardar marcador",
                    tint = if (isBookmarked) MeowAmber else MeowTextSecondary,
                    modifier = Modifier.size(19.dp)
                )
            }

            // Reload Button
            IconButton(
                onClick = onReload,
                modifier = Modifier
                    .size(38.dp)
                    .testTag("browser_reload_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Recargar página",
                    tint = MeowTextPrimary,
                    modifier = Modifier.size(19.dp)
                )
            }
        }

        // Web Loading Progress Bar
        AnimatedVisibility(visible = isLoading) {
            LinearProgressIndicator(
                progress = { loadingProgress / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.5.dp),
                color = MeowAmber,
                trackColor = MeowGraphite
            )
        }
    }
}
