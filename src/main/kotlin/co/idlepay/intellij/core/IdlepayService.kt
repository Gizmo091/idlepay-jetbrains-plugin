// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Mathieu Vedie

package co.idlepay.intellij.core

import co.idlepay.intellij.IdlepayConstants
import co.idlepay.intellij.api.IdlepayApi
import co.idlepay.intellij.model.Ad
import co.idlepay.intellij.model.DeveloperEarnings
import co.idlepay.intellij.model.DeveloperProfile
import com.intellij.openapi.application.ApplicationActivationListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.wm.IdeFrame
import com.intellij.openapi.wm.WindowManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Background engine, the JetBrains analogue of the VS Code extension's activate() loops:
 *  - ad rotation (every REFRESH_AD_MS) feeds the status-bar widget,
 *  - the credited heartbeat (every HEARTBEAT_MS) earns on the "extension" surface, gated on real
 *    Claude activity + window focus (or a fresh terminal heartbeat),
 *  - account refresh (every REFRESH_ACCOUNT_MS) pulls earnings + profile.
 *
 * This surface is additive to the Claude Code status line (which credits from the terminal via
 * identity.json). Both are legitimate, distinct impression surfaces, deduped/capped server-side.
 */
@Service(Service.Level.APP)
class IdlepayService(private val scope: CoroutineScope) {
    private val log = logger<IdlepayService>()

    @Volatile var currentAd: Ad? = null; private set
    @Volatile var earnings: DeveloperEarnings? = null; private set
    @Volatile var profile: DeveloperProfile? = null; private set
    @Volatile private var focused: Boolean = true

    private val started = AtomicBoolean(false)
    private val refreshListeners = CopyOnWriteArrayList<Runnable>()

    private val settings get() = IdlepaySettings.getInstance()

    /** Status-bar widgets register here so the loops can push repaints to their custom component. */
    fun addRefreshListener(r: Runnable) = refreshListeners.add(r)
    fun removeRefreshListener(r: Runnable) = refreshListeners.remove(r)

    /** Idempotent: called from startup activity. Configures the API origin, publishes identity, loops. */
    fun ensureStarted() {
        if (!started.compareAndSet(false, true)) return

        IdlepayApi.origin = settings.apiUrl ?: IdlepayConstants.DEFAULT_ORIGIN

        // Adopt an existing sign-in (from the official VS Code client or a prior run) so both earning
        // surfaces stay on the same linked account. Only when this plugin isn't already signed in.
        if (!settings.isSignedIn) {
            IdlepayIdentity.read()?.let { id ->
                settings.setDeveloperId(id.deviceId)
                settings.token = id.token
                log.info("Adopted existing idlepay identity from identity.json")
            }
        }

        // Publish identity so the Claude Code terminal status line can credit too (if signed in).
        IdlepayIdentity.write(settings.developerId(), settings.token, settings.apiUrl)

        subscribeFocus()
        launchAdLoop()
        launchHeartbeatLoop()
        launchAccountLoop()
        log.info("idlepay service started (signedIn=${settings.isSignedIn})")
    }

    private fun subscribeFocus() {
        val conn = ApplicationManager.getApplication().messageBus.connect()
        conn.subscribe(ApplicationActivationListener.TOPIC, object : ApplicationActivationListener {
            override fun applicationActivated(ideFrame: IdeFrame) { focused = true }
            override fun applicationDeactivated(ideFrame: IdeFrame) { focused = false }
        })
    }

    private fun launchAdLoop() = scope.launch(Dispatchers.IO) {
        while (isActive) {
            runCatching {
                IdlepayApi.fetchDisplayAd()?.let {
                    currentAd = it
                    refreshWidgets()
                }
            }
            delay(IdlepayConstants.REFRESH_AD_MS)
        }
    }

    private fun launchHeartbeatLoop() = scope.launch(Dispatchers.IO) {
        while (isActive) {
            runCatching { maybeCredit() }
            delay(IdlepayConstants.HEARTBEAT_MS)
        }
    }

    private fun launchAccountLoop() = scope.launch(Dispatchers.IO) {
        while (isActive) {
            runCatching {
                if (settings.isSignedIn) {
                    val id = settings.developerId()
                    IdlepayApi.fetchEarnings(id)?.let { earnings = it }
                    IdlepayApi.fetchProfile(id)?.let { profile = it }
                    refreshWidgets()
                }
            }
            delay(IdlepayConstants.REFRESH_ACCOUNT_MS)
        }
    }

    /**
     * Credited-impression gate, identical to extension.ts:
     * token present AND (focused OR fresh terminal heartbeat) AND recent Claude activity.
     */
    private fun maybeCredit() {
        val token = settings.token ?: return
        val now = System.currentTimeMillis()
        if (!focused && !ClaudeActivity.hasFreshStatuslineHeartbeat(now)) return
        if (!ClaudeActivity.hasRecentActivity(now)) return
        IdlepayApi.pingImpression(settings.developerId(), token)
    }

    // --- sign-in state transitions -------------------------------------------

    fun onSignedIn(token: String) {
        settings.token = token
        IdlepayIdentity.write(settings.developerId(), token, settings.apiUrl)
        scope.launch(Dispatchers.IO) {
            val id = settings.developerId()
            IdlepayApi.fetchEarnings(id)?.let { earnings = it }
            IdlepayApi.fetchProfile(id)?.let { profile = it }
            refreshWidgets()
        }
        refreshWidgets()
    }

    fun signOut() {
        settings.token = null
        earnings = null
        profile = null
        IdlepayIdentity.clear()
        // Re-publish identity without the token so the deviceId is preserved.
        IdlepayIdentity.write(settings.developerId(), null, settings.apiUrl)
        refreshWidgets()
    }

    fun refreshNow() {
        scope.launch(Dispatchers.IO) {
            runCatching { IdlepayApi.fetchDisplayAd()?.let { currentAd = it } }
            if (settings.isSignedIn) {
                val id = settings.developerId()
                IdlepayApi.fetchEarnings(id)?.let { earnings = it }
                IdlepayApi.fetchProfile(id)?.let { profile = it }
            }
            refreshWidgets()
        }
    }

    private fun refreshWidgets() {
        ApplicationManager.getApplication().invokeLater {
            for (project in ProjectManager.getInstance().openProjects) {
                if (project.isDisposed) continue
                WindowManager.getInstance().getStatusBar(project)?.updateWidget(IdlepayConstants.WIDGET_ID)
            }
            refreshListeners.forEach { runCatching { it.run() } }
        }
    }

    companion object {
        fun getInstance(): IdlepayService = service()
    }
}
