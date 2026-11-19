package me.matsumo.fanbox.feature.welcome.web

import com.multiplatform.webview.web.NativeWebView
import com.multiplatform.webview.web.WebContent
import com.multiplatform.webview.web.WebViewFactoryParam
import dev.datlag.kcef.KCEFBrowser

internal actual fun webLoginWebViewFactory(): ((WebViewFactoryParam) -> NativeWebView)? = { param ->
    val url = (param.state.content as? WebContent.Url)?.url ?: KCEFBrowser.BLANK_URI

    // requestContext を渡さない overload を選ぶ。これで Cookie がグローバル context に保存され、
    // WebViewState.cookieManager() から FANBOXSESSID を読める。
    param.client.createBrowser(url, param.rendering, param.transparent)
}
