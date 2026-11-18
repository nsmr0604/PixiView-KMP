// デスクトップ（JVM）ビルド専用の no-op スタブ集。
// purchases-kmp や moko の compose 系（Android/iOS 専用で JVM target を提供しない
// ライブラリ）の代替をここに置き、ルート build.gradle.kts の dependencySubstitution で
// jvmCompileClasspath / jvmRuntimeClasspath に差し込む。
// moko の非 compose 系（permissions / biometry / desc）のスタブは :core:common に置いてある。
// @Composable のスタブは Compose compiler でコンパイルする必要があるため、このモジュールに
// kmpCompose を適用している。
plugins {
    alias(libs.plugins.kmp)
    alias(libs.plugins.kmpCompose)
    alias(libs.plugins.kotlin.compose.compiler)
}

kotlin {
    jvm()

    sourceSets {
        jvmMain.dependencies {
            implementation(compose.runtime)

            // compose 系スタブから moko の基底クラス（PermissionsController 等）を参照する。
            implementation(project(":core:common"))
        }
    }
}
