package com.ella.music.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ella.music.R
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun ShuffleAllFloatingButton(
    visible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier
    ) {
        FloatingActionButton(
            onClick = onClick,
            minWidth = 46.dp,
            minHeight = 46.dp,
            containerColor = MiuixTheme.colorScheme.primary
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_shuffle),
                contentDescription = stringResource(R.string.shuffle),
                tint = MiuixTheme.colorScheme.onPrimary,
                modifier = Modifier.size(21.dp)
            )
        }
    }
}
