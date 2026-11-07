package com.eco.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eco.ui.theme.EchoAccent
import com.eco.ui.theme.EchoMuted
import com.eco.ui.theme.EchoText

@Composable
fun EchoHeader(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("▥", color = EchoAccent, fontSize = 24.sp)
            Text("에코", color = EchoText, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        }
        Text("우리들의 타임캡슐", color = EchoMuted, fontSize = 20.sp)
    }
}
