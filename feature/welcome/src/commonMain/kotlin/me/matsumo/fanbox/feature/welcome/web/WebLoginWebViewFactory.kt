package me.matsumo.fanbox.feature.welcome.web

import com.multiplatform.webview.web.NativeWebView
import com.multiplatform.webview.web.WebViewFactoryParam

/**
 * ログイン画面の WebView 生成に使う factory。
 *
 * Desktop の既定 factory が使うカスタム `CefRequestContext` は `WebViewState.cookieManager()`
 * が参照するグローバル context と Cookie を共有しない（upstream Issue #395）。
 * そのため Desktop だけ requestContext を渡さない factory を使い、UA は KCEF 起動時の
 * グローバル設定で装う。他のプラットフォームは既定 factory を使うため null を返す。
 */
internal expect fun webLoginWebViewFactory(): ((WebViewFactoryParam) -> NativeWebView)?
