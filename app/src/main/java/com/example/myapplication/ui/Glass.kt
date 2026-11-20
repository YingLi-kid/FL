package com.example.myapplication.ui

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.myapplication.ui.theme.TechBgBottom
import com.example.myapplication.ui.theme.TechBgMid
import com.example.myapplication.ui.theme.TechBgTop
import com.example.myapplication.ui.theme.TechBlue
import com.example.myapplication.ui.theme.TechCyan
import com.example.myapplication.ui.theme.TechPink
import com.example.myapplication.ui.theme.TechViolet

// 霓虹主渐变（青 → 蓝 → 紫），用于渐变文字、强调控件与高光线
val TechAccentBrush: Brush = Brush.linearGradient(listOf(TechCyan, TechBlue, TechViolet))

/**
 * 全局科技背景：深色纵向渐变 + 三枚高度模糊的霓虹弥散光斑。
 * 纯 Compose 实现，Modifier.blur 在 Android 12 以下自动走软件降级，不会崩溃，也无需新依赖。
 * 光斑用 offset（可为负）平移出血，不能用负 padding（Compose 会抛异常）。
 */
@Composable
fun TechBackground(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(
                    brush = Brush.verticalGradient(listOf(TechBgTop, TechBgMid, TechBgBottom))
                )
            }
    ) {
        GlowBlob(
            alignment = Alignment.TopEnd,
            color = TechCyan,
            size = 260.dp,
            alpha = 0.38f,
            offsetX = 70.dp,
            offsetY = 48.dp
        )
        GlowBlob(
            alignment = Alignment.BottomStart,
            color = TechViolet,
            size = 300.dp,
            alpha = 0.32f,
            offsetX = (-90).dp,
            offsetY = (-90).dp
        )
        GlowBlob(
            alignment = Alignment.TopStart,
            color = TechPink,
            size = 180.dp,
            alpha = 0.20f,
            offsetX = (-60).dp,
            offsetY = 240.dp
        )
    }
}

@Composable
private fun BoxScope.GlowBlob(
    alignment: Alignment,
    color: Color,
    size: Dp,
    alpha: Float,
    offsetX: Dp = 0.dp,
    offsetY: Dp = 0.dp
) {
    val radiusPx = with(LocalDensity.current) { size.toPx() / 2f }
    Box(
        modifier = Modifier
            .align(alignment)
            .offset(x = offsetX, y = offsetY)
            .size(size)
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(color.copy(alpha = alpha), color.copy(alpha = 0f)),
                    radius = radiusPx
                ),
                shape = CircleShape
            )
    )
}

/**
 * 玻璃拟态容器：半透明填充 + 1dp 霓虹细描边 + 顶部高光线，大圆角。
 * 传入 onClick 即变为可点击玻璃卡（自带涟漪），否则为静态玻璃面板。
 * 内部统一提供 onSurface 作为默认内容色，避免未指定颜色的文字回退成黑色。
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource? = null,
    corner: Dp = 22.dp,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val fill = if (isDark) Color.White.copy(alpha = 0.07f) else Color(0xFFF4F8FF).copy(alpha = 0.72f)
    val stroke = if (isDark) Color.White.copy(alpha = 0.16f) else TechBlue.copy(alpha = 0.28f)
    val shape = RoundedCornerShape(corner)

    var shapeModifier = modifier
        .clip(shape)
        .background(color = fill, shape = shape)
        .border(width = 1.dp, color = stroke, shape = shape)

    if (onClick != null) {
        val source = interactionSource ?: remember { MutableInteractionSource() }
        shapeModifier = shapeModifier.clickable(
            interactionSource = source,
            indication = LocalIndication.current,
            onClick = onClick
        )
    }

    Box(modifier = shapeModifier) {
        CompositionLocalProvider(
            LocalContentColor provides MaterialTheme.colorScheme.onSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(contentPadding),
                content = content
            )
        }
        // 顶部玻璃反光高光线
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(TechCyan.copy(alpha = 0.5f), Color.White.copy(alpha = 0.08f), Color.Transparent)
                    )
                )
        )
    }
}

/** 标题用的霓虹渐变文字样式。 */
@Composable
fun gradientTitleStyle() =
    MaterialTheme.typography.titleMedium.copy(brush = TechAccentBrush)
