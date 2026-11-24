package com.tailgunnerx.listgen

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

const val GITHUB_URL = "https://github.com/tailgunnerx/ListGen"

private val DialogSurface = Color(0xFF18181C)
private val CardSurface = Color(0xFF222228)
private val SubtleText = Color(0xFF9E9EAE)
private val DividerHairline = Color(0x22FFFFFF)
private val DialogShape = RoundedCornerShape(22.dp)
private val SectionCardShape = RoundedCornerShape(12.dp)

@Immutable
data class ChangelogEntry(
    val version: String,
    val date: String,
    val notes: String
)

val CHANGELOG = listOf(
    ChangelogEntry(
        version = "1.0",
        date = "2026-11-23",
        notes = "• Grid & compact list views with long-press drag-and-drop reordering\n" +
            "• Detailed outcome tracking (Done / Failed) with expandable Success Notes & Failure Reasons\n" +
            "• Per-list neon hue customization, Zen Mode, Lock protection & Disco Rave Mode\n" +
            "• Multi-select batch lock/delete with one-tap Undo\n" +
            "• High-performance Compose draw-phase animations & debounced background DataStore vault\n" +
            "• App Settings popup with separate Changelog window & GitHub Repository link"
    )
)

@Composable
fun AppSettingsDialog(
    onOpenChangelog: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val versionName = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull() ?: CHANGELOG.first().version
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(DialogShape)
                .background(DialogSurface)
                .border(1.dp, DividerHairline, DialogShape)
        ) {
            // Title bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Settings",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(CardSurface)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Text("✕", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "About",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(10.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(SectionCardShape)
                        .background(CardSurface)
                ) {
                    AboutRow(
                        title = "Changelog",
                        subtitle = "View release notes & version history (v$versionName)",
                        onClick = onOpenChangelog
                    ) {
                        Text(
                            text = "View",
                            color = SettingsAccent,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    HorizontalDivider(
                        color = DividerHairline,
                        modifier = Modifier.padding(horizontal = 14.dp)
                    )
                    AboutRow(
                        title = "GitHub Repository",
                        subtitle = GITHUB_URL.removePrefix("https://"),
                        subtitleColor = SettingsAccent,
                        onClick = {
                            onDismiss()
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_URL)))
                        }
                    ) {
                        Icon(
                            imageVector = OpenInNewIcon,
                            contentDescription = "Open GitHub Repository",
                            tint = SettingsAccent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // Footer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Close", color = SettingsAccent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun AboutRow(
    title: String,
    subtitle: String,
    subtitleColor: Color = SubtleText,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(text = subtitle, color = subtitleColor, fontSize = 11.sp)
        }
        trailing()
    }
}

@Composable
fun ChangelogDialog(onBack: () -> Unit) {
    AlertDialog(
        onDismissRequest = onBack,
        containerColor = DialogSurface,
        titleContentColor = Color.White,
        textContentColor = SubtleText,
        title = {
            Text("Changelog", style = MaterialTheme.typography.titleLarge, color = Color.White)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CHANGELOG.forEachIndexed { index, entry ->
                    if (index > 0) {
                        HorizontalDivider(color = DividerHairline)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "v${entry.version}",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White
                            )
                            Text(
                                text = entry.date,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (index == 0) SettingsAccent else SubtleText
                            )
                        }
                        Text(
                            text = entry.notes,
                            style = MaterialTheme.typography.bodySmall,
                            color = SubtleText
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onBack) {
                Text("Back", color = SettingsAccent)
            }
        }
    )
}
