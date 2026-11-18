package me.matsumo.fanbox.desktop

import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.datlag.kcef.KCEF
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.runBlocking
import me.matsumo.fanbox.initCoil
import me.matsumo.fanbox.initKoin
import me.matsumo.fanbox.initNapier
import me.matsumo.fanbox.PixiViewApp
import org.koin.compose.KoinContext
import java.io.File

// デスクトップ（Windows）向けのエントリーポイント。
// Android 版と同じ shared の PixiViewApp を起動する。
// 広告は無効化済み（isAdsSdkInitialized = true でも広告実装は no-op スタブ）。
fun main() {
    initNapier()
    initKoin()
    initCoil()

    // JCEF（KCEF）を WebView 利用前に初期化する。初回起動時は CEF バンドルを
    // GitHub からダウンロードするため時間がかかる。ライブラリ内部の既定の初期化は
    // エラーを握りつぶすため、ここで明示的に進捗とエラーを出力する。
    runBlocking {
        println("[KCEF] init start")
        KCEF.initBlocking(
            builder = {
                installDir(
                    File(System.getProperty("user.home")).resolve(".pixiview-desktop").resolve("kcef"),
                )
                progress {
                    onDownloading { progress -> println("[KCEF] downloading: $progress") }
                    onInitializing { println("[KCEF] initializing") }
                }
            },
            onError = { error -> println("[KCEF] error: ${error?.message}") },
            onRestartRequired = { println("[KCEF] restart required") },
        )
        println("[KCEF] init done")
    }

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "PixiView-unofficial",
            state = rememberWindowState(width = 1280.dp, height = 800.dp),
        ) {
            KoinContext {
                PixiViewApp(
                    windowSize = WindowWidthSizeClass.Expanded,
                    nativeViews = persistentMapOf(),
                    modifier = androidx.compose.ui.Modifier,
                    isAdsSdkInitialized = true,
                )
            }
        }
    }
}
