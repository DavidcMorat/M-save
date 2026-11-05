package com.example.ui.browser.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.engine.model.MediaExtractionResult
import com.example.engine.model.MediaFormatOption
import com.example.ui.theme.MeowAmber
import com.example.ui.theme.MeowBlack
import com.example.ui.theme.MeowCardBg
import com.example.ui.theme.MeowCardBorder
import com.example.ui.theme.MeowCyan
import com.example.ui.theme.MeowEmerald
import com.example.ui.theme.MeowGraphite
import com.example.ui.theme.MeowTextMuted
import com.example.ui.theme.MeowTextPrimary
import com.example.ui.theme.MeowTextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormatPickerBottomSheet(
    extractionResult: MediaExtractionResult?,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onPreview: (MediaFormatOption) -> Unit,
    onStartDownload: (MediaFormatOption) -> Unit
) {
    if (extractionResult == null) return

    var selectedFormat by remember(extractionResult) {
        mutableStateOf(extractionResult.formats.firstOrNull())
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MeowGraphite,
        contentColor = MeowTextPrimary,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .navigationBarsPadding()
        ) {
            // Header: Title & Close Button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        tint = MeowAmber,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Opciones de Contenido",
                        color = MeowTextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cerrar modal",
                        tint = MeowTextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Media Preview Card
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MeowCardBg)
                    .border(1.dp, MeowCardBorder, RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (extractionResult.thumbnail != null) {
                    AsyncImage(
                        model = extractionResult.thumbnail,
                        contentDescription = "Miniatura del contenido",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MeowBlack)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = extractionResult.title,
                        color = MeowTextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = extractionResult.author ?: "Fuente Web",
                        color = MeowCyan,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Formatos y calidades disponibles:",
                color = MeowTextSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Formats List
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
            ) {
                items(extractionResult.formats) { format ->
                    val isSelected = selectedFormat?.formatId == format.formatId

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) MeowCardBg else MeowBlack)
                            .border(
                                1.5.dp,
                                if (isSelected) MeowAmber else MeowCardBorder,
                                RoundedCornerShape(10.dp)
                            )
                            .clickable { selectedFormat = format }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { selectedFormat = format },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = MeowAmber,
                                unselectedColor = MeowTextMuted
                            )
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Icon(
                            imageVector = if (format.isAudioOnly) Icons.Default.Audiotrack else Icons.Default.Videocam,
                            contentDescription = null,
                            tint = if (format.isAudioOnly) MeowCyan else MeowAmber,
                            modifier = Modifier.size(20.dp)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (format.isAudioOnly) "Audio (${format.ext.uppercase()})" else "${format.resolution} (${format.ext.uppercase()})",
                                color = MeowTextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = format.formatNote,
                                color = MeowTextSecondary,
                                fontSize = 12.sp
                            )
                        }

                        Text(
                            text = format.displaySize,
                            color = MeowEmerald,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Action Buttons: Preview Native & Download
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Native Player Preview Button
                OutlinedButton(
                    onClick = {
                        selectedFormat?.let { onPreview(it) }
                    },
                    enabled = selectedFormat != null,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MeowCyan),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MeowCyan
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("preview_player_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Vista Previa",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Download Button
                Button(
                    onClick = {
                        selectedFormat?.let { onStartDownload(it) }
                    },
                    enabled = selectedFormat != null,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MeowAmber,
                        contentColor = MeowBlack,
                        disabledContainerColor = MeowCardBorder,
                        disabledContentColor = MeowTextMuted
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1.2f)
                        .height(50.dp)
                        .testTag("confirm_download_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Descargar",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
