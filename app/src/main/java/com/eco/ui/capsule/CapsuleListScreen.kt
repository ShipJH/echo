package com.eco.ui.capsule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eco.ui.components.EchoHeader
import com.eco.ui.components.TopTabs
import com.eco.ui.theme.EchoAccent
import com.eco.ui.theme.EchoMuted
import com.eco.ui.theme.EchoSurface
import com.eco.ui.theme.EchoText
import java.time.LocalDate

private data class CapsulePreview(val title: String, val date: String, val sealed: Boolean)

@Composable
fun CapsuleListScreen(onCreate: () -> Unit) {
    var filter by remember { mutableIntStateOf(0) }
    val previews = listOf(
        CapsulePreview("미래의 나에게", LocalDate.now().plusYears(3).toString(), true),
        CapsulePreview("오늘의 드라이브", LocalDate.now().minusMonths(2).toString(), false),
    )
    Column(modifier = Modifier.fillMaxSize().background(com.eco.ui.theme.EchoBackground)) {
        EchoHeader()
        TopTabs(selected = 1, onSelect = { if (it == 0) onCreate() })
        Row(
            modifier = Modifier.padding(start = 24.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf("봉인 중", "공개됨").forEachIndexed { index, label ->
                Text(
                    label,
                    color = if (filter == index) EchoText else EchoMuted,
                    fontSize = 28.sp,
                    modifier = Modifier.clip(RoundedCornerShape(24.dp))
                        .background(if (filter == index) EchoSurface else androidx.compose.ui.graphics.Color.Transparent)
                        .clickable { filter = index }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }
        val shown = previews.filter { it.sealed == (filter == 0) }
        if (shown.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(shown) { capsule ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                            .clip(RoundedCornerShape(16.dp)).background(EchoSurface).padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(if (capsule.sealed) "◈" else "▶", color = EchoAccent, fontSize = 30.sp)
                        Column(modifier = Modifier.padding(start = 14.dp)) {
                            Text(capsule.title, color = EchoText, fontSize = 30.sp)
                            Text(if (capsule.sealed) "${capsule.date} 공개 예정" else "${capsule.date} 공개됨", color = EchoMuted, fontSize = 26.sp)
                        }
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(if (filter == 0) "봉인 중인 타임캡슐이 없어요" else "공개된 타임캡슐이 없어요", color = EchoMuted, fontSize = 26.sp)
            }
        }
    }
}
