package com.xiaomo.hermes.hermes.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

class RedactTest {

    @Before
    fun setUp() {
        // All tests assume redaction is enabled. HERMES_REDACT_SECRETS must not
        // be set to a disabling value in the test runner environment.
        assumeTrue("redaction disabled via env", _REDACT_ENABLED)
    }

    // ---- _maskToken ----

    @Test
    fun `maskToken short token is fully masked`() {
        assertEquals("***", _maskToken("short"))
        assertEquals("***", _maskToken(""))
        assertEquals("***", _maskToken("a".repeat(17)))
    }

    @Test
    fun `maskToken long token preserves prefix and suffix`() {
        val token = "sk-proj-abcdefghijklmnop1234"
        val masked = _maskToken(token)
        assertEquals("sk-pro...1234", masked)
    }

    @Test
    fun `maskToken boundary at 18 chars preserves prefix and suffix`() {
        val token = "abcdef" + "middlemid" + "WXYZ"   // 6+9+4 = 19
        val masked = _maskToken(token)
        assertTrue(masked.startsWith("abcdef..."))
        assertTrue(masked.endsWith("WXYZ"))
    }

    // ---- redactSensitiveText basic guards ----

    @Test
    fun `redactSensitiveText null returns null`() {
        assertNull(redactSensitiveText(null))
    }

    @Test
    fun `redactSensitiveText empty returns empty`() {
        assertEquals("", redactSensitiveText(""))
    }

    @Test
    fun `redactSensitiveText non-sensitive text passes through`() {
        val text = "hello world, nothing to hide here"
        assertEquals(text, redactSensitiveText(text))
    }

    // ---- Known-prefix detection (_PREFIX_RE) ----

    @Test
    fun `redact openai sk prefix`() {
        val text = "key=sk-proj-ABCDEFGHIJKLmnop1234567890"
        val out = redactSensitiveText(text)!!
        assertTrue("got: $out", !out.contains("ABCDEFGHIJKLmnop"))
        assertTrue("got: $out", out.contains("sk-pro..."))
    }

    @Test
    fun `redact github PAT classic`() {
        val text = "ghp_abcdefghij1234567890ABCDEFG"
        val out = redactSensitiveText(text)!!
        assertTrue("got: $out", out != text)
        assertTrue("got: $out", out.startsWith("ghp_ab..."))
    }

    @Test
    fun `redact github fine grained PAT`() {
        val text = "Use github_pat_abcd1234efgh5678 to push"
        val out = redactSensitiveText(text)!!
        assertTrue("got: $out", !out.contains("abcd1234efgh5678"))
    }

    @Test
    fun `redact slack xoxb token`() {
        val text = "xoxb-123-456-abcdefghij"
        val out = redactSensitiveText(text)!!
        assertTrue("got: $out", out.contains("xoxb-1..."))
    }

    @Test
    fun `redact google AIza key`() {
        val text = "AIzaSyA0123456789_abcdefghij-ABCDEFGHIJ"
        val out = redactSensitiveText(text)!!
        assertTrue("got: $out", !out.contains("ABCDEFGHIJ"))
        assertTrue("got: $out", out.startsWith("AIzaSy..."))
    }

    @Test
    fun `redact AWS access key id`() {
        val text = "AKIAIOSFODNN7EXAMPLE"
        val out = redactSensitiveText(text)!!
        assertTrue("got: $out", out.contains("AKIAIO...") || out == "***")
    }

    @Test
    fun `redact stripe live secret`() {
        val text = "sk_live_abcdefghij1234567890"
        val out = redactSensitiveText(text)!!
        assertTrue("got: $out", !out.contains("abcdefghij1234567890"))
    }

    @Test
    fun `redact huggingface token`() {
        val text = "hf_abcdefghij1234567890"
        val out = redactSensitiveText(text)!!
        assertTrue("got: $out", out != text)
    }

    @Test
    fun `redact replicate token`() {
        val text = "r8_abcdefghij1234567890"
        val out = redactSensitiveText(text)!!
        assertTrue("got: $out", out != text)
    }

    @Test
    fun `prefix inside identifier-like context still redacted at word boundary`() {
        // Ensures the (?<!...)(?!...) boundaries work — embedded in identifier should NOT match
        val text = "foosk-abcdefghij1234567890"  // 'sk-' glued to prefix char
        val out = redactSensitiveText(text)!!
        // 'f' is a word char so \b prevents match — text should be unchanged
        assertEquals(text, out)
    }

    // ---- ENV assignment ----

    @Test
    fun `redact env assignment uppercase API_KEY`() {
        val text = "OPENAI_API_KEY=sk-proj-abcdefghij1234567890"
        val out = redactSensitiveText(text)!!
        assertTrue("got: $out", !out.contains("abcdefghij1234567890"))
        assertTrue("got: $out", out.contains("OPENAI_API_KEY="))
    }

    @Test
    fun `redact env assignment with quotes`() {
        val text = "GITHUB_TOKEN=\"ghp_abcdefghij1234567890\""
        val out = redactSensitiveText(text)!!
        assertTrue("got: $out", !out.contains("ghp_abcdefghij1234567890"))
    }

    @Test
    fun `redact env secret and password`() {
        val text = "MY_SECRET=supersecretvalue123456"
        val out = redactSensitiveText(text)!!
        assertTrue("got: $out", out.contains("MY_SECRET="))
        assertTrue("got: $out", !out.contains("supersecretvalue123456"))
    }

    // ---- JSON field ----

    @Test
    fun `redact json apiKey field`() {
        val text = """{"apiKey": "sometokenvaluehere1234"}"""
        val out = redactSensitiveText(text)!!
        assertTrue("got: $out", !out.contains("sometokenvaluehere1234"))
        assertTrue("got: $out", out.contains("\"apiKey\":"))
    }

    @Test
    fun `redact json token field`() {
        val text = """{"token": "abcdefghij1234567890"}"""
        val out = redactSensitiveText(text)!!
        assertTrue("got: $out", !out.contains("abcdefghij1234567890"))
    }

    @Test
    fun `redact json password field case insensitive`() {
        val text = """{"Password": "mysecretpass1234567890"}"""
        val out = redactSensitiveText(text)!!
        assertTrue("got: $out", !out.contains("mysecretpass1234567890"))
    }

    // ---- Authorization header ----

    @Test
    fun `redact Authorization Bearer token`() {
        val text = "Authorization: Bearer abcdef1234567890XYZABCDEF"
        val out = redactSensitiveText(text)!!
        assertTrue("got: $out", !out.contains("abcdef1234567890XYZABCDEF"))
        assertTrue("got: $out", out.contains("Authorization: Bearer "))
    }

    // ---- Telegram ----

    @Test
    fun `redact telegram bot token with bot prefix`() {
        val text = "bot1234567890:ABCDEFabcdefGHIJKLmnop-QRSTUVqrstuvWX"
        val out = redactSensitiveText(text)!!
        assertTrue("got: $out", out.contains("bot1234567890:***"))
    }

    @Test
    fun `redact telegram token without bot prefix`() {
        val text = "1234567890:ABCDEFabcdefGHIJKLmnop-QRSTUVqrstuvWX"
        val out = redactSensitiveText(text)!!
        assertTrue("got: $out", out.contains("1234567890:***"))
    }

    // ---- Private key block ----

    @Test
    fun `redact PEM private key block`() {
        val text = """
            -----BEGIN RSA PRIVATE KEY-----
            MIIBOgIBAAJBAKj34GkxFhD90vcNLYLInFEX6Ppy1tPf9Cnzj4p4WGeKLs1Pt8Qu
            -----END RSA PRIVATE KEY-----
        """.trimIndent()
        val out = redactSensitiveText(text)!!
        assertTrue("got: $out", out.contains("[REDACTED PRIVATE KEY]"))
        assertTrue("got: $out", !out.contains("MIIBOgIBAAJB"))
    }

    @Test
    fun `redact generic PEM private key`() {
        val text = "-----BEGIN PRIVATE KEY-----\nabcdef\n-----END PRIVATE KEY-----"
        val out = redactSensitiveText(text)!!
        assertTrue("got: $out", out.contains("[REDACTED PRIVATE KEY]"))
    }

    // ---- Database connection strings ----

    @Test
    fun `redact postgres connection string password`() {
        val text = "postgres://admin:supersecret@db.example.com/mydb"
        val out = redactSensitiveText(text)!!
        assertTrue("got: $out", out.contains("postgres://admin:***@"))
        assertTrue("got: $out", !out.contains("supersecret"))
    }

    @Test
    fun `redact mongodb srv connection string`() {
        val text = "mongodb+srv://user:p%40ss@cluster.mongodb.net/test"
        val out = redactSensitiveText(text)!!
        assertTrue("got: $out", out.contains("mongodb+srv://user:***@"))
    }

    @Test
    fun `redact redis connection string with user`() {
        val text = "redis://default:mypassword@localhost:6379"
        val out = redactSensitiveText(text)!!
        assertTrue("got: $out", out.contains("redis://default:***@"))
        assertTrue("got: $out", !out.contains("mypassword"))
    }

    // ---- JWT ----

    @Test
    fun `redact JWT token`() {
        val text = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.abc123"
        val out = redactSensitiveText(text)!!
        assertTrue("got: $out", !out.contains("eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.abc123"))
    }

    @Test
    fun `redact JWT header only`() {
        val text = "token: eyJabcdefghijklmnop"
        val out = redactSensitiveText(text)!!
        assertTrue("got: $out", !out.contains("eyJabcdefghijklmnop"))
    }

    // ---- URL userinfo ----

    @Test
    fun `redact https URL userinfo`() {
        val text = "https://user:secretpassword@api.example.com/v1/foo"
        val out = redactSensitiveText(text)!!
        assertTrue("got: $out", out.contains("https://user:***@api.example.com"))
        assertTrue("got: $out", !out.contains("secretpassword"))
    }

    @Test
    fun `redact wss URL userinfo`() {
        val text = "wss://u:p@ws.example.com/stream"
        val out = redactSensitiveText(text)!!
        assertTrue("got: $out", out.contains("wss://u:***@"))
    }

    // ---- URL query params ----

    @Test
    fun `redact URL access_token query param`() {
        val text = "Visit https://example.com/cb?access_token=opaquevalueabc&state=xyz"
        val out = redactSensitiveText(text)!!
        assertTrue("got: $out", out.contains("access_token=***"))
        assertTrue("got: $out", out.contains("state=xyz"))
    }

    @Test
    fun `redact URL code query param`() {
        val text = "https://oauth.example.com/?code=ABC123&state=ok"
        val out = redactSensitiveText(text)!!
        assertTrue("got: $out", out.contains("code=***"))
        assertTrue("got: $out", out.contains("state=ok"))
    }

    @Test
    fun `non-sensitive URL query params pass through`() {
        val text = "https://example.com/search?q=hello&page=2"
        val out = redactSensitiveText(text)!!
        assertEquals(text, out)
    }

    // ---- _redactQueryString direct ----

    @Test
    fun `redactQueryString sensitive keys masked`() {
        assertEquals(
            "access_token=***&state=abc",
            _redactQueryString("access_token=opaquetoken&state=abc")
        )
    }

    @Test
    fun `redactQueryString preserves non-sensitive keys`() {
        assertEquals(
            "q=hello&page=2",
            _redactQueryString("q=hello&page=2")
        )
    }

    @Test
    fun `redactQueryString preserves malformed pair`() {
        assertEquals("justflag", _redactQueryString("justflag"))
    }

    @Test
    fun `redactQueryString empty returns empty`() {
        assertEquals("", _redactQueryString(""))
    }

    // ---- Form body ----

    @Test
    fun `redactFormBody clean k=v&k=v form redacts sensitive`() {
        val body = "username=alice&password=secretp&state=abc"
        val out = _redactFormBody(body)
        assertTrue("got: $out", out.contains("password=***"))
        assertTrue("got: $out", out.contains("username=alice"))
    }

    @Test
    fun `redactFormBody multiline text passes through`() {
        val body = "hello\nworld=foo"
        assertEquals(body, _redactFormBody(body))
    }

    @Test
    fun `redactFormBody text without ampersand passes through`() {
        assertEquals("single=value", _redactFormBody("single=value"))
    }

    // ---- Discord mentions ----

    @Test
    fun `redact discord user mention`() {
        val text = "Hey <@123456789012345678>!"
        val out = redactSensitiveText(text)!!
        assertTrue("got: $out", out.contains("<@***>"))
        assertTrue("got: $out", !out.contains("123456789012345678"))
    }

    @Test
    fun `redact discord nickname mention`() {
        val text = "<@!123456789012345678>"
        val out = redactSensitiveText(text)!!
        assertTrue("got: $out", out.contains("<@!***>"))
    }

    // ---- Signal / E.164 phone ----

    @Test
    fun `redact E164 phone number long`() {
        val text = "contact +12025551234 today"
        val out = redactSensitiveText(text)!!
        assertTrue("got: $out", !out.contains("+12025551234"))
        assertTrue("got: $out", out.contains("+120"))
        assertTrue("got: $out", out.contains("1234"))
    }

    // ---- Integration: multiple secrets in one blob ----

    @Test
    fun `redact integration covers multiple secrets at once`() {
        val text = """
            config:
              openai=sk-proj-abcdefghijklmnop1234567890
              github: "ghp_abcdefghij1234567890"
              db: postgres://admin:p455w0rd@db.host/mydb
              cb: https://example.com/callback?code=XYZ123&state=ok
              auth: Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhYmMifQ.sig
        """.trimIndent()
        val out = redactSensitiveText(text)!!
        assertTrue("got: $out", !out.contains("p455w0rd"))
        assertTrue("got: $out", !out.contains("XYZ123"))
        assertTrue("got: $out", !out.contains("eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhYmMifQ.sig"))
        assertTrue("got: $out", !out.contains("abcdefghijklmnop1234567890"))
        assertTrue("got: $out", out.contains("postgres://admin:***@"))
        assertTrue("got: $out", out.contains("code=***"))
    }

    @Test
    fun `redactSensitiveText non-string input is coerced via toString`() {
        val out = redactSensitiveText(12345)
        assertNotNull(out)
        assertEquals("12345", out)
    }
}
