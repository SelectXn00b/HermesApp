package com.ai.assistance.operit.util.stream

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@MediumTest
class StreamKmpGraphTest {

    @Test
    fun testCharCondition() {
        val condition = CharCondition('a')
        assertTrue(condition.matches('a'))
        assertFalse(condition.matches('b'))
        assertEquals("'a'", condition.getDescription())
    }

    @Test
    fun testCharRangeCondition() {
        val condition = CharRangeCondition('a', 'c')
        assertTrue(condition.matches('a'))
        assertTrue(condition.matches('b'))
        assertTrue(condition.matches('c'))
        assertFalse(condition.matches('d'))
        assertEquals("[a-c]", condition.getDescription())
    }

    @Test
    fun testCharSetCondition() {
        val condition = CharSetCondition('a', 'c', 'e')
        assertTrue(condition.matches('a'))
        assertFalse(condition.matches('b'))
        assertTrue(condition.matches('c'))
        assertFalse(condition.matches('d'))
        assertTrue(condition.matches('e'))
        assertEquals("[ace]", condition.getDescription())
    }

    @Test
    fun testNotCondition() {
        val baseCondition = CharCondition('a')
        val notCondition = NotCondition(baseCondition)
        assertFalse(notCondition.matches('a'))
        assertTrue(notCondition.matches('b'))
        assertEquals("not('a')", notCondition.getDescription())
    }

    @Test
    fun testOrCondition() {
        val condition = OrCondition(CharCondition('a'), CharCondition('b'), CharCondition('c'))
        assertTrue(condition.matches('a'))
        assertTrue(condition.matches('b'))
        assertTrue(condition.matches('c'))
        assertFalse(condition.matches('d'))
        assertTrue(condition.getDescription().contains("OR"))
    }

    @Test
    fun testAndCondition() {
        val condition =
                AndCondition(
                        CharRangeCondition('a', 'z'),
                        NotCondition(CharSetCondition('x', 'y', 'z'))
                )
        assertTrue(condition.matches('a'))
        assertTrue(condition.matches('b'))
        assertFalse(condition.matches('x'))
        assertFalse(condition.matches('y'))
        assertFalse(condition.matches('z'))
        assertTrue(condition.getDescription().contains("AND"))
    }

    @Test
    fun testPredicateCondition() {
        val condition = PredicateCondition("isDigit") { it.isDigit() }
        assertTrue(condition.matches('0'))
        assertTrue(condition.matches('9'))
        assertFalse(condition.matches('a'))
        assertEquals("isDigit", condition.getDescription())
    }

    @Test
    fun testStreamKmpGraphProcessText() {
        val builder = StreamKmpGraphBuilder()
        val graph = builder.build(kmpPattern { literal("abc") })

        val matches = graph.processText("ababcabc")
        assertEquals(listOf(5, 8), matches)
    }

    @Test
    fun testStreamKmpGraphBuilder() {
        // 测试字符串模式构建
        val builder1 = StreamKmpGraphBuilder()
        val graph1 = builder1.build(kmpPattern { literal("test") })

        graph1.reset()
        assertNotEquals(StreamKmpMatchResult.NoMatch, graph1.processChar('t'))
        assertNotEquals(StreamKmpMatchResult.NoMatch, graph1.processChar('e'))
        assertNotEquals(StreamKmpMatchResult.NoMatch, graph1.processChar('s'))
        val result = graph1.processChar('t')
        assertTrue(result is StreamKmpMatchResult.Match && result.isFullMatch)

        // 测试条件列表构建
        val builder2 = StreamKmpGraphBuilder()
        val conditions =
                listOf(
                        CharCondition('a'),
                        CharRangeCondition('1', '3'),
                        CharSetCondition('x', 'y', 'z')
                )
        val graph2 = builder2.build(kmpPattern { conditions.forEach { add(it) } })

        graph2.reset()
        assertNotEquals(StreamKmpMatchResult.NoMatch, graph2.processChar('a'))
        assertNotEquals(StreamKmpMatchResult.NoMatch, graph2.processChar('2'))
        val result2 = graph2.processChar('y')
        assertTrue(result2 is StreamKmpMatchResult.Match && result2.isFullMatch)
    }

    @Test
    fun testComplexPattern() {
        val builder = StreamKmpGraphBuilder()
        val conditions =
                listOf(
                        OrCondition(CharCondition('a'), CharCondition('A')),
                        OrCondition(CharCondition('b'), CharCondition('B')),
                        OrCondition(CharCondition('c'), CharCondition('C'))
                )
        val graph = builder.build(kmpPattern { conditions.forEach { add(it) } })

        // 测试小写匹配
        graph.reset()
        graph.processChar('a')
        graph.processChar('b')
        assertTrue((graph.processChar('c') as StreamKmpMatchResult.Match).isFullMatch)

        // 测试大写匹配
        graph.reset()
        graph.processChar('A')
        graph.processChar('B')
        assertTrue((graph.processChar('C') as StreamKmpMatchResult.Match).isFullMatch)

        // 测试混合匹配
        graph.reset()
        graph.processChar('a')
        graph.processChar('B')
        assertTrue((graph.processChar('c') as StreamKmpMatchResult.Match).isFullMatch)
    }

    @Test
    fun testFailureTransitions() {
        val builder = StreamKmpGraphBuilder()
        val graph = builder.build(kmpPattern { literal("ABABC") })

        val text = "ABABABABC"
        val matches = graph.processText(text)

        assertEquals(1, matches.size)
        assertEquals(text.length, matches[0])
    }

    @Test
    fun testKmpConditionOperators() {
        // 测试加法操作符 (OR)
        val orCondition = CharCondition('a') + CharCondition('b')
        assertTrue(orCondition.matches('a'))
        assertTrue(orCondition.matches('b'))
        assertFalse(orCondition.matches('c'))

        // 测试乘法操作符 (AND)
        val andCondition = CharRangeCondition('a', 'z') * CharRangeCondition('m', 'z')
        assertFalse(andCondition.matches('a')) // 不在两个范围的交集中
        assertTrue(andCondition.matches('n')) // 在两个范围的交集中
        assertFalse(andCondition.matches('0'))

        // 测试取反操作符
        val notCondition = !CharCondition('a')
        assertFalse(notCondition.matches('a'))
        assertTrue(notCondition.matches('b'))
    }

    @Test
    fun testCharOperators() {
        // 测试 Char.unaryPlus 操作符
        val charCondition = +'a'
        assertTrue(charCondition.matches('a'))
        assertFalse(charCondition.matches('b'))

        // 测试 or 中缀操作符
        val ignoreCase = 'a' or 'A'
        assertTrue(ignoreCase.matches('a'))
        assertTrue(ignoreCase.matches('A'))
        assertFalse(ignoreCase.matches('b'))

        // 测试 to 中缀操作符 (字符范围)
        val rangeCondition = 'a' to 'c'
        assertTrue(rangeCondition.matches('a'))
        assertTrue(rangeCondition.matches('b'))
        assertTrue(rangeCondition.matches('c'))
        assertFalse(rangeCondition.matches('d'))
    }

    @Test
    fun testKmpPatternDSL() {
        val pattern = kmpPattern {
            char('a')
            digit()
            letter()
        }

        assertEquals(3, pattern.conditions.size)

        val builder = StreamKmpGraphBuilder()
        val graph = builder.build(pattern)

        graph.reset()
        graph.processChar('a')
        graph.processChar('5')
        val result = graph.processChar('b')
        assertTrue(result is StreamKmpMatchResult.Match && result.isFullMatch)

        // 测试特殊字符匹配
        val specialPattern = kmpPattern { charIgnoreCase('a') }

        val specialGraph = builder.build(specialPattern)
        specialGraph.reset()
        val resultA = specialGraph.processChar('a')
        assertTrue(resultA is StreamKmpMatchResult.Match && resultA.isFullMatch)
        specialGraph.reset()
        val resultB = specialGraph.processChar('A')
        assertTrue(resultB is StreamKmpMatchResult.Match && resultB.isFullMatch)
    }

    @Test
    fun testPredefinedConditions() {
        // 测试预定义常量
        assertTrue(DIGITS.matches('0'))
        assertTrue(DIGITS.matches('9'))
        assertFalse(DIGITS.matches('a'))

        assertTrue(LETTERS.matches('a'))
        assertTrue(LETTERS.matches('Z'))
        assertFalse(LETTERS.matches('0'))

        assertTrue(ALPHANUMERIC.matches('a'))
        assertTrue(ALPHANUMERIC.matches('0'))
        assertFalse(ALPHANUMERIC.matches('#'))

        assertTrue(WHITESPACE.matches(' '))
        assertTrue(WHITESPACE.matches('\t'))
        assertFalse(WHITESPACE.matches('a'))

        assertTrue(ANY_CHAR.matches('a'))
        assertTrue(ANY_CHAR.matches('0'))
        assertTrue(ANY_CHAR.matches('#'))
    }

    @Test
    fun testNegationFunctions() {
        // 测试 not 函数
        val notDigit = not(DIGITS)
        assertFalse(notDigit.matches('0'))
        assertTrue(notDigit.matches('a'))

        // 测试 notChars 函数
        val noVowels = notChars('a', 'e', 'i', 'o', 'u')
        assertFalse(noVowels.matches('a'))
        assertTrue(noVowels.matches('b'))

        // 测试 notInRange 函数
        val notLowerCase = notInRange('a', 'z')
        assertFalse(notLowerCase.matches('a'))
        assertTrue(notLowerCase.matches('A'))
        assertTrue(notLowerCase.matches('0'))
    }

    @Test
    fun testInversePredefinedConditions() {
        // 测试反向预定义常量
        assertFalse(NOT_DIGITS.matches('0'))
        assertTrue(NOT_DIGITS.matches('a'))

        assertFalse(NOT_LETTERS.matches('a'))
        assertTrue(NOT_LETTERS.matches('0'))

        assertFalse(NOT_ALPHANUMERIC.matches('a'))
        assertFalse(NOT_ALPHANUMERIC.matches('0'))
        assertTrue(NOT_ALPHANUMERIC.matches('#'))

        assertFalse(NOT_WHITESPACE.matches(' '))
        assertTrue(NOT_WHITESPACE.matches('a'))
    }

    @Test
    fun testKmpPatternNegationMethods() {
        val pattern = kmpPattern {
            notChar('a')
            noneOf('0', '1', '2')
            notInRange('A', 'Z')
            notDigit()
            not(WHITESPACE)
        }

        assertEquals(5, pattern.conditions.size)

        // 按照上面的条件，字符'b'应该满足所有条件
        val builder = StreamKmpGraphBuilder()
        val graph = builder.build(pattern)

        graph.reset()
        graph.processChar('b') // 匹配第1个条件
        graph.processChar('b') // 匹配第2个条件
        graph.processChar('b') // 匹配第3个条件
        graph.processChar('b') // 匹配第4个条件
        val result = graph.processChar('b') // 匹配第5个条件，是最后一个，返回true
        assertTrue(result is StreamKmpMatchResult.Match && result.isFullMatch)
    }

    @Test
    fun testComplexDSLPattern() {
        // 创建一个复杂的模式：字母+数字+非空白字符
        val pattern = kmpPattern {
            letter()
            digit()
            notWhitespace()
            any() // 匹配任何字符
        }

        val builder = StreamKmpGraphBuilder()
        val graph = builder.build(pattern)

        // 测试正面案例："a1b!"
        graph.reset()
        graph.processChar('a')
        graph.processChar('1')
        graph.processChar('b')
        val result = graph.processChar('!')
        assertTrue(result is StreamKmpMatchResult.Match && result.isFullMatch)

        // 测试复杂模式："ab123"
        val complexBuilder = StreamKmpGraphBuilder()
        val complexPattern = kmpPattern {
            char('a')
            charIgnoreCase('b')
            notChar('x')
            digit()
        }

        val complexGraph = complexBuilder.build(complexPattern)
        complexGraph.reset()
        complexGraph.processChar('a')
        complexGraph.processChar('B') // 大写B也匹配
        complexGraph.processChar('c') // 不是'x'，所以匹配
        val result2 = complexGraph.processChar('1')
        assertTrue(result2 is StreamKmpMatchResult.Match && result2.isFullMatch)
    }

    @Test
    fun testRepeat() {
        val pattern = kmpPattern {
            repeat(3) { char('a') }
            notChar('b')
        }
        val builder = StreamKmpGraphBuilder()
        val graph = builder.build(pattern)

        // The pattern is "aaa" followed by not 'b'.
        // Test with "aaaa"
        graph.reset()
        graph.processChar('a') // a
        graph.processChar('a') // aa
        graph.processChar('a') // aaa
        val result1 = graph.processChar('a') // aaaa, 'a' matches notChar('b'), final state
        assertTrue(result1 is StreamKmpMatchResult.Match && result1.isFullMatch)

        // Test with "aaac"
        graph.reset()
        graph.processChar('a') // a
        graph.processChar('a') // aa
        graph.processChar('a') // aaa
        val result2 = graph.processChar('c') // aaac, 'c' matches notChar('b'), final state
        assertTrue(result2 is StreamKmpMatchResult.Match && result2.isFullMatch)

        // Test with "aaab" - should fail on 'b'
        graph.reset()
        graph.processChar('a')
        graph.processChar('a')
        graph.processChar('a')
        val result3 = graph.processChar('b') // 'b' does not match notChar('b'), so it fails.
        assertFalse(result3 is StreamKmpMatchResult.Match && result3.isFullMatch)
    }

    @Test
    fun testGreedyStar() {
        val builder = StreamKmpGraphBuilder()

        // Test case from user prompt: (not 'a')*a
        val pattern1 = kmpPattern {
            greedyStar { notChar('a') }
            char('a')
        }
        val graph1 = builder.build(pattern1)

        // For "bbba", the pattern matches the whole string.
        assertEquals(listOf(4), graph1.processText("bbba"))
        graph1.reset()
        // For "a", the greedy star matches zero characters, then 'a' matches.
        assertEquals(listOf(1), graph1.processText("a"))
        graph1.reset()
        // For "bba_bba", it should find two non-overlapping matches
        assertEquals(listOf(3, 7), graph1.processText("bba_bba"))

        // Test case with ambiguity: a(.*)b
        val pattern2 = kmpPattern {
            char('a')
            greedyStar { any() }
            char('b')
        }
        val graph2 = builder.build(pattern2)

        // Should match "axxxb" in "axxxbyyy". processText now resets, so we test the whole
        // sequence.
        val matches = graph2.processText("axxxbyyyaxb")
        assertEquals(listOf(5, 11), matches)

        // Should find multiple matches correctly
        graph2.reset()
        val matches2 = graph2.processText("ab--axb--ab")
        assertEquals(listOf(2, 7, 11), matches2)
    }

    @Test
    fun testGreedyStarAtEnd() {
        val builder = StreamKmpGraphBuilder()
        val pattern = kmpPattern {
            char('a')
            greedyStar { char('b') }
        }
        val graph = builder.build(pattern)

        // Test matching sequences. processText finds non-overlapping matches.
        // "abb" is one match. "c" resets. "ab" is another match.
        val matches = graph.processText("abbcab")
        assertEquals(listOf(1, 2, 3, 5, 6), matches)

        // Test single `a` match
        graph.reset()
        assertEquals(listOf(1), graph.processText("a"))
    }

    @Test
    fun testStreamingMatchCapture() {
        val builder = StreamKmpGraphBuilder()
        val pattern = kmpPattern {
            group(1) {
                char('a')
                char('b')
            }
            char('c')
        }
        val graph = builder.build(pattern)

        // Test a successful match
        graph.reset()
        var result: StreamKmpMatchResult = StreamKmpMatchResult.NoMatch

        println("初始result: $result")

        result = graph.processChar('a')
        println("处理字符 'a' 后的result: $result")

        result = graph.processChar('b')
        println("处理字符 'b' 后的result: $result")

        result = graph.processChar('c')
        println("处理字符 'c' 后的result: $result")

        assertTrue(result is StreamKmpMatchResult.Match)
        val match = result as StreamKmpMatchResult.Match
        assertTrue(match.isFullMatch)
        assertEquals("ab", match.groups[GROUP_TAG_NAME])

        // 测试更多情况
        println("\n测试不匹配的情况：")
        graph.reset()
        result = graph.processChar('x')
        println("处理字符 'x' 后的result: $result")

        println("\n测试部分匹配后失败的情况：")
        graph.reset()
        result = graph.processChar('a')
        println("处理字符 'a' 后的result: $result")
        result = graph.processChar('x')
        println("处理字符 'x' 后的result: $result")
    }

    @Test
    fun testAdvancedGroupCapture() {
        val builder = StreamKmpGraphBuilder()

        // 测试多个分组
        val multiGroupPattern = kmpPattern {
            group(1) { char('a') }
            char('-')
            group(2) { digit() }
        }

        var graph = builder.build(multiGroupPattern)
        graph.reset()
        graph.processChar('a')
        graph.processChar('-')
        var result = graph.processChar('5')

        assertTrue(result is StreamKmpMatchResult.Match)
        var match = result as StreamKmpMatchResult.Match
        assertTrue(match.isFullMatch)
        assertEquals("a", match.groups[1])
        assertEquals("5", match.groups[2])

        // 测试嵌套分组
        val nestedGroupPattern = kmpPattern {
            group(1) {
                char('(')
                group(2) {
                    letter()
                    digit()
                }
                char(')')
            }
        }

        graph = builder.build(nestedGroupPattern)
        graph.reset()
        graph.processChar('(')
        graph.processChar('x')
        graph.processChar('7')
        result = graph.processChar(')')

        assertTrue(result is StreamKmpMatchResult.Match)
        match = result as StreamKmpMatchResult.Match
        assertTrue(match.isFullMatch)
        assertEquals("(x7)", match.groups[1])
        assertEquals("x7", match.groups[2])

        // 测试分组与贪婪星号组合
        val greedyGroupPattern = kmpPattern {
            group(1) {
                char('a')
                greedyStar { notChar('b') }
            }
            char('b')
        }

        graph = builder.build(greedyGroupPattern)
        graph.reset()
        graph.processChar('a')
        graph.processChar('x')
        graph.processChar('y')
        graph.processChar('z')
        result = graph.processChar('b')

        assertTrue(result is StreamKmpMatchResult.Match)
        match = result as StreamKmpMatchResult.Match
        assertTrue(match.isFullMatch)
        assertEquals("axyz", match.groups[1])

        // 测试分组与重复组合
        val repeatGroupPattern = kmpPattern { group(1) { repeat(2) { digit() } } }

        graph = builder.build(repeatGroupPattern)
        graph.reset()
        graph.processChar('1')
        result = graph.processChar('2')

        assertTrue(result is StreamKmpMatchResult.Match)
        match = result as StreamKmpMatchResult.Match
        assertTrue(match.isFullMatch)
        assertEquals("12", match.groups[1])

        // 测试分组内使用复杂条件
        val complexGroupPattern = kmpPattern {
            group(1) {
                char('x')
                group(2) { predicate("偶数") { it.isDigit() && it.digitToInt() % 2 == 0 } }
                charIgnoreCase('y')
            }
        }

        graph = builder.build(complexGroupPattern)
        graph.reset()
        graph.processChar('x')
        graph.processChar('4')
        result = graph.processChar('Y')

        assertTrue(result is StreamKmpMatchResult.Match)
        match = result as StreamKmpMatchResult.Match
        assertTrue(match.isFullMatch)
        assertEquals("x4Y", match.groups[1])
        assertEquals("4", match.groups[2])
    }
}

private const val GROUP_TAG_NAME = 1
