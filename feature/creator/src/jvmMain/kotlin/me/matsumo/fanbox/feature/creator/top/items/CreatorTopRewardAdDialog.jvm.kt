package me.matsumo.fanbox.feature.creator.top.items

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

// デスクトップでは広告を扱わないため、Plus 画面への誘導のみを行う。
@Composable
actual fun CreatorTopRewardAdDialog(
    title: String,
    message: String,
    isAbleToReward: Boolean,
    onReward: () -> Unit,
    onClickShowPlus: () -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier,
) {
    onClickShowPlus.invoke()
}
