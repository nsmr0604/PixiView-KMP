// デスクトップ用の moko-permissions / permissions-compose スタブ。MokoDescStub.jvm.kt を参照すること。
package dev.icerock.moko.permissions

class PermissionsController

interface PermissionsControllerFactory {
    fun createPermissionsController(): PermissionsController
}

class PermissionsControllerFactoryImpl : PermissionsControllerFactory {
    override fun createPermissionsController(): PermissionsController = PermissionsController()
}
