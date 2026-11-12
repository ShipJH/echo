package com.eco.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.eco.ui.theme.Echo
import com.eco.ui.theme.EchoType

/** 화면 머리 오른쪽의 '말로 하기'. 아이콘에는 항상 글자를 붙인다(디자인 가이드). */
@Composable
fun VoiceButton(onClick: () -> Unit, enabled: Boolean = true) {
    val c = Echo.colors
    Row(
        Modifier.heightIn(min = TouchMin).widthIn(min = 200.dp).background(c.tile)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = 28.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Outlined.Mic, null, tint = if (enabled) c.text else c.hint, modifier = Modifier.size(40.dp))
        Text("말로 하기", style = EchoType.label, color = if (enabled) c.text else c.hint, maxLines = 1)
    }
}

/**
 * 듣는 동안 아래에 붙는 띠. 들리는 말이 있으면 그 말을, 없으면 이 화면에서 할 수 있는 말의 예를 보인다.
 * 반전하지 않고 surface_high 를 쓴다(Night 에서 넓은 밝은 면은 눈부시다).
 */
@Composable
fun VoicePanel(heard: String, examples: String, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    val c = Echo.colors
    // 띠가 터치를 받지 않으면 '취소' 를 빗나간 손가락이 띠 밑의 조건 행·목록 줄을 눌렀다(처음엔 잠그기 버튼까지 가렸다).
    // 잠그기 화면에서는 띠를 버튼 줄 위에 띄워(EchoApp) 버튼은 늘 보인다.
    Column(modifier.fillMaxWidth().background(c.tile).pointerInput(Unit) { detectTapGestures { } }) {
        HLine()
        Row(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(72.dp).background(c.accent), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Mic, null, tint = c.background, modifier = Modifier.size(44.dp))
            }
            Column(Modifier.weight(1f).padding(horizontal = 24.dp)) {
                Text(if (heard.isBlank()) "듣고 있어요" else "“$heard”", style = EchoType.title, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(examples, style = EchoType.body, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            // '그만' 은 듣던 말을 실행하기도 해 이름과 동작이 어긋났다. 결과 없이 끝내는 '취소' 로 둔다.
            // 띠와 같은 tile 바탕이면 글자만 떠 보여 바탕색과 테두리로 버튼임을 보인다.
            Box(
                Modifier.heightIn(min = TouchMin).widthIn(min = 140.dp).background(c.background).border(1.dp, c.controlLine)
                    .clickable(role = Role.Button, onClick = onCancel).padding(horizontal = 28.dp),
                contentAlignment = Alignment.Center,
            ) { Text("취소", style = EchoType.label, color = c.text, maxLines = 1) }
        }
    }
}
