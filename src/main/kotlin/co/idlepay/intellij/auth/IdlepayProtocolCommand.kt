// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Mathieu Vedie

package co.idlepay.intellij.auth

import com.intellij.openapi.application.JBProtocolCommand

/**
 * Best-effort auto-capture of the sign-in callback. If the OS routes
 * `jetbrains://.../linked?token=…` to this IDE, JetBrains matches the `linked` command here and we
 * finalize sign-in without a manual paste.
 *
 * Whether the idlepay server produces a JetBrains-routable URL (it templates the authority
 * `idlepay.idlepay` from the VS Code extension id) is unverified — hence [IdlepaySignIn]'s paste
 * fallback always remains available.
 */
class IdlepayProtocolCommand : JBProtocolCommand("linked") {
    override suspend fun execute(
        target: String?,
        parameters: Map<String, String>,
        fragment: String?,
    ): String? {
        val token = parameters["token"]
        if (token.isNullOrBlank()) return "idlepay: missing token in sign-in callback"
        IdlepaySignIn.complete(token.trim(), null)
        return null
    }
}
