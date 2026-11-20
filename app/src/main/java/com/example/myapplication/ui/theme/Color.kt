package com.example.myapplication.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

/*
 * 科技黑 / 玻璃拟态配色：固定深色，霓虹青—蓝—紫作为强调色。
 * 所有 Material 3 角色色都在此统一声明，UI 层只通过角色名或文末的品牌常量引用。
 */

val DarkCustomColorScheme = darkColorScheme(
    primary = Color(0xFF5BE7FF),
    onPrimary = Color(0xFF04222E),
    primaryContainer = Color(0xFF0C3D4D),
    onPrimaryContainer = Color(0xFFC9F5FF),

    secondary = Color(0xFFA895FF),
    secondaryContainer = Color(0xFF251E57),
    onSecondaryContainer = Color(0xFFE6DEFF),
    tertiaryContainer = Color(0xFF4B1F38),
    onTertiaryContainer = Color(0xFFFFD3E8),

    surface = Color(0xFF0A0F1C),
    surfaceContainerLow = Color(0xFF0E1526),
    surfaceContainer = Color(0xFF131C30),
    surfaceContainerHigh = Color(0xFF1A2440),
    surfaceContainerHighest = Color(0xFF24304F),
    onSurface = Color(0xFFE9EFFC),
    onSurfaceVariant = Color(0xFFA6B3D1),

    outline = Color(0xFF7183AD),
    outlineVariant = Color(0xFF293450),

    inverseSurface = Color(0xFFE9EFFC),
    inverseOnSurface = Color(0xFF1A2030),
    inversePrimary = Color(0xFF0089A8),

    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

/* —— 品牌霓虹色与科技背景（玻璃组件/渐变/光斑使用）—— */
val TechCyan = Color(0xFF4DE8FF)
val TechBlue = Color(0xFF5B8CFF)
val TechViolet = Color(0xFF9A6CFF)
val TechPink = Color(0xFFFF6FB5)

val TechBgTop = Color(0xFF0C1326)
val TechBgMid = Color(0xFF080D1C)
val TechBgBottom = Color(0xFF04060F)
