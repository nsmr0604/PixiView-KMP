// デスクトップ用の moko-biometry / moko-permissions / moko-resources(desc) スタブ。
// これらのライブラリは JVM target を提供しないため、jvmMain のコンパイルを満たすための
// no-op 実装。デスクトップでは生体認証・権限ダイアログを利用しない（常に成功を返す）。
package dev.icerock.moko.resources.desc

interface Description

class StringDescription(val value: String) : Description

fun String.desc(): Description = StringDescription(this)
