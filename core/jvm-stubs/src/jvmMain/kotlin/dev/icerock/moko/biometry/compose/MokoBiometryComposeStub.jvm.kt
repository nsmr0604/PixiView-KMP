// デスクトップ用の moko-biometry-compose スタブ。MokoBiometryStub.jvm.kt を参照すること。
package dev.icerock.moko.biometry.compose

import androidx.compose.runtime.Composable
import dev.icerock.moko.biometry.BiometryAuthenticator
import dev.icerock.moko.biometry.BiometryAuthenticatorFactory
import dev.icerock.moko.biometry.BiometryAuthenticatorStub

class BiometryAuthenticatorFactoryImpl : BiometryAuthenticatorFactory {
    override fun createBiometryAuthenticator(): BiometryAuthenticator = BiometryAuthenticatorStub()
}

@Composable
fun rememberBiometryAuthenticatorFactory(): BiometryAuthenticatorFactory = BiometryAuthenticatorFactoryImpl()

@Composable
fun BindBiometryAuthenticatorEffect(biometryAuthenticator: BiometryAuthenticator) {
    // no-op
}
