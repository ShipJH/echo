package com.eco.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.zIndex
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.ui.unit.dp
import com.eco.ui.components.EchoHeader
import com.eco.ui.components.TopTabs
import com.eco.ui.theme.EchoBackground
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import java.time.LocalDate

@Composable
fun HomeScreen(onMyCapsules: () -> Unit) {
    var unlockDate by remember { mutableStateOf(LocalDate.now().plusMonths(6)) }
    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(EchoBackground)) {
        val compact = this.maxHeight < 500.dp
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Top) {
                EchoHeader()
                TopTabs(selected = 0, onSelect = { if (it == 1) onMyCapsules() })
                ScheduleCard(modifier = Modifier.fillMaxWidth().weight(6f), compact = compact, onDateChanged = { unlockDate = it })
                VoicePrompt(
                    modifier = Modifier.fillMaxWidth().weight(4f),
                    compact = compact,
                    unlockDate = unlockDate,
                )
            }
        }
    }
}
