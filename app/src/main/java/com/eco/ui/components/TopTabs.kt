package com.eco.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import com.eco.ui.theme.EchoMuted
import com.eco.ui.theme.EchoText
import com.eco.ui.theme.EchoTrack

@Composable
fun TopTabs(selected: Int, onSelect: (Int) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, bottom = 12.dp)) {
    val tabWidth = ((maxWidth - 24.dp) / 3).coerceAtLeast(180.dp)
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf("타임캡슐 만들기", "내 타임캡슐", "STT 테스트").forEachIndexed { index, label ->
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.width(tabWidth).heightIn(min = 64.dp).clip(RoundedCornerShape(30.dp))
                    .background(if (selected == index) EchoTrack else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(horizontal = 8.dp, vertical = 12.dp),
            ) {
                Text(text = label, color = if (selected == index) EchoText else EchoMuted, fontSize = 28.sp,
                    lineHeight = 36.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.Medium)
            }
        }
    }
    }
}
