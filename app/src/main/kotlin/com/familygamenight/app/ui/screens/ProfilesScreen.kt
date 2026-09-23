package com.familygamenight.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.familygamenight.app.data.ProfileStore
import com.familygamenight.app.ui.AppViewModel
import com.familygamenight.app.ui.Avatar
import com.familygamenight.app.ui.Panel
import com.familygamenight.app.ui.Screen
import com.familygamenight.app.ui.ScreenHeader
import com.familygamenight.app.ui.theme.Castle

@Composable
fun ProfilesScreen(vm: AppViewModel) {
    val profiles by vm.profiles.collectAsState()
    val currentId by vm.currentProfileId.collectAsState()
    val avatars by vm.avatars.collectAsState()
    var confirmDelete by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Users", onBack = { vm.back() }) {
            Button(onClick = { vm.go(Screen.EditProfile(null)) }) { Text("+ New user") }
        }
        Text(
            "Tap someone to play as them on this device.",
            modifier = Modifier.padding(horizontal = 20.dp),
            color = Castle.Parchment.copy(alpha = 0.75f),
        )
        LazyColumn(
            Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(profiles, key = { it.id }) { p ->
                val selected = p.id == currentId
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.large)
                        .background(if (selected) Castle.Velvet.copy(alpha = 0.55f) else MaterialTheme.colorScheme.surfaceContainer)
                        .border(1.dp, if (selected) Castle.Gold else Castle.Gold.copy(alpha = 0.25f), MaterialTheme.shapes.large)
                        .clickable { vm.switchUser(p.id); vm.back() }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(p.name, avatars[p.id], Color(p.color), 56.dp)
                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                        Text(p.name, style = MaterialTheme.typography.titleLarge)
                        if (selected) Text("Playing now", color = Castle.GoldPale)
                    }
                    TextButton(onClick = { vm.go(Screen.EditProfile(p.id)) }) { Text("Edit") }
                    TextButton(onClick = { confirmDelete = p.id }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }

    confirmDelete?.let { id ->
        val name = profiles.firstOrNull { it.id == id }?.name ?: ""
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete $name?") },
            text = { Text("Their picture will be removed from this device too.") },
            confirmButton = { TextButton(onClick = { vm.deleteProfile(id); confirmDelete = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditProfileScreen(vm: AppViewModel, id: String?) {
    val profiles by vm.profiles.collectAsState()
    val avatars by vm.avatars.collectAsState()
    val existing = profiles.firstOrNull { it.id == id }
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var color by remember { mutableStateOf(existing?.color ?: ProfileStore.palette[profiles.size % ProfileStore.palette.size]) }
    var picked by remember { mutableStateOf<Uri?>(null) }
    var removed by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val preview: ImageBitmap? = remember(picked) {
        picked?.let { uri ->
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { s ->
                    val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = 4 }
                    android.graphics.BitmapFactory.decodeStream(s, null, opts)?.asImageBitmap()
                }
            }.getOrNull()
        }
    }
    val shown = when {
        preview != null -> preview
        removed -> null
        else -> existing?.let { avatars[it.id] }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            picked = uri
            removed = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(if (existing == null) "New user" else "Edit ${existing.name}", onBack = if (profiles.isEmpty()) null else ({ vm.back() }))
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 520.dp).verticalScroll(rememberScrollState()).padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (profiles.isEmpty()) {
                    Text("Welcome! Create a user for whoever is holding this device.", color = Castle.GoldPale)
                }
                Avatar(name.ifBlank { "?" }, shown, Color(color), 120.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) { Text(if (shown != null) "Change picture" else "Add a picture") }
                    if (shown != null) {
                        TextButton(onClick = { picked = null; removed = true }) { Text("Remove") }
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 16) name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Panel {
                    Text("Colour (used when there's no picture)")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        ProfileStore.palette.forEach { c ->
                            Box(
                                Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color(c))
                                    .border(if (c == color) 3.dp else 1.dp, if (c == color) Castle.Gold else Color.Black, CircleShape)
                                    .clickable { color = c },
                            )
                        }
                    }
                }
                Button(
                    onClick = { vm.saveProfile(existing?.id, name, color, picked, removed && picked == null) },
                    enabled = name.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Save") }
            }
        }
    }
}
