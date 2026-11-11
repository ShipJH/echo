package com.eco.ui.components

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
import com.eco.ui.theme.Echo
import com.eco.ui.theme.EchoType

/*
 * Pleos 시스템 앱과 같은 재료만 쓴다: 각진 면, 1px 구분선, 선택은 색 반전.
 * 둥근 카드·그림자·그라데이션은 쓰지 않는다(시스템 화면에 없다).
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
            Text("ECHO", style = EchoType.title, color = Echo.colors.text,
                modifier = Modifier.padding(start = Gutter, top = 28.dp, bottom = 24.dp))
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

/** 박스형 세그먼트(Pleos Segmented menu, Type=Box). */
@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, inverted: Boolean = false) {
    val c = Echo.colors
    // 반전된 블록(선택된 행) 안에 놓일 때는 색 역할을 뒤집는다.
    val line = if (inverted) c.onInverse else c.hairline
    val onFill = if (inverted) c.onInverse else c.inverse
    val onText = if (inverted) c.inverse else c.onInverse
    val offText = if (inverted) c.onInverse else c.text
    Row(modifier.heightIn(min = TouchMin).border(1.dp, line)) {
        options.forEachIndexed { index, label ->
            val on = index == selected
            if (index > 0) Box(Modifier.width(1.dp).height(TouchMin).background(line))
            Box(
                Modifier.weight(1f).heightIn(min = TouchMin)
                    .background(if (on) onFill else Color.Transparent)
                    .semantics { this.selected = on }
                    .clickable(role = Role.Tab) { onSelect(index) },
                contentAlignment = Alignment.Center,
            ) { Text(label, style = EchoType.label, color = if (on) onText else offText, maxLines = 1) }
        }
    }
}

/** 주요 행동은 반전 블록, 나머지는 타일 바탕. 한 화면에 주요 행동은 하나만 둔다. */
@Composable
fun BlockButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val c = Echo.colors
    val fill = if (primary) c.inverse else c.tile
    val ink = (if (primary) c.onInverse else c.text).let { if (enabled) it else it.copy(alpha = 0.38f) }
    Row(
        modifier.heightIn(min = 104.dp).background(fill)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(36.dp))
            Spacer(Modifier.width(14.dp))
        }
        Text(label, style = EchoType.label, color = ink, maxLines = 1)
    }
}

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

/** 아이콘이 위, 제목과 상태가 아래인 시스템 타일. */
@Composable
fun Tile(
    icon: ImageVector,
    title: String,
    status: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    inverted: Boolean = false,
    statusColor: Color? = null,
    marker: Color? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val c = Echo.colors
    val ink = if (inverted) c.onInverse else c.text
    Row(modifier.background(if (inverted) c.inverse else Color.Transparent).clickable(role = Role.Button, onClick = onClick)) {
        if (marker != null) Box(Modifier.width(8.dp).fillMaxHeight().background(marker))
        Column(Modifier.weight(1f).padding(Gutter), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, contentDescription = null, tint = marker ?: ink, modifier = Modifier.size(56.dp))
            Spacer(Modifier.height(12.dp))
            Text(title, style = EchoType.tileTitle, color = ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (status != null) Text(status, style = EchoType.body,
                color = statusColor ?: if (inverted) ink.copy(alpha = 0.72f) else c.textSecondary, maxLines = 2)
        }
        if (trailing != null) Box(Modifier.padding(end = Gutter).align(Alignment.CenterVertically)) { trailing() }
    }
}

/** 숫자 요약 한 칸. 여러 칸을 세로 구분선으로 잇는다. */
@Composable
fun RowScope.Stat(label: String, value: String) {
    Column(Modifier.weight(1f).padding(horizontal = Gutter, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = EchoType.caption, color = Echo.colors.textSecondary, maxLines = 1)
        Text(value, style = EchoType.number, color = Echo.colors.text, maxLines = 1)
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
            Column(Modifier.widthIn(max = 880.dp).fillMaxWidth(0.8f).background(Echo.colors.background).clickable(enabled = false) {}) {
                Column(Modifier.padding(horizontal = 48.dp, vertical = 44.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(title, style = EchoType.title, color = Echo.colors.text, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    if (description != null) Text(description, style = EchoType.body, color = Echo.colors.textSecondary,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    content()
                }
                Row(Modifier.fillMaxWidth()) {
                    if (dismissLabel != null) BlockButton(dismissLabel, onDismiss, Modifier.weight(1f))
                    BlockButton(confirmLabel, onConfirm, Modifier.weight(1f), primary = true)
                }
            }
        }
    }
}
