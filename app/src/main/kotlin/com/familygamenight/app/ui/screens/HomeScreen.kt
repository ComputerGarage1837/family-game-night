package com.familygamenight.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.familygamenight.app.ui.AppViewModel
import com.familygamenight.app.ui.Avatar
import com.familygamenight.app.ui.Screen
import com.familygamenight.app.ui.UpdateState
import com.familygamenight.app.ui.theme.Castle

@Composable
fun HomeScreen(vm: AppViewModel) {
    val profiles by vm.profiles.collectAsState()
    val currentId by vm.currentProfileId.collectAsState()
    val avatars by vm.avatars.collectAsState()
    val update by vm.update.collectAsState()
    val me = profiles.firstOrNull { it.id == currentId }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 520.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Who's playing on this device
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (me != null) {
                    Avatar(me.name, avatars[me.id], Color(me.color), 52.dp)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text("Playing as", style = MaterialTheme.typography.labelMedium, color = Castle.Parchment.copy(alpha = 0.7f))
                        Text(me.name, style = MaterialTheme.typography.titleLarge)
                    }
                } else {
                    Text("No user yet", modifier = Modifier.weight(1f))
                }
                TextButton(onClick = { vm.go(Screen.Profiles) }) { Text(if (profiles.size > 1) "Switch / manage users" else "Users") }
            }

            Spacer(Modifier.height(12.dp))
            Text("⚜", fontSize = 40.sp, color = Castle.Gold)
            Text("Family Game Night", style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center)
            Text("Pull up a chair at the round table", color = Castle.Parchment.copy(alpha = 0.75f))
            Spacer(Modifier.height(12.dp))

            val big = Modifier.fillMaxWidth().height(56.dp)
            Button(onClick = { vm.go(Screen.PickGame(lan = false)) }, modifier = big, enabled = me != null) {
                Text("Play on this device", fontSize = 18.sp)
            }
            Button(
                onClick = { vm.go(Screen.PickGame(lan = true)) },
                modifier = big,
                enabled = me != null,
                colors = ButtonDefaults.buttonColors(containerColor = Castle.Velvet, contentColor = Color.White),
            ) { Text("Host a Wi-Fi game", fontSize = 18.sp) }
            OutlinedButton(onClick = { vm.go(Screen.Join) }, modifier = big, enabled = me != null) {
                Text("Join a Wi-Fi game", fontSize = 18.sp)
            }
            OutlinedButton(onClick = { vm.refreshSaves(); vm.go(Screen.Saves) }, modifier = big) {
                Text("Saved games", fontSize = 18.sp)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { vm.go(Screen.Settings) }, modifier = Modifier.weight(1f)) { Text("Settings") }
                Button(
                    onClick = { vm.checkForUpdates(manual = true) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (update is UpdateState.Available) Castle.Gold else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (update is UpdateState.Available) Castle.Ink else Castle.Parchment,
                    ),
                ) {
                    Text(
                        when (update) {
                            is UpdateState.Checking -> "Checking…"
                            is UpdateState.Available -> "Update ready!"
                            is UpdateState.Downloading -> "Downloading…"
                            is UpdateState.UpToDate -> "Up to date ✓"
                            else -> "Check for updates"
                        },
                    )
                }
            }
            (update as? UpdateState.Failed)?.let { Text(it.message, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
        }
    }
}
