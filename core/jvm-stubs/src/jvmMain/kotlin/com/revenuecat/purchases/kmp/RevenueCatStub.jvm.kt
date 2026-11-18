// デスクトップ用の RevenueCat スタブ。
// purchases-kmp は JVM target を提供しないため、jvmMain のコンパイルを満たすための
// no-op 実装。デスクトップでは課金機能を利用しない（常に非アクティブを返す）。
// commonMain から import なしで呼べるよう、sharedInstance / restorePurchases は
// メンバとして定義する。
package com.revenuecat.purchases.kmp

import com.revenuecat.purchases.kmp.models.CustomerInfo
import com.revenuecat.purchases.kmp.models.PurchasesError

enum class LogLevel { VERBOSE, DEBUG, INFO, WARN, ERROR }

object Purchases {
    var logLevel: LogLevel = LogLevel.INFO

    val sharedInstance: Purchases
        get() = this

    fun restorePurchases(onSuccess: (CustomerInfo?) -> Unit, onError: (PurchasesError) -> Unit) {
        onSuccess(null)
    }
}

fun Purchases.configure(apiKey: String) {
    // no-op
}
