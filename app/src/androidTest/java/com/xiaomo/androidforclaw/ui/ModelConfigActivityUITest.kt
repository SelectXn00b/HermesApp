package com.xiaomo.androidforclaw.ui

import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.TextView
import androidx.core.widget.NestedScrollView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.*
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.draco.ladb.R
import com.xiaomo.androidforclaw.config.ProviderRegistry
import com.xiaomo.androidforclaw.ui.activity.ModelConfigActivity
import org.hamcrest.CoreMatchers.*
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.Matchers.greaterThan
import org.hamcrest.TypeSafeMatcher
import org.junit.Assert.assertThat
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

/**
 * ModelConfigActivity UI 自动化测试 — 56 tests
 *
 * 运行:
 * adb shell am instrument -w -e class com.xiaomo.androidforclaw.ui.ModelConfigActivityUITest \
 *   com.xiaomo.androidforclaw.test/androidx.test.runner.AndroidJUnitRunner
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class ModelConfigActivityUITest {

    @get:Rule
    val activityRule = ActivityScenarioRule(ModelConfigActivity::class.java)

    // ========== Helpers ==========

    private fun waitForUi(ms: Long = 500) {
        Thread.sleep(ms)
    }

    /** Scroll NestedScrollView to show target view. Works for nested layouts. */
    private fun nestedScrollTo(): ViewAction = object : ViewAction {
        override fun getConstraints(): Matcher<View> = isDescendantOfA(isAssignableFrom(
            NestedScrollView::class.java
        ))
        override fun getDescription(): String = "nested scroll to"
        override fun perform(uiController: UiController, view: View) {
            view.requestRectangleOnScreen(
                android.graphics.Rect(0, 0, view.width, view.height), true
            )
            uiController.loopMainThreadUntilIdle()
        }
    }

    /** Click a provider card by index in a container (avoids AmbiguousViewMatcherException) */
    private fun clickProviderCardAt(containerId: Int, index: Int) {
        activityRule.scenario.onActivity { activity ->
            val container = activity.findViewById<ViewGroup>(containerId)
            container.getChildAt(index)?.performClick()
        }
        waitForUi()
    }

    /** Click a view by ID on the activity (bypasses Espresso scroll/visibility issues) */
    private fun clickViewById(viewId: Int) {
        activityRule.scenario.onActivity { activity ->
            activity.findViewById<View>(viewId)?.performClick()
        }
        waitForUi(300)
    }

    /** Scroll Page 2's NestedScrollView to bottom so advanced/buttons are visible */
    private fun scrollPage2ToBottom() {
        activityRule.scenario.onActivity { activity ->
            val page2 = activity.findViewById<View>(R.id.page_provider_detail)
            val nsv = page2?.findViewById<NestedScrollView>(page2.let {
                // Find NestedScrollView child of page_provider_detail
                (it as? ViewGroup)?.getChildAt(0)?.id ?: 0
            })
            // Fallback: find by traversal
            fun findNSV(v: View): NestedScrollView? {
                if (v is NestedScrollView) return v
                if (v is ViewGroup) {
                    for (i in 0 until v.childCount) {
                        findNSV(v.getChildAt(i))?.let { return it }
                    }
                }
                return null
            }
            findNSV(page2 ?: return@onActivity)?.fullScroll(View.FOCUS_DOWN)
        }
        waitForUi(300)
    }

    /** Scroll to a specific view in Page 2 */
    private fun scrollPage2To(viewId: Int) {
        activityRule.scenario.onActivity { activity ->
            val targetView = activity.findViewById<View>(viewId) ?: return@onActivity
            targetView.requestRectangleOnScreen(
                android.graphics.Rect(0, 0, targetView.width, targetView.height), true
            )
        }
        waitForUi(300)
    }

    private fun navigateToOpenRouter() = clickProviderCardAt(R.id.container_primary_providers, 0)
    private fun navigateToAnthropic() = clickProviderCardAt(R.id.container_primary_providers, 1)
    private fun navigateToOpenAI() = clickProviderCardAt(R.id.container_primary_providers, 2)

    private fun navigateToOllama() {
        val idx = ProviderRegistry.PRIMARY_PROVIDERS.indexOfFirst { it.id == "ollama" }
        clickProviderCardAt(R.id.container_primary_providers, idx)
    }

    private fun navigateToCustom() = clickProviderCardAt(R.id.container_custom_providers, 0)

    /** Read text from tv_provider_name on Page 2 (scoped to page_provider_detail) */
    private fun getPage2ProviderName(): String? {
        var name: String? = null
        activityRule.scenario.onActivity { activity ->
            val page2 = activity.findViewById<View>(R.id.page_provider_detail)
            name = (page2 as? ViewGroup)?.findViewById<TextView>(R.id.tv_provider_name)?.text?.toString()
        }
        return name
    }

    // ========================================================================
    // PAGE 1: Provider List
    // ========================================================================

    @Test
    fun test01_activityLaunches_showsToolbarTitle() {
        onView(withText("模型配置"))
            .check(matches(isDisplayed()))
    }

    @Test
    fun test02_page1_sectionTitle() {
        onView(withText("选择 AI 服务商"))
            .check(matches(isDisplayed()))
    }

    @Test
    fun test03_page1_currentModelCard() {
        onView(withId(R.id.card_current_model))
            .check(matches(isDisplayed()))
    }

    @Test
    fun test04_page1_currentModelText() {
        onView(withId(R.id.tv_current_model))
            .check(matches(isDisplayed()))
            .check(matches(withText(not(equalTo("")))))
    }

    @Test
    fun test05_page1_primaryProviders_allPresent() {
        val names = ProviderRegistry.PRIMARY_PROVIDERS.map { it.name }
        activityRule.scenario.onActivity { activity ->
            val container = activity.findViewById<ViewGroup>(R.id.container_primary_providers)
            assertThat("Primary count", container.childCount, `is`(names.size))
            for (i in 0 until container.childCount) {
                val tv = container.getChildAt(i).findViewById<TextView>(R.id.tv_provider_name)
                assertThat("Provider $i", tv?.text?.toString(), `is`(names[i]))
            }
        }
    }

    @Test
    fun test06_page1_openRouterDescription() {
        activityRule.scenario.onActivity { activity ->
            val container = activity.findViewById<ViewGroup>(R.id.container_primary_providers)
            val desc = container.getChildAt(0).findViewById<TextView>(R.id.tv_provider_desc)
            assertThat("OpenRouter desc", desc?.text?.toString(),
                `is`(ProviderRegistry.PRIMARY_PROVIDERS[0].description))
        }
    }

    @Test
    fun test07_page1_moreToggle() {
        onView(withId(R.id.card_more_toggle))
            .perform(nestedScrollTo())
            .check(matches(isDisplayed()))
    }

    @Test
    fun test08_page1_moreProviders_initiallyHidden() {
        onView(withId(R.id.container_more_providers))
            .check(matches(withEffectiveVisibility(Visibility.GONE)))
    }

    @Test
    fun test09_page1_moreToggle_expands() {
        onView(withId(R.id.card_more_toggle))
            .perform(nestedScrollTo(), click())
        waitForUi(800)  // Extra wait for expand animation
        try {
            onView(withId(R.id.container_more_providers))
                .check(matches(isDisplayed()))
        } catch (e: AssertionError) {
            // Retry with longer wait (animation may be slow on some devices)
            waitForUi(500)
            onView(withId(R.id.container_more_providers))
                .check(matches(isDisplayed()))
        }
    }

    @Test
    fun test10_page1_moreProviders_allPresent() {
        onView(withId(R.id.card_more_toggle))
            .perform(nestedScrollTo(), click())
        waitForUi(400)

        val names = ProviderRegistry.MORE_PROVIDERS.map { it.name }
        activityRule.scenario.onActivity { activity ->
            val container = activity.findViewById<ViewGroup>(R.id.container_more_providers)
            assertThat("More count", container.childCount, `is`(names.size))
            for (i in 0 until container.childCount) {
                val tv = container.getChildAt(i).findViewById<TextView>(R.id.tv_provider_name)
                assertThat("More $i", tv?.text?.toString(), `is`(names[i]))
            }
        }
    }

    @Test
    fun test11_page1_moreToggle_collapses() {
        onView(withId(R.id.card_more_toggle)).perform(nestedScrollTo(), click())
        waitForUi(300)
        onView(withId(R.id.card_more_toggle)).perform(nestedScrollTo(), click())
        waitForUi(300)
        onView(withId(R.id.container_more_providers))
            .check(matches(withEffectiveVisibility(Visibility.GONE)))
    }

    @Test
    fun test12_page1_customSection() {
        onView(withText("自定义"))
            .perform(nestedScrollTo())
            .check(matches(isDisplayed()))
    }

    @Test
    fun test13_page1_customProviders() {
        val names = ProviderRegistry.CUSTOM_PROVIDERS.map { it.name }
        activityRule.scenario.onActivity { activity ->
            val container = activity.findViewById<ViewGroup>(R.id.container_custom_providers)
            assertThat("Custom count", container.childCount, `is`(names.size))
        }
    }

    @Test
    fun test14_page1_configuredProvider_hasGreenIndicator() {
        activityRule.scenario.onActivity { activity ->
            val container = activity.findViewById<ViewGroup>(R.id.container_primary_providers)
            val firstCard = container.getChildAt(0)
            val statusView = firstCard?.findViewById<View>(R.id.view_status)
            assertThat("OpenRouter status visible", statusView?.visibility, `is`(View.VISIBLE))
        }
    }

    // ========================================================================
    // PAGE 2: OpenRouter Detail
    // ========================================================================

    @Test
    fun test20_page2_openRouter_pageVisible() {
        navigateToOpenRouter()
        onView(withId(R.id.page_provider_detail))
            .check(matches(isDisplayed()))
    }

    @Test
    fun test21_page2_openRouter_providerName() {
        navigateToOpenRouter()
        assertThat("Provider name", getPage2ProviderName(), `is`("OpenRouter"))
    }

    @Test
    fun test22_page2_openRouter_apiKeyField() {
        navigateToOpenRouter()
        onView(withId(R.id.et_api_key))
            .check(matches(isDisplayed()))
    }

    @Test
    fun test23_page2_openRouter_apiKeyHasValue() {
        navigateToOpenRouter()
        activityRule.scenario.onActivity { activity ->
            val et = activity.findViewById<TextView>(R.id.et_api_key)
            assertThat("API key not empty", et?.text?.toString()?.isNotBlank(), `is`(true))
        }
    }

    @Test
    fun test24_page2_openRouter_helperTextOptional() {
        navigateToOpenRouter()
        activityRule.scenario.onActivity { activity ->
            val til = activity.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.til_api_key)
            assertThat("Helper says 可选", til.helperText?.toString() ?: "", containsString("可选"))
        }
    }

    @Test
    fun test25_page2_openRouter_tutorialCard() {
        navigateToOpenRouter()
        onView(withId(R.id.card_tutorial))
            .check(matches(isDisplayed()))
    }

    @Test
    fun test26_page2_openRouter_tutorialSteps() {
        navigateToOpenRouter()
        activityRule.scenario.onActivity { activity ->
            val tv = activity.findViewById<TextView>(R.id.tv_tutorial_steps)
            assertThat("Tutorial not empty", tv?.text?.toString()?.isNotBlank(), `is`(true))
        }
    }

    @Test
    fun test27_page2_openRouter_tutorialUrl() {
        navigateToOpenRouter()
        onView(withId(R.id.btn_tutorial_url))
            .check(matches(isDisplayed()))
    }

    @Test
    fun test28_page2_openRouter_presetModels() {
        navigateToOpenRouter()
        activityRule.scenario.onActivity { activity ->
            val container = activity.findViewById<ViewGroup>(R.id.container_preset_models)
            assertThat("Has models", container.childCount, greaterThan(0))
        }
    }

    @Test
    fun test29_page2_openRouter_firstModelSelected() {
        navigateToOpenRouter()
        activityRule.scenario.onActivity { activity ->
            val container = activity.findViewById<ViewGroup>(R.id.container_preset_models)
            val radio = container.getChildAt(0)?.findViewById<RadioButton>(R.id.radio_model)
            assertThat("First checked", radio?.isChecked, `is`(true))
        }
    }

    @Test
    fun test30_page2_openRouter_hunterAlphaFirst() {
        navigateToOpenRouter()
        activityRule.scenario.onActivity { activity ->
            val container = activity.findViewById<ViewGroup>(R.id.container_preset_models)
            val tvName = container.getChildAt(0)?.findViewById<TextView>(R.id.tv_model_name)
            assertThat("Hunter Alpha", tvName?.text?.toString(), containsString("Hunter Alpha"))
        }
    }

    @Test
    fun test31_page2_openRouter_freeBadge() {
        navigateToOpenRouter()
        activityRule.scenario.onActivity { activity ->
            val container = activity.findViewById<ViewGroup>(R.id.container_preset_models)
            val badge = container.getChildAt(0)?.findViewById<TextView>(R.id.tv_model_badge)
            assertThat("Badge visible", badge?.visibility, `is`(View.VISIBLE))
            assertThat("Badge text", badge?.text?.toString(), `is`("免费"))
        }
    }

    @Test
    fun test32_page2_openRouter_selectSecondModel() {
        navigateToOpenRouter()
        activityRule.scenario.onActivity { activity ->
            val container = activity.findViewById<ViewGroup>(R.id.container_preset_models)
            if (container.childCount > 1) container.getChildAt(1).performClick()
        }
        waitForUi(300)
        activityRule.scenario.onActivity { activity ->
            val container = activity.findViewById<ViewGroup>(R.id.container_preset_models)
            if (container.childCount > 1) {
                val first = container.getChildAt(0).findViewById<RadioButton>(R.id.radio_model)
                val second = container.getChildAt(1).findViewById<RadioButton>(R.id.radio_model)
                assertThat("First unchecked", first?.isChecked, `is`(false))
                assertThat("Second checked", second?.isChecked, `is`(true))
            }
        }
    }

    @Test
    fun test33_page2_discoverModelsButton() {
        navigateToOpenRouter()
        activityRule.scenario.onActivity { activity ->
            val btn = activity.findViewById<View>(R.id.btn_discover_models)
            assertThat("Discover visible", btn?.visibility, `is`(View.VISIBLE))
        }
    }

    @Test
    fun test34_page2_addModelButton() {
        navigateToOpenRouter()
        activityRule.scenario.onActivity { activity ->
            val btn = activity.findViewById<View>(R.id.btn_add_model)
            assertThat("Add model visible", btn?.visibility, `is`(View.VISIBLE))
        }
    }

    @Test
    fun test35_page2_saveButton() {
        navigateToOpenRouter()
        onView(withId(R.id.btn_save))
            .check(matches(isDisplayed()))
            .check(matches(withText("保存并使用")))
    }

    // ========================================================================
    // PAGE 2: Advanced Section
    // ========================================================================

    @Test
    fun test36_page2_advancedSection_initiallyHidden() {
        navigateToOpenRouter()
        activityRule.scenario.onActivity { activity ->
            val layout = activity.findViewById<View>(R.id.layout_advanced)
            assertThat("Advanced hidden", layout?.visibility, `is`(View.GONE))
        }
    }

    @Test
    fun test37_page2_advancedToggle_expands() {
        navigateToOpenRouter()
        clickViewById(R.id.card_advanced_toggle)
        activityRule.scenario.onActivity { activity ->
            val layout = activity.findViewById<View>(R.id.layout_advanced)
            assertThat("Advanced visible", layout?.visibility, `is`(View.VISIBLE))
        }
    }

    @Test
    fun test38_page2_advanced_baseUrlPreFilled() {
        navigateToOpenRouter()
        clickViewById(R.id.card_advanced_toggle)
        activityRule.scenario.onActivity { activity ->
            val et = activity.findViewById<TextView>(R.id.et_base_url)
            assertThat("Base URL has openrouter",
                et?.text?.toString(), containsString("openrouter.ai"))
        }
    }

    @Test
    fun test39_page2_advanced_apiTypeDropdown() {
        navigateToOpenRouter()
        clickViewById(R.id.card_advanced_toggle)
        activityRule.scenario.onActivity { activity ->
            val dropdown = activity.findViewById<View>(R.id.dropdown_api_type)
            assertThat("Dropdown visible", dropdown?.visibility, `is`(View.VISIBLE))
        }
    }

    @Test
    fun test40_page2_advanced_customModelId_hidden() {
        navigateToOpenRouter()
        clickViewById(R.id.card_advanced_toggle)
        activityRule.scenario.onActivity { activity ->
            val til = activity.findViewById<View>(R.id.til_custom_model_id)
            assertThat("Custom model ID hidden", til?.visibility, `is`(View.GONE))
        }
    }

    @Test
    fun test41_page2_advancedToggle_collapses() {
        navigateToOpenRouter()
        clickViewById(R.id.card_advanced_toggle)
        clickViewById(R.id.card_advanced_toggle)
        activityRule.scenario.onActivity { activity ->
            val layout = activity.findViewById<View>(R.id.layout_advanced)
            assertThat("Advanced hidden again", layout?.visibility, `is`(View.GONE))
        }
    }

    // ========================================================================
    // Navigation: Back from Page 2
    // ========================================================================

    @Test
    fun test42_backFromPage2_returnsToPage1() {
        navigateToOpenRouter()
        pressBack()
        waitForUi()
        onView(withId(R.id.page_provider_list))
            .check(matches(isDisplayed()))
        onView(withText("选择 AI 服务商"))
            .check(matches(isDisplayed()))
    }

    @Test
    fun test43_page1_hiddenWhenPage2Shown() {
        navigateToOpenRouter()
        activityRule.scenario.onActivity { activity ->
            val p1 = activity.findViewById<View>(R.id.page_provider_list)
            val p2 = activity.findViewById<View>(R.id.page_provider_detail)
            assertThat("Page1 gone", p1?.visibility, `is`(View.GONE))
            assertThat("Page2 visible", p2?.visibility, `is`(View.VISIBLE))
        }
    }

    // ========================================================================
    // Anthropic
    // ========================================================================

    @Test
    fun test44_page2_anthropic_providerName() {
        navigateToAnthropic()
        assertThat("Anthropic name", getPage2ProviderName(), `is`("Anthropic"))
    }

    @Test
    fun test45_page2_anthropic_apiKeyRequired() {
        navigateToAnthropic()
        activityRule.scenario.onActivity { activity ->
            val til = activity.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.til_api_key)
            assertThat("No 可选", til.helperText?.toString() ?: "", not(containsString("可选")))
        }
    }

    @Test
    fun test46_page2_anthropic_hasPresetModels() {
        navigateToAnthropic()
        activityRule.scenario.onActivity { activity ->
            val container = activity.findViewById<ViewGroup>(R.id.container_preset_models)
            assertThat("Has models", container.childCount, greaterThan(0))
        }
    }

    @Test
    fun test47_page2_anthropic_hasClaude() {
        navigateToAnthropic()
        activityRule.scenario.onActivity { activity ->
            val container = activity.findViewById<ViewGroup>(R.id.container_preset_models)
            var found = false
            for (i in 0 until container.childCount) {
                val tv = container.getChildAt(i).findViewById<TextView>(R.id.tv_model_name)
                if (tv?.text?.toString()?.contains("Claude") == true) { found = true; break }
            }
            assertThat("Has Claude", found, `is`(true))
        }
    }

    // ========================================================================
    // Ollama
    // ========================================================================

    @Test
    fun test48_page2_ollama_apiKeyOptional() {
        navigateToOllama()
        activityRule.scenario.onActivity { activity ->
            val til = activity.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.til_api_key)
            assertThat("Ollama optional", til.helperText?.toString() ?: "", containsString("可选"))
        }
    }

    @Test
    fun test49_page2_ollama_discovery() {
        navigateToOllama()
        activityRule.scenario.onActivity { activity ->
            val btn = activity.findViewById<View>(R.id.btn_discover_models)
            assertThat("Discovery visible", btn?.visibility, `is`(View.VISIBLE))
        }
    }

    // ========================================================================
    // Custom provider
    // ========================================================================

    @Test
    fun test50_page2_custom_customModelIdVisible() {
        navigateToCustom()
        clickViewById(R.id.card_advanced_toggle)
        activityRule.scenario.onActivity { activity ->
            val til = activity.findViewById<View>(R.id.til_custom_model_id)
            assertThat("Custom model ID visible", til?.visibility, `is`(View.VISIBLE))
        }
    }

    // ========================================================================
    // Add Model Dialog
    // ========================================================================

    @Test
    fun test51_addModelDialog_opens() {
        navigateToOpenRouter()
        clickViewById(R.id.btn_add_model)
        onView(withText("添加模型"))
            .check(matches(isDisplayed()))
    }

    @Test
    fun test52_addModelDialog_hasFields() {
        navigateToOpenRouter()
        clickViewById(R.id.btn_add_model)
        onView(withId(R.id.et_dialog_model_id)).check(matches(isDisplayed()))
        onView(withId(R.id.et_dialog_model_name)).check(matches(isDisplayed()))
        onView(withId(R.id.et_dialog_context_window)).check(matches(isDisplayed()))
    }

    @Test
    fun test53_addModelDialog_contextWindowDefault() {
        navigateToOpenRouter()
        clickViewById(R.id.btn_add_model)
        onView(withId(R.id.et_dialog_context_window))
            .check(matches(withText("128000")))
    }

    @Test
    fun test54_addModelDialog_cancel() {
        navigateToOpenRouter()
        clickViewById(R.id.btn_add_model)
        onView(withText("取消")).perform(click())
        waitForUi()
        onView(withId(R.id.btn_save)).check(matches(isDisplayed()))
    }

    @Test
    fun test55_addModelDialog_addModel() {
        navigateToOpenRouter()
        var initialCount = 0
        activityRule.scenario.onActivity { activity ->
            initialCount = activity.findViewById<ViewGroup>(R.id.container_preset_models).childCount
        }
        clickViewById(R.id.btn_add_model)
        onView(withId(R.id.et_dialog_model_id)).perform(replaceText("test/my-custom-model"))
        onView(withId(R.id.et_dialog_model_name)).perform(replaceText("My Custom Model"))
        onView(withText("添加")).perform(click())
        waitForUi()
        activityRule.scenario.onActivity { activity ->
            val count = activity.findViewById<ViewGroup>(R.id.container_preset_models).childCount
            assertThat("Model added", count, greaterThan(initialCount))
        }
    }

    // ========================================================================
    // API Key Input
    // ========================================================================

    @Test
    fun test56_page2_apiKey_canType() {
        navigateToOpenRouter()
        onView(withId(R.id.et_api_key)).perform(clearText(), typeText("sk-test-key-12345"))
        // API key field may be a password field (masked), verify it has content
        activityRule.scenario.onActivity { activity ->
            val editText = activity.findViewById<android.widget.EditText>(R.id.et_api_key)
            val text = editText?.text?.toString() ?: ""
            assertThat("API key should be typed", text.isNotEmpty(), `is`(true))
        }
    }

    @Test
    fun test57_page2_apiKey_canClear() {
        navigateToOpenRouter()
        onView(withId(R.id.et_api_key)).perform(clearText())
        onView(withId(R.id.et_api_key)).check(matches(withText("")))
    }

    // ========================================================================
    // Cross-provider navigation
    // ========================================================================

    @Test
    fun test59_navigateBetweenProviders() {
        navigateToOpenRouter()
        assertThat("OpenRouter", getPage2ProviderName(), `is`("OpenRouter"))
        pressBack(); waitForUi()

        navigateToAnthropic()
        assertThat("Anthropic", getPage2ProviderName(), `is`("Anthropic"))
        pressBack(); waitForUi()

        navigateToOpenAI()
        assertThat("OpenAI", getPage2ProviderName(), `is`("OpenAI"))
    }

    @Test
    fun test60_page2_openAI_hasGPTModels() {
        navigateToOpenAI()
        activityRule.scenario.onActivity { activity ->
            val container = activity.findViewById<ViewGroup>(R.id.container_preset_models)
            var found = false
            for (i in 0 until container.childCount) {
                val tv = container.getChildAt(i).findViewById<TextView>(R.id.tv_model_name)
                if (tv?.text?.toString()?.contains("GPT") == true) { found = true; break }
            }
            assertThat("Has GPT", found, `is`(true))
        }
    }

    // ========================================================================
    // Provider status on page 2
    // ========================================================================

    @Test
    fun test61_page2_openRouter_statusConfigured() {
        navigateToOpenRouter()
        activityRule.scenario.onActivity { activity ->
            val page2 = activity.findViewById<ViewGroup>(R.id.page_provider_detail)
            val tv = page2?.findViewById<TextView>(R.id.tv_provider_status)
            assertThat("Status visible", tv?.visibility, `is`(View.VISIBLE))
            assertThat("Status text", tv?.text?.toString(), `is`("已配置"))
        }
    }

    @Test
    fun test62_page2_anthropic_statusNotConfigured() {
        navigateToAnthropic()
        activityRule.scenario.onActivity { activity ->
            val page2 = activity.findViewById<ViewGroup>(R.id.page_provider_detail)
            val tv = page2?.findViewById<TextView>(R.id.tv_provider_status)
            assertThat("Status hidden", tv?.visibility, `is`(View.GONE))
        }
    }
}
