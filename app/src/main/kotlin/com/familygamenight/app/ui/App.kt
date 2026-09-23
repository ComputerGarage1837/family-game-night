package com.familygamenight.app.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.familygamenight.app.ui.screens.ClientLobbyScreen
import com.familygamenight.app.ui.screens.EditProfileScreen
import com.familygamenight.app.ui.screens.HomeScreen
import com.familygamenight.app.ui.screens.JoinScreen
import com.familygamenight.app.ui.screens.PickGameScreen
import com.familygamenight.app.ui.screens.ProfilesScreen
import com.familygamenight.app.ui.screens.RulesScreen
import com.familygamenight.app.ui.screens.SavesScreen
import com.familygamenight.app.ui.screens.SettingsScreen
import com.familygamenight.app.ui.screens.SetupScreen
import com.familygamenight.app.ui.table.GameScreen
import com.familygamenight.app.ui.theme.Castle

@Composable
fun FamilyGameNightApp(vm: AppViewModel = viewModel()) {
    val context = LocalContext.current
    val message by vm.message.collectAsState()
    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            vm.clearMessage()
        }
    }

    val screen = vm.screen
    // The game screen handles back itself (opens its menu).
    BackHandler(enabled = vm.backStack.size > 1 && screen != Screen.Game) {
        if (screen is Screen.Setup) vm.endHosting(save = false)
        if (screen == Screen.ClientLobby) vm.leaveAsClient()
        vm.back()
    }

    // Paint the whole window (including behind the clock and nav buttons) but keep every
    // piece of UI inside the safe area, so nothing hides under the status or navigation bar.
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Castle.Night, androidx.compose.ui.graphics.Color(0xFF22160F), Castle.Night))),
    ) {
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            when (screen) {
                Screen.Home -> HomeScreen(vm)
                Screen.Profiles -> ProfilesScreen(vm)
                is Screen.EditProfile -> EditProfileScreen(vm, screen.id)
                is Screen.PickGame -> PickGameScreen(vm, screen.lan)
                is Screen.Setup -> SetupScreen(vm, screen.gameId, screen.lan)
                Screen.Join -> JoinScreen(vm)
                Screen.ClientLobby -> ClientLobbyScreen(vm)
                Screen.Game -> GameScreen(vm)
                Screen.Saves -> SavesScreen(vm)
                Screen.Settings -> SettingsScreen(vm)
                is Screen.Rules -> RulesScreen(vm, screen.gameId)
            }
        }
    }

    UpdateDialog(vm)
}

@Composable
private fun UpdateDialog(vm: AppViewModel) {
    val show by vm.showUpdateDialog.collectAsState()
    val state by vm.update.collectAsState()
    if (!show) return
    val release = when (val s = state) {
        is UpdateState.Available -> s.release
        is UpdateState.NeedsPermission -> s.release
        is UpdateState.Downloading -> s.release
        else -> return
    }
    AlertDialog(
        onDismissRequest = { vm.dismissUpdate(skipThisVersion = false) },
        title = { Text("Update available") },
        text = {
            Column {
                Text("${release.title} is ready (you have ${com.familygamenight.app.update.Updater.currentVersionName}).")
                if (release.notes.isNotBlank()) Text("\n" + release.notes.take(600))
                if (state is UpdateState.NeedsPermission) {
                    Text("\nAndroid needs permission to install updates from this app. Allow it in the screen that opened, then tap Update again.")
                }
                (state as? UpdateState.Downloading)?.let {
                    LinearProgressIndicator(progress = { it.progress }, modifier = Modifier.fillMaxSize())
                }
            }
        },
        confirmButton = {
            Button(onClick = { vm.startUpdate() }, enabled = state !is UpdateState.Downloading) { Text("Update now") }
        },
        dismissButton = {
            TextButton(onClick = { vm.dismissUpdate(skipThisVersion = true) }) { Text("Not now") }
        },
    )
}
