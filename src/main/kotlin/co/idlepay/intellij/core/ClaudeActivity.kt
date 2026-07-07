// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Mathieu Vedie

package co.idlepay.intellij.core

import co.idlepay.intellij.IdlepayConstants
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.name

/**
 * Activity gating ported from extension.ts. A credited impression is only allowed when there is
 * genuinely recent Claude activity, so the `x-idlepay-active: 1` assertion is honest.
 */
object ClaudeActivity {

    @Volatile
    private var lastActiveTranscript: Path? = null

    private fun claudeProjectsDir(): Path =
        Path.of(
            System.getProperty("user.home"),
            IdlepayConstants.CLAUDE_DIR,
            IdlepayConstants.CLAUDE_PROJECTS_DIR,
        )

    private fun mtimeMs(path: Path): Long? =
        runCatching { Files.getLastModifiedTime(path).toMillis() }.getOrNull()

    /**
     * True if any Claude transcript (~/.claude/projects/<project>/<session>.jsonl) was modified
     * within CLAUDE_ACTIVITY_WINDOW_MS. Caches the last matching transcript for a cheap fast path.
     */
    fun hasRecentActivity(nowMs: Long): Boolean {
        val cutoff = nowMs - IdlepayConstants.CLAUDE_ACTIVITY_WINDOW_MS

        lastActiveTranscript?.let { cached ->
            val m = mtimeMs(cached)
            if (m != null && m >= cutoff) return true
            lastActiveTranscript = null
        }

        val root = claudeProjectsDir()
        if (!root.isDirectory()) return false

        return try {
            Files.newDirectoryStream(root).use { projects ->
                for (project in projects) {
                    if (!project.isDirectory()) continue
                    Files.newDirectoryStream(project).use { files ->
                        for (file in files) {
                            if (!file.isRegularFile() || !file.name.endsWith(".jsonl")) continue
                            val m = mtimeMs(file) ?: continue
                            if (m >= cutoff) {
                                lastActiveTranscript = file
                                return true
                            }
                        }
                    }
                }
            }
            false
        } catch (t: Throwable) {
            false
        }
    }

    /** True if the terminal status line touched ~/.idlepay/heartbeat within the fresh window. */
    fun hasFreshStatuslineHeartbeat(nowMs: Long): Boolean {
        val m = mtimeMs(IdlepayIdentity.heartbeatFile()) ?: return false
        return nowMs - m < IdlepayConstants.STATUSLINE_HEARTBEAT_FRESH_MS
    }
}
