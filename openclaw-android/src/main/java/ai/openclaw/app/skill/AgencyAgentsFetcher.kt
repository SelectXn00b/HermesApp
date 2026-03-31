package ai.openclaw.app.skill

import android.content.Context
import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Unified skill loader: merges bundled skills (from assets/skills/) with
 * online skills (from GitHub agency-agents repo).
 */
object AgencyAgentsFetcher {

    private const val TAG = "AgencyAgentsFetcher"
    private const val CACHE_PREFS = "agency_agents_cache"
    private const val KEY_SKILL_LIST = "skill_list_json"
    private const val KEY_TIMESTAMP = "skill_list_ts"
    private const val CACHE_TTL_MS = 24L * 60 * 60 * 1000 // 24h
    private const val CONTENT_CACHE_TTL_MS = 7 * CACHE_TTL_MS // 7 days
    private const val HTTP_CACHE_SIZE = 10L * 1024 * 1024 // 10 MB

    private var httpClient: OkHttpClient? = null

    private fun getClient(context: Context): OkHttpClient {
        return httpClient ?: OkHttpClient.Builder()
            .cache(Cache(File(context.cacheDir, "skill_http_cache"), HTTP_CACHE_SIZE))
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
            .also { httpClient = it }
    }

    private val json = Json { ignoreUnknownKeys = true }

    // ── Hardcoded online skill metadata (emoji + specialty + category) ──
    private val catalog = listOf(
        OnlineSkill("Frontend Developer", "🎨", "React/Vue/Angular, UI implementation, performance", "engineering", "engineering-frontend-developer.md"),
        OnlineSkill("Backend Architect", "🏗️", "API design, database architecture, scalability", "engineering", "engineering-backend-architect.md"),
        OnlineSkill("Mobile App Builder", "📱", "iOS/Android, React Native, Flutter", "engineering", "engineering-mobile-app-builder.md"),
        OnlineSkill("AI Engineer", "🤖", "ML models, deployment, AI integration", "engineering", "engineering-ai-engineer.md"),
        OnlineSkill("DevOps Automator", "🚀", "CI/CD, infrastructure automation, cloud ops", "engineering", "engineering-devops-automator.md"),
        OnlineSkill("Rapid Prototyper", "⚡", "Fast POC development, MVPs", "engineering", "engineering-rapid-prototyper.md"),
        OnlineSkill("Senior Developer", "💎", "Laravel/Livewire, advanced patterns", "engineering", "engineering-senior-developer.md"),
        OnlineSkill("Security Engineer", "🔒", "Threat modeling, secure code review, security architecture", "engineering", "engineering-security-engineer.md"),
        OnlineSkill("Autonomous Optimization Architect", "⚡", "LLM routing, cost optimization, shadow testing", "engineering", "engineering-autonomous-optimization-architect.md"),
        OnlineSkill("Embedded Firmware Engineer", "🔩", "Bare-metal, RTOS, ESP32/STM32/Nordic firmware", "engineering", "engineering-embedded-firmware-engineer.md"),
        OnlineSkill("Incident Response Commander", "🚨", "Incident management, post-mortems, on-call", "engineering", "engineering-incident-response-commander.md"),
        OnlineSkill("Solidity Smart Contract Engineer", "⛓️", "EVM contracts, gas optimization, DeFi", "engineering", "engineering-solidity-smart-contract-engineer.md"),
        OnlineSkill("Technical Writer", "📚", "Developer docs, API reference, tutorials", "engineering", "engineering-technical-writer.md"),
        OnlineSkill("Threat Detection Engineer", "🎯", "SIEM rules, threat hunting, ATT&CK mapping", "engineering", "engineering-threat-detection-engineer.md"),
        OnlineSkill("Code Reviewer", "👁️", "Constructive code review, security, maintainability", "engineering", "engineering-code-reviewer.md"),
        OnlineSkill("Database Optimizer", "🗄️", "Schema design, query optimization, indexing strategies", "engineering", "engineering-database-optimizer.md"),
        OnlineSkill("Git Workflow Master", "🌿", "Branching strategies, conventional commits, advanced Git", "engineering", "engineering-git-workflow-master.md"),
        OnlineSkill("Software Architect", "🏛️", "System design, DDD, architectural patterns", "engineering", "engineering-software-architect.md"),
        OnlineSkill("SRE", "🛡️", "SLOs, error budgets, observability, chaos engineering", "engineering", "engineering-sre.md"),
        OnlineSkill("AI Data Remediation Engineer", "🧬", "Self-healing pipelines, air-gapped SLMs, semantic clustering", "engineering", "engineering-ai-data-remediation-engineer.md"),
        OnlineSkill("Data Engineer", "🔧", "Data pipelines, lakehouse architecture, ETL/ELT", "engineering", "engineering-data-engineer.md"),
        OnlineSkill("CMS Developer", "🧱", "WordPress & Drupal themes, plugins/modules", "engineering", "engineering-cms-developer.md"),
        OnlineSkill("Email Intelligence Engineer", "📧", "Email parsing, MIME extraction, structured data for AI agents", "engineering", "engineering-email-intelligence-engineer.md"),
        // design
        OnlineSkill("UI Designer", "🎯", "Visual design, component libraries, design systems", "design", "design-ui-designer.md"),
        OnlineSkill("UX Researcher", "🔍", "User testing, behavior analysis, research", "design", "design-ux-researcher.md"),
        OnlineSkill("UX Architect", "🏛️", "Technical architecture, CSS systems, implementation", "design", "design-ux-architect.md"),
        OnlineSkill("Brand Guardian", "🎭", "Brand identity, consistency, positioning", "design", "design-brand-guardian.md"),
        OnlineSkill("Visual Storyteller", "📖", "Visual narratives, multimedia content", "design", "design-visual-storyteller.md"),
        OnlineSkill("Whimsy Injector", "✨", "Personality, delight, playful interactions", "design", "design-whimsy-injector.md"),
        OnlineSkill("Image Prompt Engineer", "📷", "AI image generation prompts, photography", "design", "design-image-prompt-engineer.md"),
        OnlineSkill("Inclusive Visuals Specialist", "🌈", "Representation, bias mitigation, authentic imagery", "design", "design-inclusive-visuals-specialist.md"),
        // marketing
        OnlineSkill("PPC Campaign Strategist", "💰", "Google/Microsoft/Amazon Ads, account architecture, bidding", "marketing", "marketing-ppc-campaign-strategist.md"),
        OnlineSkill("Search Query Analyst", "🔍", "Search term analysis, negative keywords, intent mapping", "marketing", "marketing-search-query-analyst.md"),
        OnlineSkill("Paid Media Auditor", "📋", "200+ point account audits, competitive analysis", "marketing", "marketing-paid-media-auditor.md"),
        OnlineSkill("Tracking & Measurement Specialist", "📡", "GTM, GA4, conversion tracking, CAPI", "marketing", "marketing-tracking-measurement-specialist.md"),
        OnlineSkill("Ad Creative Strategist", "✍️", "RSA copy, Meta creative, Performance Max assets", "marketing", "marketing-ad-creative-strategist.md"),
        OnlineSkill("Paid Social Strategist", "📱", "Meta, LinkedIn, TikTok, cross-platform social", "marketing", "marketing-paid-social-strategist.md"),
        // sales
        OnlineSkill("Outbound Strategist", "🎯", "Signal-based prospecting, multi-channel sequences, ICP targeting", "sales", "sales-outbound-strategist.md"),
        OnlineSkill("Discovery Coach", "🔍", "SPIN, Gap Selling, Sandler — question design and call structure", "sales", "sales-discovery-coach.md"),
        OnlineSkill("Deal Strategist", "♟️", "MEDDPICC qualification, competitive positioning, win planning", "sales", "sales-deal-strategist.md"),
        OnlineSkill("Sales Engineer", "🛠️", "Technical demos, POC scoping, competitive battlecards", "sales", "sales-sales-engineer.md"),
        OnlineSkill("Proposal Strategist", "🏹", "RFP response, win themes, narrative structure", "sales", "sales-proposal-strategist.md"),
        OnlineSkill("Pipeline Analyst", "📊", "Forecasting, pipeline health, deal velocity, RevOps", "sales", "sales-pipeline-analyst.md"),
        OnlineSkill("Sales Coach", "🏋️", "Rep development, call coaching, pipeline review facilitation", "sales", "sales-sales-coach.md"),
        // social
        OnlineSkill("Growth Hacker", "🚀", "Rapid user acquisition, viral loops, experiments", "social", "social-growth-hacker.md"),
        OnlineSkill("Content Creator", "📝", "Multi-platform content, editorial calendars", "social", "social-content-creator.md"),
        OnlineSkill("Twitter Engager", "🐦", "Real-time engagement, thought leadership", "social", "social-twitter-engager.md"),
        OnlineSkill("TikTok Strategist", "📱", "Viral content, algorithm optimization", "social", "social-tiktok-strategist.md"),
        OnlineSkill("Instagram Curator", "📸", "Visual storytelling, community building", "social", "social-instagram-curator.md"),
        OnlineSkill("Reddit Community Builder", "🤝", "Authentic engagement, value-driven content", "social", "social-reddit-community-builder.md"),
    )

    /** The 4 featured (hot) skills shown at the top. */
    val featuredSkills: List<OnlineSkill> = listOfNotNull(
        catalog.firstOrNull { it.name == "Frontend Developer" },
        catalog.firstOrNull { it.name == "AI Engineer" },
        catalog.firstOrNull { it.name == "Mobile App Builder" },
        catalog.firstOrNull { it.name == "DevOps Automator" },
    )

    // ── Bundled skills cache ──
    private var bundledSkillsCache: List<OnlineSkill>? = null

    /** Load bundled skills from assets/skills/. Each subdirectory with SKILL.md is a bundled skill. */
    fun loadBundledSkills(context: Context): List<OnlineSkill> {
        bundledSkillsCache?.let { return it }
        val skills = mutableListOf<OnlineSkill>()
        try {
            val dirs = context.assets.list("skills") ?: emptyArray()
            for (dir in dirs) {
                try {
                    val content = context.assets.open("skills/$dir/SKILL.md").bufferedReader().use { it.readText() }
                    val skill = parseBundledSkill(dir, content)
                    if (skill != null) skills.add(skill)
                } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load bundled skills", e)
        }
        bundledSkillsCache = skills
        return skills
    }

    /** Parse SKILL.md frontmatter to extract name, description, emoji. */
    private fun parseBundledSkill(dirName: String, content: String): OnlineSkill? {
        if (!content.startsWith("---")) return null
        val endIdx = content.indexOf("---", 3)
        if (endIdx < 0) return null
        val frontmatter = content.substring(3, endIdx).trim()

        val name = Regex("""^name:\s*(.+)$""", RegexOption.MULTILINE)
            .find(frontmatter)?.groupValues?.get(1)?.trim()?.removeSurrounding("\"") ?: dirName
        val description = Regex("""^description:\s*(.+)$""", RegexOption.MULTILINE)
            .find(frontmatter)?.groupValues?.get(1)?.trim()?.removeSurrounding("\"") ?: ""
        val emoji = Regex(""""emoji"\s*:\s*"([^"]+)"""")
            .find(frontmatter)?.groupValues?.get(1) ?: "🔧"

        return OnlineSkill(name = name, emoji = emoji, specialty = description, category = "bundled", filename = "__bundled__$dirName")
    }

    /** Get the set of bundled skill filenames (always considered "installed"). */
    fun getBundledFilenames(context: Context): Set<String> {
        return loadBundledSkills(context).map { it.filename }.toSet()
    }

    /** Load online skill list — returns catalog (with cache). */
    fun loadSkills(context: Context): List<OnlineSkill> {
        val prefs = context.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE)
        val cachedTs = prefs.getLong(KEY_TIMESTAMP, 0)
        val cachedJson = prefs.getString(KEY_SKILL_LIST, null)

        if (cachedJson != null && System.currentTimeMillis() - cachedTs < CACHE_TTL_MS) {
            return try {
                json.decodeFromString<List<OnlineSkill>>(cachedJson)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to decode cached skills", e)
                catalog
            }
        }

        return try {
            val client = getClient(context)
            val fetched = fetchFromGitHub(client)
            prefs.edit()
                .putString(KEY_SKILL_LIST, json.encodeToString(fetched))
                .putLong(KEY_TIMESTAMP, System.currentTimeMillis())
                .apply()
            fetched
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch from GitHub", e)
            if (cachedJson != null) {
                try { json.decodeFromString<List<OnlineSkill>>(cachedJson) }
                catch (_: Exception) { catalog }
            } else catalog
        }
    }

    /** Load all skills: bundled + online, unified list. */
    fun loadAllSkills(context: Context): List<OnlineSkill> {
        return loadBundledSkills(context) + loadSkills(context)
    }

    /** Fetch skill list from GitHub API, discovering files not in our catalog. */
    private fun fetchFromGitHub(client: OkHttpClient): List<OnlineSkill> {
        val categories = listOf("engineering", "design", "marketing", "sales", "social")
        val discovered = mutableListOf<OnlineSkill>()

        for (cat in categories) {
            val request = Request.Builder()
                .url("https://api.github.com/repos/msitarzewski/agency-agents/contents/$cat")
                .header("Accept", "application/vnd.github.v3+json")
                .build()

            val resp = client.newCall(request).execute()
            if (!resp.isSuccessful) { resp.close(); continue }
            val body = resp.body?.string() ?: run { resp.close(); continue }
            resp.close()

            val files = try {
                json.decodeFromString<List<GithubFile>>(body)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse GitHub response for $cat", e)
                continue
            }

            for (file in files) {
                if (!file.name.endsWith(".md")) continue
                if (catalog.any { it.filename == file.name }) continue
                val displayName = file.name
                    .removePrefix("$cat-")
                    .removeSuffix(".md")
                    .split("-")
                    .joinToString(" ") { part -> part.replaceFirstChar { c -> c.uppercase() } }
                discovered.add(OnlineSkill(displayName, "📄", "", cat, file.name))
            }
        }

        return (catalog + discovered).distinctBy { it.name }.distinctBy { it.filename }
    }

    /** Fetch raw markdown content for a specific skill. */
    fun fetchContent(context: Context, skill: OnlineSkill): String {
        // Bundled skills: read from assets
        if (skill.filename.startsWith("__bundled__")) {
            val dirName = skill.filename.removePrefix("__bundled__")
            return try {
                context.assets.open("skills/$dirName/SKILL.md").bufferedReader().use { it.readText() }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to read bundled skill $dirName", e)
                ""
            }
        }

        // Online skills: fetch from GitHub with cache
        val cacheKey = "skill_content_${skill.filename}"
        val prefs = context.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE)
        val cached = prefs.getString(cacheKey, null)
        val cachedTs = prefs.getLong("${cacheKey}_ts", 0)
        if (cached != null && System.currentTimeMillis() - cachedTs < CONTENT_CACHE_TTL_MS) {
            return cached
        }

        return try {
            val url = "https://raw.githubusercontent.com/msitarzewski/agency-agents/main/${skill.category}/${skill.filename}"
            val request = Request.Builder().url(url).build()
            val client = getClient(context)
            val content = client.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() ?: "" else ""
            }
            if (content.isNotEmpty()) {
                prefs.edit()
                    .putString(cacheKey, content)
                    .putLong("${cacheKey}_ts", System.currentTimeMillis())
                    .apply()
            }
            content
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch content for ${skill.filename}", e)
            cached ?: ""
        }
    }

    /** Force refresh — clears list cache only (preserves content cache). */
    fun forceRefresh(context: Context): List<OnlineSkill> {
        context.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_SKILL_LIST)
            .remove(KEY_TIMESTAMP)
            .apply()
        return loadAllSkills(context)
    }

    @Serializable
    private data class GithubFile(
        val name: String,
        val path: String,
        val type: String,
    )
}
