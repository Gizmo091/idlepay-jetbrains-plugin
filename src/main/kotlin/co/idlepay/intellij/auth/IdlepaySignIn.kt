package co.idlepay.intellij.auth

import co.idlepay.intellij.IdlepayConstants
import co.idlepay.intellij.core.IdlepayService
import co.idlepay.intellij.core.IdlepaySettings
import com.intellij.ide.BrowserUtil
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Sign-in flow. Opens idlepay.co/connect in the browser (Google OAuth), then obtains the device
 * token one of two ways:
 *
 *  1. Auto-capture — the server redirects to `jetbrains://.../linked?token=…`, caught by
 *     [IdlepayProtocolCommand]. Depends on the OS having the `jetbrains` scheme registered and the
 *     server echoing our scheme; best-effort.
 *  2. Manual paste — the reliable fallback: the user copies the token shown on the page into the
 *     input dialog opened here.
 *
 * We pass `scheme=jetbrains`, mirroring how the VS Code client passes its own uriScheme for the
 * server to echo into the callback.
 */
object IdlepaySignIn {
    private const val SCHEME = "jetbrains"

    private fun enc(s: String): String = URLEncoder.encode(s, StandardCharsets.UTF_8)

    fun connectUrl(): String {
        val id = IdlepaySettings.getInstance().developerId()
        return "${IdlepayConstants.PORTAL_URL}/connect?device_id=${enc(id)}&scheme=${enc(SCHEME)}"
    }

    /** Open the browser and prompt for the token. Safe to call from the EDT. */
    fun start(project: Project?) {
        BrowserUtil.browse(connectUrl())
        ApplicationManager.getApplication().invokeLater {
            val input = Messages.showInputDialog(
                project,
                "Complete sign-in with Google in your browser.\n\n" +
                    "If your IDE didn't open automatically, copy the whole \"jetbrains://…\" link from the\n" +
                    "idlepay page (or just the token) and paste it here:",
                "Sign in to idlepay",
                Messages.getInformationIcon(),
            )
            val token = input?.let { extractToken(it) }
            if (!token.isNullOrBlank()) complete(token, project)
        }
    }

    /**
     * Accept either a raw token or the full `jetbrains://…/linked?…&token=…` callback URL, so the
     * user can just paste the fallback link the connect page shows when OS deep-link routing fails.
     */
    fun extractToken(input: String): String? {
        val s = input.trim()
        if (s.isEmpty()) return null
        val idx = s.indexOf("token=")
        if (idx >= 0) {
            val raw = s.substring(idx + "token=".length).substringBefore('&').substringBefore('#').trim()
            return runCatching { URLDecoder.decode(raw, StandardCharsets.UTF_8) }
                .getOrDefault(raw)
                .takeIf { it.isNotBlank() }
        }
        return s
    }

    /** Finalize sign-in with a token from either path. */
    fun complete(token: String, project: Project?) {
        if (IdlepaySettings.getInstance().isSignedIn && IdlepaySettings.getInstance().token == token) return
        IdlepayService.getInstance().onSignedIn(token)
        notify(project, "You're signed in to idlepay. Impressions will now credit your account while you code.")
    }

    fun notify(project: Project?, message: String) {
        runCatching {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("idlepay")
                .createNotification(message, NotificationType.INFORMATION)
                .notify(project)
        }
    }
}
