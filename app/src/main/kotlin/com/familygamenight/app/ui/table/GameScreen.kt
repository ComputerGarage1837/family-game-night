package com.familygamenight.app.ui.table

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.familygamenight.app.ui.AppViewModel
import com.familygamenight.app.ui.Avatar
import com.familygamenight.app.ui.Screen
import com.familygamenight.app.ui.TableModel
import com.familygamenight.app.ui.colorFor
import com.familygamenight.app.ui.theme.Castle
import com.familygamenight.core.game.Difficulty
import com.familygamenight.core.net.LanClient
import com.familygamenight.core.session.Seat

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun GameScreen(vm: AppViewModel) {
    val table by vm.table.collectAsState()
    val avatars by vm.avatars.collectAsState()
    val profiles by vm.profiles.collectAsState()
    val client by vm.client.collectAsState()
    val handoff by vm.handoff.collectAsState()
    val waitingFor by vm.waitingFor.collectAsState()
    var menuOpen by remember { mutableStateOf(false) }

    // A round table wants a wide screen; keep it awake while playing.
    val activity = LocalContext.current.findActivity()
    val view = LocalView.current
    DisposableEffect(Unit) {
        val before = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        view.keepScreenOn = true
        onDispose {
            activity?.requestedOrientation = before
            view.keepScreenOn = false
        }
    }
    BackHandler { menuOpen = true }

    val model = table
    if (model == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Setting the table…", color = Castle.GoldPale)
                TextButton(onClick = { vm.quitGame(save = false) }) { Text("Back to menu") }
            }
        }
        return
    }
    val colorOf: (String) -> Color = { id -> profiles.firstOrNull { it.id == id }?.let { Color(it.color) } ?: colorFor(id) }

    Box(Modifier.fillMaxSize()) {
        when (model.module.info.id) {
            "go_fish" -> GoFishTable(
                model = model,
                avatars = avatars,
                colorOf = colorOf,
                onAction = { vm.act(it) },
                onPlayAgain = if (model.isHost) ({ vm.rematch() }) else null,
                onHome = { vm.quitGame(save = false) },
            )
        }

        FilledTonalButton(
            onClick = { menuOpen = true },
            modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
        ) { Text("☰", fontSize = 18.sp) }

        // Status strip for joiners and for a host who chose to wait.
        val status = client?.status?.collectAsState()?.value
        val strip: String? = when {
            status is LanClient.Status.Reconnecting -> "Lost the connection to the host – reconnecting…"
            !model.isHost && model.missing.isNotEmpty() ->
                "Paused – ${model.missing.joinToString { it.name }} dropped out. Waiting for the host…"
            model.isHost && model.missing.isNotEmpty() ->
                "Paused – waiting for ${model.missing.joinToString { it.name }} to reconnect"
            else -> null
        }
        if (strip != null) {
            Row(
                Modifier.align(Alignment.Center)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xE6231913))
                    .border(1.dp, Castle.Gold, RoundedCornerShape(14.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(strip, color = Castle.Parchment)
                if (model.isHost && model.missing.isNotEmpty()) {
                    TextButton(onClick = { vm.reopenPauseOptions() }) { Text("Options") }
                }
            }
        }

        // Someone left: the host decides what happens.
        if (model.isHost && model.missing.isNotEmpty() && !waitingFor.containsAll(model.missing.map { it.profileId })) {
            PlayerLeftDialog(model, vm)
        }

        if (status is LanClient.Status.Closed) {
            AlertDialog(
                onDismissRequest = {},
                title = { Text("Game over") },
                text = { Text(status.reason) },
                confirmButton = { Button(onClick = { vm.quitGame(save = false) }) { Text("Back to menu") } },
            )
        }

        handoff?.let { seat -> PassDeviceCover(seat, avatars[seat.profileId], colorOf(seat.profileId)) { vm.acceptHandoff() } }
    }

    if (menuOpen) {
        GameMenu(model, vm, onClose = { menuOpen = false })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlayerLeftDialog(model: TableModel, vm: AppViewModel) {
    val skill = model.module.info.aiHasSkillLevels
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Game paused") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                model.missing.forEach { seat: Seat ->
                    Column {
                        Text(
                            if (seat.leftOnPurpose) "${seat.name} left the game." else "${seat.name} lost their connection.",
                            fontWeight = FontWeight.Bold,
                        )
                        Text("Replace them with a computer player:", fontSize = 13.sp)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (skill) {
                                Difficulty.entries.forEach { d ->
                                    OutlinedButton(onClick = { vm.replaceWithAi(seat, d) }) { Text(d.label) }
                                }
                            } else {
                                OutlinedButton(onClick = { vm.replaceWithAi(seat, Difficulty.MEDIUM) }) { Text("Computer") }
                            }
                        }
                    }
                }
                Text("…or wait for them to rejoin (they just join again from their device), or save now and carry on another time.", fontSize = 13.sp)
            }
        },
        confirmButton = {
            Button(onClick = { vm.waitForReconnect(model.missing) }) { Text("Wait for reconnect") }
        },
        dismissButton = {
            TextButton(onClick = { vm.quitGame(save = true) }) { Text("Save & quit") }
        },
    )
}

@Composable
private fun PassDeviceCover(seat: Seat, avatar: androidx.compose.ui.graphics.ImageBitmap?, color: Color, onReady: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Castle.Night),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Pass the device to", color = Castle.Parchment, fontSize = 18.sp)
            Avatar(seat.name, avatar, color, 96.dp)
            Text(seat.name, style = MaterialTheme.typography.headlineMedium, color = Castle.GoldPale)
            Text("No peeking, everyone else!", color = Castle.Parchment.copy(alpha = 0.7f))
            Button(onClick = onReady) { Text("I'm ${seat.name} – show my cards") }
        }
    }
}

@Composable
private fun GameMenu(model: TableModel, vm: AppViewModel, onClose: () -> Unit) {
    var confirmQuit by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(model.module.info.name) },
        text = {
            Column(Modifier.widthIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val wide = Modifier.fillMaxWidth()
                Button(onClick = onClose, modifier = wide) { Text("Back to the game") }
                OutlinedButton(onClick = { onClose(); vm.go(Screen.Rules(model.module.info.id)) }, modifier = wide) { Text("How to play") }
                if (model.isHost) {
                    OutlinedButton(onClick = { vm.saveGame(); onClose() }, modifier = wide) { Text("Save game") }
                    OutlinedButton(onClick = { vm.quitGame(save = true) }, modifier = wide) { Text("Save & quit") }
                    TextButton(onClick = { confirmQuit = true }, modifier = wide) {
                        Text("Quit without saving", color = MaterialTheme.colorScheme.error)
                    }
                } else {
                    Text("Only the host can save – the game lives on their device.", fontSize = 12.sp, textAlign = TextAlign.Center)
                    TextButton(onClick = { confirmQuit = true }, modifier = wide) {
                        Text("Leave game", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {},
    )
    if (confirmQuit) {
        AlertDialog(
            onDismissRequest = { confirmQuit = false },
            title = { Text(if (model.isHost) "Quit without saving?" else "Leave the game?") },
            text = {
                Text(
                    if (model.isHost) "This game will be lost." + if (model.lan) " Everyone else will be sent back to their menu." else ""
                    else "The host can replace you with a computer player or wait for you to come back.",
                )
            },
            confirmButton = {
                Button(
                    onClick = { vm.quitGame(save = false) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text(if (model.isHost) "Quit" else "Leave") }
            },
            dismissButton = { TextButton(onClick = { confirmQuit = false }) { Text("Stay") } },
        )
    }
}
