package com.weatherinsights.daily.forecast.live.radar.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.weatherinsights.daily.forecast.live.radar.ui.theme.GlassBorderWhite
import com.weatherinsights.daily.forecast.live.radar.ui.theme.GlassWhite

@Composable
fun GlassyPanel(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 12.dp,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(GlassWhite)
            .border(1.dp, GlassBorderWhite, RoundedCornerShape(cornerRadius)),
        content = content
    )
}
