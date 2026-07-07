package com.ella.music.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal val LibraryFloatingControlsBottomPadding = 156.dp
internal val LibraryFloatingControlsEndPadding = 22.dp
internal val LibrarySecondaryFloatingControlsBottomPadding = LibraryFloatingControlsBottomPadding + 58.dp

@Composable
fun SongSelectionActionRow(
    selectedCount: Int,
    totalCount: Int,
    rangeEnabled: Boolean,
    allSelected: Boolean,
    onRangeSelect: () -> Unit,
    onSelectAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.library_selected_fraction, selectedCount, totalCount),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = MiuixTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = stringResource(R.string.library_range_select),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = if (rangeEnabled) {
                MiuixTheme.colorScheme.onSurface
            } else {
                MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.38f)
            },
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable(enabled = rangeEnabled, onClick = onRangeSelect)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        )
        Text(
            text = stringResource(if (allSelected) R.string.common_deselect_all else R.string.common_select_all),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onSelectAll)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

@Composable
fun FloatingSelectionControls(
    visible: Boolean,
    rangeEnabled: Boolean,
    allSelected: Boolean,
    onRangeSelect: () -> Unit,
    onSelectAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.End
        ) {
            FloatingSelectionChip(
                text = stringResource(R.string.library_range_select),
                enabled = rangeEnabled,
                onClick = onRangeSelect
            )
            FloatingSelectionChip(
                text = stringResource(if (allSelected) R.string.common_deselect_all else R.string.common_select_all),
                enabled = true,
                onClick = onSelectAll
            )
        }
    }
}

@Composable
private fun FloatingSelectionChip(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Text(
        text = text,
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        color = if (enabled) {
            MiuixTheme.colorScheme.onPrimary
        } else {
            MiuixTheme.colorScheme.onPrimary.copy(alpha = 0.42f)
        },
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(MiuixTheme.colorScheme.primary.copy(alpha = if (enabled) 0.96f else 0.48f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    )
}
