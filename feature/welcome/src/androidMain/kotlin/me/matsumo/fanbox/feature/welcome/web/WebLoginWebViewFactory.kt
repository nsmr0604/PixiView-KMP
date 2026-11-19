package me.matsumo.fanbox.feature.welcome.web

import com.multiplatform.webview.web.NativeWebView
import com.multiplatform.webview.web.WebViewFactoryParam

internal actual fun webLoginWebViewFactory(): ((WebViewFactoryParam) -> NativeWebView)? = null
