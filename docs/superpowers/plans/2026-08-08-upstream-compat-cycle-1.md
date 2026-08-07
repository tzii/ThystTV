# Upstream Compatibility Cycle 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Import PR #10's pre-reviewed microports, add four narrow upstream compatibility fixes with regression coverage, and make the floating-chat video render inline in the GitHub README.

**Architecture:** Work from the isolated `codex/upstream-compat-cycle-1` branch based on ThystTV `699335172`. Keep each semantic unit in its own commit. Centralize the GraphQL endpoint, expose small internal policy/filter seams for testability, preserve ThystTV's current Hilt/player architecture, and use structural tests where compilation alone cannot prove that every call site migrated.

**Tech Stack:** Kotlin/JVM 21, Android Gradle Plugin, JUnit 4, Node.js assertions, GitHub Markdown attachments, PowerShell.

---

## Execution Environment

Run every Gradle command from:

```text
C:\Users\simon\.config\superpowers\worktrees\ThystTV\upstream-compat-cycle-1
```

Set the process-scoped toolchain before each Gradle invocation:

```powershell
$env:JAVA_HOME = 'C:\Users\simon\.config\superpowers\jdks\microsoft-jdk-21.0.12\jdk-21.0.12+8'
$env:ANDROID_HOME = 'C:\Users\simon\AppData\Local\Android\Sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:Path = "$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:Path"
```

Do not create or commit `local.properties`. Do not modify the original checkout at `C:\Users\simon\Documents\Android\ThystTV`.

### Task 1: Import PR #10's pre-reviewed patch

**Files:**
- Modify through cherry-pick: `CHANGELOG.md`
- Modify through cherry-pick: `app/src/main/java/com/github/andreyasadchy/xtra/ui/saved/downloads/DownloadsViewModel.kt`
- Modify through cherry-pick: `app/src/main/java/com/github/andreyasadchy/xtra/util/chat/ChatAdapterUtils.kt`

- [ ] **Step 1: Confirm the branch is clean and based on the design commit**

```powershell
git status --short --branch
git log -2 --oneline
```

Expected: clean working tree; `HEAD` is the planning commit containing both the approved design and this implementation plan.

- [ ] **Step 2: Cherry-pick the PR #10 patch**

```powershell
git cherry-pick 02749715694ddcf86a64df8b7fc6c6da8ee77683
```

Expected: one new commit with three changed files. Its SHA differs from `02749715` because its parent is the design commit.

- [ ] **Step 3: Verify the imported file-level scope**

```powershell
git diff-tree --no-commit-id --name-only -r HEAD
git show --stat --oneline HEAD
```

Expected file list, and no other path:

```text
CHANGELOG.md
app/src/main/java/com/github/andreyasadchy/xtra/ui/saved/downloads/DownloadsViewModel.kt
app/src/main/java/com/github/andreyasadchy/xtra/util/chat/ChatAdapterUtils.kt
```

- [ ] **Step 4: Run the existing unit-test boundary**

```powershell
.\gradlew.bat test --no-daemon --console=plain
```

Expected: `BUILD SUCCESSFUL`. These pre-reviewed microports retain their existing CI/manual-QA boundary and do not claim retroactive red-green coverage.

### Task 2: Canonicalize every Twitch GraphQL endpoint call site

**Files:**
- Create: `app/src/main/java/com/github/andreyasadchy/xtra/repository/TwitchEndpoints.kt`
- Create: `app/src/test/java/com/github/andreyasadchy/xtra/repository/TwitchEndpointsTest.kt`
- Modify: `app/src/main/java/com/github/andreyasadchy/xtra/repository/GraphQLRepository.kt`
- Modify: `app/src/main/java/com/github/andreyasadchy/xtra/repository/PlayerRepository.kt`
- Modify: `CHANGELOG.md`

- [ ] **Step 1: Write the failing endpoint and structural tests**

Create `TwitchEndpointsTest.kt` with:

```kotlin
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
        val sourceRoot = locateProductionSourceRoot()
        val kotlinFiles = Files.walk(sourceRoot).use { paths ->
            paths.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".kt") }
                .toList()
        }
        val sources = kotlinFiles.associateWith { Files.readString(it) }

        assertTrue(
            "Production source must not contain the trailing-slash GraphQL endpoint",
            sources.values.none { it.contains("https://gql.twitch.tv/gql/") }
        )

        val canonicalLocations = sources
            .filterValues { it.contains("https://gql.twitch.tv/gql") }
            .keys
            .toSet()
        val endpointDeclaration = sourceRoot.resolve(
            "com/github/andreyasadchy/xtra/repository/TwitchEndpoints.kt"
        )

        assertEquals(setOf(endpointDeclaration), canonicalLocations)
    }

    private fun locateProductionSourceRoot(): Path {
        var current = Paths.get(System.getProperty("user.dir")).toAbsolutePath()
        while (true) {
            val moduleSource = current.resolve("src/main/java")
            if (Files.isDirectory(moduleSource)) return moduleSource

            val repositorySource = current.resolve("app/src/main/java")
            if (Files.isDirectory(repositorySource)) return repositorySource

            current = current.parent
                ?: error("Could not locate app/src/main/java from ${System.getProperty("user.dir")}")
        }
    }
}
```

- [ ] **Step 2: Run the focused test and observe RED**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.github.andreyasadchy.xtra.repository.TwitchEndpointsTest" --no-daemon --console=plain
```

Expected: compilation fails because `TwitchEndpoints` does not exist. This is the missing production seam the test specifies.

- [ ] **Step 3: Add the endpoint declaration**

Create `TwitchEndpoints.kt` with:

```kotlin
package com.github.andreyasadchy.xtra.repository

internal object TwitchEndpoints {
    const val GRAPHQL = "https://gql.twitch.tv/gql"
}
```

- [ ] **Step 4: Migrate all ten production call sites**

In `GraphQLRepository.kt`, replace all eight occurrences of:

```kotlin
"https://gql.twitch.tv/gql/"
```

with:

```kotlin
TwitchEndpoints.GRAPHQL
```

This produces HttpEngine, Cronet, pre-Android-N Cronet, and OkHttp calls in both `sendQuery` and `sendPersistedQuery` of these forms:

```kotlin
httpEngine.get().newUrlRequestBuilder(TwitchEndpoints.GRAPHQL, cronetExecutor, HttpEngineUtils.byteArrayUrlCallback(continuation))
cronetEngine.get().newUrlRequestBuilder(TwitchEndpoints.GRAPHQL, request.callback, cronetExecutor)
cronetEngine.get().newUrlRequestBuilder(TwitchEndpoints.GRAPHQL, getByteArrayCronetCallback(continuation), cronetExecutor)
url(TwitchEndpoints.GRAPHQL)
```

In `PlayerRepository.kt`, replace both playback-token request calls with:

```kotlin
url(TwitchEndpoints.GRAPHQL)
```

- [ ] **Step 5: Document the compatibility fix**

Append this bullet under `CHANGELOG.md` → `[Unreleased]` → `Fixed`:

```markdown
- Twitch GraphQL and playback-token requests now use the canonical no-trailing-slash endpoint (manual port from upstream Xtra `345cff59`).
```

- [ ] **Step 6: Run the focused test and observe GREEN**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.github.andreyasadchy.xtra.repository.TwitchEndpointsTest" --no-daemon --console=plain
rg -n 'gql\.twitch\.tv/gql/?' app/src/main/java
```

Expected: focused test passes. `rg` returns exactly one line in `TwitchEndpoints.kt`, without a trailing slash.

- [ ] **Step 7: Commit the endpoint fix**

```powershell
git add CHANGELOG.md app/src/main/java/com/github/andreyasadchy/xtra/repository/TwitchEndpoints.kt app/src/main/java/com/github/andreyasadchy/xtra/repository/GraphQLRepository.kt app/src/main/java/com/github/andreyasadchy/xtra/repository/PlayerRepository.kt app/src/test/java/com/github/andreyasadchy/xtra/repository/TwitchEndpointsTest.kt
git commit -m "fix: canonicalize Twitch GraphQL endpoint"
```

### Task 3: Preserve USERNOTICE message identifiers

**Files:**
- Create: `app/src/test/java/com/github/andreyasadchy/xtra/util/chat/ChatUtilsUserNoticeTest.kt`
- Modify: `app/src/main/java/com/github/andreyasadchy/xtra/util/chat/ChatUtils.kt`
- Modify: `CHANGELOG.md`

- [ ] **Step 1: Write the failing parameterized parser test**

Create `ChatUtilsUserNoticeTest.kt` with:

```kotlin
package com.github.andreyasadchy.xtra.util.chat

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class ChatUtilsUserNoticeTest(
    private val noticeType: String,
) {

    @Test
    fun `no-message USERNOTICE preserves msg-id`() {
        val rawMessage = "@badge-info=;badges=;color=#9147FF;display-name=Viewer;" +
            "id=notice-1;login=viewer;msg-id=$noticeType;" +
            "system-msg=Viewer\\ssent\\sa\\snotice;tmi-sent-ts=1786147200000;" +
            "user-id=42 :tmi.twitch.tv USERNOTICE #channel"

        val parsed = ChatUtils.parseChatMessage(rawMessage, userNotice = true)

        assertEquals(noticeType, parsed.msgId)
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "msg-id={0}")
        fun noticeTypes(): List<Array<String>> = listOf(
            arrayOf("sub"),
            arrayOf("resub"),
            arrayOf("subgift"),
            arrayOf("raid"),
        )
    }
}
```

- [ ] **Step 2: Run the focused test and observe RED**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.github.andreyasadchy.xtra.util.chat.ChatUtilsUserNoticeTest" --no-daemon --console=plain
```

Expected: four assertion failures because the no-message USERNOTICE branch returns `msgId == null`.

- [ ] **Step 3: Preserve the identifier in the no-message branch**

In the first `ChatMessage` constructor inside `parseChatMessage`, add the field between `systemMsg` and `timestamp`:

```kotlin
msgId = prefixes["msg-id"],
```

The complete affected constructor becomes:

```kotlin
ChatMessage(
    userId = prefixes["user-id"],
    userLogin = userLogin,
    userName = prefixes["display-name"]?.replace("\\s", " "),
    systemMsg = systemMsg ?: messageInfo,
    msgId = prefixes["msg-id"],
    timestamp = prefixes["tmi-sent-ts"]?.toLong(),
    fullMsg = message
)
```

- [ ] **Step 4: Document and verify the fix**

Append this `[Unreleased]` bullet:

```markdown
- USERNOTICE chat events without message text now preserve their `msg-id` for subscription, gift, and raid handling (manual port from upstream Xtra `ac0afa3d`).
```

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.github.andreyasadchy.xtra.util.chat.ChatUtilsUserNoticeTest" --no-daemon --console=plain
```

Expected: four parameterized cases pass.

- [ ] **Step 5: Commit the USERNOTICE fix**

```powershell
git add CHANGELOG.md app/src/main/java/com/github/andreyasadchy/xtra/util/chat/ChatUtils.kt app/src/test/java/com/github/andreyasadchy/xtra/util/chat/ChatUtilsUserNoticeTest.kt
git commit -m "fix: preserve USERNOTICE message ids"
```

### Task 4: Filter stream results without broadcaster identity

**Files:**
- Create: `app/src/main/java/com/github/andreyasadchy/xtra/repository/datasource/StreamBroadcasterFilter.kt`
- Create: `app/src/test/java/com/github/andreyasadchy/xtra/repository/datasource/StreamBroadcasterFilterTest.kt`
- Modify: `app/src/main/java/com/github/andreyasadchy/xtra/repository/datasource/GameStreamsDataSource.kt`
- Modify: `app/src/main/java/com/github/andreyasadchy/xtra/repository/datasource/SearchStreamsDataSource.kt`
- Modify: `app/src/main/java/com/github/andreyasadchy/xtra/repository/datasource/StreamsDataSource.kt`
- Modify: `CHANGELOG.md`

- [ ] **Step 1: Write failing predicate and structural-application tests**

Create `StreamBroadcasterFilterTest.kt` with:

```kotlin
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
        assertFalse(Stream().hasBroadcasterIdentity())
        assertTrue(Stream(channelId = "42").hasBroadcasterIdentity())
        assertTrue(Stream(channelLogin = "channel").hasBroadcasterIdentity())
        assertTrue(Stream(channelId = "42", channelLogin = "channel").hasBroadcasterIdentity())
    }

    @Test
    fun `blank identifier values remain valid because filtering is null-only`() {
        assertTrue(Stream(channelId = "").hasBroadcasterIdentity())
        assertTrue(Stream(channelLogin = "").hasBroadcasterIdentity())
    }

    @Test
    fun `all eight stream mapping paths apply the shared filter`() {
        val sourceRoot = locateProductionSourceRoot()
        val expectedUsages = mapOf(
            "GameStreamsDataSource.kt" to 3,
            "SearchStreamsDataSource.kt" to 2,
            "StreamsDataSource.kt" to 3,
        )

        expectedUsages.forEach { (fileName, expectedCount) ->
            val source = Files.readString(sourceRoot.resolve(fileName))
            val actualCount = source.lineSequence()
                .count { it.contains(".filterValidBroadcasters()") }
            assertEquals("Unexpected filter count in $fileName", expectedCount, actualCount)
        }
    }

    private fun locateProductionSourceRoot(): Path {
        var current = Paths.get(System.getProperty("user.dir")).toAbsolutePath()
        while (true) {
            val moduleSource = current.resolve(
                "src/main/java/com/github/andreyasadchy/xtra/repository/datasource"
            )
            if (Files.isDirectory(moduleSource)) return moduleSource

            val repositorySource = current.resolve(
                "app/src/main/java/com/github/andreyasadchy/xtra/repository/datasource"
            )
            if (Files.isDirectory(repositorySource)) return repositorySource

            current = current.parent
                ?: error("Could not locate datasource production sources")
        }
    }
}
```

- [ ] **Step 2: Run the focused test and observe RED**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.github.andreyasadchy.xtra.repository.datasource.StreamBroadcasterFilterTest" --no-daemon --console=plain
```

Expected: compilation fails because `hasBroadcasterIdentity` and `filterValidBroadcasters` do not exist.

- [ ] **Step 3: Add the shared null-only filter seam**

Create `StreamBroadcasterFilter.kt` with:

```kotlin
package com.github.andreyasadchy.xtra.repository.datasource

import com.github.andreyasadchy.xtra.model.ui.Stream

internal fun Stream.hasBroadcasterIdentity(): Boolean =
    channelId != null || channelLogin != null

internal fun Iterable<Stream>.filterValidBroadcasters(): List<Stream> =
    filter { it.hasBroadcasterIdentity() }
```

- [ ] **Step 4: Apply the shared filter to all eight mapping expressions**

In `GameStreamsDataSource.kt`, append `.filterValidBroadcasters()` to the `val list` expressions in `gqlQueryLoad`, `gqlLoad`, and `helixLoad`. Each assignment ends in this form:

```kotlin
val list = items.mapNotNull { item ->
    item.node?.let {
        Stream(
            id = it.id,
            channelId = it.broadcaster?.id,
            channelLogin = it.broadcaster?.login,
            channelName = it.broadcaster?.displayName,
            channelImageURL = it.broadcaster?.profileImageURL,
            gameId = it.game?.id,
            gameSlug = it.game?.slug,
            gameName = it.game?.displayName,
            title = it.title,
            thumbnailURL = it.previewImageURL,
            createdAt = it.createdAt?.toString(),
            viewerCount = it.viewersCount,
            tags = it.freeformTags?.mapNotNull { tag -> tag.name },
        )
    }
}.filterValidBroadcasters()
```

For `gqlLoad` and `helixLoad`, preserve their existing `Stream(...)` constructors and change only the final list-expression terminator from `}` to:

```kotlin
}.filterValidBroadcasters()
```

In `SearchStreamsDataSource.kt`, append `.filterValidBroadcasters()` to the `val list` expressions in `gqlQueryLoad` and `helixLoad`:

```kotlin
}.filterValidBroadcasters()
```

In `StreamsDataSource.kt`, append `.filterValidBroadcasters()` to the `val list` expressions in `gqlQueryLoad`, `gqlLoad`, and `helixLoad`:

```kotlin
}.filterValidBroadcasters()
```

Do not change `map` to `mapNotNull` solely for this filter; the shared list filter owns broadcaster validation. Preserve all existing backend-specific mapping logic.

- [ ] **Step 5: Document and verify the filtering behavior**

Append this `[Unreleased]` bullet:

```markdown
- Stream results with both broadcaster ID and login missing are now filtered before display (manual port from upstream Xtra `cfa61fc8`).
```

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.github.andreyasadchy.xtra.repository.datasource.StreamBroadcasterFilterTest" --no-daemon --console=plain
rg -n '\.filterValidBroadcasters\(\)' app/src/main/java/com/github/andreyasadchy/xtra/repository/datasource
```

Expected: tests pass. `rg` finds three usages in `GameStreamsDataSource`, two in `SearchStreamsDataSource`, three in `StreamsDataSource`, and one extension declaration in `StreamBroadcasterFilter.kt`.

- [ ] **Step 6: Commit the stream filtering fix**

```powershell
git add CHANGELOG.md app/src/main/java/com/github/andreyasadchy/xtra/repository/datasource/StreamBroadcasterFilter.kt app/src/main/java/com/github/andreyasadchy/xtra/repository/datasource/GameStreamsDataSource.kt app/src/main/java/com/github/andreyasadchy/xtra/repository/datasource/SearchStreamsDataSource.kt app/src/main/java/com/github/andreyasadchy/xtra/repository/datasource/StreamsDataSource.kt app/src/test/java/com/github/andreyasadchy/xtra/repository/datasource/StreamBroadcasterFilterTest.kt
git commit -m "fix: filter streams without broadcaster identity"
```

### Task 5: Centralize current VOD retention policy

**Files:**
- Create: `app/src/main/java/com/github/andreyasadchy/xtra/ui/saved/bookmarks/VodRetentionPolicy.kt`
- Create: `app/src/test/java/com/github/andreyasadchy/xtra/ui/saved/bookmarks/VodRetentionPolicyTest.kt`
- Modify: `app/src/main/java/com/github/andreyasadchy/xtra/ui/saved/bookmarks/BookmarksAdapter.kt`
- Modify: `app/src/main/java/com/github/andreyasadchy/xtra/ui/saved/bookmarks/BookmarksFragment.kt`
- Modify: `CHANGELOG.md`

- [ ] **Step 1: Write the failing precedence and policy tests**

Create `VodRetentionPolicyTest.kt` with:

```kotlin
package com.github.andreyasadchy.xtra.ui.saved.bookmarks

import org.junit.Assert.assertEquals
import org.junit.Test

class VodRetentionPolicyTest {

    @Test
    fun `null or blank effective type uses seven days`() {
        assertEquals(7, vodRetentionDays(userType = null, userBroadcasterType = null))
        assertEquals(7, vodRetentionDays(userType = "", userBroadcasterType = null))
        assertEquals(7, vodRetentionDays(userType = "   ", userBroadcasterType = null))
    }

    @Test
    fun `affiliate uses fourteen days case insensitively`() {
        assertEquals(14, vodRetentionDays(userType = "affiliate", userBroadcasterType = null))
        assertEquals(14, vodRetentionDays(userType = "AfFiLiAtE", userBroadcasterType = null))
    }

    @Test
    fun `other nonblank types use sixty days`() {
        assertEquals(60, vodRetentionDays(userType = "partner", userBroadcasterType = null))
        assertEquals(60, vodRetentionDays(userType = "prime", userBroadcasterType = null))
        assertEquals(60, vodRetentionDays(userType = "turbo", userBroadcasterType = null))
    }

    @Test
    fun `fallback to broadcaster type occurs only when primary type is null`() {
        assertEquals(14, vodRetentionDays(userType = null, userBroadcasterType = "affiliate"))
        assertEquals(7, vodRetentionDays(userType = "", userBroadcasterType = "affiliate"))
    }
}
```

- [ ] **Step 2: Run the focused test and observe RED**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.github.andreyasadchy.xtra.ui.saved.bookmarks.VodRetentionPolicyTest" --no-daemon --console=plain
```

Expected: compilation fails because `vodRetentionDays` does not exist.

- [ ] **Step 3: Implement the null-only precedence policy**

Create `VodRetentionPolicy.kt` with:

```kotlin
package com.github.andreyasadchy.xtra.ui.saved.bookmarks

internal fun vodRetentionDays(
    userType: String?,
    userBroadcasterType: String?,
): Int {
    val effectiveType = userType ?: userBroadcasterType
    return when {
        effectiveType.isNullOrBlank() -> 7
        effectiveType.equals("affiliate", ignoreCase = true) -> 14
        else -> 60
    }
}
```

- [ ] **Step 4: Use the policy in bookmark display**

In `BookmarksAdapter.kt`, remove:

```kotlin
val userType = item.userType ?: item.userBroadcasterType
```

Replace the archive-expiry block with:

```kotlin
if (item.type?.lowercase() == "archive" && item.createdAt != null && context.prefs().getBoolean(C.UI_BOOKMARK_TIME_LEFT, true) && !ignore) {
    val time = TwitchApiHelper.getVodTimeLeft(
        context,
        item.createdAt,
        vodRetentionDays(item.userType, item.userBroadcasterType)
    )
    if (!time.isNullOrBlank()) {
        views.visibility = View.VISIBLE
        views.text = context.getString(R.string.vod_time_left, time)
    } else {
        views.visibility = View.GONE
    }
} else {
    views.visibility = View.GONE
}
```

- [ ] **Step 5: Use the policy in both bookmark sort directions**

In each `SORT_EXPIRES_AT` comparator in `BookmarksFragment.kt`, replace the type guard and inline `when` calculation with:

```kotlin
if (it.type?.lowercase() == "archive" && it.createdAt != null) {
    val time = TwitchApiHelper.parseIso8601DateUTC(it.createdAt)
    val days = vodRetentionDays(it.userType, it.userBroadcasterType)
    if (time != null) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val date = Instant.ofEpochMilli(time).plus(days.toLong(), ChronoUnit.DAYS)
            val diff = Duration.between(Instant.now(), date)
            if (!diff.isNegative) {
                diff.seconds
            } else null
        } else {
            val currentTime = Calendar.getInstance().time.time
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = time
            calendar.add(Calendar.DAY_OF_MONTH, days)
            val diff = ((calendar.time.time - currentTime) / 1000)
            if (diff >= 0) {
                diff
            } else null
        }
    } else null
} else null
```

Apply this complete expression once inside the ascending comparator and once inside the descending comparator.

- [ ] **Step 6: Document and verify the retention behavior**

Append this `[Unreleased]` bullet:

```markdown
- Bookmark VOD expiry now uses Twitch's current 7-day regular, 14-day Affiliate, and 60-day Partner/Prime/Turbo retention policy (manual ports from upstream Xtra `627d440f` and `15dd7d9e`).
```

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.github.andreyasadchy.xtra.ui.saved.bookmarks.VodRetentionPolicyTest" --no-daemon --console=plain
rg -n 'when \(userType\.lowercase\(\)\)|"" -> 14' app/src/main/java/com/github/andreyasadchy/xtra/ui/saved/bookmarks
```

Expected: focused tests pass. `rg` returns no match for the duplicated obsolete policy.

- [ ] **Step 7: Commit the VOD policy fix**

```powershell
git add CHANGELOG.md app/src/main/java/com/github/andreyasadchy/xtra/ui/saved/bookmarks/VodRetentionPolicy.kt app/src/main/java/com/github/andreyasadchy/xtra/ui/saved/bookmarks/BookmarksAdapter.kt app/src/main/java/com/github/andreyasadchy/xtra/ui/saved/bookmarks/BookmarksFragment.kt app/src/test/java/com/github/andreyasadchy/xtra/ui/saved/bookmarks/VodRetentionPolicyTest.kt
git commit -m "fix: update bookmark VOD retention policy"
```

### Task 6: Render the floating-chat video inline and harden documentation tests

**Files:**
- Modify: `docs/site.test.js`
- Modify: `README.md`

The exact GitHub attachment generated from the unchanged 2,450,831-byte repository MP4 is:

```text
https://github.com/user-attachments/assets/dadbe18b-ffa5-4e88-af34-467bb72e545c
```

The upload was completed through GitHub's README editor, and the editor was closed without saving changes to `master`.

- [ ] **Step 1: Make the existing documentation test independent of the working directory**

At the top of `docs/site.test.js`, replace the current imports and file reads with:

```javascript
const fs = require("fs");
const path = require("path");
const assert = require("assert");

const docsDir = __dirname;
const repoRoot = path.resolve(__dirname, "..");
const html = fs.readFileSync(path.join(docsDir, "index.html"), "utf8");
const css = fs.readFileSync(path.join(docsDir, "styles.css"), "utf8");
const js = fs.readFileSync(path.join(docsDir, "script.js"), "utf8");
const readme = fs.readFileSync(path.join(repoRoot, "README.md"), "utf8");
```

- [ ] **Step 2: Prove the path fix before adding the new failing README assertions**

```powershell
node --test docs/site.test.js
```

Expected: `Static site smoke checks passed.` and one passing Node test file when invoked from the repository root.

- [ ] **Step 3: Add the failing README presentation assertions**

Insert these assertions before the final `console.log`:

```javascript
assert.match(
  readme,
  /^https:\/\/github\.com\/user-attachments\/assets\/[0-9a-f-]+$/m,
  "README should include a standalone GitHub video attachment URL"
);
assert.match(
  readme,
  /<img src="docs\/images\/readme\/floating-chat\.png"[^>]*>/,
  "README should retain the static floating-chat preview"
);
assert.match(
  readme,
  /<a href="docs\/images\/readme\/floating-chat\.mp4">Download the floating chat demo video<\/a>/,
  "README should retain a downloadable MP4 fallback"
);
assert.doesNotMatch(
  readme,
  /<a href="docs\/images\/readme\/floating-chat\.mp4">Watch the floating chat demo video<\/a>/,
  "README should not present the MP4 only as the old watch link"
);
```

- [ ] **Step 4: Run the documentation test and observe RED**

```powershell
node --test docs/site.test.js
```

Expected: assertion failure stating that the README should include a standalone GitHub video attachment URL.

- [ ] **Step 5: Replace the old link-only README block**

Replace:

```html
<p align="center">
  <a href="docs/images/readme/floating-chat.mp4">Watch the floating chat demo video</a>
</p>
```

with this exact Markdown and fallback block:

```markdown
https://github.com/user-attachments/assets/dadbe18b-ffa5-4e88-af34-467bb72e545c

<p align="center">
  <a href="docs/images/readme/floating-chat.mp4">Download the floating chat demo video</a>
</p>
```

Keep the existing `floating-chat.png` block immediately above it unchanged.

- [ ] **Step 6: Run the documentation test and observe GREEN**

```powershell
node --test docs/site.test.js
```

Expected: `Static site smoke checks passed.` and one passing Node test file.

- [ ] **Step 7: Commit the README video and test hardening**

```powershell
git add README.md docs/site.test.js
git commit -m "docs: embed floating chat demo video"
```

### Task 7: Run the complete local verification gate

**Files:**
- Verify only; no new file is expected.

- [ ] **Step 1: Run all unit tests**

```powershell
.\gradlew.bat test --no-daemon --console=plain
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Run Android lint and assemble the debug APK**

```powershell
.\gradlew.bat lintDebug assembleDebug --no-daemon --console=plain
```

Expected: `BUILD SUCCESSFUL`; debug APK exists under `app/build/outputs/apk/debug/`.

- [ ] **Step 3: Run the root-level documentation gate**

```powershell
node --test docs/site.test.js
```

Expected: one passing Node test file.

- [ ] **Step 4: Audit scope and commit boundaries**

```powershell
git status --short --branch
git log --oneline --decorate origin/master..HEAD
git diff --stat origin/master...HEAD
git diff --check origin/master...HEAD
rg -n 'gql\.twitch\.tv/gql/' app/src/main/java
```

Expected:

- clean working tree;
- one design commit plus six implementation commits;
- no trailing-slash GraphQL endpoint in production source;
- no dependency, SDK, version, DI, player, gesture, floating-chat behavior, or quality-selection changes outside the approved files.

- [ ] **Step 5: Record the manual post-push acceptance item**

Do not push solely to satisfy this local plan. After the user authorizes a push, open the branch README on github.com and verify that the standalone `user-attachments` URL renders as an inline playable video. If GitHub renders only a link, stop before merge and revise only the README presentation commit.
