import dev.detekt.gradle.Detekt

plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.kmp.library) apply false
    alias(libs.plugins.kmp) apply false
    alias(libs.plugins.kmpCompose) apply false
    alias(libs.plugins.kmpComplete) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose.compiler) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
    alias(libs.plugins.libraries) apply false
    alias(libs.plugins.build.konfig) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.gms) apply false
    id("pixiview.primitive.detekt")
}

tasks.named<Detekt>("detekt") {
    setSource(
        files("build.gradle.kts", "settings.gradle.kts", "build-logic").asFileTree.matching {
            exclude("**/build/**", "**/resources/**")
        },
    )
}

// JVM（デスクトップ）ビルドでは、Android/iOS 専用の moko ライブラリを
// :core:common の jvmMain に置いた no-op スタブに差し替える。
// moko は JVM target を持たないため、そのままでは jvmCompileClasspath の
// 依存解決が失敗する。Android / iOS の設定（debugCompileClasspath 等）には影響しない。
subprojects {
    configurations.configureEach {
        if (name.startsWith("jvm") && name.endsWith("Classpath")) {
            // coil-gif は Android の ImageDecoder / GifDrawable 依存で JVM target が無い。
            // GIF 解読はデスクトップでは未対応（無効）とする。
            exclude(module = "coil-gif")

            resolutionStrategy.dependencySubstitution {
                substitute(module("dev.icerock.moko:permissions"))
                    .using(project(":core:common"))
                    .because("moko-permissions has no JVM target; JVM stubs live in :core:common")
                substitute(module("dev.icerock.moko:permissions-compose"))
                    .using(project(":core:jvm-stubs"))
                    .because("moko-permissions-compose has no JVM target; compose stubs live in :core:jvm-stubs")
                substitute(module("dev.icerock.moko:biometry"))
                    .using(project(":core:common"))
                    .because("moko-biometry has no JVM target; JVM stubs live in :core:common")
                substitute(module("dev.icerock.moko:biometry-compose"))
                    .using(project(":core:jvm-stubs"))
                    .because("moko-biometry-compose has no JVM target; compose stubs live in :core:jvm-stubs")
                substitute(module("com.revenuecat.purchases:purchases-kmp-core"))
                    .using(project(":core:jvm-stubs"))
                    .because("purchases-kmp has no JVM target; JVM stubs live in :core:jvm-stubs")
                substitute(module("com.revenuecat.purchases:purchases-kmp-result"))
                    .using(project(":core:jvm-stubs"))
                    .because("purchases-kmp has no JVM target; JVM stubs live in :core:jvm-stubs")
            }
        }
    }
}
