package com.eco.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
fun VoicePanel(heard: String, examples: String, onStop: () -> Unit, modifier: Modifier = Modifier) {
    val c = Echo.colors
    Column(modifier.fillMaxWidth().background(c.tile)) {
        HLine()
        Row(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(72.dp).background(c.accent), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Mic, null, tint = c.background, modifier = Modifier.size(44.dp))
            }
            Column(Modifier.weight(1f).padding(horizontal = 24.dp)) {
                Text(if (heard.isBlank()) "듣고 있어요" else "“$heard”", style = EchoType.title, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(examples, style = EchoType.body, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            SmallButton("그만", onStop)
        }
    }
}
