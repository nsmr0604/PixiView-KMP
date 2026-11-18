package me.matsumo.fanbox.core.ui.extensition

import io.github.aakira.napier.Napier
import me.matsumo.fanbox.core.logs.category.NavigationLog
import me.matsumo.fanbox.core.logs.logger.send
import java.awt.Desktop
import java.net.URI

class NavigatorExtensionImpl : NavigatorExtension {

    override fun navigateToWebPage(url: String, referrer: String) {
        NavigationLog.openUrl(
            url = url,
            referer = referrer,
            isSuccess = true,
        ).send()

        runCatching {
            Desktop.getDesktop().browse(URI(url))
        }.onFailure {
            Napier.w(it) { "Failed to open URL: $url" }
        }
    }

    override fun killApp() {
        // デスクトップではシステム終了の代替としてプロセスを正常終了させる。
        kotlin.system.exitProcess(0)
    }
}
