package co.idlepay.intellij.statusbar

import co.idlepay.intellij.IdlepayConstants
import co.idlepay.intellij.api.IdlepayApi
import co.idlepay.intellij.auth.IdlepaySignIn
import co.idlepay.intellij.core.IdlepayService
import co.idlepay.intellij.core.IdlepaySettings
import co.idlepay.intellij.model.DeveloperEarnings
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.CustomStatusBarWidget
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.Color
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.SwingUtilities

class IdlepayStatusBarWidgetFactory : StatusBarWidgetFactory {
    override fun getId(): String = IdlepayConstants.WIDGET_ID
    override fun getDisplayName(): String = "idlepay"
    override fun isAvailable(project: Project): Boolean = true
    override fun createWidget(project: Project): StatusBarWidget = IdlepayStatusBarWidget(project)
    override fun disposeWidget(widget: StatusBarWidget) = Disposer.dispose(widget)
    override fun canBeEnabledOn(statusBar: StatusBar): Boolean = true
}

private const val MAX_TEXT = 48

/** idlepay brand green (#12b981), used as the widget background. */
private val IDLEPAY_GREEN = Color(0x12, 0xB9, 0x81)

private class IdlepayStatusBarWidget(private val project: Project) : CustomStatusBarWidget {

    private val service get() = IdlepayService.getInstance()
    private val settings get() = IdlepaySettings.getInstance()
    private var statusBar: StatusBar? = null

    private val refresh = Runnable { updateLabel() }

    private val label = JBLabel().apply {
        isOpaque = true
        background = IDLEPAY_GREEN
        foreground = Color.WHITE
        border = JBUI.Borders.empty(0, 8)
    }

    init {
        label.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) { if (e.isPopupTrigger) showMenu(e) }
            override fun mouseReleased(e: MouseEvent) { if (e.isPopupTrigger) showMenu(e) }
            override fun mouseClicked(e: MouseEvent) {
                if (SwingUtilities.isLeftMouseButton(e) && !e.isPopupTrigger) onLeftClick()
            }
        })
    }

    override fun ID(): String = IdlepayConstants.WIDGET_ID
    override fun getComponent(): JComponent = label
    override fun getPresentation(): StatusBarWidget.WidgetPresentation? = null

    override fun install(statusBar: StatusBar) {
        this.statusBar = statusBar
        service.addRefreshListener(refresh)
        updateLabel()
    }

    override fun dispose() {
        service.removeRefreshListener(refresh)
        statusBar = null
    }

    // --- rendering ------------------------------------------------------------

    private fun updateLabel() {
        label.text = when {
            !settings.isSignedIn -> "✶ idlepay — sign in"
            else -> service.currentAd?.let { ad ->
                val t = ad.text.trim()
                "📣 " + if (t.length > MAX_TEXT) t.take(MAX_TEXT - 1).trimEnd() + "…" else t
            } ?: "✶ idlepay"
        }
        label.toolTipText = buildTooltip()
        label.background = IDLEPAY_GREEN
        label.revalidate()
        label.repaint()
    }

    private fun buildTooltip(): String {
        if (!settings.isSignedIn) {
            return "<html>idlepay<br>Left-click or right-click to sign in and start earning</html>"
        }
        val sb = StringBuilder("<html>idlepay")
        service.profile?.login?.let { sb.append(" — @").append(it) }
        service.earnings?.let { e ->
            sb.append("<br>Today ").append(DeveloperEarnings.microToUsd(e.todayMicroUsd))
            sb.append(" · Lifetime ").append(DeveloperEarnings.microToUsd(e.lifetimeMicroUsd))
            sb.append("<br>").append(e.impressionCount).append(" impressions")
        }
        sb.append("<br>Left-click: open ad · Right-click: menu</html>")
        return sb.toString()
    }

    // --- interaction ----------------------------------------------------------

    private fun onLeftClick() {
        if (!settings.isSignedIn) {
            IdlepaySignIn.start(project)
            return
        }
        val ad = service.currentAd
        val url = ad?.let { IdlepayApi.clickThroughUrl(it, settings.developerId()) }
        if (ad != null && url != null) {
            BrowserUtil.browse(url)
            IdlepayApi.postClick(ad.campaignId, settings.developerId())
        } else {
            BrowserUtil.browse(IdlepayConstants.DASHBOARD_URL)
        }
    }

    private fun showMenu(e: MouseEvent) {
        val group = DefaultActionGroup()
        if (settings.isSignedIn) {
            if (service.currentAd?.url != null) group.add(action("Open current ad") { onLeftClick() })
            group.add(action("Open earnings dashboard") { BrowserUtil.browse(IdlepayConstants.DASHBOARD_URL) })
            group.add(action("Refresh now") { service.refreshNow() })
            group.addSeparator()
            group.add(action("Sign out (pause earning here)") {
                service.signOut()
                IdlepaySignIn.notify(project, "Signed out of idlepay on this device. Earning is paused here.")
            })
        } else {
            group.add(action("Sign in to idlepay") { IdlepaySignIn.start(project) })
            group.add(action("Refresh now") { service.refreshNow() })
        }
        JBPopupFactory.getInstance()
            .createActionGroupPopup(
                "idlepay",
                group as ActionGroup,
                DataContext.EMPTY_CONTEXT,
                JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
                true,
            )
            .show(RelativePoint(e.component, e.point))
    }

    private fun action(text: String, run: () -> Unit) = object : DumbAwareAction(text) {
        override fun actionPerformed(e: AnActionEvent) = run()
    }
}
