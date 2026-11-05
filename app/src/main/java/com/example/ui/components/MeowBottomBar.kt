package com.example.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MeowAmber
import com.example.ui.theme.MeowBlack
import com.example.ui.theme.MeowEmerald
import com.example.ui.theme.MeowGraphite
import com.example.ui.theme.MeowTextPrimary
import com.example.ui.theme.MeowTextSecondary

enum class MeowScreen(val title: String, val icon: ImageVector) {
    BROWSER("Navegador", Icons.Default.Language),
    DOWNLOADS("Descargas", Icons.Default.FileDownload),
    SETTINGS("Ajustes", Icons.Default.Settings)
}

@Composable
fun MeowBottomBar(
    currentScreen: MeowScreen,
    activeDownloadsCount: Int,
    onScreenSelected: (MeowScreen) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        containerColor = MeowGraphite,
        contentColor = MeowTextPrimary,
        modifier = modifier.testTag("meow_bottom_bar")
    ) {
        MeowScreen.entries.forEach { screen ->
            val isSelected = currentScreen == screen

            NavigationBarItem(
                selected = isSelected,
                onClick = { onScreenSelected(screen) },
                icon = {
                    if (screen == MeowScreen.DOWNLOADS && activeDownloadsCount > 0) {
                        BadgedBox(
                            badge = {
                                Badge(
                                    containerColor = MeowEmerald,
                                    contentColor = MeowBlack
                                ) {
                                    Text(
                                        text = activeDownloadsCount.toString(),
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        ) {
                            Icon(
                                imageVector = screen.icon,
                                contentDescription = screen.title,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    } else {
                        Icon(
                            imageVector = screen.icon,
                            contentDescription = screen.title,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
                label = {
                    Text(
                        text = screen.title,
                        fontSize = 12.sp,
                        color = if (isSelected) MeowAmber else MeowTextSecondary
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MeowBlack,
                    selectedTextColor = MeowAmber,
                    indicatorColor = MeowAmber,
                    unselectedIconColor = MeowTextSecondary,
                    unselectedTextColor = MeowTextSecondary
                ),
                modifier = Modifier.testTag("nav_item_${screen.name.lowercase()}")
            )
        }
    }
}
