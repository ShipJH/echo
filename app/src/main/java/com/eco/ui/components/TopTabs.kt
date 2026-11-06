package com.eco.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import com.eco.ui.theme.EchoMuted
import com.eco.ui.theme.EchoText
import com.eco.ui.theme.EchoTrack

@Composable
fun TopTabs(selected: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier.padding(start = 22.dp, end = 22.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf("타임캡슐 만들기", "내 타임캡슐").forEachIndexed { index, label ->
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.weight(1f).heightIn(min = 64.dp).clip(RoundedCornerShape(30.dp))
                    .background(if (selected == index) EchoTrack else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(horizontal = 28.dp, vertical = 12.dp),
            ) {
                Text(text = label, color = if (selected == index) EchoText else EchoMuted, fontSize = 30.sp)
            }
        }
    }
}
