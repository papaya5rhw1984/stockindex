package io.github.papaya5rhw1984.stockindex.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** 증시 나우 다크 테마 — 블루/시안 포인트 */
object Brand {
    val Accent = Color(0xFF4F9DF7)
    val AccentSoft = Color(0xFF8FC0FB)
    val Bg = Color(0xFF0F1115)
    val Surface = Color(0xFF1A1D24)
    val SurfaceHi = Color(0xFF232733)
    val Divider = Color(0xFF2C313D)
    val Up = Color(0xFFE0574D)      // 상승(한국식 빨강)
    val Down = Color(0xFF3A7BD5)    // 하락(파랑)
    val Flat = Color(0xFF8A8F98)    // 보합
    val Live = Color(0xFF2EB872)    // 실시간(초록)
    val Saved = Color(0xFFE0A53C)   // 저장/오프라인(앰버)
}

private val DarkScheme = darkColorScheme(
    primary = Brand.Accent,
    background = Brand.Bg,
    surface = Brand.Surface,
    surfaceVariant = Brand.SurfaceHi,
    onBackground = Color(0xFFEDF0F6),
    onSurface = Color(0xFFEDF0F6),
    onSurfaceVariant = Color(0xFF9AA0AC),
    outline = Brand.Divider
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkScheme, content = content)
}
