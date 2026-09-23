package com.familygamenight.app.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.familygamenight.app.data.AiSpeed
import com.familygamenight.app.data.Profile
import com.familygamenight.app.data.ProfileStore
import com.familygamenight.app.data.SaveStore
import com.familygamenight.app.data.Settings
import com.familygamenight.app.net.LanDiscovery
import com.familygamenight.app.update.Updater
import com.familygamenight.core.game.Difficulty
import com.familygamenight.core.game.GameModule
import com.familygamenight.core.game.Games
import com.familygamenight.core.net.DEFAULT_PORT
import com.familygamenight.core.net.LanClient
import com.familygamenight.core.net.NetMessage
import com.familygamenight.core.net.PROTOCOL_VERSION
import com.familygamenight.core.session.GameHost
import com.familygamenight.core.session.HostSession
import com.familygamenight.core.session.SavedGame
import com.familygamenight.core.session.Seat
import com.familygamenight.core.session.SeatKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import java.io.File
import java.util.UUID

sealed interface Screen {
    data object Home : Screen
    data object Profiles : Screen
    data class EditProfile(val id: String?) : Screen
    data class PickGame(val lan: Boolean) : Screen
    data class Setup(val gameId: String, val lan: Boolean) : Screen
    data object Join : Screen
    data object ClientLobby : Screen
    data object Game : Screen
    data object Saves : Screen
    data object Settings : Screen
    data class Rules(val gameId: String) : Screen
}

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: Updater.Release) : UpdateState
    data class Downloading(val release: Updater.Release, val progress: Float) : UpdateState
    data class NeedsPermission(val release: Updater.Release) : UpdateState
    data class Failed(val message: String) : UpdateState
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val profileStore = ProfileStore(app)
    private val saveStore = SaveStore(app)
    val settings = Settings(app)
    val discovery = LanDiscovery(app)

    // ------------------------------------------------------------------ navigation
    val backStack = mutableStateListOf<Screen>(Screen.Home)
    val screen: Screen get() = backStack.last()

    fun go(s: Screen) {
        backStack.add(s)
    }

    fun back() {
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }

    fun home() {
        backStack.clear()
        backStack.add(Screen.Home)
    }

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    fun toast(m: String) { _message.value = m }
    fun clearMessage() { _message.value = null }

    // ------------------------------------------------------------------ profiles
    private val _profiles = MutableStateFlow(profileStore.load())
    val profiles: StateFlow<List<Profile>> = _profiles.asStateFlow()

    private val _currentProfileId = MutableStateFlow(settings.currentProfileId)
    val currentProfileId: StateFlow<String?> = _currentProfileId.asStateFlow()

    val currentProfile: Profile? get() = _profiles.value.firstOrNull { it.id == _currentProfileId.value }

    private val _localAvatars = MutableStateFlow<Map<String, ImageBitmap>>(emptyMap())
    private val _netAvatars = MutableStateFlow<Map<String, ImageBitmap>>(emptyMap())
    private val _avatars = MutableStateFlow<Map<String, ImageBitmap>>(emptyMap())
    /** profileId -> picture, for everyone we know about (local users and LAN players). */
    val avatars: StateFlow<Map<String, ImageBitmap>> = _avatars.asStateFlow()

    private fun reloadLocalAvatars() {
        viewModelScope.launch {
            val map = withContext(Dispatchers.IO) {
                _profiles.value.filter { it.hasAvatar }
                    .mapNotNull { p -> profileStore.loadAvatar(p.id)?.let { p.id to it.asImageBitmap() } }
                    .toMap()
            }
            _localAvatars.value = map
            mergeAvatars()
        }
    }

    private fun mergeAvatars() {
        _avatars.value = _netAvatars.value + _localAvatars.value
    }

    private val decodedNet = HashMap<String, Pair<String, ImageBitmap>>()
    private fun absorbNetworkAvatars(map: Map<String, String>) {
        var changed = false
        for ((id, b64) in map) {
            if (decodedNet[id]?.first == b64) continue
            ProfileStore.decodeNetworkAvatar(b64)?.let {
                decodedNet[id] = b64 to it.asImageBitmap()
                changed = true
            }
        }
        if (changed) {
            _netAvatars.value = decodedNet.mapValues { it.value.second }
            mergeAvatars()
        }
    }

    fun switchUser(id: String?) {
        _currentProfileId.value = id
        settings.currentProfileId = id
    }

    fun saveProfile(id: String?, name: String, color: Long, newAvatar: Uri?, removeAvatar: Boolean) {
        viewModelScope.launch {
            val existing = _profiles.value.firstOrNull { it.id == id }
            var p = existing?.copy(name = name.trim(), color = color) ?: profileStore.newProfile(name, color)
            withContext(Dispatchers.IO) {
                if (removeAvatar) {
                    profileStore.deleteAvatar(p.id)
                    p = p.copy(hasAvatar = false, avatarVersion = p.avatarVersion + 1)
                }
                if (newAvatar != null) {
                    if (profileStore.importAvatar(p.id, newAvatar)) {
                        p = p.copy(hasAvatar = true, avatarVersion = p.avatarVersion + 1)
                    } else {
                        _message.value = "Couldn't use that picture"
                    }
                }
            }
            val list = if (existing == null) _profiles.value + p else _profiles.value.map { if (it.id == p.id) p else it }
            _profiles.value = list
            withContext(Dispatchers.IO) { profileStore.save(list) }
            if (currentProfile == null) switchUser(p.id)
            reloadLocalAvatars()
            back()
        }
    }

    fun deleteProfile(id: String) {
        val list = _profiles.value.filterNot { it.id == id }
        _profiles.value = list
        viewModelScope.launch(Dispatchers.IO) {
            profileStore.deleteAvatar(id)
            profileStore.save(list)
        }
        if (_currentProfileId.value == id) switchUser(list.firstOrNull()?.id)
        reloadLocalAvatars()
    }

    // ------------------------------------------------------------------ hosting
    private var sessionScope: CoroutineScope? = null
    private val _session = MutableStateFlow<HostSession?>(null)
    val session: StateFlow<HostSession?> = _session.asStateFlow()
    private var sessionJobs = mutableListOf<Job>()
    private var currentSaveId: String? = null

    val hostPort: Int get() = _session.value?.port ?: -1

    private val _aiSpeed = MutableStateFlow(settings.aiSpeed)
    val aiSpeed: StateFlow<AiSpeed> = _aiSpeed.asStateFlow()

    fun setAiSpeed(speed: AiSpeed) {
        settings.aiSpeed = speed
        _aiSpeed.value = speed
        _session.value?.aiDelayMs = speed.delayMs
    }

    // Crash recovery: the host's game is quietly saved once per round.
    private var autoSaveId: String? = null
    private var lastTurnOwner: Int? = null
    private var turnsSinceAutoSave = 0

    private fun resetAutoSave(existing: String? = null) {
        autoSaveId = existing
        lastTurnOwner = null
        turnsSinceAutoSave = 0
    }

    private fun autoSave(g: GameHost) {
        val id = autoSaveId ?: "auto-${UUID.randomUUID()}".also { autoSaveId = it }
        val names = g.snapshot.value.seats.joinToString(", ") { it.name }
        val save = g.toSave(id, "${g.module.info.name} – $names", System.currentTimeMillis(), auto = true)
        viewModelScope.launch(Dispatchers.IO) { saveStore.save(save) }
    }

    private fun deleteAutoSave() {
        autoSaveId?.let { id -> viewModelScope.launch(Dispatchers.IO) { saveStore.delete(id) } }
        autoSaveId = null
    }

    fun startHosting(gameId: String, lan: Boolean) {
        val me = currentProfile ?: return toast("Create a user first")
        endHosting(save = false)
        val module = Games.byId(gameId)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        sessionScope = scope
        val s = HostSession(module, module.info.defaultRules(), lan, me.name, scope, aiDelayMs = settings.aiSpeed.delayMs)
        _session.value = s
        viewModelScope.launch {
            val avatar = withContext(Dispatchers.IO) { profileStore.avatarForNetwork(me.id) }
            s.addLocal(me.id, me.name, avatar)
            if (lan) {
                val port = withContext(Dispatchers.IO) { s.startServer(DEFAULT_PORT) }
                discovery.advertise("${me.name}'s ${module.info.name}", port)
            }
        }
        watchSession(s)
    }

    private fun watchSession(s: HostSession) {
        sessionJobs += viewModelScope.launch { s.avatars.collect { absorbNetworkAvatars(it) } }
        sessionJobs += viewModelScope.launch {
            s.game.collectLatest { g -> g?.snapshot?.collect { snap -> onHostSnapshot(s, g, snap) } }
        }
    }

    fun addLocalPlayer(profile: Profile) {
        val s = _session.value ?: return
        viewModelScope.launch {
            val avatar = withContext(Dispatchers.IO) { profileStore.avatarForNetwork(profile.id) }
            s.addLocal(profile.id, profile.name, avatar)
        }
    }

    fun startGame() {
        val s = _session.value ?: return
        if (!s.canStart()) return toast("Need ${s.module.info.minPlayers}–${s.module.info.maxPlayers} players")
        currentSaveId = null
        resetAutoSave()
        resetPassAndPlay()
        s.startGame()
        go(Screen.Game)
    }

    fun rematch() {
        val s = _session.value ?: return
        currentSaveId = null
        resetAutoSave()
        resetPassAndPlay()
        s.rematch()
    }

    fun resume(saved: SavedGame) {
        val me = currentProfile ?: return toast("Create a user first")
        endHosting(save = false)
        val module = Games.byId(saved.gameId)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        sessionScope = scope
        val s = HostSession(module, saved.rules, saved.lan, me.name, scope, aiDelayMs = settings.aiSpeed.delayMs)
        _session.value = s
        currentSaveId = if (saved.auto) null else saved.id
        resetAutoSave(if (saved.auto) saved.id else null)
        resetPassAndPlay()
        watchSession(s)
        viewModelScope.launch {
            if (saved.lan) {
                val port = withContext(Dispatchers.IO) { s.startServer(DEFAULT_PORT) }
                discovery.advertise("${me.name}'s ${module.info.name}", port)
            }
            s.resume(saved)
            backStack.clear()
            backStack.add(Screen.Home)
            go(Screen.Game)
        }
    }

    fun saveGame(): Boolean {
        val g = _session.value?.game?.value ?: return false
        val id = currentSaveId ?: UUID.randomUUID().toString().also { currentSaveId = it }
        val names = g.snapshot.value.seats.joinToString(", ") { it.name }
        val save = g.toSave(id, "${g.module.info.name} – $names", System.currentTimeMillis())
        viewModelScope.launch(Dispatchers.IO) { saveStore.save(save) }
        toast("Game saved")
        return true
    }

    fun endHosting(save: Boolean) {
        if (save && saveGame()) deleteAutoSave()
        _session.value?.end("The host ended the game")
        _session.value = null
        sessionJobs.forEach { it.cancel() }
        sessionJobs.clear()
        sessionScope?.cancel()
        sessionScope = null
        discovery.stopAdvertising()
        if (_client.value == null) _table.value = null
    }

    fun quitGame(save: Boolean) {
        if (_client.value != null) {
            leaveAsClient()
        } else {
            // Quitting without saving means no crash-recovery save should linger either.
            if (!save) deleteAutoSave()
            endHosting(save)
        }
        home()
    }

    // ------------------------------------------------------------------ pass-and-play
    /** Whose eyes the host screen is showing (matters when several people share this device). */
    private var viewerSeat = -1
    private val _handoff = MutableStateFlow<Seat?>(null)
    /** Non-null = cover the screen and ask to pass the device to this player. */
    val handoff: StateFlow<Seat?> = _handoff.asStateFlow()

    private fun resetPassAndPlay() {
        viewerSeat = -1
        _handoff.value = null
        _waitingFor.value = emptySet()
    }

    fun acceptHandoff() {
        _handoff.value = null
    }

    private val _table = MutableStateFlow<TableModel?>(null)
    val table: StateFlow<TableModel?> = _table.asStateFlow()

    private fun onHostSnapshot(s: HostSession, g: GameHost, snap: GameHost.Snapshot) {
        val locals = snap.seats.filter { it.kind == SeatKind.LOCAL }
        if (locals.isEmpty()) return
        if (viewerSeat !in locals.map { it.index }) viewerSeat = locals.first().index
        // Follow whose turn it is (not who's answering – those answers are automatic in pass-and-play).
        val owner = g.module.turnOwner(snap.state)
        if (owner != null && snap.seats[owner].kind == SeatKind.LOCAL && owner != viewerSeat) {
            viewerSeat = owner
            if (locals.size > 1 && g.module.info.hiddenHands) _handoff.value = snap.seats[owner]
        }
        if (owner != null && owner != lastTurnOwner) {
            if (lastTurnOwner != null) turnsSinceAutoSave++
            lastTurnOwner = owner
            if (turnsSinceAutoSave >= snap.seats.size) {
                turnsSinceAutoSave = 0
                autoSave(g)
            }
        }
        _table.value = TableModel(
            module = g.module,
            rules = g.rules,
            seats = snap.seats,
            viewerSeat = viewerSeat,
            view = g.module.view(snap.state, viewerSeat),
            version = snap.version,
            isHost = true,
            lan = g.lan,
        )
        if (g.module.currentSeat(snap.state) == null) {
            // Finished games don't need their saves any more.
            currentSaveId?.let { id -> viewModelScope.launch(Dispatchers.IO) { saveStore.delete(id) } }
            currentSaveId = null
            deleteAutoSave()
        }
    }

    // ------------------------------------------------------------------ dropped players
    private val _waitingFor = MutableStateFlow<Set<String>>(emptySet())
    /** Players the host chose to wait for (so the dialog stays closed until someone new drops). */
    val waitingFor: StateFlow<Set<String>> = _waitingFor.asStateFlow()

    fun waitForReconnect(missing: List<Seat>) {
        _waitingFor.value = missing.map { it.profileId }.toSet()
    }

    /** Re-opens the "someone left" options after choosing to wait. */
    fun reopenPauseOptions() {
        _waitingFor.value = emptySet()
    }

    fun replaceWithAi(seat: Seat, difficulty: Difficulty) {
        _session.value?.game?.value?.replaceWithAi(seat.index, difficulty)
    }

    // ------------------------------------------------------------------ playing
    fun act(action: JsonElement) {
        val t = _table.value ?: return
        val c = _client.value
        if (c != null) {
            c.act(action)
            return
        }
        val err = _session.value?.game?.value?.submit(t.viewerSeat, action)
        if (err != null) toast(err)
    }

    // ------------------------------------------------------------------ joining
    private var clientScope: CoroutineScope? = null
    private val _client = MutableStateFlow<LanClient?>(null)
    val client: StateFlow<LanClient?> = _client.asStateFlow()
    private var clientJobs = mutableListOf<Job>()

    fun join(host: String, port: Int) {
        val me = currentProfile ?: return toast("Create a user first")
        leaveAsClient()
        settings.lastJoinAddress = if (port == DEFAULT_PORT) host else "$host:$port"
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        clientScope = scope
        viewModelScope.launch {
            val avatar = withContext(Dispatchers.IO) { profileStore.avatarForNetwork(me.id) }
            val c = LanClient(scope, host, port, NetMessage.Hello(PROTOCOL_VERSION, me.id, me.name, avatar))
            _client.value = c
            c.start()
            clientJobs += viewModelScope.launch { c.avatars.collect { absorbNetworkAvatars(it) } }
            clientJobs += viewModelScope.launch {
                c.table.collect { t ->
                    if (t == null) return@collect
                    val module = Games.byId(t.gameId)
                    _table.value = TableModel(module, t.rules, t.seats, t.yourSeat, t.view, t.version, isHost = false, lan = true)
                    if (screen != Screen.Game) {
                        backStack.clear()
                        backStack.add(Screen.Home)
                        go(Screen.Game)
                    }
                }
            }
            go(Screen.ClientLobby)
        }
    }

    fun leaveAsClient() {
        _client.value?.leave()
        _client.value = null
        clientJobs.forEach { it.cancel() }
        clientJobs.clear()
        val scope = clientScope
        clientScope = null
        // Let the goodbye get out before tearing the connection down.
        viewModelScope.launch {
            kotlinx.coroutines.delay(600)
            scope?.cancel()
        }
        if (_session.value == null) _table.value = null
    }

    // ------------------------------------------------------------------ saves
    private val _saves = MutableStateFlow<List<SavedGame>>(emptyList())
    val saves: StateFlow<List<SavedGame>> = _saves.asStateFlow()

    fun refreshSaves() {
        viewModelScope.launch { _saves.value = withContext(Dispatchers.IO) { saveStore.list() } }
    }

    fun deleteSave(id: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { saveStore.delete(id) }
            refreshSaves()
        }
    }

    // ------------------------------------------------------------------ updates
    private val _update = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val update: StateFlow<UpdateState> = _update.asStateFlow()
    private val _showUpdateDialog = MutableStateFlow(false)
    val showUpdateDialog: StateFlow<Boolean> = _showUpdateDialog.asStateFlow()
    private var downloadedApk: File? = null

    fun checkForUpdates(manual: Boolean) {
        if (_update.value is UpdateState.Checking || _update.value is UpdateState.Downloading) return
        _update.value = UpdateState.Checking
        viewModelScope.launch {
            _update.value = try {
                val r = Updater.latest()
                if (r != null && r.versionCode > Updater.currentVersionCode) {
                    if (manual || r.versionCode != settings.skippedVersion) _showUpdateDialog.value = true
                    UpdateState.Available(r)
                } else {
                    UpdateState.UpToDate
                }
            } catch (e: Exception) {
                if (manual) UpdateState.Failed("Couldn't reach GitHub: ${e.message ?: "no connection"}") else UpdateState.Idle
            }
        }
    }

    fun dismissUpdate(skipThisVersion: Boolean) {
        (_update.value as? UpdateState.Available)?.let { if (skipThisVersion) settings.skippedVersion = it.release.versionCode }
        _showUpdateDialog.value = false
    }

    fun startUpdate() {
        val release = when (val u = _update.value) {
            is UpdateState.Available -> u.release
            is UpdateState.NeedsPermission -> u.release
            else -> return
        }
        val ctx = getApplication<Application>()
        if (!Updater.canInstall(ctx)) {
            _update.value = UpdateState.NeedsPermission(release)
            Updater.openInstallPermissionSettings(ctx)
            return
        }
        _showUpdateDialog.value = false
        val ready = downloadedApk
        if (ready != null && ready.exists() && ready.name.contains("-${release.versionCode}.")) {
            Updater.install(ctx, ready)
            _update.value = UpdateState.Available(release)
            return
        }
        _update.value = UpdateState.Downloading(release, 0f)
        viewModelScope.launch {
            try {
                val apk = Updater.download(ctx, release) { p -> _update.value = UpdateState.Downloading(release, p) }
                downloadedApk = apk
                _update.value = UpdateState.Available(release)
                Updater.install(ctx, apk)
            } catch (e: Exception) {
                _update.value = UpdateState.Failed("Download failed: ${e.message ?: "unknown error"}")
            }
        }
    }

    // Kept last: Kotlin runs initialisers top to bottom, and this uses state declared above.
    init {
        if (currentProfile == null) switchUser(_profiles.value.firstOrNull()?.id)
        if (_profiles.value.isEmpty()) go(Screen.EditProfile(null))
        reloadLocalAvatars()
        if (settings.autoCheckUpdates) checkForUpdates(manual = false)
    }

    override fun onCleared() {
        endHosting(save = false)
        leaveAsClient()
        discovery.stopSearching()
        super.onCleared()
    }
}
