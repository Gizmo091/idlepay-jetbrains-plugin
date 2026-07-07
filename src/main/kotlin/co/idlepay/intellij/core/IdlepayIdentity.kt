package co.idlepay.intellij.core

import co.idlepay.intellij.IdlepayConstants
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.openapi.diagnostic.logger
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Writes ~/.idlepay/identity.json — the single file that lets the installed Claude Code status line
 * script credit impressions from the JetBrains terminal. Shape ({ deviceId, token, apiUrl? }) is
 * exactly what the script's readIdentity() validates (deviceId and token must both be strings).
 */
object IdlepayIdentity {
    private val log = logger<IdlepayIdentity>()

    private fun idlepayDir(): Path =
        Path.of(System.getProperty("user.home"), IdlepayConstants.IDLEPAY_DIR)

    fun identityFile(): Path = idlepayDir().resolve(IdlepayConstants.IDENTITY_FILE)

    fun heartbeatFile(): Path = idlepayDir().resolve(IdlepayConstants.HEARTBEAT_FILE)

    /** Write/refresh identity.json for the given developer id and token. */
    fun write(developerId: String, token: String?, apiUrl: String? = null) {
        try {
            val dir = idlepayDir()
            Files.createDirectories(dir)
            val obj = JsonObject().apply {
                addProperty("deviceId", developerId)
                if (!token.isNullOrBlank()) addProperty("token", token)
                if (!apiUrl.isNullOrBlank()) addProperty("apiUrl", apiUrl)
            }
            val tmp = dir.resolve("${IdlepayConstants.IDENTITY_FILE}.tmp-${ProcessHandle.current().pid()}")
            Files.writeString(tmp, obj.toString())
            try {
                Files.move(tmp, identityFile(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (e: Exception) {
                Files.writeString(identityFile(), obj.toString())
                runCatching { Files.deleteIfExists(tmp) }
            }
        } catch (t: Throwable) {
            log.warn("Failed to write identity.json: ${t.message}")
        }
    }

    data class Identity(val deviceId: String, val token: String)

    /**
     * Read a valid identity.json ({ deviceId, token } both non-blank), if any. Lets the plugin adopt
     * a sign-in already performed by the official VS Code client (or a previous run), keeping both
     * earning surfaces on the same linked account.
     */
    fun read(): Identity? {
        return try {
            val txt = Files.readString(identityFile())
            val o = JsonParser.parseString(txt).asJsonObject
            val deviceId = o.get("deviceId")?.takeIf { !it.isJsonNull }?.asString
            val token = o.get("token")?.takeIf { !it.isJsonNull }?.asString
            if (!deviceId.isNullOrBlank() && !token.isNullOrBlank()) Identity(deviceId, token) else null
        } catch (t: Throwable) {
            null
        }
    }

    /** Remove identity.json (sign-out): the terminal status line stops crediting. */
    fun clear() {
        runCatching { Files.deleteIfExists(identityFile()) }
            .onFailure { log.debug("Failed to delete identity.json: ${it.message}") }
    }
}
