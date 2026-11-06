package com.eco.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.eco.ui.capsule.CapsuleListScreen
import com.eco.ui.home.HomeScreen

private enum class EchoDestination { CREATE, CAPSULES }

@Composable
fun EchoApp() {
    var destination by remember { mutableStateOf(EchoDestination.CREATE) }
    when (destination) {
        EchoDestination.CREATE -> HomeScreen(onMyCapsules = { destination = EchoDestination.CAPSULES })
        EchoDestination.CAPSULES -> CapsuleListScreen(onCreate = { destination = EchoDestination.CREATE })
    }
}
