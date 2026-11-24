package com.tailgunnerx.listgen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

private fun sampleCampingList() = SList(
    id = "camping",
    title = "Camping Trip",
    accentColorArgb = Color(0xFF00E5FF).toArgb(),
    trackCompletion = true,
    failuresCountAsProgress = true,
    showReasonOnSuccess = true,
    showReasonOnFailure = true,
    items = listOf(
        ListItem(
            id = "c1",
            text = "Pack 4-season tent & stakes",
            status = ItemStatus.DONE,
            reason = "Waterproof seams checked & all 12 aluminum stakes packed."
        ),
        ListItem(
            id = "c2",
            text = "Reserve Pine Ridge site #14",
            status = ItemStatus.FAILED,
            reason = "Site #14 booked out — grabbed lakeside Site #9 instead."
        ),
        ListItem(
            id = "c3",
            text = "Charge 20,000mAh power bank",
            status = ItemStatus.DONE,
            reason = "Topped off to 100% + braided USB-C cable."
        ),
        ListItem(
            id = "c4",
            text = "Pre-chill cooler & meal prep",
            status = ItemStatus.DONE,
            reason = ""
        ),
        ListItem(
            id = "c5",
            text = "Download offline trail maps",
            status = ItemStatus.NONE
        ),
        ListItem(
            id = "c6",
            text = "Check tire pressure & spare",
            status = ItemStatus.NONE
        )
    )
)

private fun sampleLists(): List<SList> = listOf(
    sampleCampingList(),
    SList(
        id = "pc_build",
        title = "PC Build",
        accentColorArgb = Color(0xFFE040FB).toArgb(),
        trackCompletion = true,
        items = listOf(
            ListItem(text = "Mount Gen5 NVMe SSD", status = ItemStatus.DONE),
            ListItem(text = "Flash latest motherboard BIOS", status = ItemStatus.DONE),
            ListItem(text = "Enable EXPO 6000MT/s profile", status = ItemStatus.DONE),
            ListItem(text = "Run 30m stability stress test", status = ItemStatus.DONE)
        )
    ),
    SList(
        id = "app_launch",
        title = "App Launch",
        accentColorArgb = Color(0xFFFF9100).toArgb(),
        trackCompletion = true,
        raveMode = true,
        items = listOf(
            ListItem(text = "120Hz drag-and-drop polish", status = ItemStatus.DONE),
            ListItem(text = "R8 shrink & ProGuard rules", status = ItemStatus.DONE),
            ListItem(text = "Strip icon metadata", status = ItemStatus.DONE),
            ListItem(text = "Capture README screenshots", status = ItemStatus.NONE),
            ListItem(text = "Tag v1.0 GitHub release", status = ItemStatus.NONE)
        )
    ),
    SList(
        id = "morning",
        title = "Daily Routine",
        accentColorArgb = Color(0xFF00FF9C).toArgb(),
        trackCompletion = true,
        isLocked = true,
        items = listOf(
            ListItem(text = "Hydrate + electrolytes", status = ItemStatus.DONE),
            ListItem(text = "30m Zone 2 run", status = ItemStatus.DONE),
            ListItem(text = "Cold brew & reading", status = ItemStatus.DONE),
            ListItem(text = "Review daily top 3 priorities", status = ItemStatus.NONE)
        )
    ),
    SList(
        id = "watchlist",
        title = "Watchlist",
        accentColorArgb = Color(0xFF7C4DFF).toArgb(),
        trackCompletion = false,
        items = listOf(
            ListItem(text = "Dune: Part Two", status = ItemStatus.DONE),
            ListItem(text = "Blade Runner 2049", status = ItemStatus.DONE),
            ListItem(text = "Arrival", status = ItemStatus.NONE),
            ListItem(text = "Ex Machina", status = ItemStatus.NONE),
            ListItem(text = "Interstellar", status = ItemStatus.NONE)
        )
    )
)

@Composable
private fun PreviewThemeWrapper(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF00B0FF),
            background = Color(0xFF0F0F0F),
            surface = Color(0xFF1E1E1E)
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF0F0F0F)
        ) {
            content()
        }
    }
}

@Preview(name = "1_GridDashboard", widthDp = 412, heightDp = 892, showBackground = true, backgroundColor = 0xFF0F0F0F)
@Composable
fun GridDashboardPreview() {
    val vm = remember { ListViewModel(vaultManager = null, initialLists = sampleLists(), initialGridView = true) }
    PreviewThemeWrapper {
        MainScreen(viewModel = vm)
    }
}

@Preview(name = "2_OutcomeTracking", widthDp = 412, heightDp = 892, showBackground = true, backgroundColor = 0xFF0F0F0F)
@Composable
fun OutcomeTrackingPreview() {
    val camping = remember { sampleCampingList() }
    val vm = remember { ListViewModel(vaultManager = null, initialLists = listOf(camping), initialGridView = true) }
    PreviewThemeWrapper {
        DetailScreen(
            list = camping,
            viewModel = vm,
            onBack = {},
            onUpdate = {}
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(name = "3_ListCustomization", widthDp = 412, heightDp = 892, showBackground = true, backgroundColor = 0xFF0F0F0F)
@Composable
fun CustomizationSheetPreview() {
    val camping = remember { sampleCampingList().copy(raveMode = true) }
    val vm = remember { ListViewModel(vaultManager = null, initialLists = listOf(camping), initialGridView = true) }
    PreviewThemeWrapper {
        Box(modifier = Modifier.fillMaxSize()) {
            DetailScreen(
                list = camping,
                viewModel = vm,
                onBack = {},
                onUpdate = {}
            )
            // Scrim + anchored ModalBottomSheet preview matching live Android sheet
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.58f))
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(Color(0xFF1E1E1E)),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                BottomSheetDefaults.DragHandle(color = Color.Gray)
                ListCustomizationContent(
                    list = camping,
                    onDismiss = {},
                    onUpdate = {}
                )
            }
        }
    }
}
