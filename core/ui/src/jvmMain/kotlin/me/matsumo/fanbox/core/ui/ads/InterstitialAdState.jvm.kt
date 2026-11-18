package me.matsumo.fanbox.core.ui.ads

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

// デスクトップでは広告を扱わないため、常にロード不要・表示成功相当のスタブ。
class InterstitialAdStateImpl : InterstitialAdState {
    override fun load() {
        // not implemented
    }

    override suspend fun show(): Boolean = true
}

@Composable
actual fun rememberInterstitialAdState(adUnitId: String, enable: Boolean): InterstitialAdState = remember {
    InterstitialAdStateImpl()
}
