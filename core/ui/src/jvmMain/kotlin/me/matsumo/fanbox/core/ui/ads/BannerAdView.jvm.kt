package me.matsumo.fanbox.core.ui.ads

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// デスクトップでは広告を非表示にする（実機の debug ビルドと同じ扱い）。
@Composable
actual fun BannerAdView(modifier: Modifier) {
    Box(modifier.height(60.dp))
}
