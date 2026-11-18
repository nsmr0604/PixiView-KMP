package me.matsumo.fanbox.di

import me.matsumo.fanbox.BuildKonfig
import me.matsumo.fanbox.core.common.PixiViewConfig
import org.koin.core.module.Module
import org.koin.dsl.module

actual val appPlatformModule: Module = module {
    single {
        // デスクトップは非公式の debug 相当ビルドとして扱うため、
        // 開発モードと CAIOS ID のダミー化が常に有効になる。
        PixiViewConfig(
            versionCode = BuildKonfig.VERSION_CODE,
            versionName = BuildKonfig.VERSION_NAME,
            developerPassword = BuildKonfig.DEVELOPER_PASSWORD,
            pixivClientId = BuildKonfig.PIXIV_CLIENT_ID,
            pixivClientSecret = BuildKonfig.PIXIV_CLIENT_SECRET,
            bannerAdUnitId = BuildKonfig.ADMOB_ANDROID_BANNER_AD_UNIT_ID,
            nativeAdUnitId = BuildKonfig.ADMOB_ANDROID_NATIVE_AD_UNIT_ID,
            rewardAdUnitId = BuildKonfig.ADMOB_ANDROID_REWARD_AD_UNIT_ID,
            interstitialAdUnitId = BuildKonfig.ADMOB_ANDROID_INTERSTITIAL_AD_UNIT_ID,
            appOpenAdUnitId = BuildKonfig.ADMOB_ANDROID_APP_OPEN_AD_UNIT_ID,
            platform = "Desktop",
            platformVersion = "${System.getProperty("os.name")} ${System.getProperty("os.version")}",
            device = "${System.getProperty("os.arch")}(JVM)",
            deviceAbis = System.getProperty("os.arch"),
            openaiApiKey = BuildKonfig.OPENAI_API_KEY,
            purchaseApiKey = BuildKonfig.PURCHASE_ANDROID_API_KEY,
            isDebug = true,
        )
    }
}
