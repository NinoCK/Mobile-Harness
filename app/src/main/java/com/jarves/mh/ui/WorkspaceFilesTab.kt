package com.jarves.mh.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarves.mh.data.WorkspaceFileOps
import com.jarves.mh.model.FileExportPhase
import com.jarves.mh.model.FileExportState
import com.jarves.mh.model.WorkspaceEntry
import com.jarves.mh.ui.theme.PocketBlue
import com.jarves.mh.ui.theme.PocketGreen
import com.jarves.mh.ui.theme.PocketOrange
import java.util.Locale
import kotlin.math.roundToInt

private val PocketPurple = Color(0xFFB79CFF)
private val PocketPink = Color(0xFFF59AC0)
private val PocketAmber = Color(0xFFFFC85C)
private val PocketTeal = Color(0xFF5ED3D0)
private val PocketRed = Color(0xFFFF7A7A)

private enum class ExportTarget { PROJECT, SELECTION }

/**
 * Project Files page. Folders are browsed one level at a time with a breadcrumb trail; every row
 * has a checkbox so any mix of files and folders can be ticked across folders and exported as a
 * ZIP. The download button beside refresh zips the whole project. Navigation, the selection and export
 * progress live in the ViewModel so they survive opening a file or switching tabs.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun WorkspaceFilesTab(
    files: List<WorkspaceEntry>,
    currentDir: String,
    selection: Map<String, WorkspaceEntry>,
    artifacts: List<WorkspaceEntry>,
    loading: Boolean,
    export: FileExportState?,
    exportBlockedReason: String?,
    projectSlug: String,
    suggestedProjectRoot: String?,
    onRefresh: () -> Unit,
    onOpenDirectory: (String) -> Unit,
    onSelectionChange: (Map<String, WorkspaceEntry>) -> Unit,
    onOpenFile: (WorkspaceEntry) -> Unit,
    onUseSuggestedProjectRoot: () -> Unit,
    onExportProject: (Uri, Boolean) -> Unit,
    onExportSelection: (Uri, Boolean) -> Unit,
    onSaveFile: (Uri, String) -> Unit,
    onCancelExport: () -> Unit,
    onDismissExport: () -> Unit,
    onOpenExportLocation: () -> Unit,
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val entries = remember(files) {
        files.sortedWith(
            compareByDescending<WorkspaceEntry> { it.isDirectory }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name },
        )
    }
    val selected = selection.keys

    var sheet by rememberSaveable { mutableStateOf<ExportTarget?>(null) }
    var includeBuildFiles by rememberSaveable { mutableStateOf(false) }
    // Captured when the save picker opens, read when it returns.
    var pendingIncludeBuildFiles by rememberSaveable { mutableStateOf(false) }
    var pendingSavePath by rememberSaveable { mutableStateOf<String?>(null) }

    val projectLauncher = rememberLauncherForActivityResult(CreateTypedDocument()) { uri ->
        if (uri != null) onExportProject(uri, pendingIncludeBuildFiles)
    }
    val selectionLauncher = rememberLauncherForActivityResult(CreateTypedDocument()) { uri ->
        if (uri != null) onExportSelection(uri, pendingIncludeBuildFiles)
    }
    val fileLauncher = rememberLauncherForActivityResult(CreateTypedDocument()) { uri ->
        val path = pendingSavePath
        pendingSavePath = null
        if (uri != null && path != null) onSaveFile(uri, path)
    }

    fun blocked(): Boolean {
        val reason = exportBlockedReason ?: return false
        Toast.makeText(context, reason, Toast.LENGTH_SHORT).show()
        return true
    }

    // Everything in this folder may already be included through a ticked ancestor.
    val currentIncludedWith = remember(currentDir, selected) {
        when {
            currentDir.isEmpty() -> null
            currentDir in selected -> currentDir
            else -> WorkspaceFileOps.coveringSelection(currentDir, selected)
        }
    }
    // "Select all" skips build/cache folders; they can still be ticked one by one.
    val selectable = remember(entries) {
        entries.filter { !WorkspaceFileOps.isExportExcludedName(it.name) }.ifEmpty { entries }
    }
    val selectAllState = when {
        currentIncludedWith != null -> ToggleableState.On
        selectable.isNotEmpty() && selectable.all { it.path in selected } -> ToggleableState.On
        entries.any { it.path in selected } -> ToggleableState.Indeterminate
        else -> ToggleableState.Off
    }

    LaunchedEffect(sheet, selection.isEmpty()) {
        if (sheet == ExportTarget.SELECTION && selection.isEmpty()) sheet = null
    }
    BackHandler(enabled = currentDir.isNotEmpty()) {
        onOpenDirectory(currentDir.substringBeforeLast('/', ""))
    }
    BackHandler(enabled = currentDir.isEmpty() && selection.isNotEmpty()) {
        onSelectionChange(emptyMap())
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = if (selection.isEmpty()) 24.dp else 124.dp),
        ) {
            if (suggestedProjectRoot != null) {
                item(key = "suggested-root") {
                    SuggestedRootCard(suggestedProjectRoot, onUseSuggestedProjectRoot, Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp))
                }
            }
            if (currentDir.isEmpty() && artifacts.isNotEmpty()) {
                item(key = "build-outputs") {
                    BuildOutputsCard(
                        artifacts = artifacts,
                        modifier = Modifier.padding(
                            start = 14.dp,
                            end = 14.dp,
                            top = if (suggestedProjectRoot == null) 12.dp else 0.dp,
                            bottom = 6.dp,
                        ),
                        onSave = { artifact ->
                            if (!blocked()) {
                                pendingSavePath = artifact.path
                                fileLauncher.launch(artifact.name to mimeTypeFor(artifact.name))
                            }
                        },
                    )
                }
            }
            stickyHeader(key = "folder-bar") {
                FolderBar(
                    projectSlug = projectSlug,
                    currentDir = currentDir,
                    itemCount = entries.size,
                    loading = loading,
                    selectAllState = selectAllState,
                    includedWith = currentIncludedWith,
                    canSelectAll = currentIncludedWith == null && entries.isNotEmpty(),
                    onNavigate = onOpenDirectory,
                    onRefresh = onRefresh,
                    onDownloadProject = { sheet = ExportTarget.PROJECT },
                    onSelectAll = {
                        onSelectionChange(
                            if (selectAllState == ToggleableState.On) {
                                selection.filterKeys { path -> entries.none { it.path == path } }
                            } else {
                                withAdded(selection, selectable)
                            },
                        )
                    },
                )
            }
            when {
                entries.isEmpty() && loading -> items(6, key = { "skeleton-$it" }) { SkeletonRow(it) }
                entries.isEmpty() -> item(key = "empty-$currentDir") { EmptyFolder(isRoot = currentDir.isEmpty()) }
                else -> items(entries, key = { "entry:${it.path}" }) { entry ->
                    val explicit = entry.path in selected
                    val includedWith = if (explicit) null else WorkspaceFileOps.coveringSelection(entry.path, selected)
                    FileRow(
                        entry = entry,
                        checked = explicit || includedWith != null,
                        includedByParent = includedWith != null,
                        skippedByDefault = entry.isDirectory && WorkspaceFileOps.isExportExcludedName(entry.name),
                        modifier = Modifier.animateItem(),
                        onOpen = { if (entry.isDirectory) onOpenDirectory(entry.path) else onOpenFile(entry) },
                        onToggle = { onSelectionChange(toggled(selection, entry)) },
                        onLongPress = {
                            if (includedWith == null) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onSelectionChange(toggled(selection, entry))
                            }
                        },
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = selection.isNotEmpty(),
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)) { it } + fadeIn(),
            exit = slideOutVertically(tween(200)) { it } + fadeOut(tween(160)),
        ) {
            SelectionBar(
                selection = selection.values,
                onClear = { onSelectionChange(emptyMap()) },
                onExport = { sheet = ExportTarget.SELECTION },
            )
        }

        ExportOverlay(export = export, onCancel = onCancelExport, onDismiss = onDismissExport, onOpenLocation = onOpenExportLocation)
    }

    sheet?.let { target ->
        val zipName = when (target) {
            ExportTarget.PROJECT -> "$projectSlug.zip"
            ExportTarget.SELECTION -> selection.values.singleOrNull()?.let { "${it.name}.zip" } ?: "$projectSlug-selection.zip"
        }
        ExportSheet(
            target = target,
            projectSlug = projectSlug,
            zipName = zipName,
            selection = selection.values.toList(),
            includeBuildFiles = includeBuildFiles,
            onIncludeBuildFilesChange = { includeBuildFiles = it },
            onRemove = { path -> onSelectionChange(selection - path) },
            onDismiss = { sheet = null },
            onSave = {
                if (!blocked()) {
                    pendingIncludeBuildFiles = includeBuildFiles
                    sheet = null
                    when (target) {
                        ExportTarget.PROJECT -> projectLauncher.launch(zipName to "application/zip")
                        ExportTarget.SELECTION -> selectionLauncher.launch(zipName to "application/zip")
                    }
                }
            },
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Folder bar: breadcrumbs, download-project, refresh and "select all"
// ---------------------------------------------------------------------------------------------

@Composable
private fun FolderBar(
    projectSlug: String,
    currentDir: String,
    itemCount: Int,
    loading: Boolean,
    selectAllState: ToggleableState,
    includedWith: String?,
    canSelectAll: Boolean,
    onNavigate: (String) -> Unit,
    onRefresh: () -> Unit,
    onDownloadProject: () -> Unit,
    onSelectAll: () -> Unit,
) {
    Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.padding(top = 4.dp)) {
            Row(Modifier.padding(start = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Breadcrumbs(projectSlug, currentDir, onNavigate, Modifier.weight(1f))
                FilledTonalIconButton(onClick = onDownloadProject) {
                    Icon(Icons.Default.Download, "Download project as ZIP", Modifier.size(20.dp))
                }
                RefreshButton(loading, onRefresh)
            }
            Row(Modifier.padding(start = 10.dp, end = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                TriStateCheckbox(state = selectAllState, onClick = onSelectAll, enabled = canSelectAll)
                Text(
                    when {
                        includedWith != null -> "Included with ${includedWith.substringAfterLast('/')}"
                        selectAllState == ToggleableState.On -> "Unselect all"
                        else -> "Select all"
                    },
                    Modifier.weight(1f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (includedWith != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (itemCount == 1) "1 item" else "%,d items".format(Locale.US, itemCount),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        }
    }
}

@Composable
private fun Breadcrumbs(projectSlug: String, currentDir: String, onNavigate: (String) -> Unit, modifier: Modifier = Modifier) {
    val scroll = rememberScrollState()
    LaunchedEffect(currentDir, scroll.maxValue) { scroll.animateScrollTo(scroll.maxValue) }
    val segments = if (currentDir.isEmpty()) emptyList() else currentDir.split('/')
    Row(modifier.horizontalScroll(scroll), verticalAlignment = Alignment.CenterVertically) {
        Crumb(Icons.Default.Home, projectSlug, active = segments.isEmpty()) { onNavigate("") }
        segments.forEachIndexed { index, name ->
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                null,
                Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
            Crumb(null, name, active = index == segments.lastIndex) {
                onNavigate(segments.take(index + 1).joinToString("/"))
            }
        }
    }
}

@Composable
private fun Crumb(icon: ImageVector?, label: String, active: Boolean, onClick: () -> Unit) {
    val color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent)
            .clickable(enabled = !active, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(16.dp), tint = color)
            Spacer(Modifier.width(6.dp))
        }
        Text(
            label,
            fontSize = 14.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
            color = color,
            maxLines = 1,
        )
    }
}

@Composable
private fun RefreshButton(loading: Boolean, onRefresh: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "refresh")
    val spin by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
        label = "refreshSpin",
    )
    IconButton(onClick = onRefresh, enabled = !loading) {
        Icon(
            Icons.Default.Refresh,
            "Refresh files",
            Modifier.graphicsLayer { rotationZ = if (loading) spin else 0f },
            tint = if (loading) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Rows
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileRow(
    entry: WorkspaceEntry,
    checked: Boolean,
    includedByParent: Boolean,
    skippedByDefault: Boolean,
    modifier: Modifier,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    onLongPress: () -> Unit,
) {
    val background by animateColorAsState(
        if (checked && !includedByParent) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent,
        label = "rowBackground",
    )
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 1.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(background)
            .combinedClickable(onClick = onOpen, onLongClick = onLongPress)
            .padding(end = 10.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() }, enabled = !includedByParent)
        FileTypeBadge(entry)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(
                entry.name,
                fontSize = 15.sp,
                fontWeight = if (entry.isDirectory) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    entryDetails(entry),
                    Modifier.weight(1f, fill = false),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (skippedByDefault && !checked) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Not in project ZIP",
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(PocketAmber.copy(alpha = 0.14f))
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = PocketAmber,
                        maxLines = 1,
                    )
                }
            }
        }
        if (entry.isDirectory) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                "Open folder",
                Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun FileTypeBadge(entry: WorkspaceEntry, size: Int = 38) {
    val (icon, tint) = fileVisual(entry)
    Box(
        Modifier.size(size.dp).clip(RoundedCornerShape(11.dp)).background(tint.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, Modifier.size((size * 0.55f).dp), tint = tint)
    }
}

@Composable
private fun fileVisual(entry: WorkspaceEntry): Pair<ImageVector, Color> {
    if (entry.isDirectory) {
        return if (WorkspaceFileOps.isExportExcludedName(entry.name)) {
            Icons.Default.FolderOff to PocketOrange.copy(alpha = 0.7f)
        } else {
            Icons.Default.Folder to PocketOrange
        }
    }
    return when (entry.name.substringAfterLast('.', "").lowercase(Locale.US)) {
        "apk", "aab" -> Icons.Default.Android to PocketGreen
        "kt", "kts", "java", "js", "mjs", "cjs", "ts", "tsx", "jsx", "py", "c", "cc", "cpp", "h", "hpp", "rs", "go",
        "swift", "dart", "php", "rb", "cs", "html", "htm", "css", "scss", "vue", "svelte", "sql",
        -> Icons.Default.Code to PocketBlue
        "json", "xml", "yaml", "yml", "toml", "gradle", "properties", "ini", "cfg", "conf", "lock", "pro",
        -> Icons.Default.DataObject to PocketPurple
        "png", "jpg", "jpeg", "gif", "webp", "svg", "ico", "bmp", "avif" -> Icons.Default.Image to PocketPink
        "zip", "jar", "aar", "tar", "gz", "tgz", "7z", "rar", "xz", "zst" -> Icons.Default.FolderZip to PocketAmber
        "sh", "bash", "zsh", "bat", "cmd", "ps1" -> Icons.Default.Terminal to PocketTeal
        "keystore", "jks", "pem", "key", "p12", "crt" -> Icons.Default.Key to PocketRed
        else -> Icons.Default.Description to MaterialTheme.colorScheme.onSurfaceVariant
    }
}

@Composable
private fun SkeletonRow(index: Int) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(tween(750, delayMillis = index * 70), RepeatMode.Reverse),
        label = "skeletonAlpha",
    )
    val shade = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f * alpha)
    Row(Modifier.fillMaxWidth().padding(start = 22.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(20.dp).clip(RoundedCornerShape(4.dp)).background(shade))
        Spacer(Modifier.width(20.dp))
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(shade))
        Spacer(Modifier.width(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.width((90 + (index * 37) % 110).dp).height(12.dp).clip(RoundedCornerShape(6.dp)).background(shade))
            Box(Modifier.width(54.dp).height(9.dp).clip(RoundedCornerShape(6.dp)).background(shade))
        }
    }
}

@Composable
private fun EmptyFolder(isRoot: Boolean) {
    Column(
        Modifier.fillMaxWidth().padding(top = 40.dp, start = 28.dp, end = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(68.dp).clip(CircleShape).background(PocketOrange.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Folder, null, Modifier.size(32.dp), tint = PocketOrange)
        }
        Spacer(Modifier.height(14.dp))
        Text(if (isRoot) "No files yet" else "This folder is empty", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        if (isRoot) {
            Spacer(Modifier.height(4.dp))
            Text(
                "Ask your coding agent to create something in this project.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun SuggestedRootCard(root: String, onUse: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth().padding(bottom = 10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Project folder detected", fontWeight = FontWeight.Bold)
            Text(
                "Use $root as the project root so Chat, Terminal, Changes, and Preview all run from the same folder.",
                fontSize = 13.sp,
            )
            Button(onClick = onUse, modifier = Modifier.fillMaxWidth()) { Text("Use $root as project root") }
        }
    }
}

@Composable
private fun BuildOutputsCard(artifacts: List<WorkspaceEntry>, modifier: Modifier, onSave: (WorkspaceEntry) -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = PocketGreen.copy(alpha = 0.07f),
        border = BorderStroke(1.dp, PocketGreen.copy(alpha = 0.28f)),
    ) {
        Column(Modifier.padding(start = 14.dp, end = 10.dp, top = 10.dp, bottom = 4.dp)) {
            Text("Built APKs", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = PocketGreen)
            artifacts.take(3).forEach { artifact ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Android, null, Modifier.size(20.dp), tint = PocketGreen)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(artifact.name, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            entryDetails(artifact),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                    FilledTonalButton(
                        onClick = { onSave(artifact) },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = PocketGreen.copy(alpha = 0.18f),
                            contentColor = PocketGreen,
                        ),
                    ) {
                        Icon(Icons.Default.SaveAlt, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Save", fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Selection bar and export sheet
// ---------------------------------------------------------------------------------------------

@Composable
private fun SelectionBar(selection: Collection<WorkspaceEntry>, onClear: () -> Unit, onExport: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 6.dp,
        shadowElevation = 14.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
    ) {
        Row(Modifier.padding(start = 4.dp, end = 10.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClear) { Icon(Icons.Default.Close, "Clear selection") }
            Column(Modifier.weight(1f).clickable(onClick = onExport)) {
                AnimatedContent(
                    targetState = selection.size,
                    transitionSpec = {
                        val up = targetState > initialState
                        (slideInVertically { if (up) it else -it } + fadeIn())
                            .togetherWith(slideOutVertically { if (up) -it else it } + fadeOut())
                    },
                    label = "selectedCount",
                ) { count ->
                    Text("$count selected", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                }
                Text(
                    selectionSummary(selection),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Button(onClick = onExport, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)) {
                Icon(Icons.Default.FolderZip, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Export ZIP")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExportSheet(
    target: ExportTarget,
    projectSlug: String,
    zipName: String,
    selection: List<WorkspaceEntry>,
    includeBuildFiles: Boolean,
    onIncludeBuildFilesChange: (Boolean) -> Unit,
    onRemove: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
            Text(
                when (target) {
                    ExportTarget.PROJECT -> "Download whole project"
                    ExportTarget.SELECTION -> if (selection.size == 1) "Export 1 item" else "Export ${selection.size} items"
                },
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(2.dp))
            Text(zipName, fontSize = 13.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            when (target) {
                ExportTarget.PROJECT -> Row(verticalAlignment = Alignment.CenterVertically) {
                    FileTypeBadge(WorkspaceEntry(path = projectSlug, name = projectSlug, isDirectory = true, depth = 0))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Every file and folder in $projectSlug", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text(
                            "Packed in a $projectSlug/ folder inside the ZIP",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                ExportTarget.SELECTION -> LazyColumn(Modifier.heightIn(max = 280.dp)) {
                    items(selection, key = { it.path }) { entry -> SelectedItemRow(entry) { onRemove(entry.path) } }
                }
            }
            Spacer(Modifier.height(16.dp))
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(start = 14.dp, end = 10.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Include build outputs & caches", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text(
                            if (target == ExportTarget.SELECTION) {
                                "build, .gradle, .git, node_modules… inside the folders you picked. Folders you tick directly are always included."
                            } else {
                                "build, .gradle, .git, node_modules, APK files… Leave off to export only the source code."
                            },
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Switch(checked = includeBuildFiles, onCheckedChange = onIncludeBuildFilesChange)
                }
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onSave,
                enabled = target == ExportTarget.PROJECT || selection.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.Default.Download, null, Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text("Choose where to save", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun SelectedItemRow(entry: WorkspaceEntry, onRemove: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        FileTypeBadge(entry, size = 34)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.name, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                entry.path.substringBeforeLast('/', "").ifEmpty { "Project root" } + " · " + entryDetails(entry),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Default.Close, "Remove ${entry.name}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Export progress
// ---------------------------------------------------------------------------------------------

@Composable
private fun ExportOverlay(export: FileExportState?, onCancel: () -> Unit, onDismiss: () -> Unit, onOpenLocation: () -> Unit) {
    // Keep showing the last state while the overlay fades out.
    var last by remember { mutableStateOf(export) }
    if (export != null) last = export
    val running = export?.phase == FileExportPhase.SCANNING || export?.phase == FileExportPhase.WRITING
    BackHandler(enabled = export != null) { if (running) onCancel() else onDismiss() }
    AnimatedVisibility(visible = export != null, enter = fadeIn(tween(180)), exit = fadeOut(tween(200))) {
        val shown = last ?: return@AnimatedVisibility
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { if (!running) onDismiss() },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Card(
                modifier = Modifier
                    .padding(24.dp)
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .animateEnterExit(
                        enter = scaleIn(spring(dampingRatio = 0.75f), initialScale = 0.9f),
                        exit = scaleOut(targetScale = 0.95f),
                    )
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    ExportStatus(shown, onCancel, onDismiss, onOpenLocation)
                }
            }
        }
    }
}

@Composable
private fun ExportStatus(export: FileExportState, onCancel: () -> Unit, onDismiss: () -> Unit, onOpenLocation: () -> Unit) {
    val (icon, tint) = when (export.phase) {
        FileExportPhase.DONE -> Icons.Default.Check to PocketGreen
        FileExportPhase.FAILED -> Icons.Default.ErrorOutline to PocketRed
        else -> (if (export.isArchive) Icons.Default.FolderZip else Icons.Default.SaveAlt) to MaterialTheme.colorScheme.primary
    }
    Box(
        Modifier.size(64.dp).clip(CircleShape).background(tint.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, Modifier.size(32.dp), tint = tint)
    }
    Spacer(Modifier.height(14.dp))
    Text(
        when (export.phase) {
            FileExportPhase.SCANNING -> "Collecting files"
            FileExportPhase.WRITING -> if (export.isArchive) "Creating ZIP" else "Saving file"
            FileExportPhase.DONE -> if (export.isArchive) "ZIP saved" else "File saved"
            FileExportPhase.FAILED -> "Export failed"
        },
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
    )
    Spacer(Modifier.height(4.dp))
    Text(
        export.fileName,
        fontSize = 13.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(18.dp))
    when (export.phase) {
        FileExportPhase.SCANNING -> {
            LinearProgressIndicator(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)))
            Spacer(Modifier.height(10.dp))
            Text(
                if (export.filesTotal == 0) "Scanning project…" else "%,d files found".format(Locale.US, export.filesTotal),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(18.dp))
            OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
        }
        FileExportPhase.WRITING -> {
            val fraction = when {
                export.bytesTotal > 0 -> export.bytesDone.toFloat() / export.bytesTotal
                export.filesTotal > 0 -> export.filesDone.toFloat() / export.filesTotal
                else -> 0f
            }.coerceIn(0f, 1f)
            val animated by animateFloatAsState(fraction, tween(240), label = "exportProgress")
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Text(
                    "${(animated * 100).roundToInt()}%",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "${formatBytes(export.bytesDone)} of ${formatBytes(export.bytesTotal)}",
                    Modifier.padding(bottom = 5.dp),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { animated },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                drawStopIndicator = {},
            )
            Spacer(Modifier.height(10.dp))
            if (export.isArchive) {
                Text(
                    "%,d of %,d files".format(Locale.US, export.filesDone, export.filesTotal),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (export.currentPath.isNotEmpty()) {
                Text(
                    shortenPath(export.currentPath),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(18.dp))
            OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
        }
        FileExportPhase.DONE -> {
            Text(
                buildString {
                    if (export.isArchive) append("%,d files · ".format(Locale.US, export.filesDone))
                    append(formatBytes(export.bytesTotal))
                },
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (export.skippedFiles > 0) {
                Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, null, Modifier.size(16.dp), tint = PocketAmber)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "${export.skippedFiles} unreadable ${if (export.skippedFiles == 1) "file was" else "files were"} skipped",
                        fontSize = 12.sp,
                        color = PocketAmber,
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                ) {
                    Text("Done")
                }
                if (export.documentUri != null) {
                    Button(onClick = onOpenLocation, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.FolderOpen, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Open folder", maxLines = 1)
                    }
                }
            }
        }
        FileExportPhase.FAILED -> {
            Text(
                export.error ?: "Something went wrong while writing the file.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(18.dp))
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Close") }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------------------------

/** ACTION_CREATE_DOCUMENT with a per-launch file name and MIME type: input is (name, mimeType). */
private class CreateTypedDocument : ActivityResultContract<Pair<String, String>, Uri?>() {
    override fun createIntent(context: Context, input: Pair<String, String>): Intent =
        Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(input.second)
            .putExtra(Intent.EXTRA_TITLE, input.first)

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        if (resultCode == Activity.RESULT_OK) intent?.data else null
}

/** Ticks or unticks [entry]; ticking a folder drops anything inside it that it now covers. */
private fun toggled(selection: Map<String, WorkspaceEntry>, entry: WorkspaceEntry): Map<String, WorkspaceEntry> =
    if (entry.path in selection) selection - entry.path else withAdded(selection, listOf(entry))

private fun withAdded(selection: Map<String, WorkspaceEntry>, added: List<WorkspaceEntry>): Map<String, WorkspaceEntry> {
    val merged = selection + added.associateBy { it.path }
    val keep = WorkspaceFileOps.normalizeSelection(merged.keys).toSet()
    return merged.filterKeys { it in keep }
}

private fun selectionSummary(selection: Collection<WorkspaceEntry>): String {
    val folders = selection.count { it.isDirectory }
    val files = selection.size - folders
    val fileBytes = selection.filter { !it.isDirectory }.sumOf { it.sizeBytes }
    return buildList {
        if (folders > 0) add(if (folders == 1) "1 folder" else "$folders folders")
        if (files > 0) add((if (files == 1) "1 file" else "$files files") + " (${formatBytes(fileBytes)})")
    }.joinToString(" · ")
}

private fun entryDetails(entry: WorkspaceEntry): String =
    if (entry.isDirectory) {
        when (entry.childCount) {
            0 -> "Empty folder"
            1 -> "1 item"
            else -> "%,d items".format(Locale.US, entry.childCount)
        }
    } else {
        listOf(formatBytes(entry.sizeBytes), relativeTime(entry.modifiedMillis)).filter { it.isNotEmpty() }.joinToString(" · ")
    }

private fun mimeTypeFor(name: String): String {
    val extension = name.substringAfterLast('.', "").lowercase(Locale.US)
    return when (extension) {
        "apk" -> "application/vnd.android.package-archive"
        "aab" -> "application/octet-stream"
        else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/octet-stream"
    }
}

private fun shortenPath(path: String, max: Int = 46): String =
    if (path.length <= max) path else "…" + path.takeLast(max - 1)

private fun relativeTime(millis: Long): String =
    if (millis <= 0L) "" else DateUtils.getRelativeTimeSpanString(millis, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()

private fun formatBytes(bytes: Long): String = when {
    bytes < 1_024 -> "$bytes B"
    bytes < 1_048_576 -> "%.1f KB".format(Locale.US, bytes / 1_024.0)
    bytes < 1_073_741_824 -> "%.1f MB".format(Locale.US, bytes / 1_048_576.0)
    else -> "%.2f GB".format(Locale.US, bytes / 1_073_741_824.0)
}
