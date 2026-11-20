package com.example.myapplication.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.myapplication.data.LanguageOption
import com.example.myapplication.data.supportedLanguages
import com.example.myapplication.ui.theme.TechCyan
import kotlin.math.PI
import kotlin.math.cos

/**
 * 渐变主按钮：青→蓝→紫霓虹填充，按压缩放回弹，禁用时置灰。
 */
@Composable
fun NeonButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "neonButtonPress"
    )
    val shape = RoundedCornerShape(16.dp)
    val brush = if (enabled) {
        TechAccentBrush
    } else {
        Brush.linearGradient(listOf(Color(0xFF3A4254), Color(0xFF2B3242)))
    }

    Box(
        modifier = modifier
            .scale(scale)
            .clip(shape)
            .background(brush = brush, shape = shape)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick
            )
            .padding(horizontal = 24.dp, vertical = 15.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = if (enabled) Color.White else Color(0xFF8A91A6),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.size(8.dp))
            }
            Text(
                text = text,
                color = if (enabled) Color.White else Color(0xFF8A91A6),
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

/**
 * 描边次按钮：半透明霓虹容器 + 1dp 青色描边。
 */
@Composable
fun GlassOutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null
) {
    val shape = RoundedCornerShape(16.dp)
    val content = if (enabled) TechCyan else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = modifier
            .clip(shape)
            .background(TechCyan.copy(alpha = if (enabled) 0.10f else 0.05f), shape)
            .border(1.dp, content.copy(alpha = if (enabled) 0.55f else 0.25f), shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leadingIcon != null) {
            Icon(leadingIcon, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(6.dp))
        }
        Text(text = text, color = content, style = MaterialTheme.typography.titleMedium)
    }
}

/** 分区标题：青色图标 + 标题文字。 */
@Composable
fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = TechCyan, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(6.dp))
        }
        Text(text = text, style = MaterialTheme.typography.titleMedium, color = TechCyan)
    }
}

/** 胶囊语言选择器（横向滚动），选中项霓虹高亮。 */
@Composable
fun LanguageSelector(
    selected: String,
    onSelect: (String) -> Unit,
    languages: List<LanguageOption> = supportedLanguages,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(languages) { lang ->
            val isSelected = lang.code == selected
            val shape = RoundedCornerShape(50)
            Box(
                modifier = Modifier
                    .clip(shape)
                    .background(
                        if (isSelected) TechCyan.copy(alpha = 0.20f)
                        else Color.White.copy(alpha = 0.06f),
                        shape
                    )
                    .border(
                        1.dp,
                        if (isSelected) TechCyan else Color.White.copy(alpha = 0.12f),
                        shape
                    )
                    .clickable { onSelect(lang.code) }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    text = lang.label,
                    color = if (isSelected) TechCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

/**
 * 录音/播放波形动画：active 时竖条随相位起伏，静止时为低矮基线。
 * 纯 Compose 实现，无第三方依赖。
 */
@Composable
fun VoiceWave(
    modifier: Modifier = Modifier,
    active: Boolean = false,
    barCount: Int = 26,
    color: Color = TechCyan
) {
    val transition = rememberInfiniteTransition(label = "voiceWave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 720, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "voiceWavePhase"
    )

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(barCount) { index ->
            val heightFraction = if (active) {
                val t = (phase + index * 0.33f) % 1f
                0.25f + 0.75f * (0.5f - 0.5f * cos(2 * PI * t)).toFloat()
            } else {
                0.25f
            }
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height((10 + heightFraction * 30).dp)
                    .background(
                        color = color.copy(alpha = if (active) 0.85f else 0.35f),
                        shape = RoundedCornerShape(2.dp)
                    )
            )
        }
    }
}

/** 一条勾选状态行：用于授权声明等确认项。 */
@Composable
fun CheckRow(
    checked: Boolean,
    onToggle: () -> Unit,
    title: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.clickable(onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Rounded.CheckCircle,
            contentDescription = null,
            tint = if (checked) TechCyan else MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.size(10.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
