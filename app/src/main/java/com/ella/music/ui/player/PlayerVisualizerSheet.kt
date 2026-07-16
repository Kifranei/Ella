package com.ella.music.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.ui.settings.SettingsIntSliderPreference
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun VisualizerSheetContent(
    enabled: Boolean,
    opacity: Int,
    onBack: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onOpacityChange: (Int) -> Unit
) {
    HalfSheetTitle(title = stringResource(R.string.player_visualizer_settings), onBack = onBack)
    Spacer(modifier = Modifier.height(22.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f))
            .clickable { onEnabledChange(!enabled) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.player_music_visualizer),
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .width(56.dp)
                .height(32.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(
                    if (enabled) MiuixTheme.colorScheme.primary
                    else MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.26f)
                )
                .padding(4.dp),
            contentAlignment = if (enabled) Alignment.CenterEnd else Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(MiuixTheme.colorScheme.background)
            )
        }
    }
    Spacer(modifier = Modifier.height(14.dp))
    SettingsIntSliderPreference(
        title = stringResource(R.string.player_visualizer_opacity),
        summary = stringResource(R.string.player_visualizer_opacity_summary),
        valueText = "$opacity%",
        value = opacity.coerceIn(20, 100),
        valueRange = 20..100,
        steps = 15,
        onValueChange = { onOpacityChange(it.coerceIn(20, 100)) }
    )
    Spacer(modifier = Modifier.height(20.dp))
    Text(
        text = stringResource(R.string.player_visualizer_permission_summary),
        fontSize = 13.sp,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        modifier = Modifier.padding(horizontal = 4.dp)
    )
}
