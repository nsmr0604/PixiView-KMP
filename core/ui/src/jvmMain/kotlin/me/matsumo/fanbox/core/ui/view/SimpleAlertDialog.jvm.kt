package me.matsumo.fanbox.core.ui.view

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// デスクトップでは Compose の Dialog でネイティブアラートの代替を行う。
@Composable
actual fun SimpleAlertDialog(
    title: String,
    description: String,
    positiveText: String?,
    negativeText: String?,
    onClickPositive: () -> Unit,
    onClickNegative: () -> Unit,
    isCaution: Boolean,
    modifier: Modifier,
) {
    AlertDialog(
        modifier = modifier,
        onDismissRequest = onClickNegative,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Text(
                text = description,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            if (positiveText != null) {
                TextButton(
                    modifier = Modifier.padding(8.dp),
                    onClick = onClickPositive,
                ) {
                    Text(
                        text = positiveText,
                        color = if (isCaution) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        },
        dismissButton = {
            if (negativeText != null) {
                TextButton(
                    modifier = Modifier.padding(8.dp),
                    onClick = onClickNegative,
                ) {
                    Text(
                        text = negativeText,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        },
    )
}
