package com.github.andreyasadchy.xtra.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class TwitchEndpointsTest {

    @Test
    fun `GraphQL endpoint is canonical and has no trailing slash`() {
        assertEquals("https://gql.twitch.tv/gql", TwitchEndpoints.GRAPHQL)
        assertFalse(TwitchEndpoints.GRAPHQL.endsWith("/"))
    }

    @Test
    fun `production source declares the canonical endpoint once and no trailing copy`() {
        val sourceRoot = findProductionSourceRoot()
        val kotlinFiles = Files.walk(sourceRoot).use { paths ->
            paths.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".kt") }.toList()
        }
        val sourceByFile = kotlinFiles.associateWith { String(Files.readAllBytes(it), Charsets.UTF_8) }
        val canonicalEndpoint = "https://gql.twitch.tv/gql"

        assertTrue(sourceByFile.values.none { it.contains("$canonicalEndpoint/") })
        assertEquals(
            1,
            sourceByFile.values.sumOf { it.countNonOverlappingOccurrences(canonicalEndpoint) }
        )
        assertEquals(
            setOf(sourceRoot.resolve("com/github/andreyasadchy/xtra/repository/TwitchEndpoints.kt")),
            sourceByFile.filterValues { it.contains(canonicalEndpoint) }.keys
        )
    }

    private fun String.countNonOverlappingOccurrences(literal: String): Int {
        require(literal.isNotEmpty()) { "Literal must not be empty" }
        var count = 0
        var startIndex = 0
        while (true) {
            val matchIndex = indexOf(literal, startIndex)
            if (matchIndex == -1) {
                return count
            }
            count++
            startIndex = matchIndex + literal.length
        }
    }

    private fun findProductionSourceRoot(): Path {
        var current: Path? = Paths.get(System.getProperty("user.dir")).toAbsolutePath()
        while (current != null) {
            val directSourceRoot = current.resolve("src/main/java")
            if (Files.isDirectory(directSourceRoot)) {
                return directSourceRoot
            }

            val appSourceRoot = current.resolve("app/src/main/java")
            if (Files.isDirectory(appSourceRoot)) {
                return appSourceRoot
            }

            current = current.parent
        }
        error("Could not locate production Kotlin source root from ${System.getProperty("user.dir")}")
    }
}
