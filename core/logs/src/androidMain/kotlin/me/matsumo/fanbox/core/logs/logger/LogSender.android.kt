package me.matsumo.fanbox.core.logs.logger

import android.annotation.SuppressLint
import android.content.Context
import kotlinx.serialization.json.JsonObject
import me.matsumo.fanbox.core.model.Setting

// 非公式ビルドでは診断ログを Firebase（Analytics / Crashlytics）へ送信しない。
// 元の実装はコメントアウトして残しており、戻す場合はコメントを外す。
// （iOS 側の LogSenderImpl はもともと no-op 実装）
@Suppress("unused", "UNUSED_PARAMETER")
@SuppressLint("MissingPermission")
class LogSenderImpl(context: Context) : LogSender {

    // private val analytics = FirebaseAnalytics.getInstance(context)

    override fun init(setting: Setting) {
        // Firebase.crashlytics.setCustomKeys {
        //     key("pixiViewId", setting.pixiViewId)
        //     key("isUseAppLock", setting.isUseAppLock)
        //     key("isUseDynamicColor", setting.isUseDynamicColor)
        //     key("isUseGridMode", setting.isUseGridMode)
        //     key("isUseInfinityPostDetail", setting.isUseInfinityPostDetail)
        //     key("isDefaultFollowTabInHome", setting.isDefaultFollowTabInHome)
        //     key("isHideAdultContents", setting.isHideAdultContents)
        //     key("isOverrideAdultContents", setting.isOverrideAdultContents)
        //     key("isHideRestricted", setting.isHideRestricted)
        //     key("isTestUser", setting.isTestUser)
        //     key("isPlusMode", setting.isPlusMode)
        //     key("isDeveloperMode", setting.isDeveloperMode)
        // }
    }

    override fun sendLog(log: JsonObject) {
        // val eventName = listOf(
        //     log["event_category"]?.jsonPrimitive?.content,
        //     log["event_name"]?.jsonPrimitive?.content,
        // ).joinToString("_")
        //
        // analytics.logEvent(eventName) {
        //     for ((key, value) in log) {
        //         param(key, value)
        //     }
        // }
    }

    // private fun ParametersBuilder.param(key: String, value: JsonElement) {
    //     val longValue = value.jsonPrimitive.longOrNull
    //     val doubleValue = value.jsonPrimitive.doubleOrNull
    //
    //     when {
    //         longValue != null -> param(key, longValue)
    //         doubleValue != null -> param(key, doubleValue)
    //         else -> param(key, value.jsonPrimitive.content)
    //     }
    // }
}
