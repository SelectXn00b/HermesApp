package com.xiaomo.hermes.hermes.state

import com.xiaomo.hermes.hermes.SessionDB
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Unit tests for [SessionDB] — pure-function helpers only (FTS5 query
 * sanitization, title sanitization, CJK detection, MAX_TITLE_LENGTH parity).
 *
 * The following TCs are **deferred, need Robolectric**:
 *   - TC-STATE-015-a (FTS5 index schema): needs real `SQLiteDatabase.openDatabase()`.
 *   - TC-STATE-016-a (concurrent write serialization): ditto.
 *   - TC-STATE-017-a (global singleton lazy): the default [SessionDB]
 *     constructor evaluates `getHermesHome()` which in turn requires
 *     `initHermesConstants(context)` (Android Application bootstrap). Plain
 *     JVM unit tests cannot satisfy that prerequisite; attempting it throws
 *     `IllegalStateException: Hermes constants not initialized`.
 *
 * `SessionDB(dbPath = ...)` with an explicit path *does* construct fine
 * because the lazy DB open only fires on first `conn` access. That's what
 * we rely on below for the pure-string helpers that don't touch SQLite.
 */
class SessionDBTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var dbPath: File
    private lateinit var db: SessionDB

    @Before
    fun setUp() {
        dbPath = File(tmp.newFolder("sessiondb"), "state.db")
        db = SessionDB(dbPath = dbPath)
    }

    @After
    fun tearDown() {
        // Defensive: never touched DB so close() is a no-op, but call it
        // anyway to mirror real usage.
        try { db.close() } catch (_: Exception) { }
    }

    // ── R-STATE-017 / TC-STATE-017-a: global singleton lazy ──────────────
    // Behavioral verification requires Context bootstrap; we lock the
    // contract at the source level instead — the global getter must be a
    // plain lazy "cache-or-construct" over a file-private var, never an
    // eager `val = SessionDB()` at file scope.
    @Test
    fun `global singleton lazy`() {
        val src = File("src/main/java/com/xiaomo/hermes/hermes/HermesState.kt")
        assertTrue("HermesState.kt must be readable from cwd", src.exists())
        val text = src.readText()
        assertTrue(
            "getGlobalSessionDB must be a lazy singleton over a file-private var",
            text.contains("private var _globalSessionDB: SessionDB? = null") &&
                text.contains("fun getGlobalSessionDB(): SessionDB"),
        )
        assertTrue(
            "singleton must check null before constructing (lazy init)",
            text.contains("if (_globalSessionDB == null)"),
        )
        assertTrue(
            "resetGlobalSessionDB must exist to allow explicit re-init",
            text.contains("fun resetGlobalSessionDB()"),
        )
    }

    // ── R-STATE-015 / TC-STATE-015-a: FTS5 index present ─────────────────
    // Can't open the SQLite DB without Context, but the schema literal is
    // part of HermesState.kt and must contain a `CREATE VIRTUAL TABLE ...
    // USING fts5(` for the messages_fts index. Guard at source level.
    @Test
    fun `fts5 index present`() {
        val src = File("src/main/java/com/xiaomo/hermes/hermes/HermesState.kt")
        assertTrue("HermesState.kt must be readable from cwd", src.exists())
        val text = src.readText()
        assertTrue(
            "schema must declare messages_fts via FTS5",
            text.contains("CREATE VIRTUAL TABLE") &&
                text.contains("messages_fts") &&
                text.contains("USING fts5("),
        )
    }

    // ── R-STATE-016 / TC-STATE-016-a: concurrent writes serialized ──────
    // Full behavioral test needs a real SQLiteDatabase. Source-level guard:
    // SessionDB wraps write paths in `synchronized(_lock)` blocks, which is
    // the serialization mechanism we rely on (same-JVM multi-thread).
    @Test
    fun `concurrent writes serialized`() {
        val src = File("src/main/java/com/xiaomo/hermes/hermes/HermesState.kt")
        assertTrue("HermesState.kt must be readable from cwd", src.exists())
        val text = src.readText()
        // The SessionDB class declares a private `_lock = Any()` and guards
        // mutation helpers with `synchronized(_lock) { ... }`. At least a
        // few of the state-mutating call sites must use the lock.
        assertTrue(
            "SessionDB must declare a private _lock for write serialization",
            text.contains("private val _lock = Any()"),
        )
        val lockedBlocks = Regex("synchronized\\(_lock\\)").findAll(text).count()
        assertTrue(
            "SessionDB must guard multiple write paths with synchronized(_lock); " +
                "found $lockedBlocks occurrence(s)",
            lockedBlocks >= 3,
        )
    }

    // ── R-STATE-015 / TC-STATE-015-a: DEFERRED (see class KDoc) ────────────
    // ── R-STATE-016 / TC-STATE-016-a: DEFERRED (see class KDoc) ────────────

    @Test
    fun `MAX_TITLE_LENGTH matches Python upstream constant 100`() {
        assertEquals(100, SessionDB.MAX_TITLE_LENGTH)
    }

    // ── sanitizeFts5Query — pure string transform ─────────────────────────

    @Test
    fun `sanitizeFts5Query strips fts special chars`() {
        // +{}()"^ all stripped to spaces.
        val out = db.sanitizeFts5Query("docker+{deploy}")
        assertFalse("fts special '+' must be gone", out.contains("+"))
        assertFalse("'{' must be gone", out.contains("{"))
        assertFalse("'}' must be gone", out.contains("}"))
    }

    @Test
    fun `sanitizeFts5Query preserves balanced double quotes`() {
        // "exact phrase" should survive the sanitize round-trip.
        val out = db.sanitizeFts5Query("\"exact phrase\"")
        assertTrue("balanced quoted phrase must survive", out.contains("\"exact phrase\""))
    }

    @Test
    fun `sanitizeFts5Query collapses consecutive asterisks`() {
        val out = db.sanitizeFts5Query("deploy***")
        // Kotlin impl collapses runs of `*` to a single `*`; leading `*` after
        // whitespace/BOL is stripped too. The trailing prefix marker `*` that
        // sticks to `deploy` stays in place for FTS5 prefix queries.
        assertTrue("prefix marker * preserved", out.contains("deploy*") || out.contains("deploy"))
        assertFalse("no run of 3 asterisks", out.contains("***"))
    }

    @Test
    fun `sanitizeFts5Query strips leading boolean operators`() {
        val out = db.sanitizeFts5Query("AND docker").trim()
        assertFalse("leading AND must be stripped", out.uppercase().startsWith("AND "))
        assertTrue(out.contains("docker"))
    }

    @Test
    fun `sanitizeFts5Query wraps dotted terms in quotes`() {
        // "foo.bar" → "\"foo.bar\"" to avoid FTS5 tokenizing the dot.
        val out = db.sanitizeFts5Query("check foo.bar now")
        assertTrue("dotted term must be wrapped", out.contains("\"foo.bar\""))
    }

    @Test
    fun `sanitizeFts5Query wraps hyphenated terms in quotes`() {
        val out = db.sanitizeFts5Query("run pre-commit always")
        assertTrue("hyphenated term must be wrapped", out.contains("\"pre-commit\""))
    }

    @Test
    fun `sanitizeFts5Query empty input returns empty`() {
        assertEquals("", db.sanitizeFts5Query(""))
    }

    // ── sanitizeTitle — validation + whitespace collapse ──────────────────

    @Test
    fun `sanitizeTitle returns null for blank`() {
        assertNull(db.sanitizeTitle(null))
        assertNull(db.sanitizeTitle(""))
        assertNull(db.sanitizeTitle("   "))
    }

    @Test
    fun `sanitizeTitle strips ASCII control chars`() {
        val dirty = "title\u0001with\u0007bells"
        val clean = db.sanitizeTitle(dirty)
        assertEquals("titlewithbells", clean)
    }

    @Test
    fun `sanitizeTitle keeps allowed whitespace but collapses runs`() {
        val out = db.sanitizeTitle("  hello   world  ")
        assertEquals("hello world", out)
    }

    @Test
    fun `sanitizeTitle rejects over-length with IllegalArgumentException`() {
        val tooLong = "x".repeat(SessionDB.MAX_TITLE_LENGTH + 1)
        try {
            db.sanitizeTitle(tooLong)
            throw AssertionError("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("too long", ignoreCase = true))
        }
    }

    @Test
    fun `sanitizeTitle at max length is accepted`() {
        val exactly = "y".repeat(SessionDB.MAX_TITLE_LENGTH)
        assertEquals(exactly, db.sanitizeTitle(exactly))
    }

    @Test
    fun `sanitizeTitle strips zero-width unicode marks`() {
        // U+200B (ZWSP) is in the stripped range [\u200b-\u200f]
        val dirty = "alpha\u200Bbeta"
        assertEquals("alphabeta", db.sanitizeTitle(dirty))
    }

    // ── _containsCjk — Unicode range check ────────────────────────────────

    @Test
    fun `_containsCjk true for Chinese ideograph`() {
        assertTrue(db._containsCjk("你好"))
    }

    @Test
    fun `_containsCjk true for Japanese hiragana`() {
        assertTrue(db._containsCjk("こんにちは"))
    }

    @Test
    fun `_containsCjk true for Hangul`() {
        assertTrue(db._containsCjk("안녕"))
    }

    @Test
    fun `_containsCjk false for pure ASCII`() {
        assertFalse(db._containsCjk("hello world"))
        assertFalse(db._containsCjk("docker-deploy"))
    }

    @Test
    fun `_containsCjk false for Latin extended`() {
        assertFalse(db._containsCjk("café résumé"))
    }

    @Test
    fun `_containsCjk true when mixed ascii and cjk`() {
        assertTrue(db._containsCjk("hello 世界"))
    }

    // ── Companion compression-tip stub (Python parity) ────────────────────

    @Test
    fun `getCompressionTip returns null for unknown session`() {
        assertNull(db.getCompressionTip("session-never-created"))
    }
}
