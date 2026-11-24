package com.tailgunnerx.listgen

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.math.min
import kotlin.random.Random

// --- Shared Shapes & Brushes (Zero Reallocation) ---

private val CardShape24 = RoundedCornerShape(24.dp)
private val CardShape16 = RoundedCornerShape(16.dp)
private val RowShape12 = RoundedCornerShape(12.dp)
private val ReasonShape8 = RoundedCornerShape(8.dp)
private val HueShape20 = RoundedCornerShape(20.dp)
private val ZeroShape = RoundedCornerShape(0.dp)
private val HueBrush = Brush.horizontalGradient(
    colors = listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)
)

// --- Data Models ---

@Immutable
data class ListItem(
    @SerializedName("id") val id: String = UUID.randomUUID().toString(),
    @SerializedName("text") val text: String = "",
    @SerializedName("status") val status: ItemStatus = ItemStatus.NONE,
    @SerializedName("reason") val reason: String = ""
)

enum class ItemStatus {
    @SerializedName("NONE") NONE,
    @SerializedName("DONE") DONE,
    @SerializedName("FAILED") FAILED
}

@Immutable
data class SList(
    @SerializedName("id") val id: String = UUID.randomUUID().toString(),
    @SerializedName("title") val title: String = "New List",
    @SerializedName("items") val items: List<ListItem> = emptyList(),
    @SerializedName("accentColorArgb") val accentColorArgb: Int = Color(0xFF00B0FF).toArgb(),
    @SerializedName("showReasonOnSuccess") val showReasonOnSuccess: Boolean = false,
    @SerializedName("showReasonOnFailure") val showReasonOnFailure: Boolean = false,
    @SerializedName("trackCompletion") val trackCompletion: Boolean = false,
    @SerializedName("failuresCountAsProgress") val failuresCountAsProgress: Boolean = true,
    @SerializedName("isLocked") val isLocked: Boolean = false,
    @SerializedName("isZenMode") val isZenMode: Boolean = false,
    @SerializedName("raveMode") val raveMode: Boolean = false
) {
    val accentColor: Color get() = Color(accentColorArgb)
}

@Immutable
private data class ListMetrics(
    val totalTasks: Int,
    val progressCount: Int,
    val remaining: Int,
    val progress: Float,
    val allCompleted: Boolean,
    val preview2: List<ListItem>,
    val preview3: List<ListItem>
)

private fun SList.computeMetrics(): ListMetrics {
    var totalTasks = 0
    var doneCount = 0
    var nonNoneCount = 0
    var p1: ListItem? = null
    var p2: ListItem? = null
    var p3: ListItem? = null

    for (i in items.indices) {
        val item = items[i]
        if (item.text.isNotBlank()) {
            totalTasks++
            if (item.status == ItemStatus.DONE) doneCount++
            if (item.status != ItemStatus.NONE) nonNoneCount++
            if (p1 == null) p1 = item
            else if (p2 == null) p2 = item
            else if (p3 == null) p3 = item
        }
    }

    val progressCount = if (failuresCountAsProgress) nonNoneCount else doneCount
    val allCompleted = totalTasks > 0 && nonNoneCount == totalTasks
    val progress = if (totalTasks > 0) progressCount.toFloat() / totalTasks else 0f
    val remaining = totalTasks - nonNoneCount
    val preview2 = when {
        p1 == null -> emptyList()
        p2 == null -> listOf(p1)
        else -> listOf(p1, p2)
    }
    val preview3 = when {
        p1 == null -> emptyList()
        p2 == null -> listOf(p1)
        p3 == null -> listOf(p1, p2)
        else -> listOf(p1, p2, p3)
    }

    return ListMetrics(
        totalTasks = totalTasks,
        progressCount = progressCount,
        remaining = remaining,
        progress = progress,
        allCompleted = allCompleted,
        preview2 = preview2,
        preview3 = preview3
    )
}

fun generateRandomNeonColor(): Color {
    val h = Random.nextFloat() * 360f
    val s = 0.9f
    val v = 1.0f
    return Color.hsv(h, s, v)
}

private fun ListItem.normalizedOrNull(): ListItem? {
    val normalizedText = text.trim()
    if (normalizedText.isEmpty()) return null
    val normalizedReason = reason.trim()
    if (normalizedText === text && normalizedReason === reason) return this
    return copy(text = normalizedText, reason = normalizedReason)
}

private fun SList.normalized(): SList {
    var changed = false
    val result = ArrayList<ListItem>(items.size)
    for (i in items.indices) {
        val orig = items[i]
        val norm = orig.normalizedOrNull()
        if (norm == null) {
            changed = true
        } else {
            if (norm !== orig) changed = true
            result.add(norm)
        }
    }
    return if (changed) copy(items = result) else this
}

private fun List<SList>.normalized(): List<SList> {
    var changed = false
    val result = ArrayList<SList>(size)
    for (i in indices) {
        val orig = this[i]
        val norm = orig.normalized()
        if (norm !== orig) changed = true
        result.add(norm)
    }
    return if (changed) result else this
}

// --- Persistence ---

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "obsidian_vault")
private val appSaveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

class VaultManager(context: Context) {
    private val appContext = context.applicationContext
    private val gson = Gson()
    private val vaultKey = stringPreferencesKey("list_data")
    private val listType = object : TypeToken<List<SList>>() {}.type

    suspend fun saveLists(lists: List<SList>) = withContext(Dispatchers.IO) {
        val json = gson.toJson(lists.normalized(), listType)
        appContext.dataStore.edit { it[vaultKey] = json }
    }

    suspend fun loadLists(): List<SList> = withContext(Dispatchers.IO) {
        val json = appContext.dataStore.data.map { it[vaultKey] }.first() ?: return@withContext emptyList()
        runCatching {
            gson.fromJson<List<SList>>(json, listType).orEmpty().normalized()
        }.getOrElse {
            emptyList()
        }
    }
}

// --- State Management ---

class ListViewModel(private val vaultManager: VaultManager) : ViewModel() {
    companion object {
        private const val DEBOUNCE_SAVE_MS = 250L

        fun factory(vaultManager: VaultManager): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return ListViewModel(vaultManager) as T
                }
            }
    }
    
    var lists by mutableStateOf(listOf<SList>())
    var isLoaded by mutableStateOf(false)
    var activeListId by mutableStateOf<String?>(null)
    var isGridView by mutableStateOf(true)
    var isSelectionMode by mutableStateOf(false)
    var selectedListIds = mutableStateListOf<String>()
    var focusedItemId by mutableStateOf<String?>(null)
    var lastDeletedBatch = mutableStateListOf<SList>()

    private val saveMutex = Mutex()
    private var debounceSaveJob: Job? = null
    @Volatile
    private var hasDirtyState = false

    init {
        viewModelScope.launch {
            lists = vaultManager.loadLists()
            isLoaded = true
        }
    }

    private fun persistImmediate() {
        if (!isLoaded) return
        debounceSaveJob?.cancel()
        debounceSaveJob = null
        hasDirtyState = true
        val snapshot = lists
        appSaveScope.launch {
            saveMutex.withLock {
                hasDirtyState = false
                vaultManager.saveLists(snapshot)
            }
        }
    }

    private fun persistDebounced() {
        if (!isLoaded) return
        hasDirtyState = true
        debounceSaveJob?.cancel()
        debounceSaveJob = viewModelScope.launch {
            delay(DEBOUNCE_SAVE_MS)
            val snapshot = lists
            appSaveScope.launch {
                saveMutex.withLock {
                    hasDirtyState = false
                    vaultManager.saveLists(snapshot)
                }
            }
        }
    }

    fun flushPendingSaves() {
        if (!isLoaded || !hasDirtyState) return
        debounceSaveJob?.cancel()
        debounceSaveJob = null
        val snapshot = lists
        appSaveScope.launch {
            saveMutex.withLock {
                if (hasDirtyState) {
                    hasDirtyState = false
                    vaultManager.saveLists(snapshot)
                }
            }
        }
    }

    override fun onCleared() {
        flushPendingSaves()
        super.onCleared()
    }

    fun addList() {
        if (!isLoaded) return
        lists = lists + SList(
            title = "List ${lists.count() + 1}",
            accentColorArgb = generateRandomNeonColor().toArgb()
        )
        persistImmediate()
    }

    fun toggleSelection(id: String) {
        if (!isLoaded) return
        val target = lists.find { it.id == id } ?: return
        if (target.isLocked) return
        if (selectedListIds.contains(id)) selectedListIds.remove(id)
        else selectedListIds.add(id)
    }

    fun lockSelected() {
        if (!isLoaded) return
        val selectedSet = selectedListIds.toHashSet()
        lists = lists.map { 
            if (selectedSet.contains(it.id)) it.copy(isLocked = true) else it 
        }
        selectedListIds.clear()
        isSelectionMode = false
        persistImmediate()
    }

    fun deleteSelected() {
        if (!isLoaded) return
        val selectedSet = selectedListIds.toHashSet()
        val deletableIds = lists
            .asSequence()
            .filter { selectedSet.contains(it.id) && !it.isLocked }
            .map { it.id }
            .toHashSet()
        val toDelete = lists.filter { deletableIds.contains(it.id) }
        lastDeletedBatch.clear()
        lastDeletedBatch.addAll(toDelete)
        lists = lists.filterNot { deletableIds.contains(it.id) }
        selectedListIds.clear()
        isSelectionMode = false
        persistImmediate()
    }

    fun undoDelete() {
        if (!isLoaded) return
        if (lastDeletedBatch.isNotEmpty()) {
            lists = lists + lastDeletedBatch
            lastDeletedBatch.clear()
            persistImmediate()
        }
    }

    fun updateList(updatedList: SList, shouldPersist: Boolean = true) {
        if (!isLoaded) return
        lists = lists.map { if (it.id == updatedList.id) updatedList else it }
        if (shouldPersist) persistDebounced()
    }

    fun moveList(fromIndex: Int, toIndex: Int, shouldPersist: Boolean = true) {
        if (!isLoaded || fromIndex == toIndex) return
        if (fromIndex !in lists.indices || toIndex !in lists.indices) return
        val mutable = lists.toMutableList()
        val item = mutable.removeAt(fromIndex)
        mutable.add(toIndex, item)
        lists = mutable
        if (shouldPersist) persistImmediate() else hasDirtyState = true
    }

    fun moveItem(listId: String, fromIndex: Int, toIndex: Int, shouldPersist: Boolean = true) {
        if (!isLoaded || fromIndex == toIndex) return
        val list = lists.find { it.id == listId } ?: return
        if (fromIndex !in list.items.indices || toIndex !in list.items.indices) return
        val mutable = list.items.toMutableList()
        val item = mutable.removeAt(fromIndex)
        mutable.add(toIndex, item)
        updateList(list.copy(items = mutable), shouldPersist)
        if (!shouldPersist) hasDirtyState = true
    }

    fun finishReorder() {
        persistImmediate()
    }
}

// --- Drag and Drop Logic ---

@Composable
fun rememberDragDropState(
    lazyListState: LazyListState,
    onMove: (Int, Int) -> Unit,
    onDragEnd: () -> Unit
): DragDropState {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val currentOnMove by rememberUpdatedState(onMove)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    val state = remember(lazyListState) {
        DragDropState(
            lazyListState = lazyListState,
            scope = scope,
            onMove = { f, t -> currentOnMove(f, t) },
            onDragEnd = { currentOnDragEnd() },
            haptic = haptic
        )
    }

    LaunchedEffect(state.draggedItemKey) {
        if (state.draggedItemKey == null) return@LaunchedEffect
        while (state.draggedItemKey != null) {
            withFrameNanos { }
            val scrollDelta = state.computeAutoScrollDelta()
            if (scrollDelta != 0f) {
                val consumed = lazyListState.scrollBy(scrollDelta)
                if (consumed != 0f) {
                    state.checkAndPerformSwap()
                }
            }
        }
    }

    return state
}

class DragDropState(
    val lazyListState: LazyListState,
    private val scope: CoroutineScope,
    private val onMove: (Int, Int) -> Unit,
    private val onDragEnd: () -> Unit,
    private val haptic: androidx.compose.ui.hapticfeedback.HapticFeedback
) {
    var draggedItemKey by mutableStateOf<String?>(null)
        private set

    var settlingItemKey by mutableStateOf<String?>(null)
        private set

    private var dragDeltaY by mutableFloatStateOf(0f)
    private var startItemOffset = 0
    private var expectedIndex = -1
    private var releaseVisualTop = 0f
    private val settleProgress = Animatable(0f)
    private var settleJob: Job? = null

    fun isActiveOrSettling(key: String): Boolean =
        draggedItemKey == key || settlingItemKey == key

    fun scaleFor(key: String, activeScale: Float = 1.02f): Float = when (key) {
        draggedItemKey -> activeScale
        settlingItemKey -> 1f + (activeScale - 1f) * settleProgress.value
        else -> 1f
    }

    fun displacementFor(key: String): Float {
        if (draggedItemKey == key) {
            val itemInfo = lazyListState.layoutInfo.visibleItemsInfo
                .firstOrNull { it.key == key } ?: return 0f
            return (startItemOffset + dragDeltaY) - itemInfo.offset
        }
        if (settlingItemKey == key) {
            val itemInfo = lazyListState.layoutInfo.visibleItemsInfo
                .firstOrNull { it.key == key } ?: return 0f
            return (releaseVisualTop - itemInfo.offset) * settleProgress.value
        }
        return 0f
    }

    fun onDragStart(offset: Offset) {
        val layoutInfo = lazyListState.layoutInfo
        val hitY = (offset.y + layoutInfo.viewportStartOffset).toInt()
        val hitItem = layoutInfo.visibleItemsInfo.firstOrNull { item ->
            (item.key is String) && hitY in item.offset..(item.offset + item.size)
        } ?: return

        settleJob?.cancel()
        settlingItemKey = null
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        draggedItemKey = hitItem.key as String
        expectedIndex = hitItem.index
        startItemOffset = hitItem.offset
        dragDeltaY = 0f
    }

    fun onDrag(dragAmount: Offset) {
        if (draggedItemKey == null) return
        dragDeltaY += dragAmount.y
        checkAndPerformSwap()
    }

    internal fun checkAndPerformSwap() {
        val key = draggedItemKey ?: return
        val visibleItems = lazyListState.layoutInfo.visibleItemsInfo
        val currentItem = visibleItems.firstOrNull { it.key == key } ?: return

        // Wait until Compose finishes laying out the previous swap so indices & offsets are fresh
        if (expectedIndex != -1 && currentItem.index != expectedIndex) return

        val visualTop = startItemOffset + dragDeltaY
        val visualBottom = visualTop + currentItem.size

        // Hysteresis thresholds (55% going down, 45% going up) prevent boundary bounce/jitter
        val nextItem = visibleItems.firstOrNull {
            it.index == currentItem.index + 1 && it.key is String
        }
        if (nextItem != null && visualBottom > nextItem.offset + nextItem.size * 0.55f) {
            expectedIndex = nextItem.index
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onMove(currentItem.index, nextItem.index)
            return
        }

        val prevItem = visibleItems.firstOrNull {
            it.index == currentItem.index - 1 && it.key is String
        }
        if (prevItem != null && visualTop < prevItem.offset + prevItem.size * 0.45f) {
            expectedIndex = prevItem.index
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onMove(currentItem.index, prevItem.index)
        }
    }

    internal fun computeAutoScrollDelta(): Float {
        val key = draggedItemKey ?: return 0f
        val layoutInfo = lazyListState.layoutInfo
        val currentItem = layoutInfo.visibleItemsInfo.firstOrNull { it.key == key } ?: return 0f
        val visualTop = startItemOffset + dragDeltaY
        val visualBottom = visualTop + currentItem.size
        val viewportStart = layoutInfo.viewportStartOffset.toFloat()
        val viewportEnd = layoutInfo.viewportEndOffset.toFloat()
        val edgeZone = (layoutInfo.viewportSize.height * 0.12f).coerceIn(80f, 180f)
        val maxSpeed = 22f

        return when {
            visualTop < viewportStart + edgeZone -> {
                val ratio = ((viewportStart + edgeZone - visualTop) / edgeZone).coerceIn(0f, 1f)
                -maxSpeed * ratio
            }
            visualBottom > viewportEnd - edgeZone -> {
                val ratio = ((visualBottom - (viewportEnd - edgeZone)) / edgeZone).coerceIn(0f, 1f)
                maxSpeed * ratio
            }
            else -> 0f
        }
    }

    fun onDragInterrupted() {
        val key = draggedItemKey
        if (key != null) {
            releaseVisualTop = startItemOffset + dragDeltaY
            draggedItemKey = null
            expectedIndex = -1
            dragDeltaY = 0f
            settlingItemKey = key
            onDragEnd()

            settleJob?.cancel()
            settleJob = scope.launch {
                settleProgress.snapTo(1f)
                settleProgress.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
                if (settlingItemKey == key) {
                    settlingItemKey = null
                }
            }
        }
    }
}

@Composable
fun rememberDragDropGridState(
    lazyGridState: LazyGridState,
    onMove: (Int, Int) -> Unit,
    onDragEnd: () -> Unit
): DragDropGridState {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val currentOnMove by rememberUpdatedState(onMove)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    val state = remember(lazyGridState) {
        DragDropGridState(
            lazyGridState = lazyGridState,
            scope = scope,
            onMove = { f, t -> currentOnMove(f, t) },
            onDragEnd = { currentOnDragEnd() },
            haptic = haptic
        )
    }

    LaunchedEffect(state.draggedItemKey) {
        if (state.draggedItemKey == null) return@LaunchedEffect
        while (state.draggedItemKey != null) {
            withFrameNanos { }
            val scrollDelta = state.computeAutoScrollDelta()
            if (scrollDelta != 0f) {
                val consumed = lazyGridState.scrollBy(scrollDelta)
                if (consumed != 0f) {
                    state.checkAndPerformSwap()
                }
            }
        }
    }

    return state
}

class DragDropGridState(
    val lazyGridState: LazyGridState,
    private val scope: CoroutineScope,
    private val onMove: (Int, Int) -> Unit,
    private val onDragEnd: () -> Unit,
    private val haptic: androidx.compose.ui.hapticfeedback.HapticFeedback
) {
    var draggedItemKey by mutableStateOf<String?>(null)
        private set

    var settlingItemKey by mutableStateOf<String?>(null)
        private set

    private var dragDelta by mutableStateOf(Offset.Zero)
    private var startItemOffset = androidx.compose.ui.unit.IntOffset.Zero
    private var expectedIndex = -1
    private var releaseVisualOffset = Offset.Zero
    private val settleProgress = Animatable(0f)
    private var settleJob: Job? = null

    fun isActiveOrSettling(key: String): Boolean =
        draggedItemKey == key || settlingItemKey == key

    fun scaleFor(key: String, activeScale: Float = 1.05f): Float = when (key) {
        draggedItemKey -> activeScale
        settlingItemKey -> 1f + (activeScale - 1f) * settleProgress.value
        else -> 1f
    }

    fun displacementFor(key: String): Offset {
        if (draggedItemKey == key) {
            val itemInfo = lazyGridState.layoutInfo.visibleItemsInfo
                .firstOrNull { it.key == key } ?: return Offset.Zero
            return Offset(
                (startItemOffset.x + dragDelta.x) - itemInfo.offset.x,
                (startItemOffset.y + dragDelta.y) - itemInfo.offset.y
            )
        }
        if (settlingItemKey == key) {
            val itemInfo = lazyGridState.layoutInfo.visibleItemsInfo
                .firstOrNull { it.key == key } ?: return Offset.Zero
            val p = settleProgress.value
            return Offset(
                (releaseVisualOffset.x - itemInfo.offset.x) * p,
                (releaseVisualOffset.y - itemInfo.offset.y) * p
            )
        }
        return Offset.Zero
    }

    fun onDragStart(offset: Offset) {
        val layoutInfo = lazyGridState.layoutInfo
        val visible = layoutInfo.visibleItemsInfo
        if (visible.isEmpty()) return

        val minX = visible.minOf { it.offset.x }
        val maxRight = visible.maxOf { it.offset.x + it.size.width }
        val horizontalContentPad = if (visible.size >= 2) {
            ((layoutInfo.viewportSize.width - (maxRight - minX)) / 2).coerceAtLeast(0)
        } else {
            0
        }
        val hitX = (offset.x - horizontalContentPad).toInt()
        val hitY = (offset.y + layoutInfo.viewportStartOffset).toInt()

        val hitItem = visible.firstOrNull { item ->
            (item.key is String) &&
                hitX in item.offset.x..(item.offset.x + item.size.width) &&
                hitY in item.offset.y..(item.offset.y + item.size.height)
        } ?: return

        settleJob?.cancel()
        settlingItemKey = null
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        draggedItemKey = hitItem.key as String
        expectedIndex = hitItem.index
        startItemOffset = hitItem.offset
        dragDelta = Offset.Zero
    }

    fun onDrag(dragAmount: Offset) {
        if (draggedItemKey == null) return
        dragDelta += dragAmount
        checkAndPerformSwap()
    }

    internal fun checkAndPerformSwap() {
        val key = draggedItemKey ?: return
        val visibleItems = lazyGridState.layoutInfo.visibleItemsInfo
        val currentItem = visibleItems.firstOrNull { it.key == key } ?: return

        if (expectedIndex != -1 && currentItem.index != expectedIndex) return

        val visualCenterX = startItemOffset.x + dragDelta.x + currentItem.size.width * 0.5f
        val visualCenterY = startItemOffset.y + dragDelta.y + currentItem.size.height * 0.5f

        val targetItem = visibleItems.firstOrNull { item ->
            if (item.key !is String || item.key == key) return@firstOrNull false
            val insetX = item.size.width * 0.18f
            val insetY = item.size.height * 0.18f
            visualCenterX >= item.offset.x + insetX &&
                visualCenterX <= item.offset.x + item.size.width - insetX &&
                visualCenterY >= item.offset.y + insetY &&
                visualCenterY <= item.offset.y + item.size.height - insetY
        }

        if (targetItem != null) {
            expectedIndex = targetItem.index
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onMove(currentItem.index, targetItem.index)
        }
    }

    internal fun computeAutoScrollDelta(): Float {
        val key = draggedItemKey ?: return 0f
        val layoutInfo = lazyGridState.layoutInfo
        val currentItem = layoutInfo.visibleItemsInfo.firstOrNull { it.key == key } ?: return 0f
        val visualTop = startItemOffset.y + dragDelta.y
        val visualBottom = visualTop + currentItem.size.height
        val viewportStart = layoutInfo.viewportStartOffset.toFloat()
        val viewportEnd = layoutInfo.viewportEndOffset.toFloat()
        val edgeZone = (layoutInfo.viewportSize.height * 0.12f).coerceIn(80f, 180f)
        val maxSpeed = 22f

        return when {
            visualTop < viewportStart + edgeZone -> {
                val ratio = ((viewportStart + edgeZone - visualTop) / edgeZone).coerceIn(0f, 1f)
                -maxSpeed * ratio
            }
            visualBottom > viewportEnd - edgeZone -> {
                val ratio = ((visualBottom - (viewportEnd - edgeZone)) / edgeZone).coerceIn(0f, 1f)
                maxSpeed * ratio
            }
            else -> 0f
        }
    }

    fun onDragInterrupted() {
        val key = draggedItemKey
        if (key != null) {
            releaseVisualOffset = Offset(
                startItemOffset.x + dragDelta.x,
                startItemOffset.y + dragDelta.y
            )
            draggedItemKey = null
            expectedIndex = -1
            dragDelta = Offset.Zero
            settlingItemKey = key
            onDragEnd()

            settleJob?.cancel()
            settleJob = scope.launch {
                settleProgress.snapTo(1f)
                settleProgress.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
                if (settlingItemKey == key) {
                    settlingItemKey = null
                }
            }
        }
    }
}

// --- UI Components ---

@Composable
fun ListGenApp() {
    val context = LocalContext.current
    val vaultManager = remember { VaultManager(context) }
    val viewModel: ListViewModel = viewModel(factory = ListViewModel.factory(vaultManager))
    @Suppress("DEPRECATION")
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                viewModel.flushPendingSaves()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    Surface(modifier = Modifier.fillMaxSize().statusBarsPadding(), color = Color(0xFF0F0F0F)) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (!viewModel.isLoaded) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color(0xFF00B0FF)
                )
            } else if (viewModel.activeListId == null) {
                MainScreen(viewModel)
            } else {
                val currentList = viewModel.lists.find { it.id == viewModel.activeListId }
                if (currentList != null) {
                    DetailScreen(
                        list = currentList,
                        viewModel = viewModel,
                        onBack = { viewModel.activeListId = null },
                        onUpdate = { viewModel.updateList(it) }
                    )
                } else {
                    LaunchedEffect(viewModel.activeListId) {
                        viewModel.activeListId = null
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainScreen(viewModel: ListViewModel) {
    var showAppSettings by remember { mutableStateOf(false) }
    var showChangelog by remember { mutableStateOf(false) }

    val gridState = rememberLazyGridState()
    val dragDropGridState = rememberDragDropGridState(gridState, onMove = { from, to ->
        if (from < viewModel.lists.size && to < viewModel.lists.size) {
            viewModel.moveList(from, to, shouldPersist = false)
        }
    }, onDragEnd = { viewModel.finishReorder() })

    val listState = rememberLazyListState()
    val dragDropListState = rememberDragDropState(listState, onMove = { from, to ->
        if (from < viewModel.lists.size && to < viewModel.lists.size) {
            viewModel.moveList(from, to, shouldPersist = false)
        }
    }, onDragEnd = { viewModel.finishReorder() })

    if (showChangelog) {
        ChangelogDialog(
            onBack = {
                showChangelog = false
                showAppSettings = true
            }
        )
    } else if (showAppSettings) {
        AppSettingsDialog(
            onOpenChangelog = {
                showAppSettings = false
                showChangelog = true
            },
            onDismiss = { showAppSettings = false }
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(top = 24.dp, start = 24.dp, end = 24.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_neon_list),
                    contentDescription = "App Icon",
                    modifier = Modifier.size(36.dp).padding(end = 8.dp),
                    tint = Color.Unspecified
                )
                Text("ListGen", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { if (viewModel.lastDeletedBatch.isNotEmpty()) viewModel.undoDelete() },
                    enabled = viewModel.lastDeletedBatch.isNotEmpty()
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Undo, 
                        contentDescription = "Undo", 
                        tint = if (viewModel.lastDeletedBatch.isNotEmpty()) Color(0xFF00B0FF) else Color.DarkGray
                    )
                }
                
                IconButton(onClick = { viewModel.isGridView = !viewModel.isGridView }) {
                    Icon(
                        imageVector = if (viewModel.isGridView) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                        contentDescription = "Toggle View",
                        tint = Color.Gray
                    )
                }

                if (viewModel.isSelectionMode) {
                    IconButton(onClick = { viewModel.lockSelected() }) {
                        Icon(Icons.Default.Lock, "Lock Selected", tint = Color(0xFF00B0FF))
                    }
                    IconButton(onClick = { viewModel.deleteSelected() }) {
                        Icon(Icons.Default.DeleteForever, "Delete Selected", tint = Color(0xFFFF5252))
                    }
                    IconButton(onClick = { 
                        viewModel.isSelectionMode = false
                        viewModel.selectedListIds.clear()
                    }) {
                        Icon(Icons.Default.Close, "Cancel", tint = Color.White)
                    }
                } else {
                    IconButton(onClick = { viewModel.isSelectionMode = true }) {
                        Icon(Icons.Default.DeleteSweep, "Selection Mode", tint = Color.Gray)
                    }
                    IconButton(
                        onClick = { showAppSettings = true },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = SettingsIcon,
                            contentDescription = "Settings",
                            tint = SettingsAccent,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        
        if (viewModel.isGridView) {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { offset -> dragDropGridState.onDragStart(offset) },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            dragDropGridState.onDrag(dragAmount)
                        },
                        onDragEnd = { dragDropGridState.onDragInterrupted() },
                        onDragCancel = { dragDropGridState.onDragInterrupted() }
                    )
                },
                contentPadding = PaddingValues(start = 12.dp, top = 16.dp, end = 12.dp, bottom = 120.dp)
            ) {
                itemsIndexed(items = viewModel.lists, key = { _, list -> list.id }) { _, list ->
                    val isElevated = dragDropGridState.isActiveOrSettling(list.id)
                    ListCard(
                        list = list,
                        isSelectionMode = viewModel.isSelectionMode,
                        isSelected = viewModel.selectedListIds.contains(list.id),
                        onClick = { 
                            if (viewModel.isSelectionMode) viewModel.toggleSelection(list.id)
                            else viewModel.activeListId = list.id 
                        },
                        modifier = Modifier
                            .then(if (isElevated) Modifier else Modifier.animateItem())
                            .zIndex(if (isElevated) 1f else 0f)
                            .graphicsLayer {
                                if (isElevated) {
                                    val disp = dragDropGridState.displacementFor(list.id)
                                    val s = dragDropGridState.scaleFor(list.id, 1.05f)
                                    translationX = disp.x
                                    translationY = disp.y
                                    scaleX = s
                                    scaleY = s
                                }
                            }
                    )
                }
                item { AddListCard(onClick = { viewModel.addList() }, isGrid = true) }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { offset -> dragDropListState.onDragStart(offset) },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            dragDropListState.onDrag(dragAmount)
                        },
                        onDragEnd = { dragDropListState.onDragInterrupted() },
                        onDragCancel = { dragDropListState.onDragInterrupted() }
                    )
                },
                verticalArrangement = Arrangement.spacedBy(10.dp), // Balanced tightness for compact mode
                contentPadding = PaddingValues(start = 24.dp, top = 16.dp, end = 24.dp, bottom = 120.dp)
            ) {
                itemsIndexed(items = viewModel.lists, key = { _, list -> list.id }) { _, list ->
                    val isElevated = dragDropListState.isActiveOrSettling(list.id)
                    ListRow(
                        list = list,
                        isSelectionMode = viewModel.isSelectionMode,
                        isSelected = viewModel.selectedListIds.contains(list.id),
                        onClick = { 
                            if (viewModel.isSelectionMode) viewModel.toggleSelection(list.id)
                            else viewModel.activeListId = list.id 
                        },
                        modifier = Modifier
                            .then(if (isElevated) Modifier else Modifier.animateItem())
                            .zIndex(if (isElevated) 1f else 0f)
                            .graphicsLayer {
                                if (isElevated) {
                                    val s = dragDropListState.scaleFor(list.id, 1.02f)
                                    translationY = dragDropListState.displacementFor(list.id)
                                    scaleX = s
                                    scaleY = s
                                }
                            }
                    )
                }
                item { AddListCard(onClick = { viewModel.addList() }, isGrid = false) }
            }
        }
    }
}

@Composable
fun NeonProgressBar(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    isRaveMode: Boolean = false,
    isComplete: Boolean = false
) {
    val clampedProgress = progress.coerceIn(0.01f, 1f)

    if (!isRaveMode) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(CircleShape)
                .background(Color.DarkGray.copy(alpha = 0.3f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(clampedProgress)
                    .fillMaxHeight()
                    .background(color)
            )
        }
        return
    }

    val infiniteTransition = rememberInfiniteTransition(label = "progress_bar_transition")
    val glowPulseState = infiniteTransition.animateFloat(
        initialValue = if (isComplete) 0.85f else 0.55f,
        targetValue = if (isComplete) 1.25f else 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (isComplete) 1400 else 2200,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "progress_bar_glow"
    )
    val shimmerShiftState = infiniteTransition.animateFloat(
        initialValue = -0.6f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (isComplete) 1800 else 2800,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "progress_bar_shimmer"
    )

    val fillColors = remember(color, isComplete) {
        if (isComplete) {
            listOf(
                color.copy(alpha = 0.85f),
                Color.White.copy(alpha = 0.95f),
                Color(0xFF9D7CFF).copy(alpha = 0.85f),
                color.copy(alpha = 0.95f)
            )
        } else {
            listOf(
                color.copy(alpha = 0.85f),
                Color.White.copy(alpha = 0.78f),
                color.copy(alpha = 0.98f)
            )
        }
    }
    val shimmerColors = remember(isComplete) {
        listOf(
            Color.White.copy(alpha = if (isComplete) 0.22f else 0.12f),
            Color.Transparent,
            Color.White.copy(alpha = if (isComplete) 0.32f else 0.16f)
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(CircleShape)
            .background(Color.DarkGray.copy(alpha = 0.22f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(clampedProgress)
                .fillMaxHeight()
                .graphicsLayer {
                    val baseAlpha = if (isComplete) 0.55f else 0.35f
                    alpha = (baseAlpha * glowPulseState.value).coerceIn(0f, 1f)
                }
                .blur(if (isComplete) 10.dp else 6.dp)
                .background(color)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth(clampedProgress)
                .fillMaxHeight()
                .drawBehind {
                    val shift = shimmerShiftState.value
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = fillColors,
                            start = Offset.Zero,
                            end = Offset(360f + (shift * 220f), 0f)
                        )
                    )
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = shimmerColors,
                            start = Offset(shift * 280f, 0f),
                            end = Offset((shift * 280f) + 220f, 0f)
                        )
                    )
                }
        )
    }
}

@Composable
fun CompletionIndicator(isComplete: Boolean, text: String, modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        if (isComplete) {
            Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(0xFF00FF9C)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Check, null, tint = Color.Black, modifier = Modifier.size(7.dp))
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text("Complete", color = Color(0xFF00FF9C), fontSize = 11.sp, fontWeight = FontWeight.Bold)
        } else {
            Text(text, color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ListCard(list: SList, isSelectionMode: Boolean, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val metrics = remember(list.items, list.failuresCountAsProgress) { list.computeMetrics() }
    val totalTasks = metrics.totalTasks
    val progressCount = metrics.progressCount
    val allCompleted = metrics.allCompleted
    val progress = metrics.progress
    val remaining = metrics.remaining

    val glowPulseState: State<Float>?
    val shimmerShiftState: State<Float>?
    if (list.raveMode) {
        val infiniteTransition = rememberInfiniteTransition(label = "card_rave_transition")
        glowPulseState = infiniteTransition.animateFloat(
            initialValue = 0.6f, targetValue = 1.0f,
            animationSpec = infiniteRepeatable(animation = tween(2500, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse),
            label = "card_glow_pulse"
        )
        shimmerShiftState = infiniteTransition.animateFloat(
            initialValue = -0.8f, targetValue = 1.8f,
            animationSpec = infiniteRepeatable(animation = tween(3800, easing = LinearEasing), repeatMode = RepeatMode.Restart),
            label = "card_shimmer_shift"
        )
    } else {
        glowPulseState = null
        shimmerShiftState = null
    }

    val borderBrush = remember(list.accentColorArgb, list.raveMode) {
        Brush.linearGradient(
            listOf(
                list.accentColor.copy(alpha = if (list.raveMode) 0.8f else 0.4f),
                list.accentColor.copy(alpha = if (list.raveMode) 0.4f else 0.2f)
            )
        )
    }

    Box(contentAlignment = Alignment.Center, modifier = modifier) {
        // --- REAL DIFFUSED GLOW AURA (Rave Only) ---
        if (glowPulseState != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val scale = (1.5f * glowPulseState.value).coerceAtMost(1f)
                        scaleX = scale
                        scaleY = scale
                    }
                    .blur(48.dp)
                    .drawBehind {
                        val pulse = glowPulseState.value
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(list.accentColor.copy(alpha = 0.7f * pulse), Color.Transparent),
                                center = center,
                                radius = size.width / 1.5f
                            )
                        )
                    }
            )
        }

        Box(
            modifier = Modifier
                .aspectRatio(0.8f) // Made longer vertically to fit more data
                .run {
                    if (glowPulseState != null) {
                        this.graphicsLayer {
                            shadowElevation = (20f * glowPulseState.value).dp.toPx()
                            shape = CardShape24
                            clip = false
                            ambientShadowColor = list.accentColor
                            spotShadowColor = list.accentColor
                        }
                    } else this
                }
                .clip(CardShape24)
                .background(Color(0xFF161616))
                .border(
                    width = if (list.raveMode) 3.dp else 1.dp, 
                    brush = borderBrush,
                    shape = CardShape24
                )
                .clickable { onClick() }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        if (shimmerShiftState != null && glowPulseState != null) {
                            val shift = shimmerShiftState.value
                            val pulse = glowPulseState.value
                            drawRect(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        list.accentColor.copy(alpha = 0.12f),
                                        Color(0xFF181818),
                                        list.accentColor.copy(alpha = 0.06f)
                                    ),
                                    start = Offset.Zero,
                                    end = Offset(420f + (shift * 260f), 280f)
                                )
                            )
                            drawRect(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.White.copy(alpha = 0.08f * pulse),
                                        Color.Transparent
                                    ),
                                    start = Offset(shift * 260f, 0f),
                                    end = Offset((shift * 260f) + 220f, 220f)
                                )
                            )
                        } else {
                            drawRect(color = list.accentColor.copy(alpha = 0.03f))
                        }
                    }
            )
            
            Row(modifier = Modifier.align(Alignment.TopEnd).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (list.isLocked) {
                    Icon(imageVector = Icons.Default.Lock, contentDescription = "Locked", tint = Color.White.copy(alpha = 0.2f), modifier = Modifier.size(14.dp))
                    if (isSelectionMode) Spacer(modifier = Modifier.width(4.dp))
                }
                if (isSelectionMode) {
                    Checkbox(checked = isSelected, onCheckedChange = { onClick() }, colors = CheckboxDefaults.colors(checkedColor = Color(0xFFFF5252), uncheckedColor = Color.DarkGray), modifier = Modifier.size(24.dp))
                }
            }

            Column(modifier = Modifier.padding(12.dp).fillMaxSize()) {
                Text(list.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, modifier = Modifier.padding(end = 32.dp))
                Text(if (list.trackCompletion) "$remaining remaining" else "$totalTasks items", color = if (list.trackCompletion && remaining > 0) (if (list.raveMode) Color.White else list.accentColor) else Color.White.copy(alpha = 0.5f), fontSize = 9.sp, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(4.dp))
                val previewItems = if (list.trackCompletion) metrics.preview2 else metrics.preview3
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    previewItems.forEach { item ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(3.dp).clip(CircleShape).background(if (item.status == ItemStatus.DONE) Color(0xFF00FF9C) else list.accentColor.copy(alpha = 0.5f)))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(text = item.text, color = Color.LightGray.copy(alpha = 0.7f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                if (list.trackCompletion) {
                    Spacer(modifier = Modifier.weight(1f))
                    Spacer(modifier = Modifier.height(8.dp)) // Clearance for progress
                    Column {
                        NeonProgressBar(progress = progress, color = list.accentColor, isRaveMode = list.raveMode, isComplete = allCompleted)
                        Spacer(modifier = Modifier.height(4.dp)) // Extra gap for 'Complete' text
                        CompletionIndicator(isComplete = allCompleted, text = "$progressCount/$totalTasks", modifier = Modifier.align(Alignment.End))
                        Spacer(modifier = Modifier.height(4.dp)) // Final bottom buffer
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ListRow(list: SList, isSelectionMode: Boolean, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val metrics = remember(list.items, list.failuresCountAsProgress) { list.computeMetrics() }
    val totalTasks = metrics.totalTasks
    val progressCount = metrics.progressCount
    val allCompleted = metrics.allCompleted
    val progress = metrics.progress
    val remaining = metrics.remaining

    val glowPulseState: State<Float>?
    val shimmerShiftState: State<Float>?
    if (list.raveMode) {
        val infiniteTransition = rememberInfiniteTransition(label = "row_rave_transition")
        glowPulseState = infiniteTransition.animateFloat(
            initialValue = 0.8f, targetValue = 1.0f,
            animationSpec = infiniteRepeatable(animation = tween(2000, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse),
            label = "row_glow_pulse"
        )
        shimmerShiftState = infiniteTransition.animateFloat(
            initialValue = -0.8f, targetValue = 1.8f,
            animationSpec = infiniteRepeatable(animation = tween(3400, easing = LinearEasing), repeatMode = RepeatMode.Restart),
            label = "row_shimmer_shift"
        )
    } else {
        glowPulseState = null
        shimmerShiftState = null
    }

    val borderBrush = remember(list.accentColorArgb, list.raveMode) {
        Brush.linearGradient(
            listOf(
                list.accentColor.copy(alpha = if (list.raveMode) 0.8f else 0.3f),
                list.accentColor.copy(alpha = if (list.raveMode) 0.3f else 0.1f)
            )
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .run {
                if (glowPulseState != null) {
                    this.graphicsLayer {
                        shadowElevation = (8f * glowPulseState.value).dp.toPx()
                        shape = RowShape12
                        clip = false
                        ambientShadowColor = list.accentColor
                        spotShadowColor = list.accentColor
                    }
                } else this
            }
            .clip(RowShape12)
            .background(Color(0xFF161616))
            .border(
                width = if (list.raveMode) 1.dp else 0.5.dp,
                brush = borderBrush,
                shape = RowShape12
            )
            .clickable { onClick() },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    if (shimmerShiftState != null && glowPulseState != null) {
                        val shift = shimmerShiftState.value
                        val pulse = glowPulseState.value
                        drawRect(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    list.accentColor.copy(alpha = 0.14f),
                                    Color(0xFF171717),
                                    list.accentColor.copy(alpha = 0.05f)
                                ),
                                start = Offset.Zero,
                                end = Offset(460f + (shift * 260f), 120f)
                            )
                        )
                        drawRect(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.White.copy(alpha = 0.09f * pulse),
                                    Color.Transparent
                                ),
                                start = Offset(shift * 320f, 0f),
                                end = Offset((shift * 320f) + 240f, 96f)
                            )
                        )
                    } else {
                        drawRect(color = list.accentColor.copy(alpha = 0.03f))
                    }
                }
        )

        Row(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(list.accentColor))
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Text(list.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (list.trackCompletion) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("$remaining left", color = if (remaining > 0) list.accentColor else Color.White.copy(alpha = 0.5f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(modifier = Modifier.weight(1f)) { NeonProgressBar(progress = progress, color = list.accentColor, isRaveMode = list.raveMode, isComplete = allCompleted) }
                        Spacer(modifier = Modifier.width(8.dp))
                        CompletionIndicator(isComplete = allCompleted, text = "$progressCount/$totalTasks", modifier = Modifier)
                    }
                } else {
                    Text("$totalTasks items", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                }
            }
            if (list.isLocked) {
                Icon(Icons.Default.Lock, "Locked", tint = Color.White.copy(alpha = 0.3f), modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
            }
            if (isSelectionMode) {
                Checkbox(checked = isSelected, onCheckedChange = { onClick() }, colors = CheckboxDefaults.colors(checkedColor = Color(0xFFFF5252), uncheckedColor = Color.DarkGray))
            } else {
                Icon(Icons.Default.ChevronRight, "Go", tint = list.accentColor.copy(alpha = 0.4f), modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
fun AddListCard(onClick: () -> Unit, isGrid: Boolean) {
    Box(
        modifier = Modifier
            .run { if (isGrid) aspectRatio(1f) else fillMaxWidth().height(74.dp) }
            .clip(if (isGrid) CardShape24 else CardShape16)
            .background(Color(0xFF161616))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Default.Add, "Add List", tint = Color.DarkGray, modifier = Modifier.size(32.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DetailScreen(list: SList, viewModel: ListViewModel, onBack: () -> Unit, onUpdate: (SList) -> Unit) {
    BackHandler(onBack = onBack)
    var showSettings by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(showSettings) { if (showSettings) focusManager.clearFocus() }

    val metrics = remember(list.items, list.failuresCountAsProgress) { list.computeMetrics() }
    val totalTasks = metrics.totalTasks
    val progressCount = metrics.progressCount
    val allCompleted = metrics.allCompleted
    val progress = metrics.progress
    val remaining = metrics.remaining

    val taskListState = rememberLazyListState()
    val dragDropTaskState = rememberDragDropState(taskListState, onMove = { from, to ->
        if (from < list.items.size && to < list.items.size) {
            viewModel.moveItem(list.id, from, to, shouldPersist = false)
        }
    }, onDragEnd = { viewModel.finishReorder() })

    Scaffold(
        containerColor = Color(0xFF0F0F0F),
        topBar = {
            Column(modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp)
                ) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White) }
                    Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                        Text(list.title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
                        if (list.trackCompletion) {
                            Text("$remaining tasks remaining", fontSize = 12.sp, color = if (list.raveMode) Color.White else list.accentColor, fontWeight = FontWeight.Medium)
                        }
                    }
                    
                    IconButton(onClick = { onUpdate(list.copy(isZenMode = !list.isZenMode)) }) {
                        Icon(
                            imageVector = if (list.isZenMode) Icons.Default.Expand else Icons.Default.Compress,
                            contentDescription = "Toggle Zen Mode",
                            tint = if (list.isZenMode) list.accentColor else Color.Gray
                        )
                    }

                    Box(
                        modifier = Modifier.size(48.dp).clip(CircleShape).combinedClickable(
                            onClick = { if (!list.isLocked) onUpdate(list.copy(isLocked = true)) },
                            onLongClick = { if (list.isLocked) { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onUpdate(list.copy(isLocked = false)) } }
                        ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = if (list.isLocked) Icons.Default.Lock else Icons.Default.LockOpen, contentDescription = "Lock", tint = if (list.isLocked) Color(0xFFFF5252) else Color.Gray, modifier = Modifier.size(20.dp))
                    }

                    IconButton(onClick = { showSettings = true }) { Icon(Icons.Default.Edit, "Settings", tint = if (list.raveMode) Color.White else list.accentColor) }
                }
                if (list.trackCompletion) {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 8.dp, bottom = 12.dp)) {
                        NeonProgressBar(progress = progress, color = list.accentColor, isRaveMode = list.raveMode, isComplete = allCompleted)
                        Spacer(modifier = Modifier.height(4.dp))
                        CompletionIndicator(isComplete = allCompleted, text = "$progressCount/$totalTasks complete", modifier = Modifier.align(Alignment.End))
                    }
                }
            }
        },
        floatingActionButton = {
            if (!list.isLocked) {
                FloatingActionButton(
                    onClick = {
                        val newItem = ListItem()
                        viewModel.focusedItemId = newItem.id
                        onUpdate(list.copy(items = list.items + newItem))
                    },
                    containerColor = if (list.raveMode) Color.White else list.accentColor, contentColor = Color.Black, shape = CircleShape
                ) { Icon(Icons.Default.Add, "Add Item") }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            state = taskListState,
            modifier = Modifier.fillMaxSize().padding(innerPadding)
                .pointerInput(list.isLocked) {
                    if (!list.isLocked) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { offset -> dragDropTaskState.onDragStart(offset) },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragDropTaskState.onDrag(dragAmount)
                            },
                            onDragEnd = { dragDropTaskState.onDragInterrupted() },
                            onDragCancel = { dragDropTaskState.onDragInterrupted() }
                        )
                    }
                }
                .run { if (list.isZenMode) this else padding(horizontal = 16.dp) },
            verticalArrangement = Arrangement.spacedBy(
                when {
                    list.raveMode && list.isZenMode -> 6.dp
                    list.raveMode -> 16.dp
                    list.isZenMode -> 2.dp
                    else -> 12.dp
                }
            ),
            contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp)
        ) {
            itemsIndexed(items = list.items, key = { _, item -> item.id }) { _, item ->
                val isElevated = dragDropTaskState.isActiveOrSettling(item.id)
                ListItemRow(
                    item = item,
                    showReasonOnSuccess = if (list.isZenMode) false else list.showReasonOnSuccess,
                    showReasonOnFailure = if (list.isZenMode) false else list.showReasonOnFailure,
                    accentColor = list.accentColor,
                    isListLocked = list.isLocked,
                    isZenMode = list.isZenMode,
                    isRaveMode = list.raveMode,
                    shouldRequestFocus = viewModel.focusedItemId == item.id,
                    onFocusHandled = { if (viewModel.focusedItemId == item.id) viewModel.focusedItemId = null },
                    onUpdate = { updatedItem ->
                        onUpdate(list.copy(items = list.items.map { if (it.id == item.id) updatedItem else it }))
                    },
                    onDelete = { onUpdate(list.copy(items = list.items.filter { it.id != item.id })) },
                    modifier = Modifier
                        .then(if (isElevated) Modifier else Modifier.animateItem())
                        .zIndex(if (isElevated) 1f else 0f)
                        .graphicsLayer {
                            if (isElevated) {
                                val s = dragDropTaskState.scaleFor(item.id, 1.02f)
                                translationY = dragDropTaskState.displacementFor(item.id)
                                scaleX = s
                                scaleY = s
                            }
                        }
                )
            }
        }
        if (showSettings) {
            ListSettingsSheet(list = list, onDismiss = { showSettings = false }, onUpdate = onUpdate)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListSettingsSheet(list: SList, onDismiss: () -> Unit, onUpdate: (SList) -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    ModalBottomSheet(
        onDismissRequest = onDismiss, 
        containerColor = Color(0xFF1E1E1E),
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.Gray) }
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 48.dp).verticalScroll(rememberScrollState())) {
            Text("List Customization", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(modifier = Modifier.height(24.dp))

            Text("NAME", color = list.accentColor, fontSize = 12.sp, fontWeight = FontWeight.Black)
            TextField(value = list.title, onValueChange = { if (!list.isLocked && it.length <= 30) onUpdate(list.copy(title = it)) }, modifier = Modifier.fillMaxWidth(), enabled = !list.isLocked, singleLine = true, textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = 18.sp), keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences), colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, focusedIndicatorColor = list.accentColor, unfocusedIndicatorColor = Color.DarkGray, disabledContainerColor = Color.Transparent, disabledTextColor = Color.Gray))
            Spacer(modifier = Modifier.height(32.dp))

            Text("TRACKING OPTIONS", color = list.accentColor, fontSize = 12.sp, fontWeight = FontWeight.Black)
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Track Completion", color = Color.White)
                Switch(enabled = !list.isLocked, checked = list.trackCompletion, onCheckedChange = { onUpdate(list.copy(trackCompletion = it)) }, colors = SwitchDefaults.colors(checkedThumbColor = list.accentColor))
            }
            if (list.trackCompletion) {
                Spacer(modifier = Modifier.height(8.dp))
                Text("FAILURE LOGIC", color = list.accentColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(
                        selected = list.failuresCountAsProgress,
                        onClick = { onUpdate(list.copy(failuresCountAsProgress = true)) },
                        label = { Text("Fail = Progress") },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = list.accentColor.copy(alpha = 0.2f), selectedLabelColor = list.accentColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FilterChip(
                        selected = !list.failuresCountAsProgress,
                        onClick = { onUpdate(list.copy(failuresCountAsProgress = false)) },
                        label = { Text("Success Only") },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = list.accentColor.copy(alpha = 0.2f), selectedLabelColor = list.accentColor)
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text("EXPERIMENTAL EFFECTS", color = Color(0xFFFFD740), fontSize = 12.sp, fontWeight = FontWeight.Black)
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Disco Rave Mode 🌈", color = Color.White, fontWeight = FontWeight.Bold)
                Switch(enabled = !list.isLocked, checked = list.raveMode, onCheckedChange = { onUpdate(list.copy(raveMode = it)) }, colors = SwitchDefaults.colors(checkedThumbColor = Color.Magenta))
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Show Reason on Success", color = Color.White)
                Switch(enabled = !list.isLocked, checked = list.showReasonOnSuccess, onCheckedChange = { onUpdate(list.copy(showReasonOnSuccess = it)) }, colors = SwitchDefaults.colors(checkedThumbColor = list.accentColor))
            }
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Show Reason on Failure", color = Color.White)
                Switch(enabled = !list.isLocked, checked = list.showReasonOnFailure, onCheckedChange = { onUpdate(list.copy(showReasonOnFailure = it)) }, colors = SwitchDefaults.colors(checkedThumbColor = list.accentColor))
            }
            Spacer(modifier = Modifier.height(32.dp))

            Text("CHOOSE YOUR HUE", color = list.accentColor, fontSize = 12.sp, fontWeight = FontWeight.Black)
            Spacer(modifier = Modifier.height(16.dp))
            HuePicker(currentColor = list.accentColor, onColorChanged = { newColor -> if (!list.isLocked) onUpdate(list.copy(accentColorArgb = newColor.toArgb())) })
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = if (list.raveMode) Color.White else list.accentColor, contentColor = if (list.raveMode) Color.Black else Color.White), shape = RoundedCornerShape(16.dp)) { Text("SAVE & FINISH", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
        }
    }
}

@Composable
fun HuePicker(currentColor: Color, onColorChanged: (Color) -> Unit) {
    val currentOnColorChanged by rememberUpdatedState(onColorChanged)
    val hsv = remember { FloatArray(3) }
    Box(
        modifier = Modifier.fillMaxWidth().height(40.dp).clip(HueShape20).background(HueBrush)
            .pointerInput(Unit) { detectTapGestures { offset -> val hue = (offset.x / size.width).coerceIn(0f, 1f) * 360f; currentOnColorChanged(Color.hsv(hue, 0.9f, 1.0f)) } }
            .pointerInput(Unit) { detectDragGestures { change, _ -> val hue = (change.position.x / size.width).coerceIn(0f, 1f) * 360f; currentOnColorChanged(Color.hsv(hue, 0.9f, 1.0f)) } }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            android.graphics.Color.colorToHSV(currentColor.toArgb(), hsv)
            val huePos = hsv[0] / 360f * size.width
            drawCircle(color = Color.White, radius = 16.dp.toPx(), center = Offset(huePos, size.height / 2), style = Stroke(width = 3.dp.toPx()))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ListItemRow(
    item: ListItem, 
    showReasonOnSuccess: Boolean,
    showReasonOnFailure: Boolean,
    accentColor: Color,
    isListLocked: Boolean,
    isZenMode: Boolean = false,
    isRaveMode: Boolean = false,
    shouldRequestFocus: Boolean,
    onFocusHandled: () -> Unit,
    onUpdate: (ListItem) -> Unit, 
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val auraColor = when (item.status) {
        ItemStatus.DONE -> Color(0xFF00FF9C)
        ItemStatus.FAILED -> Color(0xFFFF007F)
        ItemStatus.NONE -> Color.Transparent
    }
    
    val backgroundColor = when (item.status) {
        ItemStatus.DONE -> Color(0xFF003D26)
        ItemStatus.FAILED -> Color(0xFF3D001E)
        ItemStatus.NONE -> Color(0xFF1E1E1E)
    }

    val pulseScaleState: State<Float>? = if (!isRaveMode && item.status != ItemStatus.NONE) {
        val infiniteTransition = rememberInfiniteTransition(label = "item_aura_transition")
        infiniteTransition.animateFloat(
            initialValue = 0.8f, targetValue = 1.2f,
            animationSpec = infiniteRepeatable(animation = tween(2000, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse),
            label = "item_aura_pulse"
        )
    } else {
        null
    }
    var isEditing by remember { mutableStateOf(item.text.isEmpty()) }
    var hasHadFocus by remember { mutableStateOf(false) }
    var isEditingReason by remember { mutableStateOf(false) }
    
    val showReason = (item.status == ItemStatus.DONE && showReasonOnSuccess) || 
                     (item.status == ItemStatus.FAILED && showReasonOnFailure)
    val raveHighlightColor = when (item.status) {
        ItemStatus.DONE -> Color(0xFF00FF9C)
        ItemStatus.FAILED -> Color(0xFFFF4FA3)
        ItemStatus.NONE -> accentColor
    }
    val raveSolidBase = when (item.status) {
        ItemStatus.DONE -> Color(0xFF0F6A46)
        ItemStatus.FAILED -> Color(0xFF7A1832)
        ItemStatus.NONE -> Color(0xFF0E1014)
    }
    val itemShape = if (isZenMode) ZeroShape else CardShape16
    
    val focusRequester = remember { FocusRequester() }
    val reasonFocusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(shouldRequestFocus) {
        if (shouldRequestFocus && !isListLocked) {
            delay(50)
            isEditing = true
            focusRequester.requestFocus()
            onFocusHandled()
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .run {
                    if (isRaveMode) {
                        shadow(
                            elevation = if (item.status == ItemStatus.NONE) 6.dp else 10.dp,
                            shape = itemShape,
                            clip = false,
                            ambientColor = raveHighlightColor.copy(alpha = if (item.status == ItemStatus.NONE) 0.12f else 0.20f),
                            spotColor = raveHighlightColor.copy(alpha = if (item.status == ItemStatus.NONE) 0.18f else 0.28f)
                        )
                    } else this
                }
                .clip(itemShape)
                .background(if (isRaveMode) raveSolidBase else backgroundColor)
                .run {
                    if (isRaveMode) {
                        border(
                            width = 1.dp,
                            color = raveHighlightColor.copy(alpha = if (item.status == ItemStatus.NONE) 0.35f else 0.55f),
                            shape = itemShape
                        )
                    } else this
                }
                .drawBehind {
                    if (pulseScaleState != null) {
                        drawRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(auraColor.copy(alpha = 0.15f * pulseScaleState.value), Color.Transparent),
                                startX = 0f,
                                endX = size.width
                            ),
                            size = size
                        )
                    }
                }
                .padding(if (isZenMode) 8.dp else 12.dp)
        ) {
            if (isEditing && !isListLocked) {
                Column {
                    TextField(value = item.text, onValueChange = { onUpdate(item.copy(text = it.replace("\n", ""))) }, placeholder = { Text("Task...", color = Color.Gray) }, modifier = Modifier.fillMaxWidth().focusRequester(focusRequester).onFocusChanged { if (it.isFocused) hasHadFocus = true; if (!it.isFocused && hasHadFocus && item.text.isBlank()) onDelete() }, singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, capitalization = KeyboardCapitalization.Sentences), keyboardActions = KeyboardActions(onDone = { if (item.text.isBlank()) onDelete() else { isEditing = false; keyboardController?.hide(); focusManager.clearFocus() } }), colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, focusedIndicatorColor = accentColor, unfocusedIndicatorColor = Color.Transparent, focusedTextColor = Color.White, unfocusedTextColor = Color.White, cursorColor = accentColor))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        IconButton(onClick = { if (item.text.isBlank()) onDelete() else isEditing = false }) { Icon(Icons.Default.Edit, "Stop Editing", tint = accentColor, modifier = Modifier.size(20.dp)) }
                        IconButton(onClick = { }, modifier = Modifier.size(48.dp)) { Box(modifier = Modifier.combinedClickable(onClick = { }, onLongClick = { if (!isListLocked) { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onUpdate(item.copy(status = if (item.status == ItemStatus.DONE) ItemStatus.NONE else ItemStatus.DONE)) } })) { Icon(Icons.Default.CheckCircle, "Done", tint = if (item.status == ItemStatus.DONE) Color(0xFF00FF9C) else Color.Gray.copy(alpha = if (isListLocked) 0.2f else 1f)) } }
                        IconButton(onClick = { }, modifier = Modifier.size(48.dp)) { Box(modifier = Modifier.combinedClickable(onClick = { }, onLongClick = { if (!isListLocked) { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onUpdate(item.copy(status = if (item.status == ItemStatus.FAILED) ItemStatus.NONE else ItemStatus.FAILED)) } })) { Icon(Icons.Default.Cancel, "Fail", tint = if (item.status == ItemStatus.FAILED) Color(0xFFFF007F) else Color.Gray.copy(alpha = if (isListLocked) 0.2f else 1f)) } }
                        IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Delete", tint = Color.DarkGray, modifier = Modifier.size(20.dp)) }
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = if (item.text.isBlank()) "New Task" else item.text, color = if (isListLocked) Color.LightGray.copy(alpha = 0.5f) else Color.White, fontWeight = if (item.status != ItemStatus.NONE) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f).padding(start = if (isZenMode) 8.dp else 16.dp), fontSize = if (isZenMode) 14.sp else 16.sp, maxLines = if (isZenMode) 1 else Int.MAX_VALUE, overflow = TextOverflow.Ellipsis)
                    Row {
                        if (!isListLocked && !isZenMode) { IconButton(onClick = { isEditing = true }) { Icon(Icons.Default.Edit, "Edit", tint = Color.Gray, modifier = Modifier.size(20.dp)) } }
                        IconButton(onClick = { }, modifier = Modifier.size(if (isZenMode) 32.dp else 48.dp)) { Box(modifier = Modifier.combinedClickable(onClick = { }, onLongClick = { if (!isListLocked) { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onUpdate(item.copy(status = if (item.status == ItemStatus.DONE) ItemStatus.NONE else ItemStatus.DONE)) } })) { Icon(imageVector = Icons.Default.CheckCircle, contentDescription = "Done", tint = if (item.status == ItemStatus.DONE) Color(0xFF00FF9C) else Color.Gray.copy(alpha = if (isListLocked) 0.2f else 1f), modifier = Modifier.size(if (isZenMode) 18.dp else 24.dp)) } }
                        IconButton(onClick = { }, modifier = Modifier.size(if (isZenMode) 32.dp else 48.dp)) { Box(modifier = Modifier.combinedClickable(onClick = { }, onLongClick = { if (!isListLocked) { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onUpdate(item.copy(status = if (item.status == ItemStatus.FAILED) ItemStatus.NONE else ItemStatus.FAILED)) } })) { Icon(imageVector = Icons.Default.Cancel, contentDescription = "Fail", tint = if (item.status == ItemStatus.FAILED) Color(0xFFFF007F) else Color.Gray.copy(alpha = if (isListLocked) 0.2f else 1f), modifier = Modifier.size(if (isZenMode) 18.dp else 24.dp)) } }
                    }
                }
            }
        }
        AnimatedVisibility(visible = showReason && !isZenMode, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(modifier = Modifier.padding(horizontal = 16.dp).padding(top = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    val label = if (item.status == ItemStatus.DONE) "Success Note:" else "Failure Reason:"
                    val labelColor = if (item.status == ItemStatus.DONE) Color(0xFF00FF9C) else Color(0xFFFF007F)
                    Text(label, color = labelColor, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    if (!isListLocked) { IconButton(onClick = { isEditingReason = !isEditingReason }, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Edit, "Edit Reason", tint = if (isEditingReason) labelColor else Color.Gray, modifier = Modifier.size(14.dp)) } }
                }
                Spacer(modifier = Modifier.height(4.dp))
                if (isEditingReason && !isListLocked) {
                    TextField(value = item.reason, onValueChange = { onUpdate(item.copy(reason = it)) }, modifier = Modifier.fillMaxWidth().focusRequester(reasonFocusRequester), colors = TextFieldDefaults.colors(focusedContainerColor = Color.Black.copy(alpha = 0.2f), unfocusedContainerColor = Color.Black.copy(alpha = 0.2f), focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, focusedTextColor = Color.White, unfocusedTextColor = Color.White), shape = ReasonShape8, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, capitalization = KeyboardCapitalization.Sentences), keyboardActions = KeyboardActions(onDone = { isEditingReason = false; keyboardController?.hide(); focusManager.clearFocus() }))
                    LaunchedEffect(Unit) { reasonFocusRequester.requestFocus() }
                } else {
                    Text(text = if (item.reason.isBlank()) "No note added." else item.reason, color = Color.LightGray, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp).fillMaxWidth())
                }
            }
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF00B0FF), background = Color(0xFF0F0F0F), surface = Color(0xFF1E1E1E))) { ListGenApp() } }
    }
}
