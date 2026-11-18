// デスクトップ用の moko-permissions-compose スタブ。MokoPermissionsStub.jvm.kt を参照すること。
// PermissionsControllerFactory は実ライブラリではこの compose パッケージに存在する。
package dev.icerock.moko.permissions.compose

import androidx.compose.runtime.Composable
import dev.icerock.moko.permissions.PermissionsController
import dev.icerock.moko.permissions.PermissionsControllerFactoryImpl

interface PermissionsControllerFactory {
    fun createPermissionsController(): PermissionsController
}

private class PermissionsControllerFactoryImplCompose : PermissionsControllerFactory {
    override fun createPermissionsController(): PermissionsController = PermissionsController()
}

@Composable
fun rememberPermissionsControllerFactory(): PermissionsControllerFactory = PermissionsControllerFactoryImplCompose()

@Composable
fun BindEffect(permissionsController: PermissionsController) {
    // no-op
}
