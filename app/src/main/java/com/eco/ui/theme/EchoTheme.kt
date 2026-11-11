package com.eco.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Pleos Connect 디자인 가이드 Colors(document.pleos.ai/docs/connect/guide/docs-design/foundations/colors)의
 * 토큰 값. 가이드 이미지에 적힌 hex 를 2026-11-11 에 옮겼다. 괄호 안이 가이드의 토큰 이름이다.
 * 에뮬레이터 차량 설정 앱 실측과 거의 같지만, Day 의 선택 블록은 앱이 #000000, 가이드가 basic_600 이라 가이드를 따른다.
 * ECHO 만의 색은 [accent] 하나다.
 */
@Immutable
data class EchoColors(
    /** background_secondary = basic_00 */
    val background: Color,
    /** surface_high = basic_100. 눌림·선택된 면, 보조 버튼 바탕. */
    val tile: Color,
    /** 구분선. Day basic_400(시스템 앱 실측 #A6A6A6 과 같음), Night basic_200. */
    val hairline: Color,
    /** 세그먼트 같은 조작부 테두리. 비텍스트 대비 3:1 을 넘기려고 구분선보다 진하게. */
    val controlLine: Color,
    /** text_*_primary = static_dark_100 / static_light_100 */
    val text: Color,
    /** text_*_tertiary(64%). secondary(84%)는 본문과 차이가 작아 보조 문구에는 tertiary 를 쓴다. */
    val textSecondary: Color,
    /** 48%. 비활성 글자, 대기 중 막대. 비텍스트 대비 3:1 이상. */
    val hint: Color,
    /** button_filled_enabled = basic_600. 주요 버튼·선택된 탐색 항목·선택된 세그먼트 칸. */
    val inverse: Color,
    val onInverse: Color,
    /** informative_positive. 기본 바탕 위 글자로만 쓴다(surface_high 위에서는 대비 부족). */
    val state: Color,
    /** informative_negative. 녹음 점처럼 글자가 아닌 곳에만 쓴다(흰 바탕 글자 대비 3.56:1). */
    val recording: Color,
    /** 앱 아이콘 틸. '새로 열린 녹음' 과 녹음 입력 표시에만 쓴다. */
    val accent: Color,
    val scrim: Color,
)

private val Ink = Color(0xFF131417)
private val Paper = Color(0xFFFFFFFF)

private val Day = EchoColors(
    background = Paper,
    tile = Color(0xFFEDEEF2),
    hairline = Color(0xFFA4A8AE),
    controlLine = Ink.copy(alpha = 0.48f),
    text = Ink,
    textSecondary = Ink.copy(alpha = 0.64f),
    hint = Ink.copy(alpha = 0.48f),
    inverse = Color(0xFF44464E),
    onInverse = Paper,
    state = Color(0xFF0064FF),
    recording = Color(0xFFFE3D16),
    // #72DED1 은 흰 바탕에서 대비가 부족해 같은 색상으로 어둡게 내렸다(대비 5.21:1).
    accent = Color(0xFF0B7A70),
    scrim = Color(0x99000000),
)

private val Night = EchoColors(
    background = Ink,
    tile = Color(0xFF313236),
    // 가이드에 구분선 토큰이 없다. basic_100(#313236)은 바탕과 1.5:1 이라 거의 안 보여 basic_200 을 쓴다.
    hairline = Color(0xFF44464E),
    controlLine = Paper.copy(alpha = 0.48f),
    text = Paper,
    textSecondary = Paper.copy(alpha = 0.64f),
    hint = Paper.copy(alpha = 0.48f),
    inverse = Color(0xFFE0E1E6),
    onInverse = Ink,
    state = Color(0xFF0082FF),
    recording = Color(0xFFFF4C28),
    accent = Color(0xFF72DED1),
    scrim = Color(0xB3000000),
)

/**
 * 시스템 `sans-serif` 패밀리가 이미 Asta Sans 로 덮여 있다(/product/etc/fonts_customization.xml).
 * 그 파일은 600 을 AstaSans-Bold, 700 이상을 ExtraBold 에 묶어 두었으니 SemiBold 는 쓰지 않는다.
 */
object EchoType {
    private val family = FontFamily.SansSerif
    val display = TextStyle(fontFamily = family, fontSize = 150.sp, lineHeight = 160.sp, fontWeight = FontWeight.Light)
    // 시스템 앱 크기(22~30)로 시작했으나 넓은 타일에서 글자가 묻혀 한 단계씩 올렸다.
    val headline = TextStyle(fontFamily = family, fontSize = 48.sp, lineHeight = 60.sp, fontWeight = FontWeight.Medium)
    /** 큰 타일 안의 제목. */
    val tileTitle = TextStyle(fontFamily = family, fontSize = 42.sp, lineHeight = 54.sp, fontWeight = FontWeight.Medium)
    val title = TextStyle(fontFamily = family, fontSize = 36.sp, lineHeight = 48.sp, fontWeight = FontWeight.Medium)
    val body = TextStyle(fontFamily = family, fontSize = 32.sp, lineHeight = 46.sp, fontWeight = FontWeight.Normal)
    // 26sp 에서 숫자 잉크 높이가 18px(2.86mm)로 가이드의 일반 글자 3mm 에 못 미쳤다(2026-11-11 리뷰 실측). 28sp 로 올렸다.
    val caption = TextStyle(fontFamily = family, fontSize = 28.sp, lineHeight = 38.sp, fontWeight = FontWeight.Normal)
    val label = TextStyle(fontFamily = family, fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.Medium)
    val number = TextStyle(fontFamily = family, fontSize = 52.sp, lineHeight = 60.sp, fontWeight = FontWeight.Bold)
}

private val LocalEchoColors = staticCompositionLocalOf { Day }

object Echo {
    val colors: EchoColors
        @Composable @ReadOnlyComposable get() = LocalEchoColors.current
}

@Composable
fun EchoTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (dark) Night else Day
    val material = if (dark) {
        darkColorScheme(primary = colors.inverse, onPrimary = colors.onInverse, background = colors.background,
            onBackground = colors.text, surface = colors.background, onSurface = colors.text)
    } else {
        lightColorScheme(primary = colors.inverse, onPrimary = colors.onInverse, background = colors.background,
            onBackground = colors.text, surface = colors.background, onSurface = colors.text)
    }
    CompositionLocalProvider(LocalEchoColors provides colors) {
        MaterialTheme(colorScheme = material, content = content)
    }
}
