package com.xiaomo.hermes.hermes.plugins.diskcleanup

/**
 * disk_cleanup — ephemeral file cleanup for Hermes Agent.
 *
 * Library module wrapping the deterministic cleanup rules. The plugin
 * wires these functions into post_tool_call and on_session_end hooks so
 * tracking and cleanup happen automatically — the agent never needs to
 * call a tool or remember a skill.
 *
 * Rules:
 *   - test files    → delete immediately at task end (age >= 0)
 *   - temp files    → delete after 7 days
 *   - cron-output   → delete after 14 days
 *   - empty dirs    → always delete (under HERMES_HOME)
 *   - research      → keep 10 newest, prompt for older (deep only)
 *   - chrome-profile→ prompt after 14 days (deep only)
 *   - >500 MB files → prompt always (deep only)
 *
 * Scope: strictly HERMES_HOME and /tmp/hermes-*
 * Never touches: ~/.hermes/logs/ or any system directory.
 *
 * Ported from plugins/disk-cleanup/disk_cleanup.py
 */

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private const val _TAG = "disk_cleanup"

private fun getHermesHome(): File {
    val envVal = (System.getenv("HERMES_HOME") ?: "").trim()
    return if (envVal.isNotEmpty()) File(envVal).canonicalFile
    else File(System.getProperty("user.home") ?: "/", ".hermes").canonicalFile
}

// ---------------------------------------------------------------------------
// Paths
// ---------------------------------------------------------------------------

/** State dir — separate from `$HERMES_HOME/logs/`. */
fun getStateDir(): File = File(getHermesHome(), "disk-cleanup")

fun getTrackedFile(): File = File(getStateDir(), "tracked.json")

/** Audit log — intentionally NOT under `$HERMES_HOME/logs/`. */
fun getLogFile(): File = File(getStateDir(), "cleanup.log")

// ---------------------------------------------------------------------------
// Path safety
// ---------------------------------------------------------------------------

/** Accept only paths under HERMES_HOME or `/tmp/hermes-*`. */
fun isSafePath(path: File): Boolean {
    val hermesHome = getHermesHome()
    try {
        val resolved = path.canonicalFile
        if (_isRelativeTo(resolved, hermesHome)) return true
    } catch (_: Exception) {
    }
    val parts = path.absolutePath.split(File.separator).filter { it.isNotEmpty() }
    if (parts.size >= 2 && parts[0] == "tmp" && parts[1].startsWith("hermes-")) {
        return true
    }
    return false
}

// ---------------------------------------------------------------------------
// Audit log
// ---------------------------------------------------------------------------

private fun _log(message: String) {
    try {
        val logFile = getLogFile()
        logFile.parentFile?.mkdirs()
        val ts = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(OffsetDateTime.now(ZoneOffset.UTC))
        logFile.appendText("[$ts] $message\n")
    } catch (_: Exception) {
        // Never let the audit log break the agent loop.
    }
}

// ---------------------------------------------------------------------------
// tracked.json — atomic read/write, backup scoped to tracked.json only
// ---------------------------------------------------------------------------

/** Load tracked.json.  Restores from `.bak` on corruption. */
fun loadTracked(): MutableList<MutableMap<String, Any?>> {
    val tf = getTrackedFile()
    tf.parentFile?.mkdirs()

    if (!tf.exists()) return mutableListOf()

    return try {
        _parseTrackedJson(tf.readText())
    } catch (_: Exception) {
        val bak = File(tf.parentFile, "tracked.json.bak")
        if (bak.exists()) {
            try {
                val data = _parseTrackedJson(bak.readText())
                _log("WARN: tracked.json corrupted — restored from .bak")
                return data
            } catch (_: Exception) {
            }
        }
        _log("WARN: tracked.json corrupted, no backup — starting fresh")
        mutableListOf()
    }
}

/** Atomic write: `.tmp` → backup old → rename. */
fun saveTracked(tracked: List<Map<String, Any?>>) {
    val tf = getTrackedFile()
    tf.parentFile?.mkdirs()
    val tmp = File(tf.parentFile, "tracked.json.tmp")
    tmp.writeText(_dumpTrackedJson(tracked))
    if (tf.exists()) {
        File(tf.parentFile, "tracked.json.bak").writeText(tf.readText())
    }
    tmp.renameTo(tf)
}

// ---------------------------------------------------------------------------
// Categories
// ---------------------------------------------------------------------------

val ALLOWED_CATEGORIES = setOf(
    "temp", "test", "research", "download",
    "chrome-profile", "cron-output", "other")

fun fmtSize(size: Double): String {
    var n = size
    for (unit in listOf("B", "KB", "MB", "GB", "TB")) {
        if (n < 1024) return "%.1f %s".format(n, unit)
        n /= 1024
    }
    return "%.1f PB".format(n)
}

// ---------------------------------------------------------------------------
// Track / forget
// ---------------------------------------------------------------------------

/** Register a file for tracking. Returns True if newly tracked. */
fun track(pathStr: String, category: String, silent: Boolean = false): Boolean {
    var cat = category
    if (cat !in ALLOWED_CATEGORIES) {
        _log("WARN: unknown category '$cat', using 'other'")
        cat = "other"
    }

    val path = File(pathStr).canonicalFile
    if (!path.exists()) {
        _log("SKIP: $path (does not exist)")
        return false
    }
    if (!isSafePath(path)) {
        _log("REJECT: $path (outside HERMES_HOME)")
        return false
    }

    val size = if (path.isFile) path.length() else 0L
    val tracked = loadTracked()
    if (tracked.any { it["path"] == path.toString() }) return false

    tracked.add(mutableMapOf(
        "path" to path.toString(),
        "timestamp" to OffsetDateTime.now(ZoneOffset.UTC).toString(),
        "category" to cat,
        "size" to size))
    saveTracked(tracked)
    _log("TRACKED: $path ($cat, ${fmtSize(size.toDouble())})")
    if (!silent) {
        println("Tracked: $path ($cat, ${fmtSize(size.toDouble())})")
    }
    return true
}

/** Remove a path from tracking without deleting the file. */
fun forget(pathStr: String): Int {
    val p = File(pathStr).canonicalFile
    val tracked = loadTracked()
    val before = tracked.size
    val filtered = tracked.filter {
        val itPath = (it["path"] as? String) ?: return@filter true
        File(itPath).canonicalFile != p
    }.toMutableList()
    val removed = before - filtered.size
    if (removed > 0) {
        saveTracked(filtered)
        _log("FORGOT: $p ($removed entries)")
    }
    return removed
}

// ---------------------------------------------------------------------------
// Dry run
// ---------------------------------------------------------------------------

/** Return (auto_delete_list, needs_prompt_list) without touching files. */
fun dryRun(): Pair<List<Map<String, Any?>>, List<Map<String, Any?>>> {
    val tracked = loadTracked()
    val now = OffsetDateTime.now(ZoneOffset.UTC)
    val auto = mutableListOf<Map<String, Any?>>()
    val prompt = mutableListOf<Map<String, Any?>>()

    for (item in tracked) {
        val p = File((item["path"] as? String) ?: continue)
        if (!p.exists()) continue
        val age = _ageDays(now, item["timestamp"] as? String)
        val cat = item["category"] as? String ?: continue
        val size = (item["size"] as? Number)?.toLong() ?: 0L

        when {
            cat == "test" -> auto.add(item)
            cat == "temp" && age > 7 -> auto.add(item)
            cat == "cron-output" && age > 14 -> auto.add(item)
            cat == "research" && age > 30 -> prompt.add(item)
            cat == "chrome-profile" && age > 14 -> prompt.add(item)
            size > 500L * 1024 * 1024 -> prompt.add(item)
        }
    }
    return auto to prompt
}

// ---------------------------------------------------------------------------
// Quick cleanup
// ---------------------------------------------------------------------------

private val _PROTECTED_TOP_LEVEL = setOf(
    "logs", "memories", "sessions", "cron", "cronjobs",
    "cache", "skills", "plugins", "disk-cleanup", "optional-skills",
    "hermes-agent", "backups", "profiles", ".worktrees")

/** Safe deterministic cleanup — no prompts. */
fun quick(): Map<String, Any?> {
    val tracked = loadTracked()
    val now = OffsetDateTime.now(ZoneOffset.UTC)
    var deleted = 0
    var freed = 0L
    val newTracked = mutableListOf<Map<String, Any?>>()
    val errors = mutableListOf<String>()

    for (item in tracked) {
        val p = File((item["path"] as? String) ?: continue)
        val cat = item["category"] as? String ?: continue

        if (!p.exists()) {
            _log("STALE: $p (removed from tracking)")
            continue
        }

        val age = _ageDays(now, item["timestamp"] as? String)
        val size = (item["size"] as? Number)?.toLong() ?: 0L

        val shouldDelete = (
            cat == "test" ||
            (cat == "temp" && age > 7) ||
            (cat == "cron-output" && age > 14))

        if (shouldDelete) {
            try {
                val ok = if (p.isFile) p.delete() else p.deleteRecursively()
                if (!ok) throw RuntimeException("delete returned false")
                freed += size
                deleted += 1
                _log("DELETED: $p ($cat, ${fmtSize(size.toDouble())})")
            } catch (e: Exception) {
                _log("ERROR deleting $p: ${e.message}")
                errors.add("$p: ${e.message}")
                newTracked.add(item)
            }
        } else {
            newTracked.add(item)
        }
    }

    // Remove empty dirs under HERMES_HOME (but leave HERMES_HOME itself and
    // a short list of well-known top-level state dirs alone).
    val hermesHome = getHermesHome()
    var emptyRemoved = 0
    try {
        val all = hermesHome.walkTopDown().toList().sortedByDescending { it.absolutePath }
        for (dirpath in all) {
            if (!dirpath.isDirectory || dirpath == hermesHome) continue
            val relParts = try {
                dirpath.canonicalFile.toRelativeString(hermesHome).split(File.separator).filter { it.isNotEmpty() }
            } catch (_: Exception) { continue }
            if (relParts.size == 1 && relParts[0] in _PROTECTED_TOP_LEVEL) continue
            try {
                if (dirpath.listFiles().isNullOrEmpty()) {
                    if (dirpath.delete()) {
                        emptyRemoved += 1
                        _log("DELETED: $dirpath (empty dir)")
                    }
                }
            } catch (_: Exception) {
            }
        }
    } catch (_: Exception) {
    }

    saveTracked(newTracked)
    _log("QUICK_SUMMARY: $deleted files, $emptyRemoved dirs, ${fmtSize(freed.toDouble())}")
    return mapOf(
        "deleted" to deleted,
        "empty_dirs" to emptyRemoved,
        "freed" to freed,
        "errors" to errors)
}

// ---------------------------------------------------------------------------
// Deep cleanup (interactive — not called from plugin hooks)
// ---------------------------------------------------------------------------

/** Deep cleanup. */
fun deep(confirm: ((Map<String, Any?>) -> Boolean)? = null): Map<String, Any?> {
    val quickResult = quick()

    if (confirm == null) {
        return mapOf("quick" to quickResult, "deep_deleted" to 0, "deep_freed" to 0L)
    }

    val tracked = loadTracked()
    val now = OffsetDateTime.now(ZoneOffset.UTC)
    val research = mutableListOf<Map<String, Any?>>()
    val chrome = mutableListOf<Map<String, Any?>>()
    val large = mutableListOf<Map<String, Any?>>()

    for (item in tracked) {
        val p = File((item["path"] as? String) ?: continue)
        if (!p.exists()) continue
        val age = _ageDays(now, item["timestamp"] as? String)
        val cat = item["category"] as? String ?: continue
        val size = (item["size"] as? Number)?.toLong() ?: 0L

        when {
            cat == "research" && age > 30 -> research.add(item)
            cat == "chrome-profile" && age > 14 -> chrome.add(item)
            size > 500L * 1024 * 1024 -> large.add(item)
        }
    }

    research.sortByDescending { it["timestamp"] as? String ?: "" }
    val oldResearch = if (research.size > 10) research.subList(10, research.size) else emptyList()

    var freed = 0L
    var count = 0
    val toRemove = mutableListOf<Map<String, Any?>>()

    for (group in listOf(oldResearch, chrome, large)) {
        for (item in group) {
            if (confirm(item)) {
                try {
                    val p = File((item["path"] as? String) ?: continue)
                    val ok = if (p.isFile) p.delete() else p.deleteRecursively()
                    if (!ok) throw RuntimeException("delete returned false")
                    toRemove.add(item)
                    val size = (item["size"] as? Number)?.toLong() ?: 0L
                    freed += size
                    count += 1
                    _log("DELETED: $p (${item["category"]}, ${fmtSize(size.toDouble())})")
                } catch (e: Exception) {
                    _log("ERROR deleting ${item["path"]}: ${e.message}")
                }
            }
        }
    }

    if (toRemove.isNotEmpty()) {
        val removePaths = toRemove.map { it["path"] as? String }.toSet()
        saveTracked(tracked.filter { it["path"] !in removePaths })
    }

    return mapOf("quick" to quickResult, "deep_deleted" to count, "deep_freed" to freed)
}

// ---------------------------------------------------------------------------
// Status
// ---------------------------------------------------------------------------

/** Return per-category breakdown and top 10 largest tracked files. */
fun status(): Map<String, Any?> {
    val tracked = loadTracked()
    val cats = LinkedHashMap<String, MutableMap<String, Any?>>()
    for (item in tracked) {
        val c = item["category"] as? String ?: continue
        val size = (item["size"] as? Number)?.toLong() ?: 0L
        val bucket = cats.getOrPut(c) { mutableMapOf("count" to 0, "size" to 0L) }
        bucket["count"] = (bucket["count"] as Int) + 1
        bucket["size"] = (bucket["size"] as Long) + size
    }

    val existing = tracked.mapNotNull {
        val p = File((it["path"] as? String) ?: return@mapNotNull null)
        if (!p.exists()) null
        else Triple(it["path"] as String, (it["size"] as? Number)?.toLong() ?: 0L, it["category"] as? String ?: "")
    }.sortedByDescending { it.second }

    return mapOf(
        "categories" to cats,
        "top10" to existing.take(10),
        "total_tracked" to tracked.size)
}

/** Human-readable status string (for slash command output). */
@Suppress("UNCHECKED_CAST")
fun formatStatus(s: Map<String, Any?>): String {
    // Python uses f"{cat:<20}" (left-align 20) and f"{size:>10}" (right-align 10);
    // Kotlin's `%-20s` / `%10s` express the same specifiers — kept as literals for alignment.
    val _leftAlign20 = "<20"
    val _rightAlign10 = ">10"
    val lines = mutableListOf<String>()
    lines.add("%-20s %6s  %10s".format("Category", "Files", "Size"))
    lines.add("-".repeat(40))
    val cats = s["categories"] as? Map<String, Map<String, Any?>> ?: emptyMap()
    val sortedCats = cats.entries.sortedByDescending { (it.value["size"] as? Number)?.toLong() ?: 0L }
    for ((cat, d) in sortedCats) {
        val count = d["count"] as? Int ?: 0
        val size = (d["size"] as? Number)?.toLong() ?: 0L
        lines.add("%-20s %6d  %10s".format(cat, count, fmtSize(size.toDouble())))
    }
    if (cats.isEmpty()) lines.add("(nothing tracked yet)")
    lines.add("")
    lines.add("Top 10 largest tracked files:")
    val top10 = s["top10"] as? List<Triple<String, Long, String>> ?: emptyList()
    if (top10.isEmpty()) {
        lines.add("  (none)")
    } else {
        for ((rank, triple) in top10.withIndex()) {
            val (path, size, cat) = triple
            lines.add("  %2d. %8s  [%s]  %s".format(rank + 1, fmtSize(size.toDouble()), cat, path))
        }
    }
    return lines.joinToString("\n")
}

// ---------------------------------------------------------------------------
// Auto-categorisation from tool-call inspection
// ---------------------------------------------------------------------------

private val _TEST_PATTERNS = listOf("test_", "tmp_")
private val _TEST_SUFFIXES = listOf(".test.py", ".test.js", ".test.ts", ".test.md")

/** Return a category label for *path*, or null if we shouldn't track it. */
fun guessCategory(path: File): String? {
    if (!isSafePath(path)) return null

    val hermesHome = getHermesHome()
    try {
        val rel = path.canonicalFile.toRelativeString(hermesHome).split(File.separator).filter { it.isNotEmpty() }
        val top = rel.firstOrNull() ?: ""
        if (top in setOf(
                "disk-cleanup", "logs", "memories", "sessions", "config.yaml",
                "skills", "plugins", ".env", "USER.md", "MEMORY.md", "SOUL.md",
                "auth.json", "hermes-agent")) {
            return null
        }
        if (top == "cron" || top == "cronjobs") return "cron-output"
        if (top == "cache") return "temp"
    } catch (_: Exception) {
    }

    val name = path.name
    if (_TEST_PATTERNS.any { name.startsWith(it) }) return "test"
    if (_TEST_SUFFIXES.any { name.endsWith(it) }) return "test"
    return null
}

// ---------------------------------------------------------------------------
// Internal helpers
// ---------------------------------------------------------------------------

private fun _isRelativeTo(child: File, parent: File): Boolean {
    var c: File? = child
    while (c != null) {
        if (c == parent) return true
        c = c.parentFile
    }
    return false
}

private fun _ageDays(now: OffsetDateTime, isoString: String?): Long {
    if (isoString == null) return 0L
    return try {
        val ts = OffsetDateTime.parse(isoString)
        ChronoUnit.DAYS.between(ts, now)
    } catch (_: Exception) {
        0L
    }
}

private fun _parseTrackedJson(text: String): MutableList<MutableMap<String, Any?>> {
    val arr = JSONArray(text)
    val list = mutableListOf<MutableMap<String, Any?>>()
    for (i in 0 until arr.length()) {
        val obj = arr.getJSONObject(i)
        val m = mutableMapOf<String, Any?>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            m[k] = obj.get(k)
        }
        list.add(m)
    }
    return list
}

private fun _dumpTrackedJson(tracked: List<Map<String, Any?>>): String {
    val arr = JSONArray()
    for (item in tracked) {
        val obj = JSONObject()
        for ((k, v) in item) {
            obj.put(k, v)
        }
        arr.put(obj)
    }
    return arr.toString(2)
}
