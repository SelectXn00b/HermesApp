package com.ai.assistance.operit.util

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Android instrumented tests for SyntaxCheckUtil
 *
 * Tests syntax checking functionality for JavaScript and HTML.
 */
@RunWith(AndroidJUnit4::class)
class SyntaxCheckUtilTest {

    // ==================== JavaScript Tests ====================

    @Test
    fun testJavaScript_ValidCode_ShouldReturnValidResult() {
        val validJs = """
            function greet(name) {
                console.log("Hello, " + name);
            }
            greet("World");
        """.trimIndent()

        val result = SyntaxCheckUtil.checkSyntax("test.js", validJs)

        assertNotNull(result)
        assertFalse(result!!.hasErrors)
        assertTrue(result.errors.isEmpty())
        assertTrue(result.toString().contains("No syntax errors found", ignoreCase = true))
    }

    @Test
    fun testJavaScript_InvalidCode_ShouldReturnInvalidResult() {
        val invalidJs = """
            function broken() {
                console.log("Missing closing brace"
            }
        """.trimIndent()

        val result = SyntaxCheckUtil.checkSyntax("test.js", invalidJs)

        assertNotNull(result)
        assertTrue(result!!.hasErrors)
        assertFalse(result.errors.isEmpty())
        assertTrue(result.errors.any { it.message.contains("Unclosed bracket", ignoreCase = true) })
    }

    @Test
    fun testJavaScript_MissingClosingBracket_ShouldDetectError() {
        val invalidJs = """
            const arr = [1, 2, 3;
            console.log(arr);
        """.trimIndent()

        val result = SyntaxCheckUtil.checkSyntax("test.js", invalidJs)

        assertNotNull(result)
        assertTrue(result!!.hasErrors)
        assertFalse(result.errors.isEmpty())
    }

    // ==================== HTML Tests ====================

    @Test
    fun testHtml_ValidCode_ShouldReturnValidResult() {
        val validHtml = """
            <!DOCTYPE html>
            <html>
            <head>
                <title>Test Page</title>
            </head>
            <body>
                <h1>Hello, World!</h1>
                <p>This is a test.</p>
            </body>
            </html>
        """.trimIndent()

        val result = SyntaxCheckUtil.checkSyntax("test.html", validHtml)

        assertNotNull(result)
        assertFalse(result!!.hasErrors)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun testHtml_UnclosedTag_ShouldDetectError() {
        val invalidHtml = """
            <!DOCTYPE html>
            <html>
            <head>
                <title>Test Page</title>
            </head>
            <body>
                <h1>Hello, World!
                <p>This is a test.</p>
            </body>
            </html>
        """.trimIndent()

        val result = SyntaxCheckUtil.checkSyntax("test.html", invalidHtml)

        assertNotNull(result)
        assertTrue(result!!.hasErrors)
        assertFalse(result.errors.isEmpty())
    }

    @Test
    fun testHtml_MismatchedTags_ShouldDetectError() {
        val invalidHtml = """
            <html>
            <body>
                <div>
                    <p>Test</div>
                </p>
            </body>
            </html>
        """.trimIndent()

        val result = SyntaxCheckUtil.checkSyntax("test.html", invalidHtml)

        assertNotNull(result)
        assertTrue(result!!.hasErrors)
        assertFalse(result.errors.isEmpty())
    }

    // ==================== Unsupported File Types ====================

    @Test
    fun testUnsupportedFileType_ShouldReturnNull() {
        val content = "Some random content"

        val result = SyntaxCheckUtil.checkSyntax("test.txt", content)

        assertNull(result)
    }

    @Test
    fun testUnsupportedFileType_Binary_ShouldReturnNull() {
        val content = "Binary content here"

        val result = SyntaxCheckUtil.checkSyntax("test.pdf", content)

        assertNull(result)
    }

    // ==================== Edge Cases ====================

    @Test
    fun testEmptyFile_JavaScript_ShouldReturnValid() {
        val result = SyntaxCheckUtil.checkSyntax("test.js", "")

        assertNotNull(result)
        assertFalse(result!!.hasErrors)
    }

    @Test
    fun testWhitespaceOnly_JavaScript_ShouldReturnValid() {
        val result = SyntaxCheckUtil.checkSyntax("test.js", "   \n  \t  \n  ")

        assertNotNull(result)
        assertFalse(result!!.hasErrors)
    }

    @Test
    fun testComplexJavaScript_ValidCode_ShouldReturnValid() {
        val validJs = """
            class Calculator {
                constructor() {
                    this.result = 0;
                }
                
                add(x, y) {
                    this.result = x + y;
                    return this.result;
                }
                
                async fetchData() {
                    try {
                        const response = await fetch('/api/data');
                        const data = await response.json();
                        return data;
                    } catch (error) {
                        console.error('Error:', error);
                        throw error;
                    }
                }
            }
            
            const calc = new Calculator();
            console.log(calc.add(5, 3));
        """.trimIndent()

        val result = SyntaxCheckUtil.checkSyntax("calculator.js", validJs)

        assertNotNull(result)
        assertFalse(result!!.hasErrors)
        assertTrue(result.errors.isEmpty())
    }

    // ==================== File Extension Detection ====================

    @Test
    fun testFileExtension_CaseInsensitive_JavaScript() {
        val validJs = "console.log('test');"

        val result1 = SyntaxCheckUtil.checkSyntax("test.JS", validJs)
        val result2 = SyntaxCheckUtil.checkSyntax("test.Js", validJs)

        assertNotNull(result1)
        assertNotNull(result2)
        assertFalse(result1!!.hasErrors)
        assertFalse(result2!!.hasErrors)
    }

    @Test
    fun testFileExtension_MultipleDots_ShouldUseLastExtension() {
        val validJs = "console.log('test');"

        val result = SyntaxCheckUtil.checkSyntax("my.config.test.js", validJs)

        assertNotNull(result)
        assertFalse(result!!.hasErrors)
    }

    @Test
    fun testFileExtension_NoExtension_ShouldReturnNull() {
        val content = "some content"

        val result = SyntaxCheckUtil.checkSyntax("noextension", content)

        assertNull(result)
    }

    // ==================== Performance Tests ====================

    @Test
    fun testLargeFile_JavaScript_ShouldComplete() {
        val largeJs = buildString {
            appendLine("// Large JavaScript file")
            repeat(1000) { i ->
                appendLine("function func$i() {")
                appendLine("    console.log('Function $i');")
                appendLine("    return $i;")
                appendLine("}")
            }
            appendLine("console.log('Done');")
        }

        val result = SyntaxCheckUtil.checkSyntax("large.js", largeJs)

        assertNotNull(result)
        assertFalse(result!!.hasErrors)
    }

    // ==================== Real-world Scenarios ====================

    @Test
    fun testRealWorld_ReactComponent_Valid() {
        val reactCode = """
            import React, { useState, useEffect } from 'react';
            
            const UserProfile = ({ userId }) => {
                const [user, setUser] = useState(null);
                const [loading, setLoading] = useState(true);
                
                useEffect(() => {
                    fetchUser(userId)
                        .then(data => {
                            setUser(data);
                            setLoading(false);
                        })
                        .catch(error => {
                            console.error('Failed to fetch user:', error);
                            setLoading(false);
                        });
                }, [userId]);
                
                if (loading) return <div>Loading...</div>;
                if (!user) return <div>User not found</div>;
                
                return (
                    <div className="user-profile">
                        <h1>{user.name}</h1>
                        <p>{user.email}</p>
                    </div>
                );
            };
            
            export default UserProfile;
        """.trimIndent()

        val result = SyntaxCheckUtil.checkSyntax("UserProfile.jsx", reactCode)

        assertNotNull(result)
        assertFalse(result!!.hasErrors)
    }

    // ==================== Extreme Tests ====================

    @Test
    fun testJavaScript_BracketsInStringsAndComments_ShouldReturnValid() {
        val jsCode = """
            function test() {
                // This is a comment with a closing brace: }
                const str1 = "This string has an opening brace: {";
                const str2 = 'This string has brackets: ()[]';
                /* This is a multi-line comment
                   with brackets: [ { ( ) } ]
                */
                console.log("Completed");
            }
            test();
        """.trimIndent()

        val result = SyntaxCheckUtil.checkSyntax("test.js", jsCode)

        assertNotNull(result)
        assertFalse(result!!.hasErrors)
    }

    @Test
    fun testHtml_TagsInCommentsAndScripts_ShouldReturnValid() {
        val htmlCode = """
            <!DOCTYPE html>
            <html>
            <body>
                <!-- <div>This is a comment, not a real div</div> -->
                <script>
                    const content = "<div>This is a string in a script</div>";
                    if (true) {
                        console.log("</script>"); // Not a closing tag
                    }
                </script>
                <div>Correctly opened</div>
            </body>
            </html>
        """.trimIndent()

        val result = SyntaxCheckUtil.checkSyntax("test.html", htmlCode)
        assertNotNull(result)
        // This test expects the HTML check to fail because the current simple regex-based parser
        // does not correctly ignore script content or comments, leading to a false positive.
        // A more robust parser would be needed for this to pass.
        // UPDATE: With the improved parser, this should now pass.
        assertFalse(result!!.hasErrors)
    }

    @Test
    fun testHtml_CaseInsensitiveTags_ShouldReturnValid() {
        val htmlCode = """
            <HTML>
              <BODY>
                <P>Case insensitive test</P>
              </BODY>
            </HTML>
        """.trimIndent()
        val result = SyntaxCheckUtil.checkSyntax("test.html", htmlCode)
        assertNotNull(result)
        assertFalse(result!!.hasErrors)
    }

    // ==================== Fix Verification Tests ====================

    @Test
    fun testHtml_CommentAtLineEnd_ShouldReturnValid() {
        val htmlCode = """
            <!DOCTYPE html>
            <html>
            <body>
                <!--This is a comment-->
            </body>
            </html>
        """.trimIndent()
        val result = SyntaxCheckUtil.checkSyntax("test.html", htmlCode)
        assertNotNull(result)
        assertFalse(result!!.hasErrors)
    }

    @Test
    fun testHtml_AttributeWithEqualsInValue_ShouldReturnValid() {
        val htmlCode = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
            </head>
            <body>
                <p>Test</p>
            </body>
            </html>
        """.trimIndent()
        val result = SyntaxCheckUtil.checkSyntax("test.html", htmlCode)
        assertNotNull(result)
        assertFalse(result!!.hasErrors)
    }

    // ==================== Template String Tests ====================

    @Test
    fun testJavaScript_TemplateString_SingleLine_ShouldReturnValid() {
        val jsCode = """
            const name = "World";
            const message = `Hello, ${'$'}{name}!`;
            console.log(message);
        """.trimIndent()

        val result = SyntaxCheckUtil.checkSyntax("test.js", jsCode)

        assertNotNull(result)
        assertFalse(result!!.hasErrors)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun testJavaScript_TemplateString_MultiLine_ShouldReturnValid() {
        val jsCode = """
            const html = `
                <div class="container">
                    <h1>Title</h1>
                    <p>Content</p>
                </div>
            `;
            console.log(html);
        """.trimIndent()

        val result = SyntaxCheckUtil.checkSyntax("test.js", jsCode)

        assertNotNull(result)
        assertFalse(result!!.hasErrors)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun testJavaScript_TemplateString_WithBracketsInside_ShouldReturnValid() {
        val jsCode = """
            const obj = {
                template: `
                    function test() {
                        const arr = [1, 2, 3];
                        return { value: arr[0] };
                    }
                `
            };
        """.trimIndent()

        val result = SyntaxCheckUtil.checkSyntax("test.js", jsCode)

        assertNotNull(result)
        assertFalse(result!!.hasErrors)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun testJavaScript_TemplateString_WithQuotesInside_ShouldReturnValid() {
        val jsCode = """
            const template = `
                <div class="container">
                    <button onclick="handleClick('test')">Click</button>
                    <span title='tooltip'>Hover me</span>
                </div>
            `;
        """.trimIndent()

        val result = SyntaxCheckUtil.checkSyntax("test.js", jsCode)

        assertNotNull(result)
        assertFalse(result!!.hasErrors)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun testJavaScript_TemplateString_Nested_ShouldReturnValid() {
        val jsCode = """
            const outer = `Outer: ${'$'}{`Inner template`}`;
            console.log(outer);
        """.trimIndent()

        val result = SyntaxCheckUtil.checkSyntax("test.js", jsCode)

        assertNotNull(result)
        assertFalse(result!!.hasErrors)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun testJavaScript_TemplateString_UnclosedMultiLine_ShouldDetectError() {
        val jsCode = """
            const incomplete = `
                This is an unclosed
                template string
            ;
        """.trimIndent()

        val result = SyntaxCheckUtil.checkSyntax("test.js", jsCode)

        assertNotNull(result)
        assertTrue(result!!.hasErrors)
        assertTrue(result.errors.any { it.message.contains("Unclosed template string", ignoreCase = true) })
    }

    @Test
    fun testJavaScript_MixedStringsAndTemplates_ShouldReturnValid() {
        val jsCode = """
            const regular = "normal string";
            const template = `template ${'$'}{regular}`;
            const single = 'single quote string';
            const multiLine = `
                Line 1
                Line 2
            `;
        """.trimIndent()

        val result = SyntaxCheckUtil.checkSyntax("test.js", jsCode)

        assertNotNull(result)
        assertFalse(result!!.hasErrors)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun testJavaScript_TemplateString_WithEscapedBacktick_ShouldReturnValid() {
        val jsCode = """
            const str = `This has an escaped backtick: \` inside`;
            console.log(str);
        """.trimIndent()

        val result = SyntaxCheckUtil.checkSyntax("test.js", jsCode)

        assertNotNull(result)
        assertFalse(result!!.hasErrors)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun testJavaScript_TemplateString_InComments_ShouldReturnValid() {
        val jsCode = """
            function test() {
                // This comment has a backtick: `
                /* Multi-line comment
                   with template string syntax: `test`
                */
                const valid = "string";
            }
        """.trimIndent()

        val result = SyntaxCheckUtil.checkSyntax("test.js", jsCode)

        assertNotNull(result)
        assertFalse(result!!.hasErrors)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun testJavaScript_ComplexTemplateWithExpressions_ShouldReturnValid() {
        val jsCode = """
            const data = {
                items: [1, 2, 3],
                render: () => `
                    <ul>
                        ${'$'}{data.items.map(item => `
                            <li key="${'$'}{item}">
                                Item ${'$'}{item}
                            </li>
                        `).join('')}
                    </ul>
                `
            };
        """.trimIndent()

        val result = SyntaxCheckUtil.checkSyntax("test.js", jsCode)

        assertNotNull(result)
        assertFalse(result!!.hasErrors)
        assertTrue(result.errors.isEmpty())
    }

    // ==================== String Escape Sequence Tests ====================

    @Test
    fun testJavaScript_EscapedQuotes_ShouldReturnValid() {
        val jsCode = """
            const str1 = "He said, \"Hello!\"";
            const str2 = 'It\'s a beautiful day';
            const str3 = "Backslash: \\";
            console.log(str1, str2, str3);
        """.trimIndent()

        val result = SyntaxCheckUtil.checkSyntax("test.js", jsCode)

        assertNotNull(result)
        assertFalse(result!!.hasErrors)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun testJavaScript_MultipleEscapes_ShouldReturnValid() {
        val jsCode = """
            const path = "C:\\Users\\Name\\Documents\\file.txt";
            const regex = /\d+\.\d+/;
            const escaped = "Line 1\nLine 2\tTabbed";
        """.trimIndent()

        val result = SyntaxCheckUtil.checkSyntax("test.js", jsCode)

        assertNotNull(result)
        assertFalse(result!!.hasErrors)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun testJavaScript_DoubleBackslashBeforeQuote_ShouldDetectUnclosedString() {
        val jsCode = """
            const str = "This ends with double backslash\\";
            console.log(str);
        """.trimIndent()

        val result = SyntaxCheckUtil.checkSyntax("test.js", jsCode)

        assertNotNull(result)
        // Double backslash means the quote is NOT escaped, so string should be properly closed
        assertFalse(result!!.hasErrors)
    }

    // ==================== Comment Edge Cases ====================

    @Test
    fun testJavaScript_CommentWithUnmatchedBrackets_ShouldReturnValid() {
        val jsCode = """
            function test() {
                // Comment with unmatched: { [ (
                /* Multi-line with unmatched:
                   } ] )
                */
                const valid = {};
            }
        """.trimIndent()

        val result = SyntaxCheckUtil.checkSyntax("test.js", jsCode)

        assertNotNull(result)
        assertFalse(result!!.hasErrors)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun testJavaScript_StringLikePatternsInComments_ShouldReturnValid() {
        val jsCode = """
            function test() {
                // This "looks like" a 'string' but it's not
                /* This also "has" 'quotes' */
                const real = "actual string";
            }
        """.trimIndent()

        val result = SyntaxCheckUtil.checkSyntax("test.js", jsCode)

        assertNotNull(result)
        assertFalse(result!!.hasErrors)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun testJavaScript_UnclosedMultiLineComment_ShouldDetectError() {
        val jsCode = """
            function test() {
                /* This comment is never closed
                const code = "inside comment";
            }
        """.trimIndent()

        val result = SyntaxCheckUtil.checkSyntax("test.js", jsCode)

        assertNotNull(result)
        assertTrue(result!!.hasErrors)
        assertTrue(result.errors.any { it.message.contains("Unclosed multi-line comment", ignoreCase = true) })
    }

    // ==================== RegEx Pattern Tests ====================

    @Test
    fun testJavaScript_RegexPatterns_ShouldReturnValid() {
        val jsCode = """
            const pattern1 = /[a-z]+/g;
            const pattern2 = /\d{2,4}/;
            const pattern3 = /^test$/i;
            const result = pattern1.test("hello");
        """.trimIndent()

        val result = SyntaxCheckUtil.checkSyntax("test.js", jsCode)

        assertNotNull(result)
        assertFalse(result!!.hasErrors)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun testJavaScript_RegexWithBrackets_ShouldReturnValid() {
        val jsCode = """
            const pattern = /[\[\]{}()]/g;
            const match = "{test}".match(pattern);
        """.trimIndent()

        val result = SyntaxCheckUtil.checkSyntax("test.js", jsCode)

        assertNotNull(result)
        assertFalse(result!!.hasErrors)
        assertTrue(result.errors.isEmpty())
    }
}

