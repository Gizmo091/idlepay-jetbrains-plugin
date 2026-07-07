package co.idlepay.intellij.core

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

/** Kicks the background service once, on the first project opened. */
class IdlepayStartup : ProjectActivity {
    override suspend fun execute(project: Project) {
        IdlepayService.getInstance().ensureStarted()
    }
}
