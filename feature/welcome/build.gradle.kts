@file:Suppress("UnusedPrivateProperty")

plugins {
    id("pixiview.primitive.kmp.common")
    id("pixiview.primitive.kmp.jvm")
    id("pixiview.primitive.android.library")
    id("pixiview.primitive.kmp.compose")
    id("pixiview.primitive.kmp.android")
    id("pixiview.primitive.kmp.ios")
    id("pixiview.primitive.detekt")
}

kotlin {
    android {
        namespace = "me.matsumo.fanbox.feature.welcome"

        withHostTest {}
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:common"))
            implementation(project(":core:model"))
            implementation(project(":core:repository"))
            implementation(project(":core:datastore"))
            implementation(project(":core:ui"))
            implementation(project(":core:logs"))
            implementation(project(":core:resources"))

            implementation(libs.moko.permissions)
            implementation(libs.moko.permissions.compose)
            implementation(libs.webview.compose)
        }

        jvmMain.dependencies {
            // 自前 factory で KCEFBrowser を生成するため、Desktop 側で明示的に参照する
            implementation(libs.kcef)
        }

        val androidHostTest by getting {
            dependencies {
                implementation(kotlin("test-junit"))
            }
        }
    }
}
