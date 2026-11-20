package com.example.myapplication.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

// 玻璃拟态采用更圆润的转角：卡片 24dp、对话框 30dp
private val AppShapes = Shapes(
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp)
)

// 固定科技黑深色主题，不再提供浅色/跟随系统切换
@Composable
fun MyApplicationTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkCustomColorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
