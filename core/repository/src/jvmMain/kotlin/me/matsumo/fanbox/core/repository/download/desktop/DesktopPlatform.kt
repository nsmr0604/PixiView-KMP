package me.matsumo.fanbox.core.repository.download.desktop

/** デスクトップアプリが動作している OS。 */
enum class DesktopPlatform {
    Windows,
    MacOS,
    Linux,
}

/** 現在動作しているデスクトップの OS。 */
val currentDesktopPlatform: DesktopPlatform by lazy {
    val osName = System.getProperty("os.name").lowercase()

    when {
        osName.contains("windows") -> DesktopPlatform.Windows

        osName.contains("mac") -> DesktopPlatform.MacOS

        else -> DesktopPlatform.Linux
    }
}
