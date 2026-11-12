package com.eco.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.eco.R
import com.eco.ui.theme.Echo
import com.eco.ui.theme.EchoType

/*
 * Pleos 시스템 앱과 같은 재료만 쓴다: 각진 면, 1px 구분선.
 * 색 반전은 시스템처럼 세 곳에만 쓴다: 선택된 탐색 항목, 선택된 세그먼트 칸, 주요 버튼.
 * 큰 면을 반전하면 Night 에서 화면에서 가장 밝은 곳이 되어 눈부시다(2026-11-11 리뷰 실측: 봉인 화면 밝은 면 23~36%).
 */

/** 터치 대상 최소 높이. 기획서 UI 원칙(88px 이상)에 맞춘다. */
val TouchMin: Dp = 88.dp
val Gutter: Dp = 32.dp

@Composable
fun HLine(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(Echo.colors.hairline))
}

@Composable
fun VLine(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxHeight().width(1.dp).background(Echo.colors.hairline))
}

data class NavItem(val label: String, val icon: ImageVector)

/** 차량 설정 앱의 왼쪽 목록과 같은 형태. 선택 항목은 반전 블록. */
@Composable
fun NavRail(items: List<NavItem>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxHeight()) {
        Column(Modifier.width(340.dp).fillMaxHeight()) {
            // 시스템 설정 앱처럼 앱 아이콘과 이름을 함께 둔다. 런처 아이콘의 남색 배경은 시스템 톤과 어긋나 전경만 쓴다.
            Row(Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.echo_icon_foreground), contentDescription = null, modifier = Modifier.size(80.dp))
                Text("ECHO", style = EchoType.title, color = Echo.colors.text)
            }
            items.forEachIndexed { index, item ->
                val on = index == selected
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 96.dp)
                        .background(if (on) Echo.colors.inverse else Color.Transparent)
                        .semantics { this.selected = on }
                        .clickable(role = Role.Tab) { onSelect(index) }
                        .padding(horizontal = Gutter),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    val tint = if (on) Echo.colors.onInverse else Echo.colors.text
                    Icon(item.icon, contentDescription = null, tint = tint, modifier = Modifier.size(36.dp))
                    Text(item.label, style = EchoType.label, color = tint, maxLines = 1)
                }
            }
        }
        VLine()
    }
}

/**
 * 박스형 세그먼트(Pleos Segmented menu, Type=Box). 비선택 칸은 controller_tab_background 처럼 옅게 깔고,
 * 테두리는 비텍스트 대비 3:1 을 넘는 controlLine 으로 그린다.
 */
@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = Echo.colors
    Row(modifier.heightIn(min = TouchMin).background(c.text.copy(alpha = 0.05f)).border(1.dp, c.controlLine)) {
        options.forEachIndexed { index, label ->
            val on = index == selected
            if (index > 0) Box(Modifier.width(1.dp).height(TouchMin).background(c.controlLine))
            Box(
                Modifier.weight(1f).heightIn(min = TouchMin)
                    .background(if (on) c.inverse else Color.Transparent)
                    .semantics { this.selected = on }
                    .clickable(role = Role.Tab) { onSelect(index) },
                contentAlignment = Alignment.Center,
            ) { Text(label, style = EchoType.label, color = if (on) c.onInverse else c.text, maxLines = 1) }
        }
    }
}

/** 주요 행동은 반전 블록(button_filled), 나머지는 surface_high. 한 화면에 주요 행동은 하나만 둔다. */
@Composable
fun BlockButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    icon: ImageVector? = null,
    iconTint: Color? = null,
    enabled: Boolean = true,
) {
    val c = Echo.colors
    // 비활성 주요 버튼까지 반전 색으로 칠하면 Night 에서 누를 수 없는 버튼이 가장 밝은 면이 된다.
    val fill = if (primary && enabled) c.inverse else c.tile
    // 비활성은 alpha 0.38 대신 hint 색을 쓴다. 0.38 은 대비 2.6:1 로 차 안에서 라벨이 안 읽혔다.
    val ink = if (!enabled) c.hint else if (primary) c.onInverse else c.text
    Row(
        modifier.heightIn(min = 104.dp).background(fill)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = if (enabled) iconTint ?: ink else ink, modifier = Modifier.size(40.dp))
            Spacer(Modifier.width(14.dp))
        }
        Text(label, style = EchoType.label, color = ink, maxLines = 1)
    }
}

/** 머리 오른쪽의 작은 버튼(선택·취소). 글자만 두면 버튼으로 보이지 않아 타일 바탕을 깐다. */
@Composable
fun SmallButton(label: String, onClick: () -> Unit, color: Color? = null, icon: ImageVector? = null) {
    Row(
        Modifier.heightIn(min = TouchMin).widthIn(min = 140.dp).background(Echo.colors.tile)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, null, tint = color ?: Echo.colors.text, modifier = Modifier.size(36.dp))
        Text(label, style = EchoType.label, color = color ?: Echo.colors.text, maxLines = 1)
    }
}

/** 아래 버튼 줄(BlockButton) 높이. 띠·토스트를 그 위에 띄울 때 쓴다. */
val BOTTOM_BAR = 104.dp

/** 화면 머리: 위에 맥락 한 줄(회색), 아래 제목. 뒤로 가기가 있으면 왼쪽에 둔다. */
@Composable
fun ScreenHeader(title: String, context: String? = null, onBack: (() -> Unit)? = null, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(start = if (onBack == null) Gutter else 8.dp, end = Gutter, top = 24.dp, bottom = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            Box(
                Modifier.size(TouchMin).clickable(role = Role.Button, onClick = onBack).semantics { contentDescription = "뒤로" },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, null, tint = Echo.colors.text, modifier = Modifier.size(40.dp)) }
            Spacer(Modifier.width(8.dp))
        }
        Column(Modifier.weight(1f)) {
            if (context != null) Text(context, style = EchoType.caption, color = Echo.colors.textSecondary, maxLines = 1)
            Text(title, style = EchoType.headline, color = Echo.colors.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        trailing()
    }
}

/**
 * 아이콘이 위, 제목과 상태가 아래인 시스템 타일. [action] 이면 아이콘을 반전 사각형에 담아
 * 누를 수 있는 주요 행동임을 보인다(면 전체는 반전하지 않는다).
 */
@Composable
fun Tile(
    icon: ImageVector,
    title: String,
    status: String?,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    action: Boolean = false,
    actionFill: Color? = null,
    marker: Color? = null,
    bottom: (@Composable () -> Unit)? = null,
) {
    val c = Echo.colors
    Row(if (onClick != null) modifier.clickable(role = Role.Button, onClick = onClick) else modifier) {
        if (marker != null) Box(Modifier.width(8.dp).fillMaxHeight().background(marker))
        Column(Modifier.weight(1f).padding(Gutter), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (action) {
                Box(Modifier.size(88.dp).background(actionFill ?: c.inverse), contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = if (actionFill != null) c.background else c.onInverse, modifier = Modifier.size(48.dp))
                }
            } else {
                Icon(icon, contentDescription = null, tint = marker ?: c.text, modifier = Modifier.size(56.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text(title, style = EchoType.tileTitle, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (status != null) Text(status, style = EchoType.body, color = c.textSecondary, maxLines = 2)
            if (bottom != null) { Spacer(Modifier.height(8.dp)); bottom() }
        }
    }
}

/** Pleos System popup: 제목, 설명(선택), 가로 정렬 버튼. */
@Composable
fun SystemPopup(
    title: String,
    description: String?,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String? = "취소",
    content: @Composable ColumnScope.() -> Unit = {},
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Echo.colors.scrim).clickable(onClick = onDismiss), contentAlignment = Alignment.Center) {
            // Night 에서 대화상자 바탕과 스크림이 거의 같아 경계가 흐렸다. 1px 선을 두른다.
            Column(Modifier.widthIn(max = 880.dp).fillMaxWidth(0.8f).background(Echo.colors.background).border(1.dp, Echo.colors.controlLine)
                .clickable(enabled = false) {}) {
                Column(Modifier.padding(horizontal = 48.dp, vertical = 44.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(title, style = EchoType.title, color = Echo.colors.text, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    if (description != null) Text(description, style = EchoType.body, color = Echo.colors.textSecondary,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    content()
                }
                // 이 앱의 팝업은 모두 되돌릴 수 없는 일(지금 열기·버리기·삭제)을 묻는다. 확인 쪽을 반전해 더 눌리게 두지 않는다.
                Row(Modifier.fillMaxWidth()) {
                    if (dismissLabel != null) { BlockButton(dismissLabel, onDismiss, Modifier.weight(1f)); VLine(Modifier.height(104.dp)) }
                    BlockButton(confirmLabel, onConfirm, Modifier.weight(1f))
                }
            }
        }
    }
}
