// デスクトップ用の RevenueCat ktx スタブ。RevenueCatStub.jvm.kt を参照すること。
package com.revenuecat.purchases.kmp.ktx

import com.revenuecat.purchases.kmp.Purchases
import com.revenuecat.purchases.kmp.models.CustomerInfo
import com.revenuecat.purchases.kmp.models.Offerings
import com.revenuecat.purchases.kmp.models.StoreProduct

suspend fun Purchases.awaitCustomerInfo(): CustomerInfo = CustomerInfo(emptyMap())

suspend fun Purchases.awaitOfferings(): Offerings = Offerings(current = null)

suspend fun Purchases.awaitPurchase(pkg: StoreProduct): PurchaseResult = PurchaseResult(customerInfo = CustomerInfo(emptyMap()))

class PurchaseResult(val customerInfo: CustomerInfo)
