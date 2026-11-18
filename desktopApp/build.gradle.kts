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
            // 配布は createDistributable で作るポータブルディレクトリを zip 化して行う。
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)

            // 非公式ビルドであることを明示する（pixiv API 側の identity とは無関係）。
            packageName = "PixiView-unofficial"
            packageVersion = "1.0.0"
        }
    }
}
