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
 * Pleos 시스템 앱(차량 설정, ai.umos.vehiclecontrol)의 화면을 2026-11-11 에뮬레이터
 * v2.1.0 에서 픽셀로 잰 값. 디자인 가이드 문서는 색을 이미지로만 공개해서 문서 값이 없다.
 * 앱 색은 이 값과 어긋나지 않게 두고, ECHO 만의 색은 [accent] 하나로 제한한다.
 */
@Immutable
data class EchoColors(
    val background: Color,
    /** 눌림·선택된 타일 바탕. */
    val tile: Color,
    val hairline: Color,
    val text: Color,
    val textSecondary: Color,
    /** 반전 선택 블록(탐색 메뉴, 세그먼트, 주요 버튼). Day 는 검정, Night 는 밝은 회색. */
    val inverse: Color,
    val onInverse: Color,
    /** 상태 값(잠김·장소 등). 시스템이 쓰는 파랑. */
    val state: Color,
    /** 앱 아이콘 틸. '열린 기억' 과 녹음 중 표시에만 쓴다. */
    val accent: Color,
    val recording: Color,
    val scrim: Color,
)

private val Day = EchoColors(
    background = Color(0xFFFFFFFF),
    tile = Color(0xFFF0F0F0),
    hairline = Color(0xFFA6A6A6),
    text = Color(0xFF000000),
    // 시스템 앱은 #7C7C7C 를 쓰지만 흰 바탕 대비가 4.17:1 로 가이드(4.5:1)에 못 미쳐 내렸다(5.33:1).
    textSecondary = Color(0xFF6B6B6B),
    inverse = Color(0xFF000000),
    onInverse = Color(0xFFFFFFFF),
    state = Color(0xFF1262F5),
    // #72DED1 은 흰 바탕에서 대비가 부족해 같은 색상으로 어둡게 내렸다(대비 5.4:1).
    accent = Color(0xFF0B7A70),
    recording = Color(0xFFD93025),
    scrim = Color(0x99000000),
)

private val Night = EchoColors(
    background = Color(0xFF151515),
    tile = Color(0xFF222222),
    hairline = Color(0xFF333333),
    text = Color(0xFFDDDDDD),
    textSecondary = Color(0xFF9A9A9A),
    inverse = Color(0xFFDDDDDD),
    onInverse = Color(0xFF151515),
    state = Color(0xFF1262F5),
    accent = Color(0xFF72DED1),
    recording = Color(0xFFFF5A4F),
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
    // 캡션 26sp 는 160dpi 에서 약 4mm 라 가이드의 '핵심 정보 4mm 이상' 을 넘긴다.
    val headline = TextStyle(fontFamily = family, fontSize = 48.sp, lineHeight = 60.sp, fontWeight = FontWeight.Medium)
    /** 큰 타일 안의 제목. */
    val tileTitle = TextStyle(fontFamily = family, fontSize = 42.sp, lineHeight = 54.sp, fontWeight = FontWeight.Medium)
    val title = TextStyle(fontFamily = family, fontSize = 36.sp, lineHeight = 48.sp, fontWeight = FontWeight.Medium)
    val body = TextStyle(fontFamily = family, fontSize = 32.sp, lineHeight = 46.sp, fontWeight = FontWeight.Normal)
    val caption = TextStyle(fontFamily = family, fontSize = 26.sp, lineHeight = 36.sp, fontWeight = FontWeight.Normal)
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
