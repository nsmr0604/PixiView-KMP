// デスクトップ用の RevenueCat models スタブ。RevenueCatStub.jvm.kt を参照すること。
package com.revenuecat.purchases.kmp.models

class CustomerInfo(val entitlements: Map<String, EntitlementInfo> = emptyMap())

class EntitlementInfo(
    val isActive: Boolean,
    val willRenew: Boolean,
    val periodType: PeriodType,
    val productIdentifier: String,
    val productPlanIdentifier: String?,
    val unsubscribeDetectedAtMillis: Long?,
)

enum class PeriodType { NORMAL, TRIAL, INTRO, PREPAID, UNKNOWN }

enum class PeriodUnit { DAY, WEEK, MONTH, YEAR, UNKNOWN }

enum class PackageType { UNKNOWN, WEEKLY, MONTHLY, TWO_MONTH, QUARTERLY, SIX_MONTH, ANNUAL, LIFETIME, CUSTOM }

class Period(val value: Int, val unit: PeriodUnit)

class Phase(val billingPeriod: Period)

class SubscriptionOptions(val freeTrial: SubscriptionOptions?)

val SubscriptionOptions.freePhase: Phase
    get() = Phase(billingPeriod = Period(value = 0, unit = PeriodUnit.UNKNOWN))

class Price(val amountMicros: Long, val formatted: String)

class StoreProduct(
    val id: String,
    val price: Price,
    val subscriptionOptions: SubscriptionOptions?,
)

class Package(
    val identifier: String,
    val packageType: PackageType,
    val storeProduct: StoreProduct,
)

class Offering(val availablePackages: List<Package> = emptyList())

class Offerings(val current: Offering?)

enum class PurchasesErrorCode { UNKNOWN_ERROR, PURCHASE_CANCELLED_ERROR, STORE_PROBLEM_ERROR, NETWORK_ERROR }

open class PurchasesException(val code: PurchasesErrorCode) : Exception()

class PurchasesTransactionException(val userCancelled: Boolean) : PurchasesException(PurchasesErrorCode.UNKNOWN_ERROR)

class PurchasesError(val code: PurchasesErrorCode, val underlyingErrorMessage: String? = null)
