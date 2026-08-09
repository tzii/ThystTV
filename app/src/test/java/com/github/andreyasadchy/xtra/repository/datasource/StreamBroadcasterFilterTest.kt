package com.github.andreyasadchy.xtra.repository.datasource

import com.github.andreyasadchy.xtra.model.ui.Stream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class StreamBroadcasterFilterTest {

    @Test
    fun `identity predicate drops only streams with both identifiers null`() {
        val missingIdentity = Stream()
        val idOnly = Stream(channelId = "123")
        val loginOnly = Stream(channelLogin = "streamer")
        val bothIdentifiers = Stream(channelId = "123", channelLogin = "streamer")

        assertFalse(missingIdentity.hasBroadcasterIdentity())
        assertTrue(idOnly.hasBroadcasterIdentity())
        assertTrue(loginOnly.hasBroadcasterIdentity())
        assertTrue(bothIdentifiers.hasBroadcasterIdentity())
        assertEquals(
            listOf(idOnly, loginOnly, bothIdentifiers),
            listOf(missingIdentity, idOnly, loginOnly, bothIdentifiers).filterValidBroadcasters()
        )
    }

    @Test
    fun `blank identifier values remain valid because filtering is null-only`() {
        val missingIdentity = Stream()
        val blankId = Stream(channelId = "")
        val blankLogin = Stream(channelLogin = "")

        assertEquals(
            listOf(blankId, blankLogin),
            listOf(missingIdentity, blankId, blankLogin).filterValidBroadcasters()
        )
    }

    @Test
    fun `all eight stream mapping paths apply the shared filter`() {
        val sourceRoot = findDataSourceRoot()
        val expectedMethods = mapOf(
            "GameStreamsDataSource.kt" to listOf("gqlQueryLoad", "gqlLoad", "helixLoad"),
            "SearchStreamsDataSource.kt" to listOf("gqlQueryLoad", "helixLoad"),
            "StreamsDataSource.kt" to listOf("gqlQueryLoad", "gqlLoad", "helixLoad"),
        )

        expectedMethods.forEach { (fileName, methodNames) ->
            val source = String(
                Files.readAllBytes(sourceRoot.resolve(fileName)),
                Charsets.UTF_8
            ).withoutCommentsAndStrings()
            methodNames.forEach { methodName ->
                assertMappingUsesSharedFilter(source, fileName, methodName)
            }
        }
    }

    private fun assertMappingUsesSharedFilter(source: String, fileName: String, methodName: String) {
        val label = "$fileName::$methodName"
        val method = methodSection(source, methodName, label)
        val pageReturnIndex = method.indexOf("return LoadResult.Page")
        assertTrue("$label must return LoadResult.Page", pageReturnIndex >= 0)

        val beforePageReturn = method.substring(0, pageReturnIndex)
        val listAssignments = Regex("""\bval\s+list\s*=""").findAll(beforePageReturn).toList()
        assertEquals("$label must declare exactly one active val list mapping", 1, listAssignments.size)

        val listStartIndex = listAssignments.single().range.first
        val mappingMatch = Regex(
            """\bval\s+list\s*=\s*[^\r\n;{}]+?\.\s*map(?:NotNull)?\s*\{"""
        )
            .find(beforePageReturn, listStartIndex)
        assertTrue(
            "$label val list must directly begin a map or mapNotNull expression",
            mappingMatch?.range?.first == listStartIndex
        )

        val mappingOpenBrace = beforePageReturn.indexOf('{', mappingMatch!!.range.first)
        val mappingCloseBrace = matchingBraceIndex(beforePageReturn, mappingOpenBrace, label)
        val afterMapping = beforePageReturn.substring(mappingCloseBrace + 1)
        val filterCall = ".filterValidBroadcasters()"

        assertTrue(
            "$label val list mapping must directly chain $filterCall",
            afterMapping.trimStart().startsWith(filterCall)
        )
        assertEquals(
            "$label must apply $filterCall exactly once before return LoadResult.Page",
            1,
            beforePageReturn.countOccurrences(filterCall)
        )
    }

    private fun methodSection(source: String, methodName: String, label: String): String {
        val declaration = "private suspend fun $methodName("
        val startIndex = source.indexOf(declaration)
        assertTrue("$label declaration was not found", startIndex >= 0)
        val nextMethodIndex = source.indexOf("private suspend fun ", startIndex + declaration.length)
        val endIndex = if (nextMethodIndex >= 0) nextMethodIndex else source.length
        return source.substring(startIndex, endIndex)
    }

    private fun matchingBraceIndex(source: String, openingIndex: Int, label: String): Int {
        assertTrue("$label mapping lambda opening brace was not found", openingIndex >= 0)
        var depth = 0
        for (index in openingIndex until source.length) {
            when (source[index]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        return index
                    }
                }
            }
        }
        error("$label mapping lambda closing brace was not found")
    }

    private fun String.countOccurrences(literal: String): Int {
        var count = 0
        var startIndex = 0
        while (true) {
            val matchIndex = indexOf(literal, startIndex)
            if (matchIndex < 0) {
                return count
            }
            count++
            startIndex = matchIndex + literal.length
        }
    }

    private fun String.withoutCommentsAndStrings(): String {
        val masked = StringBuilder(length)
        var index = 0
        while (index < length) {
            when {
                startsWith("//", index) -> {
                    masked.append("  ")
                    index += 2
                    while (index < length && this[index] != '\n') {
                        masked.append(' ')
                        index++
                    }
                }
                startsWith("/*", index) -> {
                    var depth = 1
                    masked.append("  ")
                    index += 2
                    while (index < length && depth > 0) {
                        when {
                            startsWith("/*", index) -> {
                                depth++
                                masked.append("  ")
                                index += 2
                            }
                            startsWith("*/", index) -> {
                                depth--
                                masked.append("  ")
                                index += 2
                            }
                            else -> {
                                masked.append(this[index].maskedCharacter())
                                index++
                            }
                        }
                    }
                }
                startsWith("\"\"\"", index) -> {
                    masked.append("   ")
                    index += 3
                    while (index < length && !startsWith("\"\"\"", index)) {
                        masked.append(this[index].maskedCharacter())
                        index++
                    }
                    if (index < length) {
                        masked.append("   ")
                        index += 3
                    }
                }
                this[index] == '"' || this[index] == '\'' -> {
                    index = maskQuotedLiteral(masked, index, this[index])
                }
                else -> {
                    masked.append(this[index])
                    index++
                }
            }
        }
        return masked.toString()
    }

    private fun String.maskQuotedLiteral(
        masked: StringBuilder,
        openingIndex: Int,
        delimiter: Char,
    ): Int {
        var index = openingIndex
        var escaped = false
        do {
            val character = this[index]
            masked.append(character.maskedCharacter())
            index++
            if (escaped) {
                escaped = false
            } else if (character == '\\') {
                escaped = true
            } else if (index > openingIndex + 1 && character == delimiter) {
                break
            }
        } while (index < length)
        return index
    }

    private fun Char.maskedCharacter(): Char = if (this == '\n' || this == '\r') this else ' '

    private fun findDataSourceRoot(): Path {
        var current: Path? = Paths.get(System.getProperty("user.dir")).toAbsolutePath()
        while (current != null) {
            val directSourceRoot = current.resolve(
                "src/main/java/com/github/andreyasadchy/xtra/repository/datasource"
            )
            if (Files.isDirectory(directSourceRoot)) {
                return directSourceRoot
            }

            val appSourceRoot = current.resolve(
                "app/src/main/java/com/github/andreyasadchy/xtra/repository/datasource"
            )
            if (Files.isDirectory(appSourceRoot)) {
                return appSourceRoot
            }

            current = current.parent
        }
        error("Could not locate datasource source root from ${System.getProperty("user.dir")}")
    }
}
