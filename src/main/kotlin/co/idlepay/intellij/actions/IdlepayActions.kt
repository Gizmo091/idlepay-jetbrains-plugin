package co.idlepay.intellij.actions

import co.idlepay.intellij.IdlepayConstants
import co.idlepay.intellij.auth.IdlepaySignIn
import co.idlepay.intellij.core.IdlepayService
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

class SignInAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) = IdlepaySignIn.start(e.project)
}

class SignOutAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        IdlepayService.getInstance().signOut()
        IdlepaySignIn.notify(e.project, "Signed out of idlepay on this device. Earning is paused here.")
    }
}

class OpenDashboardAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) = BrowserUtil.browse(IdlepayConstants.DASHBOARD_URL)
}
