// デスクトップ用の moko-biometry スタブ。MokoDescStub.jvm.kt を参照すること。
package dev.icerock.moko.biometry

import dev.icerock.moko.resources.desc.Description

interface BiometryAuthenticator {
    suspend fun checkBiometryAuthentication(
        requestTitle: Description,
        requestReason: Description,
        failureButtonText: Description,
        allowDeviceCredentials: Boolean,
    ): Boolean
}

interface BiometryAuthenticatorFactory {
    fun createBiometryAuthenticator(): BiometryAuthenticator
}

class BiometryAuthenticatorStub : BiometryAuthenticator {
    // デスクトップでは認証を要求せず、常に成功扱いとする。
    override suspend fun checkBiometryAuthentication(
        requestTitle: Description,
        requestReason: Description,
        failureButtonText: Description,
        allowDeviceCredentials: Boolean,
    ): Boolean = true
}
