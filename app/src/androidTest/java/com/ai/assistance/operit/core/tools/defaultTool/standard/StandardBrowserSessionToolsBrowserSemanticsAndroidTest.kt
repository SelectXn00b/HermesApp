package com.ai.assistance.operit.core.tools.defaultTool.standard

import android.content.Context
import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StandardBrowserSessionToolsBrowserSemanticsAndroidTest {

    private lateinit var context: Context
    private lateinit var tools: StandardBrowserSessionTools

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        resetStaticState()
        tools = StandardBrowserSessionTools(context)
    }

    @After
    fun tearDown() {
        resetStaticState()
    }

    @Test
    fun snapshotNode_shouldOnlyUseLatestStoredSnapshot() {
        val session = createSession("session-1")
        val snapshot =
            createSnapshot(
                sessionId = "session-1",
                nodes =
                    mapOf(
                        "e1" to createSnapshotNode(ref = "e1", role = "button", name = "Submit")
                    )
            )
        setInstanceField(session, "lastSnapshot", snapshot)

        val resolved = invokePrivate(tools, "snapshotNode", session, "e2")

        assertNull(resolved)
    }

    @Test
    fun buildClickCode_shouldUseRoleLocatorAndSingleQuotes() {
        val session = createSession("session-2")
        val snapshot =
            createSnapshot(
                sessionId = "session-2",
                nodes =
                    mapOf(
                        "e2" to createSnapshotNode(ref = "e2", role = "button", name = "Submit")
                    )
            )
        setInstanceField(session, "lastSnapshot", snapshot)

        val code =
            invokePrivate(tools, "buildClickCode", session, "e2", "left", false, emptySet<String>()) as String

        assertEquals("await page.getByRole('button', { name: 'Submit' }).click();", code)
    }

    @Test
    fun closeSession_shouldKeepActiveSessionWhenClosingBackgroundTab() {
        val active = createSession("active-session", currentUrl = "https://active.test")
        val background = createSession("background-session", currentUrl = "https://background.test")

        putSession("active-session", active)
        putSession("background-session", background)
        setSessionOrder(listOf("active-session", "background-session"))
        setStaticField("activeSessionId", "active-session")

        val closed = invokePrivate(tools, "closeSession", "background-session") as Boolean

        assertTrue(closed)
        assertEquals("active-session", getStaticField("activeSessionId"))
        assertTrue(sessions().containsKey("active-session"))
        assertFalse(sessions().containsKey("background-session"))
        assertEquals(listOf("active-session"), sessionOrder())
    }

    private fun createSession(
        sessionId: String,
        currentUrl: String = "about:blank",
        pageTitle: String = ""
    ): Any {
        val session =
            runOnMain {
                invokePrivate(
                    tools,
                    "createSessionOnMain",
                    context.applicationContext,
                    sessionId,
                    null,
                    null
                )
            }
        setInstanceField(session, "currentUrl", currentUrl)
        setInstanceField(session, "pageTitle", pageTitle)
        return session
    }

    private fun createSnapshotNode(
        ref: String,
        role: String,
        name: String,
        value: String? = null,
        isActive: Boolean = false,
        lineText: String = ""
    ): Any {
        val clazz =
            Class.forName(
                "com.ai.assistance.operit.core.tools.defaultTool.standard.StandardBrowserSessionTools\$BrowserSnapshotNode"
            )
        val ctor = clazz.declaredConstructors.first()
        ctor.isAccessible = true
        return ctor.newInstance(ref, role, name, value, isActive, lineText)
    }

    private fun createSnapshot(sessionId: String, nodes: Map<String, Any>): Any {
        val clazz =
            Class.forName(
                "com.ai.assistance.operit.core.tools.defaultTool.standard.StandardBrowserSessionTools\$BrowserSnapshot"
            )
        val ctor = clazz.declaredConstructors.first()
        ctor.isAccessible = true
        return ctor.newInstance(
            sessionId,
            1L,
            "Title",
            "- document 'Title'",
            nodes,
            System.currentTimeMillis()
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun sessions(): ConcurrentHashMap<String, Any> =
        getStaticField("sessions") as ConcurrentHashMap<String, Any>

    @Suppress("UNCHECKED_CAST")
    private fun sessionOrder(): MutableList<String> = getStaticField("sessionOrder") as MutableList<String>

    private fun putSession(sessionId: String, session: Any) {
        sessions()[sessionId] = session
    }

    private fun setSessionOrder(ids: List<String>) {
        val order = sessionOrder()
        order.clear()
        order.addAll(ids)
    }

    private fun resetStaticState() {
        runOnMain {
            sessions().values.forEach { session ->
                val webView = getInstanceField(session, "webView") as WebView
                runCatching {
                    webView.stopLoading()
                    webView.destroy()
                }
            }
            sessions().clear()
            sessionOrder().clear()
            setStaticField("activeSessionId", null)
            setStaticField("browserHost", null)
        }
    }

    private fun getStaticField(name: String): Any? {
        val field = StandardBrowserSessionTools::class.java.getDeclaredField(name)
        field.isAccessible = true
        return field.get(null)
    }

    private fun setStaticField(name: String, value: Any?) {
        val field = StandardBrowserSessionTools::class.java.getDeclaredField(name)
        field.isAccessible = true
        field.set(null, value)
    }

    private fun getInstanceField(instance: Any, name: String): Any? {
        val field = instance.javaClass.getDeclaredField(name)
        field.isAccessible = true
        return field.get(instance)
    }

    private fun setInstanceField(instance: Any, name: String, value: Any?) {
        val field = instance.javaClass.getDeclaredField(name)
        field.isAccessible = true
        field.set(instance, value)
    }

    private fun invokePrivate(instance: Any, name: String, vararg args: Any?): Any? {
        val method =
            instance.javaClass.declaredMethods.first { candidate ->
                candidate.name == name && candidate.parameterTypes.size == args.size
            }
        method.isAccessible = true
        return method.invoke(instance, *args)
    }

    private fun <T> runOnMain(block: () -> T): T {
        val latch = CountDownLatch(1)
        var value: T? = null
        var error: Throwable? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            try {
                value = block()
            } catch (throwable: Throwable) {
                error = throwable
            } finally {
                latch.countDown()
            }
        }
        assertTrue("Timed out waiting for main thread work", latch.await(10, TimeUnit.SECONDS))
        error?.let { throw it }
        return value as T
    }
}
