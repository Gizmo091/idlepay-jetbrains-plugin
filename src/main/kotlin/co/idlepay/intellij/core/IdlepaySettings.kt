// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Mathieu Vedie

package co.idlepay.intellij.core

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import java.util.UUID

/**
 * Application-level persisted identity: the stable developer id (a generated UUID, the analogue of
 * upstream's globalState `idlepay.developerId`) and the device token obtained at sign-in
 * (`idlepay.deviceToken`).
 *
 * The token is also mirrored to ~/.idlepay/identity.json by [IdlepayIdentity] because the Claude
 * Code status line reads it from there — so it lives in plaintext on disk by design, exactly as the
 * upstream client requires.
 */
@Service(Service.Level.APP)
@State(name = "IdlepaySettings", storages = [Storage("idlepay.xml")])
class IdlepaySettings : PersistentStateComponent<IdlepaySettings.State> {

    class State {
        var developerId: String = ""
        var token: String? = null

        /** Optional API origin override; null → default backend. */
        var apiUrl: String? = null
    }

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    /** Stable developer id, generated and persisted on first access. */
    fun developerId(): String {
        if (state.developerId.isBlank()) {
            state.developerId = UUID.randomUUID().toString()
        }
        return state.developerId
    }

    /** Override the developer id (e.g. to adopt an id already linked via identity.json). */
    fun setDeveloperId(id: String) {
        if (id.isNotBlank()) state.developerId = id
    }

    var token: String?
        get() = state.token?.takeIf { it.isNotBlank() }
        set(value) {
            state.token = value?.takeIf { it.isNotBlank() }
        }

    var apiUrl: String?
        get() = state.apiUrl?.takeIf { it.isNotBlank() }
        set(value) {
            state.apiUrl = value?.takeIf { it.isNotBlank() }
        }

    val isSignedIn: Boolean
        get() = token != null

    companion object {
        fun getInstance(): IdlepaySettings = service()
    }
}
