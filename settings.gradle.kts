@file:Suppress("UnstableApiUsage")

include(":core:resources")

rootProject.name = "PixiView-KMP"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven("https://www.jitpack.io")
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        maven("https://maven.pkg.jetbrains.space/public/p/ktor/eap")
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        // fankt fork（JVM 対応）の開発用。CI では GitHub Packages を参照する。
        mavenLocal()
        google()
        mavenCentral()
        // KCEF（JCEF）の依存 jogl / gluegen は 2.5.0 が Maven Central に存在しない。
        // jitpack より前に置く（jitpack は未収録アーティファクトで 401 を返し解決が中断される）。
        maven("https://jogamp.org/deployment/maven")
        maven("https://www.jitpack.io")
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        maven("https://maven.pkg.jetbrains.space/public/p/ktor/eap")
    }
}

include(":shared")
include(":androidApp")
include(":desktopApp")

include(":core:ui")
include(":core:billing")
include(":core:jvm-stubs")
include(":core:repository")
include(":core:datastore")
include(":core:model")
include(":core:logs")
include(":core:common")

include(":feature:welcome")
include(":feature:library")
include(":feature:setting")
include(":feature:about")
include(":feature:post")
include(":feature:creator")
include(":feature:service")
