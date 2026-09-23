package com.familygamenight.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.familygamenight.app.net.LanDiscovery
import com.familygamenight.app.ui.AppViewModel
import com.familygamenight.app.ui.Avatar
import com.familygamenight.app.ui.Panel
import com.familygamenight.app.ui.Screen
import com.familygamenight.app.ui.ScreenHeader
import com.familygamenight.app.ui.SectionTitle
import com.familygamenight.app.ui.colorFor
import com.familygamenight.app.ui.theme.Castle
import com.familygamenight.core.game.Difficulty
import com.familygamenight.core.game.GameInfo
import com.familygamenight.core.game.Games
import com.familygamenight.core.session.SeatKind

@Composable
fun PickGameScreen(vm: AppViewModel, lan: Boolean) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(if (lan) "Host a Wi-Fi game" else "Choose a game", onBack = { vm.back() })
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Games.all.forEach { m ->
                val info = m.info
                Panel(Modifier.clickable {
                    vm.startHosting(info.id, lan)
                    vm.go(Screen.Setup(info.id, lan))
                }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🐟", fontSize = 40.sp)
                        Column(Modifier.weight(1f).padding(start = 14.dp)) {
                            Text(info.name, style = MaterialTheme.typography.headlineSmall, color = Castle.GoldPale)
                            Text(info.tagline)
                            Text("${info.minPlayers}–${info.maxPlayers} players", color = Castle.Parchment.copy(alpha = 0.7f), fontSize = 13.sp)
                        }
                        TextButton(onClick = { vm.go(Screen.Rules(info.id)) }) { Text("Rules") }
                    }
                }
            }
            Text(
                "More family favourites are on the way.",
                color = Castle.Parchment.copy(alpha = 0.6f),
                modifier = Modifier.padding(8.dp),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SetupScreen(vm: AppViewModel, gameId: String, lan: Boolean) {
    val session by vm.session.collectAsState()
    val s = session ?: return
    val info = s.module.info
    val players by s.players.collectAsState()
    val rules by s.rules.collectAsState()
    val profiles by vm.profiles.collectAsState()
    val avatars by vm.avatars.collectAsState()
    var aiDifficulty by remember { mutableStateOf(Difficulty.MEDIUM) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(info.name, onBack = { vm.endHosting(save = false); vm.back() }) {
            TextButton(onClick = { vm.go(Screen.Rules(gameId)) }) { Text("How to play") }
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 640.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            ) {
                if (lan) {
                    Panel {
                        Text("Waiting for players to join…", style = MaterialTheme.typography.titleMedium, color = Castle.GoldPale)
                        Text("On each other phone (same Wi-Fi) tap “Join a Wi-Fi game”. This game should appear automatically.")
                        val addrs = remember { LanDiscovery.localAddresses() }
                        if (addrs.isNotEmpty()) {
                            val port = vm.hostPort
                            Text(
                                "If it doesn't, type this address: " + addrs.joinToString(" or ") { if (port > 0 && port != com.familygamenight.core.net.DEFAULT_PORT) "$it:$port" else it },
                                color = Castle.Parchment.copy(alpha = 0.8f),
                            )
                        }
                    }
                }

                SectionTitle("Players (${players.size}/${info.maxPlayers})")
                players.forEach { p ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val color = profiles.firstOrNull { it.id == p.profileId }?.let { Color(it.color) } ?: colorFor(p.profileId)
                        Avatar(p.name, avatars[p.profileId], color, 40.dp)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(p.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                when (p.kind) {
                                    SeatKind.LOCAL -> "On this device"
                                    SeatKind.REMOTE -> "Joined over Wi-Fi"
                                    SeatKind.AI -> if (info.aiHasSkillLevels) "Computer · ${p.difficulty?.label ?: "Normal"}" else "Computer"
                                },
                                fontSize = 13.sp,
                                color = Castle.Parchment.copy(alpha = 0.7f),
                            )
                        }
                        if (p.kind == SeatKind.AI && info.aiHasSkillLevels) {
                            TextButton(onClick = {
                                val next = Difficulty.entries[((p.difficulty ?: Difficulty.MEDIUM).ordinal + 1) % Difficulty.entries.size]
                                s.setAiDifficulty(p.profileId, next)
                            }) { Text("Level") }
                        }
                        if (p.profileId != vm.currentProfileId.value) {
                            TextButton(onClick = { s.remove(p.profileId) }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }

                SectionTitle("Add players")
                Panel {
                    Text("Computer player", style = MaterialTheme.typography.titleMedium)
                    if (info.aiHasSkillLevels) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Difficulty.entries.forEach { d ->
                                FilterChip(selected = aiDifficulty == d, onClick = { aiDifficulty = d }, label = { Text(d.label) })
                            }
                        }
                        Text(aiDifficulty.blurb, fontSize = 13.sp, color = Castle.Parchment.copy(alpha = 0.75f))
                    } else {
                        Text("This is a game of chance, so the computer just plays fair – no difficulty levels.", fontSize = 13.sp)
                    }
                    Button(
                        onClick = { s.addAi(if (info.aiHasSkillLevels) aiDifficulty else null) },
                        enabled = players.size < info.maxPlayers,
                    ) { Text("+ Add computer player") }
                }
                Spacer(Modifier.height(10.dp))
                val others = profiles.filter { pr -> players.none { it.profileId == pr.profileId() } }
                if (others.isNotEmpty()) {
                    Panel {
                        Text("Someone else on this device", style = MaterialTheme.typography.titleMedium)
                        if (info.hiddenHands) {
                            Text(
                                "Pass-and-play: the screen is covered between turns so nobody peeks. Hand the phone over when asked.",
                                fontSize = 13.sp, color = Castle.Parchment.copy(alpha = 0.75f),
                            )
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            others.forEach { pr ->
                                OutlinedButton(onClick = { vm.addLocalPlayer(pr) }, enabled = players.size < info.maxPlayers) {
                                    Text("+ ${pr.name}")
                                }
                            }
                        }
                    }
                }

                if (info.optionalRules.isNotEmpty()) {
                    SectionTitle("House rules")
                    Panel { RuleSwitches(info, rules) { id, on -> s.setRules(rules + (id to on)) } }
                }

                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { vm.startGame() },
                    enabled = s.canStart() && players.size >= info.minPlayers,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) { Text(if (players.size < info.minPlayers) "Need at least ${info.minPlayers} players" else "Start game", fontSize = 18.sp) }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

private fun com.familygamenight.app.data.Profile.profileId() = id

@Composable
fun RuleSwitches(info: GameInfo, rules: Map<String, Boolean>, onChange: ((String, Boolean) -> Unit)?) {
    info.optionalRules.forEach { r ->
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(r.title, style = MaterialTheme.typography.titleMedium)
                Text(r.description, fontSize = 13.sp, color = Castle.Parchment.copy(alpha = 0.75f))
            }
            Switch(
                checked = rules[r.id] ?: r.defaultOn,
                onCheckedChange = onChange?.let { f -> { on: Boolean -> f(r.id, on) } },
                enabled = onChange != null,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}

@Composable
fun RulesScreen(vm: AppViewModel, gameId: String) {
    val info = Games.byId(gameId).info
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("${info.name} rules", onBack = { vm.back() })
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 640.dp).verticalScroll(rememberScrollState()).padding(16.dp)) {
                Panel {
                    info.howToPlay.forEachIndexed { i, line ->
                        Row {
                            Text("${i + 1}.", color = Castle.Gold, modifier = Modifier.padding(end = 8.dp))
                            Text(line)
                        }
                    }
                }
                if (info.optionalRules.isNotEmpty()) {
                    SectionTitle("Optional house rules")
                    Panel {
                        info.optionalRules.forEach { r ->
                            Text(r.title + if (r.defaultOn) "  (on by default)" else "", style = MaterialTheme.typography.titleMedium, color = Castle.GoldPale)
                            Text(r.description)
                        }
                    }
                }
                if (info.aiHasSkillLevels) {
                    SectionTitle("Computer players")
                    Panel {
                        Text("The computer only knows what a real player at the table would: its own cards and what everyone has said. It never peeks.")
                        Difficulty.entries.forEach { d -> Text("• ${d.label}: ${d.blurb}") }
                    }
                }
            }
        }
    }
}

@Composable
fun JoinScreen(vm: AppViewModel) {
    val found by vm.discovery.found.collectAsState()
    var address by remember { mutableStateOf(vm.settings.lastJoinAddress) }
    androidx.compose.runtime.DisposableEffect(Unit) {
        vm.discovery.startSearching()
        onDispose { vm.discovery.stopSearching() }
    }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Join a Wi-Fi game", onBack = { vm.back() })
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 560.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Games on this Wi-Fi", style = MaterialTheme.typography.titleMedium, color = Castle.Gold)
                if (found.isEmpty()) Text("Looking… make sure the host has started a Wi-Fi game.", color = Castle.Parchment.copy(alpha = 0.7f))
                found.forEach { g ->
                    Row(
                        Modifier.fillMaxWidth()
                            .clip(MaterialTheme.shapes.large)
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .border(1.dp, Castle.Gold.copy(alpha = 0.4f), MaterialTheme.shapes.large)
                            .clickable { vm.join(g.host, g.port) }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("🏰", fontSize = 28.sp)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(g.name, style = MaterialTheme.typography.titleMedium)
                            Text(g.host, fontSize = 12.sp, color = Castle.Parchment.copy(alpha = 0.6f))
                        }
                        Text("Join ›", color = Castle.Gold)
                    }
                }
                SectionTitle("Or type the host's address")
                androidx.compose.material3.OutlinedTextField(
                    value = address,
                    onValueChange = { address = it.trim() },
                    label = { Text("e.g. 192.168.1.20") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = {
                        val host = address.substringBefore(':')
                        val port = address.substringAfter(':', "").toIntOrNull() ?: com.familygamenight.core.net.DEFAULT_PORT
                        vm.join(host, port)
                    },
                    enabled = address.isNotBlank(),
                ) { Text("Join") }
            }
        }
    }
}

@Composable
fun ClientLobbyScreen(vm: AppViewModel) {
    val client by vm.client.collectAsState()
    val c = client ?: return
    val status by c.status.collectAsState()
    val lobby by c.lobby.collectAsState()
    val hostName by c.hostName.collectAsState()
    val avatars by vm.avatars.collectAsState()

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Waiting at the table", onBack = { vm.leaveAsClient(); vm.back() })
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 560.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                when (val st = status) {
                    is com.familygamenight.core.net.LanClient.Status.Connecting -> Text("Connecting to ${c.host}…")
                    is com.familygamenight.core.net.LanClient.Status.Reconnecting -> Text("Can't reach the host – retrying (attempt ${st.attempt})…", color = MaterialTheme.colorScheme.error)
                    is com.familygamenight.core.net.LanClient.Status.Closed -> {
                        Text(st.reason, color = MaterialTheme.colorScheme.error)
                        Button(onClick = { vm.leaveAsClient(); vm.back() }) { Text("Back") }
                    }
                    is com.familygamenight.core.net.LanClient.Status.Connected ->
                        Text("Connected to ${hostName ?: "host"}. Waiting for them to start…", color = Castle.GoldPale)
                }
                lobby?.let { l ->
                    val info = Games.byId(l.gameId).info
                    Text(info.name, style = MaterialTheme.typography.headlineSmall, color = Castle.Gold)
                    l.players.forEach { p ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Avatar(p.name, avatars[p.profileId], colorFor(p.profileId), 40.dp)
                            Text(
                                p.name + if (p.kind == SeatKind.AI) "  (computer${p.difficulty?.let { " · " + it.label } ?: ""})" else "",
                                modifier = Modifier.padding(start = 12.dp),
                            )
                        }
                    }
                    SectionTitle("House rules")
                    Panel { RuleSwitches(info, l.rules, null) }
                }
            }
        }
    }
}

@Composable
fun SavesScreen(vm: AppViewModel) {
    val saves by vm.saves.collectAsState()
    LaunchedEffect(Unit) { vm.refreshSaves() }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Saved games", onBack = { vm.back() })
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (saves.isEmpty()) Text("No saved games yet. The host can save from the game menu (☰).")
            saves.forEach { sv ->
                Panel {
                    Text(sv.title, style = MaterialTheme.typography.titleMedium, color = Castle.GoldPale)
                    val date = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT)
                        .format(java.util.Date(sv.savedAtMillis))
                    Text("$date · ${if (sv.lan) "Wi-Fi game" else "This device"}", fontSize = 13.sp)
                    if (sv.lan) {
                        val remote = sv.seats.filter { it.kind == SeatKind.REMOTE }.joinToString { it.name }
                        if (remote.isNotEmpty()) Text("$remote will need to join again (or be replaced by the computer).", fontSize = 13.sp)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { vm.resume(sv) }) { Text("Resume") }
                        TextButton(onClick = { vm.deleteSave(sv.id) }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(vm: AppViewModel) {
    var auto by remember { mutableStateOf(vm.settings.autoCheckUpdates) }
    val update by vm.update.collectAsState()
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Settings", onBack = { vm.back() })
        Column(Modifier.widthIn(max = 560.dp).verticalScroll(rememberScrollState()).padding(16.dp)) {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Check for updates automatically", style = MaterialTheme.typography.titleMedium)
                        Text("Looks for a new version on GitHub each time the app opens.", fontSize = 13.sp)
                    }
                    Switch(checked = auto, onCheckedChange = { auto = it; vm.settings.autoCheckUpdates = it })
                }
                Text("Version ${com.familygamenight.app.update.Updater.currentVersionName}", color = Castle.Parchment.copy(alpha = 0.7f))
                Button(onClick = { vm.checkForUpdates(manual = true) }) { Text("Check for updates now") }
                when (val u = update) {
                    is com.familygamenight.app.ui.UpdateState.UpToDate -> Text("You have the latest version ✓", color = Castle.GoldPale)
                    is com.familygamenight.app.ui.UpdateState.Failed -> Text(u.message, color = MaterialTheme.colorScheme.error)
                    is com.familygamenight.app.ui.UpdateState.Downloading -> Text("Downloading… ${(u.progress * 100).toInt()}%")
                    else -> Unit
                }
            }
        }
    }
}
