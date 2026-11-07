package com.eco.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
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
fun HomeScreen(onMyCapsules: () -> Unit, onSttTest: () -> Unit) {
    var unlockDate by remember { mutableStateOf(LocalDate.now().plusMonths(6)) }
    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(EchoBackground)) {
        val compact = maxHeight < 700.dp || maxWidth < 700.dp
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Top) {
            EchoHeader()
            TopTabs(selected = 0, onSelect = { if (it == 1) onMyCapsules() else if (it == 2) onSttTest() })
            BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f)) {
                val contentHeight = maxHeight
                Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                    ScheduleCard(
                        modifier = Modifier.fillMaxWidth().heightIn(min = contentHeight * 0.6f),
                        compact = compact,
                        onDateChanged = { unlockDate = it },
                    )
                    VoicePrompt(
                        modifier = Modifier.fillMaxWidth().heightIn(min = contentHeight * 0.4f),
                        compact = compact,
                        unlockDate = unlockDate,
                    )
                }
            }
        }
    }
}
