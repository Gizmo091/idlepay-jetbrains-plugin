// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Mathieu Vedie

package co.idlepay.intellij

/**
 * Constants ported verbatim from the upstream idlepay client (github.com/idlepay-co/client,
 * src/api.ts + src/extension.ts). Timings and keys are kept identical so this JetBrains client
 * behaves like the official VS Code extension against the same backend.
 */
object IdlepayConstants {
    const val DEFAULT_ORIGIN = "https://qrqt933izf.eu-west-1.awsapprunner.com"
    const val PORTAL_URL = "https://idlepay.co"
    const val DASHBOARD_URL = "https://idlepay.co/dashboard"

    // Timeouts (ms)
    const val REQUEST_TIMEOUT_MS = 5_000L
    const val ADS_REQUEST_TIMEOUT_MS = 12_000L

    // Loop intervals / windows (ms) — from extension.ts
    const val REFRESH_AD_MS = 30_000L
    const val REFRESH_ACCOUNT_MS = 60_000L
    const val HEARTBEAT_MS = 30_000L
    const val STATUSLINE_HEARTBEAT_FRESH_MS = 90_000L
    const val CLAUDE_ACTIVITY_WINDOW_MS = 5 * 60_000L

    // Surface tag sent on the credited impression beacon.
    const val SURFACE = "extension"

    // Default status-bar ad color when the sponsor supplies none.
    const val JADE_HEX = "#12b981"

    // ~/.idlepay files
    const val IDLEPAY_DIR = ".idlepay"
    const val IDENTITY_FILE = "identity.json"
    const val HEARTBEAT_FILE = "heartbeat"

    // ~/.claude
    const val CLAUDE_DIR = ".claude"
    const val CLAUDE_PROJECTS_DIR = "projects"

    // Persisted state keys
    const val DEVELOPER_ID_KEY = "idlepay.developerId"
    const val DEVICE_TOKEN_KEY = "idlepay.deviceToken"

    // Status-bar widget id
    const val WIDGET_ID = "co.idlepay.statusbar"
}
