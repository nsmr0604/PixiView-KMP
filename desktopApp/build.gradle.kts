import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// デスクトップ（Windows/macOS/Linux）向けの Compose Desktop アプリケーション。
// Android 版と同じ shared モジュールの UI とロジックを再利用する。
plugins {
    alias(libs.plugins.kmp)
    alias(libs.plugins.kmpCompose)
    alias(libs.plugins.kotlin.compose.compiler)
    alias(libs.plugins.detekt)
}

kotlin {
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    sourceSets {
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(project(":shared"))

            // Coil の KtorNetworkFetcherFactory が内部で HttpClient を作るため、
            // JVM では HTTP エンジンが必要。
            implementation(libs.ktor.okhttp)

            // Main.kt の WindowSizeClass（shared は implementation なので再宣言が必要）。
            implementation(libs.window.size)
            implementation(libs.kotlinx.collections.immutable)
            implementation(libs.koin.compose)

            // PixiViewApp のシグネチャに NativeView（core/ui）が含まれるため。
            implementation(project(":core:ui"))

            // WebView ログインの JCEF（KCEF）を起動時に明示的に初期化するため。
            // shared 側では runtime のみの依存となっている。
            implementation(libs.kcef)
        }
    }
}

compose.desktop {
    application {
        mainClass = "me.matsumo.fanbox.desktop.MainKt"

        nativeDistributions {
            // jpackage のランタイム画像に含める JDK モジュール。既定は 7 個のみで、
            // jdk.unsupported が含まれない。datastore が同梱する protobuf lite は
            // sun.misc.Unsafe (jdk.unsupported) を使うため、無いと起動直後に
            // 「Lite gencode is primarily intended for Android use...」でクラッシュする
            // (2026-11-18 実機実測)。java.sql は Room の JDBC ドライバーが使う。
            // jdeps は multi-release jar のモジュール解決に失敗して使えないため、
            // 実機検証で不足分を確認して足す運用にする。
            modules(
                "java.base",
                "java.datatransfer",
                "java.xml",
                "java.prefs",
                "java.desktop",
                "java.logging",
                "jdk.crypto.ec",
                "jdk.unsupported",
                "java.sql",
            )

            // 配布は実行ファイル形式。packageDistributionForCurrentOS が
            // 実行環境の OS に対応するフォーマットだけをビルドする。
            // Windows: msi / exe、macOS: dmg、Linux: deb / AppImage
            targetFormats(
                TargetFormat.Msi,
                TargetFormat.Exe,
                TargetFormat.Dmg,
                TargetFormat.Deb,
                TargetFormat.AppImage,
            )

            // 非公式ビルドであることを明示する（pixiv API 側の identity とは無関係）。
            packageName = "PixiView-unofficial"

            // MSI (WiX) のバージョン比較は先頭 3 成分のみで、同一バージョンの
            // 再インストールは 1638「別バージョンが既にインストール済み」で弾かれる。
            // ビルドごとに増える番号を第 3 成分に載せる (CI は run 番号、ローカルは 0)。
            // ローカルビルド同士の上書きは先にアンインストールする前提。
            val baseVersion = libs.versions.versionName.get()
            val buildSeq = 1000 + (System.getenv("GITHUB_RUN_NUMBER")?.toInt() ?: 0)
            packageVersion = baseVersion.split(".").take(2).joinToString(".") + "." + buildSeq
        }
    }
}
