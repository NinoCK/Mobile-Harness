package com.jarves.mh.data

import com.jarves.mh.model.WorkspaceEntry
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * File-system operations behind the project Files tab: lazy directory listing, build-output
 * discovery and exporting (ZIP or raw single file). Everything is confined to the project root:
 * symlinks and paths that canonicalise outside the root are never listed or exported.
 *
 * By default exports carry the project's source only: build outputs, Gradle/IDE caches, VCS data
 * and dependency folders ([EXPORT_EXCLUDED_DIRS]) are skipped wherever they are found inside an
 * exported folder. A path the user selected explicitly is always exported, even an excluded one.
 * Passing `skipBuildFiles = false` exports everything except the agent's runtime metadata.
 */
object WorkspaceFileOps {

    /** One file or directory scheduled for export, with its root-relative path. */
    data class ExportItem(val file: File, val relativePath: String, val isDirectory: Boolean, val sizeBytes: Long)

    data class ExportPlan(val items: List<ExportItem>) {
        val fileCount: Int = items.count { !it.isDirectory }
        val totalBytes: Long = items.sumOf { if (it.isDirectory) 0L else it.sizeBytes }
    }

    data class ExportProgress(
        val filesDone: Int,
        val bytesDone: Long,
        val currentPath: String,
        val skippedFiles: Int,
    )

    /** Agent runtime state that lives inside the workspace but is not part of the user's project. */
    fun isRuntimeMetadata(relativePath: String): Boolean =
        relativePath == ".claude" || relativePath == ".claude.json" || relativePath.startsWith(".claude/")

    /** True for folders (build, .gradle, .git, node_modules, ...) that exports skip by default. */
    fun isExportExcludedName(name: String): Boolean = name in EXPORT_EXCLUDED_DIRS

    /**
     * The selected ancestor whose export would include [path], or null. With [skipBuildFiles] an
     * excluded folder between the two (e.g. `app/build` under a selected `app`) breaks coverage,
     * because the export skips it.
     */
    fun coveringSelection(path: String, selected: Set<String>, skipBuildFiles: Boolean = true): String? {
        var node = path.trim('/')
        while (node.isNotEmpty()) {
            if (skipBuildFiles && isExportExcludedName(node.substringAfterLast('/'))) return null
            val parent = node.substringBeforeLast('/', "")
            if (parent.isEmpty()) return null
            if (parent in selected) return parent
            node = parent
        }
        return null
    }

    /** Lists the direct children of [relativeDir] ("" for the project root). */
    fun listDirectory(root: File, relativeDir: String): List<WorkspaceEntry> {
        val directory = resolveInside(root, relativeDir) ?: return emptyList()
        if (!directory.isDirectory) return emptyList()
        val depth = if (relativeDir.isEmpty()) 0 else relativeDir.count { it == '/' } + 1
        val rootPath = root.canonicalFile.toPath()
        return directory.listFiles().orEmpty()
            .asSequence()
            .filter { !Files.isSymbolicLink(it.toPath()) }
            .mapNotNull { file ->
                val relative = if (relativeDir.isEmpty()) file.name else "$relativeDir/${file.name}"
                if (isRuntimeMetadata(relative) || !isInside(rootPath, file)) return@mapNotNull null
                val isDirectory = file.isDirectory
                WorkspaceEntry(
                    path = relative,
                    name = file.name,
                    isDirectory = isDirectory,
                    depth = depth,
                    sizeBytes = if (isDirectory) 0L else file.length(),
                    childCount = if (isDirectory) countChildren(file, relative) else 0,
                    modifiedMillis = file.lastModified(),
                )
            }
            .toList()
    }

    /** Finds installable build outputs (APK/AAB) under `<module>/build/outputs`, newest first. */
    fun findBuildArtifacts(root: File, limit: Int = 6): List<WorkspaceEntry> {
        if (!root.isDirectory) return emptyList()
        val moduleCandidates = buildList {
            add(root)
            root.childDirectories().forEach { child ->
                add(child)
                addAll(child.childDirectories())
            }
        }.take(MAX_ARTIFACT_MODULE_CANDIDATES)
        return moduleCandidates.asSequence()
            .map { File(it, "build/outputs") }
            .filter { it.isDirectory && !Files.isSymbolicLink(it.toPath()) && isInside(root, it) }
            .flatMap { outputs ->
                outputs.walkTopDown()
                    .maxDepth(6)
                    .onEnter { !Files.isSymbolicLink(it.toPath()) }
                    .filter { it.isFile && it.extension.lowercase() in INSTALLABLE_EXTENSIONS }
            }
            .distinctBy { it.absolutePath }
            .map { file ->
                val relative = file.relativeTo(root).invariantSeparatorsPath
                WorkspaceEntry(
                    path = relative,
                    name = file.name,
                    isDirectory = false,
                    depth = relative.count { it == '/' },
                    sizeBytes = file.length(),
                    modifiedMillis = file.lastModified(),
                )
            }
            .sortedByDescending { it.modifiedMillis }
            .take(limit)
            .toList()
    }

    /** Drops duplicates and any path whose export is already covered by a selected ancestor. */
    fun normalizeSelection(paths: Collection<String>, skipBuildFiles: Boolean = true): List<String> {
        val cleaned = paths.map { it.trim('/') }.filter { it.isNotEmpty() }.distinct().sorted()
        if (cleaned.isEmpty()) return emptyList()
        val selected = cleaned.toSet()
        return cleaned.filter { path -> coveringSelection(path, selected, skipBuildFiles) == null }
    }

    /**
     * Collects every file and directory to export. [paths] null (or containing "") exports the whole
     * project; otherwise only the selected paths. With [skipBuildFiles] the [EXPORT_EXCLUDED_DIRS]
     * are skipped inside any exported folder and a whole-project export also leaves out loose
     * APK/AAB packages. [onScanned] receives the running file count so the UI can show scan progress.
     */
    fun planExport(
        root: File,
        paths: Collection<String>?,
        skipBuildFiles: Boolean = true,
        checkActive: () -> Unit = {},
        onScanned: (Int) -> Unit = {},
    ): ExportPlan {
        val wholeProject = paths == null || paths.any { it.trim('/').isEmpty() }
        val selection = if (wholeProject) listOf("") else normalizeSelection(paths.orEmpty(), skipBuildFiles)
        val rootPath = root.canonicalFile.toPath()
        val items = mutableListOf<ExportItem>()
        val seen = hashSetOf<String>()
        var files = 0
        selection.forEach { selected ->
            val start = resolveInside(root, selected) ?: return@forEach
            if (!start.exists() || Files.isSymbolicLink(start.toPath())) return@forEach
            start.walkTopDown()
                .onEnter { directory ->
                    directory == start || (
                        !(skipBuildFiles && isExportExcludedName(directory.name)) &&
                            !Files.isSymbolicLink(directory.toPath()) &&
                            !isRuntimeMetadata(directory.relativeTo(root).invariantSeparatorsPath) &&
                            isInside(rootPath, directory)
                        )
                }
                .forEach { file ->
                    if (file == root) return@forEach
                    val relative = file.relativeTo(root).invariantSeparatorsPath
                    if (isRuntimeMetadata(relative) || Files.isSymbolicLink(file.toPath()) || !isInside(rootPath, file)) return@forEach
                    val isDirectory = file.isDirectory
                    if (skipBuildFiles && wholeProject && !isDirectory && file.extension.lowercase() in INSTALLABLE_EXTENSIONS) return@forEach
                    if (!seen.add(relative)) return@forEach
                    items += ExportItem(file, relative, isDirectory, if (isDirectory) 0L else file.length())
                    if (!isDirectory) {
                        files++
                        if (files % 250 == 0) {
                            checkActive()
                            onScanned(files)
                        }
                    }
                }
        }
        onScanned(files)
        return ExportPlan(items)
    }

    /**
     * Writes [plan] as a ZIP whose entries live under [rootFolder]/. Files that vanish or cannot be
     * read mid-export are skipped and counted instead of failing the whole archive.
     */
    fun writeZip(
        plan: ExportPlan,
        output: OutputStream,
        rootFolder: String,
        checkActive: () -> Unit = {},
        onProgress: (ExportProgress) -> Unit = {},
    ) {
        var filesDone = 0
        var bytesDone = 0L
        var skipped = 0
        val prefix = rootFolder.trim('/').let { if (it.isEmpty()) "" else "$it/" }
        val buffer = ByteArray(COPY_BUFFER_BYTES)
        ZipOutputStream(output.buffered(COPY_BUFFER_BYTES)).use { zip ->
            if (prefix.isNotEmpty()) {
                zip.putNextEntry(ZipEntry(prefix))
                zip.closeEntry()
            }
            plan.items.forEach { item ->
                checkActive()
                if (item.isDirectory) {
                    zip.putNextEntry(ZipEntry("$prefix${item.relativePath}/").apply { time = item.file.lastModified() })
                    zip.closeEntry()
                    return@forEach
                }
                val input = runCatching { item.file.inputStream() }.getOrNull()
                if (input == null) {
                    skipped++
                    bytesDone += item.sizeBytes
                    onProgress(ExportProgress(filesDone, bytesDone, item.relativePath, skipped))
                    return@forEach
                }
                zip.setLevel(if (item.file.extension.lowercase() in PRECOMPRESSED_EXTENSIONS) Deflater.NO_COMPRESSION else Deflater.DEFAULT_COMPRESSION)
                zip.putNextEntry(ZipEntry("$prefix${item.relativePath}").apply { time = item.file.lastModified() })
                val startBytes = bytesDone
                input.use { stream ->
                    copyWithProgress(stream, zip, buffer, checkActive) { copied ->
                        bytesDone = startBytes + copied
                        onProgress(ExportProgress(filesDone, bytesDone, item.relativePath, skipped))
                    }
                }
                zip.closeEntry()
                filesDone++
                onProgress(ExportProgress(filesDone, bytesDone, item.relativePath, skipped))
            }
        }
    }

    /** Copies one file verbatim (used when exactly one file is exported). */
    fun writeSingleFile(
        file: File,
        relativePath: String,
        output: OutputStream,
        checkActive: () -> Unit = {},
        onProgress: (ExportProgress) -> Unit = {},
    ) {
        val buffer = ByteArray(COPY_BUFFER_BYTES)
        output.buffered(COPY_BUFFER_BYTES).use { out ->
            file.inputStream().use { input ->
                copyWithProgress(input, out, buffer, checkActive) { copied ->
                    onProgress(ExportProgress(0, copied, relativePath, 0))
                }
            }
        }
        onProgress(ExportProgress(1, file.length(), relativePath, 0))
    }

    /** Resolves a root-relative path, refusing anything that escapes the root. */
    fun resolveInside(root: File, relativePath: String): File? {
        val trimmed = relativePath.trim('/')
        val file = if (trimmed.isEmpty()) root else File(root, trimmed)
        return file.takeIf { isInside(root, it) }
    }

    private fun isInside(root: File, file: File): Boolean = isInside(root.canonicalFile.toPath(), file)

    private fun isInside(rootPath: Path, file: File): Boolean = runCatching {
        file.canonicalFile.toPath().startsWith(rootPath)
    }.getOrDefault(false)

    private fun countChildren(directory: File, relative: String): Int {
        val names = directory.list() ?: return 0
        return names.count { !isRuntimeMetadata("$relative/$it") }
    }

    private fun File.childDirectories(): List<File> =
        listFiles().orEmpty().filter {
            it.isDirectory && !Files.isSymbolicLink(it.toPath()) && it.name !in ARTIFACT_SEARCH_SKIP
        }

    private inline fun copyWithProgress(
        input: InputStream,
        output: OutputStream,
        buffer: ByteArray,
        checkActive: () -> Unit,
        onCopied: (Long) -> Unit,
    ) {
        var copied = 0L
        var sinceReport = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            output.write(buffer, 0, read)
            copied += read
            sinceReport += read
            if (sinceReport >= PROGRESS_STEP_BYTES) {
                sinceReport = 0L
                checkActive()
                onCopied(copied)
            }
        }
        onCopied(copied)
    }

    private const val COPY_BUFFER_BYTES = 64 * 1024
    private const val PROGRESS_STEP_BYTES = 512L * 1024L
    private const val MAX_ARTIFACT_MODULE_CANDIDATES = 400
    private val INSTALLABLE_EXTENSIONS = setOf("apk", "aab")
    private val EXPORT_EXCLUDED_DIRS = setOf(
        "build", ".gradle", ".kotlin", ".cxx", ".externalNativeBuild", ".idea", ".git", ".claude",
        "node_modules", ".next", ".cache", ".venv", "venv", "__pycache__",
    )
    private val ARTIFACT_SEARCH_SKIP = setOf(".git", ".gradle", ".claude", "node_modules", "build", ".idea", ".venv", "venv")
    private val PRECOMPRESSED_EXTENSIONS = setOf(
        "apk", "aab", "jar", "aar", "zip", "gz", "tgz", "xz", "bz2", "7z", "zst", "br",
        "png", "jpg", "jpeg", "webp", "gif", "avif", "mp3", "mp4", "m4a", "ogg", "webm", "woff", "woff2",
    )
}
