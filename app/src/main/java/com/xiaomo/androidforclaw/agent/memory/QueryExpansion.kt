package com.xiaomo.androidforclaw.agent.memory

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/memory/query-expansion.ts
 *   (extractKeywords, expandQueryForFts, isQueryStopWordToken, STOP_WORDS_*)
 *
 * AndroidForClaw adaptation: keyword extraction and query expansion for memory search.
 * Supports multilingual stop words (EN, ZH, JA, KO, ES, PT, AR) and CJK tokenization.
 */

/**
 * QueryExpansion — Extract keywords and build FTS5 queries.
 * Aligned with OpenClaw query-expansion.ts.
 */
object QueryExpansion {

    // ── Multilingual stop words (aligned with OpenClaw) ──

    /** English stop words (90) */
    private val STOP_WORDS_EN = setOf(
        "a", "an", "the", "is", "are", "was", "were", "be", "been", "being",
        "have", "has", "had", "do", "does", "did", "will", "would", "could",
        "should", "may", "might", "shall", "can", "need", "dare", "ought",
        "used", "to", "of", "in", "for", "on", "with", "at", "by", "from",
        "as", "into", "through", "during", "before", "after", "above", "below",
        "between", "out", "off", "over", "under", "again", "further", "then",
        "once", "here", "there", "when", "where", "why", "how", "all", "both",
        "each", "few", "more", "most", "other", "some", "such", "no", "nor",
        "not", "only", "own", "same", "so", "than", "too", "very", "just",
        "don", "now", "and", "but", "or", "if", "that", "this", "it", "its",
        "what", "which", "who", "whom", "these", "those", "i", "me", "my",
        "we", "our", "you", "your", "he", "him", "his", "she", "her", "they",
        "them", "their", "about", "up",
        // Additional from OpenClaw: time, vague, request words
        "yesterday", "today", "tomorrow", "earlier", "later", "recently",
        "thing", "things", "stuff", "something",
        "please", "help", "find", "show", "get", "tell", "give"
    )

    /** Chinese stop words (67) */
    private val STOP_WORDS_ZH = setOf(
        "的", "了", "在", "是", "我", "你", "他", "她", "它", "们",
        "这", "那", "哪", "什么", "怎么", "为什么", "如何", "哪里", "哪个",
        "和", "与", "或", "但", "而", "又", "也", "都", "就", "才",
        "把", "被", "从", "到", "向", "对", "于", "以", "为", "因",
        "所以", "因为", "虽然", "但是", "如果", "那么",
        "有", "没有", "不", "不是", "可以", "能", "会", "要",
        "一个", "一些", "这个", "那个", "很", "非常", "比较",
        "吗", "呢", "吧", "啊", "哦", "嗯",
        "帮", "帮我", "找", "看", "告诉", "给我"
    )

    /** Japanese stop words (30) */
    private val STOP_WORDS_JA = setOf(
        "の", "に", "は", "を", "た", "が", "で", "て", "と", "し",
        "れ", "さ", "ある", "いる", "する", "も", "な", "こと", "として",
        "い", "や", "から", "まで", "よ", "ね", "です", "ます",
        "何", "どこ", "どう"
    )

    /** Korean stop words (72) */
    private val STOP_WORDS_KO = setOf(
        "은", "는", "이", "가", "을", "를", "의", "에", "에서", "로",
        "으로", "와", "과", "도", "만", "부터", "까지", "보다", "처럼", "같이",
        "에게", "한테", "대로", "밖에", "마다",
        "나", "저", "너", "당신", "그", "그녀", "우리", "그들",
        "이것", "그것", "저것", "여기", "거기", "저기",
        "하다", "이다", "있다", "없다", "되다", "않다", "못하다",
        "그리고", "그러나", "하지만", "또는", "그래서", "때문에",
        "것", "수", "등", "때", "곳", "점",
        "매우", "아주", "정말", "너무", "좀", "잘",
        "무엇", "어디", "언제", "왜", "어떻게",
        "어제", "오늘", "내일", "최근",
        "해줘", "알려줘", "찾아줘", "보여줘"
    )

    /** Spanish stop words (52) */
    private val STOP_WORDS_ES = setOf(
        "el", "la", "los", "las", "un", "una", "unos", "unas",
        "de", "del", "al", "en", "con", "por", "para", "sin", "sobre",
        "y", "o", "pero", "que", "como", "si", "no", "más", "muy",
        "es", "son", "está", "están", "ser", "estar", "ha", "han",
        "yo", "tú", "él", "ella", "nosotros", "ellos", "ellas",
        "este", "esta", "estos", "estas", "ese", "esa",
        "qué", "cómo", "dónde", "cuándo", "por qué"
    )

    /** Portuguese stop words (50) */
    private val STOP_WORDS_PT = setOf(
        "o", "a", "os", "as", "um", "uma", "uns", "umas",
        "de", "do", "da", "dos", "das", "em", "no", "na", "nos", "nas",
        "com", "por", "para", "sem", "sobre",
        "e", "ou", "mas", "que", "como", "se", "não", "mais", "muito",
        "é", "são", "está", "estão", "ser", "estar",
        "eu", "tu", "ele", "ela", "nós", "eles", "elas",
        "este", "esta", "esse", "essa"
    )

    /** Arabic stop words (46) */
    private val STOP_WORDS_AR = setOf(
        "في", "من", "إلى", "على", "عن", "مع", "هذا", "هذه", "ذلك", "تلك",
        "و", "أو", "لكن", "ثم", "حتى", "لأن", "إذا", "كما",
        "هو", "هي", "هم", "هن", "أنا", "أنت", "نحن", "أنتم",
        "كان", "يكون", "ليس", "لا", "لم", "لن", "قد",
        "ما", "ماذا", "أين", "متى", "كيف", "لماذا",
        "كل", "بعض", "أي", "جدا", "فقط", "أيضا"
    )

    /** Korean trailing particles for stripping (sorted by descending length) */
    private val KO_TRAILING_PARTICLES = listOf(
        "에서", "으로", "에게", "한테", "처럼", "같이", "보다", "까지", "부터", "마다", "밖에", "대로",
        "은", "는", "이", "가", "을", "를", "의", "에", "로", "와", "과", "도", "만"
    )

    /** Unicode word token pattern */
    private val TOKEN_PATTERN = Regex("[\\p{L}\\p{N}_]+")

    /** CJK Unified Ideographs range check */
    private fun isChinese(ch: Char): Boolean = ch.code in 0x4E00..0x9FFF
    private fun isJapaneseKana(ch: Char): Boolean = ch.code in 0x3040..0x30FF
    private fun isKoreanHangul(ch: Char): Boolean = ch.code in 0xAC00..0xD7AF || ch.code in 0x3131..0x3163

    /**
     * Check if a token is a stop word in any language.
     * Aligned with OpenClaw isQueryStopWordToken.
     */
    fun isQueryStopWordToken(token: String): Boolean {
        return token in STOP_WORDS_EN || token in STOP_WORDS_ZH || token in STOP_WORDS_JA ||
            token in STOP_WORDS_KO || token in STOP_WORDS_ES || token in STOP_WORDS_PT ||
            token in STOP_WORDS_AR
    }

    /**
     * Strip Korean trailing particle from a token.
     * Aligned with OpenClaw stripKoreanTrailingParticle.
     */
    private fun stripKoreanTrailingParticle(token: String): String? {
        for (particle in KO_TRAILING_PARTICLES) {
            if (token.endsWith(particle) && token.length > particle.length) {
                return token.dropLast(particle.length)
            }
        }
        return null
    }

    /**
     * Check if a keyword is valid (not too short, not pure numbers, not all punctuation).
     * Aligned with OpenClaw isValidKeyword.
     */
    private fun isValidKeyword(token: String): Boolean {
        if (token.isEmpty()) return false
        // Pure ASCII letters shorter than 3 chars
        if (token.all { it in 'a'..'z' || it in 'A'..'Z' } && token.length < 3) return false
        // Pure numbers
        if (token.all { it.isDigit() }) return false
        // All punctuation/symbol
        if (token.all { it.category in setOf(CharCategory.OTHER_PUNCTUATION, CharCategory.MATH_SYMBOL, CharCategory.CURRENCY_SYMBOL, CharCategory.MODIFIER_SYMBOL, CharCategory.OTHER_SYMBOL, CharCategory.DASH_PUNCTUATION, CharCategory.START_PUNCTUATION, CharCategory.END_PUNCTUATION, CharCategory.CONNECTOR_PUNCTUATION, CharCategory.INITIAL_QUOTE_PUNCTUATION, CharCategory.FINAL_QUOTE_PUNCTUATION) }) return false
        return true
    }

    /**
     * Tokenize text with CJK support.
     * Aligned with OpenClaw tokenize (query-expansion.ts).
     *
     * - Chinese: character unigrams + bigrams
     * - Japanese: extract Latin, katakana, kanji runs; kanji → unigrams + bigrams
     * - Korean: word-level with particle stripping
     * - Other: standard word tokens
     */
    private fun tokenize(text: String): List<String> {
        val lowered = text.lowercase()
        val tokens = mutableListOf<String>()
        val segments = lowered.split(Regex("[\\s\\p{P}]+")).filter { it.isNotEmpty() }

        for (segment in segments) {
            val hasJapanese = segment.any { isJapaneseKana(it) }
            val hasChinese = segment.any { isChinese(it) }
            val hasKorean = segment.any { isKoreanHangul(it) }

            when {
                hasJapanese -> {
                    // Japanese: extract Latin, katakana, kanji runs
                    val latinRuns = Regex("[a-z0-9_]+").findAll(segment).map { it.value }
                    val katakanaRuns = Regex("[\u30A0-\u30FF]+").findAll(segment).map { it.value }
                    val kanjiRuns = Regex("[\u4E00-\u9FFF]+").findAll(segment).map { it.value }
                    tokens.addAll(latinRuns)
                    tokens.addAll(katakanaRuns)
                    for (kanji in kanjiRuns) {
                        // Kanji: unigrams + bigrams
                        for (ch in kanji) tokens.add(ch.toString())
                        if (kanji.length >= 2) {
                            for (i in 0 until kanji.length - 1) {
                                tokens.add(kanji.substring(i, i + 2))
                            }
                        }
                    }
                }
                hasChinese && !hasJapanese -> {
                    // Chinese: character unigrams + bigrams
                    val chars = segment.filter { isChinese(it) }
                    for (ch in chars) tokens.add(ch.toString())
                    if (chars.length >= 2) {
                        for (i in 0 until chars.length - 1) {
                            tokens.add("${chars[i]}${chars[i + 1]}")
                        }
                    }
                    // Also extract any Latin/number runs
                    tokens.addAll(Regex("[a-z0-9_]+").findAll(segment).map { it.value })
                }
                hasKorean -> {
                    // Korean: word as-is, plus stripped stem
                    if (!isQueryStopWordToken(segment)) {
                        tokens.add(segment)
                        val stripped = stripKoreanTrailingParticle(segment)
                        if (stripped != null && !isQueryStopWordToken(stripped)) {
                            tokens.add(stripped)
                        }
                    }
                }
                else -> {
                    tokens.add(segment)
                }
            }
        }

        return tokens
    }

    /**
     * Extract keywords from a query string.
     * Aligned with OpenClaw extractKeywords.
     */
    fun extractKeywords(query: String): List<String> {
        return tokenize(query)
            .filter { !isQueryStopWordToken(it) && isValidKeyword(it) }
            .distinct()
    }

    /**
     * Build an FTS5-compatible expanded query string.
     * Aligned with OpenClaw expandQueryForFts.
     *
     * Uses "OR" join (not "AND") for broader recall.
     */
    fun buildFtsQuery(raw: String): String? {
        val keywords = extractKeywords(raw)
        if (keywords.isEmpty()) return null
        return keywords.joinToString(" OR ")
    }

    /**
     * Expand a query for FTS search.
     * Aligned with OpenClaw expandQueryForFts.
     *
     * @return Triple of (original, keywords, expanded query)
     */
    fun expandQueryForFts(query: String): Triple<String, List<String>, String> {
        val original = query.trim()
        val keywords = extractKeywords(original)
        val expanded = if (keywords.isNotEmpty()) {
            "$original OR ${keywords.joinToString(" OR ")}"
        } else {
            original
        }
        return Triple(original, keywords, expanded)
    }

    /**
     * Expand a query with individual keywords for broader recall.
     */
    fun expandQuery(query: String): List<String> {
        val keywords = extractKeywords(query)
        val expanded = mutableListOf(query)  // original query first

        if (keywords.size > 1) {
            for (keyword in keywords) {
                if (keyword.length >= 4) {
                    expanded.add(keyword)
                }
            }
        }

        return expanded.distinct()
    }
}
