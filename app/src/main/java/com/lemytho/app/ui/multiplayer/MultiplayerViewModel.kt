package com.lemytho.app.ui.multiplayer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lemytho.app.BuildConfig
import com.lemytho.app.data.local.WordRepository
import com.lemytho.app.data.model.Role
import com.lemytho.app.di.AppContainer
import com.lemytho.app.di.ClientIdStore
import com.lemytho.app.di.LobbySession
import com.lemytho.app.di.LobbySessionStore
import com.lemytho.app.di.PseudoStore
import com.lemytho.app.di.ServerStore
import com.lemytho.app.net.ConnectionManager
import com.lemytho.app.net.ConnectionStatus
import com.lemytho.app.net.GameProtocol
import com.lemytho.app.net.LobbyResult
import com.lemytho.app.net.Protocol
import com.lemytho.app.net.ServerEvent
import com.lemytho.app.net.SyncSnapshot
import com.lemytho.app.ui.FunnyNames
import com.lemytho.app.ui.VotePhase
import com.lemytho.app.engine.Victory
import com.lemytho.app.engine.WordGuessMatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * ViewModel du mode multijoueur.
 *
 * Gère le lobby (hôte/invité), le setup hôte et, côté invité, la réception
 * de la projection publique. Aucune règle métier n'est dupliquée ici : la
 * logique de jeu reste chez l'hôte ([com.lemytho.app.ui.GameViewModel]).
 */
class MultiplayerViewModel(
    private val connectionManager: ConnectionManager,
    private val wordRepository: WordRepository,
    private val pseudoStore: PseudoStore,
    private val serverStore: ServerStore,
    private val clientIdStore: ClientIdStore,
    private val lobbySessionStore: LobbySessionStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(MultiplayerUiState())
    val uiState: StateFlow<MultiplayerUiState> = _uiState.asStateFlow()

    private val clientId: String = clientIdStore.getOrCreate()
    private var joining: Boolean = false

    init {
        _uiState.update {
            it.copy(
                myPseudo = pseudoStore.load(),
                serverUrl = serverStore.load() ?: it.serverUrl
            )
        }
        viewModelScope.launch {
            connectionManager.status.collect { status ->
                _uiState.update { it.copy(connectionStatus = status) }
                if (status is ConnectionStatus.Connected && !joining) {
                    maybeRejoin()
                }
            }
        }
        viewModelScope.launch {
            connectionManager.events.collect { event -> handleEvent(event) }
        }
        viewModelScope.launch {
            wordRepository.getCategories().collect { categories ->
                _uiState.update { it.copy(categories = categories) }
            }
        }
    }

    fun updateServerUrl(url: String) {
        val trimmed = url.trim()
        _uiState.update { it.copy(serverUrl = trimmed) }
        serverStore.save(trimmed)
    }

    /** Rétablit l'URL du serveur par défaut (et l'oublie des préférences). */
    fun resetServerUrl() {
        serverStore.clear()
        _uiState.update { it.copy(serverUrl = BuildConfig.SERVER_URL) }
    }

    fun updatePseudo(pseudo: String) {
        _uiState.update { it.copy(myPseudo = pseudo) }
        pseudoStore.save(pseudo)
    }

    /** Tire un pseudo amusant aléatoire (thème espion/enquête). */
    fun randomPseudo() {
        val pseudo = FunnyNames.random()
        _uiState.update { it.copy(myPseudo = pseudo) }
        pseudoStore.save(pseudo)
    }

    fun goToHost() {
        _uiState.update { it.copy(screen = MultiplayerScreen.HostLobby, error = null) }
    }

    /** Retour au salon de l'hôte après une annulation de partie, avec un message. */
    fun abortToLobby(message: String) {
        _uiState.update {
            it.copy(
                screen = MultiplayerScreen.HostLobby,
                isHost = true,
                error = message,
                roundPlayerIds = emptySet()
            )
        }
    }

    fun goToJoin() {
        _uiState.update { it.copy(screen = MultiplayerScreen.JoinLobby, error = null) }
    }

    /** Deep link d'invitation : pré-remplit serveur + code puis ouvre l'écran de saisie. */
    fun handleDeepLink(code: String?, server: String?) {
        _uiState.update { state ->
            state.copy(
                serverUrl = server?.trim()?.ifBlank { null } ?: state.serverUrl,
                joinCode = code?.trim()?.uppercase() ?: state.joinCode,
                screen = MultiplayerScreen.JoinLobby,
                error = null
            )
        }
    }

    fun goToHostSetup() {
        _uiState.update { it.copy(screen = MultiplayerScreen.HostSetup, error = null) }
    }

    /** Retour du setup hôte vers le salon (sans fermer la partie). */
    fun backToHostLobby() {
        _uiState.update { it.copy(screen = MultiplayerScreen.HostLobby, error = null) }
    }

    /** Retour de l'écran de saisie du code vers le menu multijoueur. */
    fun backToMenu() {
        _uiState.update { it.copy(screen = MultiplayerScreen.Menu, error = null) }
    }

    fun setCategory(category: String?) {
        _uiState.update { it.copy(selectedCategory = category) }
    }

    fun setThreePlayerIsUnknown(value: Boolean) {
        _uiState.update { it.copy(threePlayerIsUnknown = value) }
    }

    fun startHosting() {
        val state = _uiState.value
        val url = state.serverUrl
        if (state.myPseudo.isBlank()) {
            _uiState.update { it.copy(error = "Indique un pseudo pour créer la partie") }
            return
        }
        if (url.isBlank()) {
            _uiState.update { it.copy(error = "Renseigne l'adresse du serveur") }
            return
        }
        viewModelScope.launch {
            joining = true
            connectionManager.connect(url)
            if (!connectionManager.awaitConnected()) {
                joining = false
                _uiState.update { it.copy(error = "Délai de connexion dépassé") }
                return@launch
            }
            when (val result = connectionManager.createLobby(state.myPseudo.trim(), clientId)) {
                is LobbyResult.Success -> applyLobbySuccess(
                    result = result,
                    isHost = true,
                    screen = MultiplayerScreen.HostLobby,
                    serverUrl = url,
                    pseudo = state.myPseudo.trim()
                )
                is LobbyResult.Failure -> _uiState.update { it.copy(error = result.message) }
            }
            joining = false
        }
    }

    fun joinLobby(code: String) {
        val state = _uiState.value
        val url = state.serverUrl
        val normalizedCode = code.trim().uppercase()
        if (state.myPseudo.isBlank()) {
            _uiState.update { it.copy(error = "Indique un pseudo pour rejoindre la partie") }
            return
        }
        if (url.isBlank()) {
            _uiState.update { it.copy(error = "Renseigne l'adresse du serveur") }
            return
        }
        if (normalizedCode.isBlank()) {
            _uiState.update { it.copy(error = "Renseigne le code du salon") }
            return
        }
        viewModelScope.launch {
            joining = true
            connectionManager.connect(url)
            if (!connectionManager.awaitConnected()) {
                joining = false
                _uiState.update { it.copy(error = "Délai de connexion dépassé") }
                return@launch
            }
            when (val result = connectionManager.joinLobby(normalizedCode, state.myPseudo.trim(), clientId)) {
                is LobbyResult.Success -> applyLobbySuccess(
                    result = result,
                    isHost = false,
                    screen = MultiplayerScreen.Waiting,
                    serverUrl = url,
                    pseudo = state.myPseudo.trim(),
                    fallbackCode = normalizedCode
                )
                is LobbyResult.Failure -> _uiState.update { it.copy(error = result.message) }
            }
            joining = false
        }
    }

    /** Retour au premier plan : heartbeat, sinon recâblage + rejoin. */
    fun onAppForeground() {
        val session = lobbySessionStore.load() ?: return
        viewModelScope.launch {
            if (connectionManager.status.value is ConnectionStatus.Connected) {
                if (!connectionManager.heartbeat()) {
                    connectionManager.disconnect()
                    rejoinSession(session)
                }
            } else {
                rejoinSession(session)
            }
        }
    }

    // --- Prêt dans le salon ---

    /** Bascule le statut « prêt » localement (optimiste) puis le diffuse au serveur. */
    fun setReady(ready: Boolean) {
        val myId = _uiState.value.myPlayerId ?: return
        _uiState.update { state ->
            state.copy(
                members = state.members.map {
                    if (it.playerId == myId) it.copy(ready = ready) else it
                }
            )
        }
        connectionManager.setReady(ready)
    }

    // --- Invité : jeu ---

    fun guestRevealDone() {
        val state = _uiState.value
        if (state.isHost) return
        val myId = state.myPlayerId ?: return
        connectionManager.sendToHost(
            Protocol.EVENT_PLAYER_REVEAL,
            JSONObject().put("playerId", myId)
        )
        _uiState.update { it.copy(revealConfirmed = true) }
    }

    fun guestCastVote(targetId: Int) {
        val state = _uiState.value
        if (state.isHost) return
        val myId = state.myPlayerId ?: return
        connectionManager.sendToHost(
            Protocol.EVENT_PLAYER_VOTE,
            JSONObject().put("playerId", myId).put("targetId", targetId)
        )
        _uiState.update { it.copy(hasVoted = true) }
    }

    fun guestSubmitGuess(text: String) {
        val state = _uiState.value
        if (state.isHost || state.guessSubmitted) return
        val myId = state.myPlayerId ?: return
        val elimination = state.elimination ?: return
        if (elimination.playerId != myId || elimination.role != Role.UNKNOWN) return
        if (elimination.guessResolved) return
        val trimmed = text.trim().take(WordGuessMatcher.MAX_GUESS_LENGTH)
        if (trimmed.isEmpty()) return
        connectionManager.sendToHost(
            Protocol.EVENT_PLAYER_GUESS,
            JSONObject().put("playerId", myId).put("text", trimmed)
        )
        _uiState.update { it.copy(guessSubmitted = true) }
    }

    /** L'invité consulte le résultat final à son propre rythme. */
    fun guestSeeResults() {
        val state = _uiState.value
        if (state.isHost || state.guestResult == null) return
        _uiState.update { it.copy(screen = MultiplayerScreen.GuestResult) }
    }

    /** L'invité se déclare prêt à rejouer la manche suivante. */
    fun guestMarkReady() {
        val state = _uiState.value
        if (state.isHost || state.wantsReplay) return
        val myId = state.myPlayerId ?: return
        connectionManager.setReady(true)
        _uiState.update {
            it.copy(
                wantsReplay = true,
                members = it.members.map { m ->
                    if (m.playerId == myId) m.copy(ready = true) else m
                },
                screen = MultiplayerScreen.Waiting
            )
        }
    }

    /** Départ franc : leave puis déconnexion, session oubliée. */
    fun quit() {
        lobbySessionStore.clear()
        viewModelScope.launch {
            connectionManager.leaveLobby()
            connectionManager.disconnect()
        }
        _uiState.value = MultiplayerUiState(
            myPseudo = pseudoStore.load(),
            serverUrl = serverStore.load() ?: BuildConfig.SERVER_URL
        )
    }

    private fun applyLobbySuccess(
        result: LobbyResult.Success,
        isHost: Boolean,
        screen: MultiplayerScreen,
        serverUrl: String,
        pseudo: String,
        fallbackCode: String? = null
    ) {
        val code = result.code ?: fallbackCode
        if (code != null) {
            lobbySessionStore.save(
                LobbySession(
                    serverUrl = serverUrl,
                    code = code,
                    pseudo = pseudo,
                    isHost = isHost
                )
            )
        }
        val keepRound = !isHost && _uiState.value.inRound
        _uiState.update {
            it.copy(
                screen = if (keepRound) it.screen else screen,
                isHost = isHost,
                myPlayerId = result.playerId,
                lobbyCode = code,
                members = result.members,
                error = null,
                awaitingSync = keepRound,
                hostSeatResumedTick = if (isHost && result.resumed) it.hostSeatResumedTick + 1 else it.hostSeatResumedTick,
                hostResumeInRound = if (isHost && result.resumed) result.inRound else it.hostResumeInRound
            )
        }
    }

    private fun maybeRejoin() {
        val session = lobbySessionStore.load() ?: return
        if (_uiState.value.lobbyCode == null) return
        viewModelScope.launch { rejoinSession(session) }
    }

    private suspend fun rejoinSession(session: LobbySession) {
        if (joining) return
        joining = true
        connectionManager.connect(session.serverUrl)
        if (!connectionManager.awaitConnected()) {
            joining = false
            _uiState.update { it.copy(error = "Reconnexion impossible") }
            return
        }
        val result = if (session.isHost) {
            connectionManager.createLobby(session.pseudo, clientId)
        } else {
            connectionManager.joinLobby(session.code, session.pseudo, clientId)
        }
        when (result) {
            is LobbyResult.Success -> applyLobbySuccess(
                result = result,
                isHost = session.isHost,
                screen = if (session.isHost) MultiplayerScreen.HostLobby else MultiplayerScreen.Waiting,
                serverUrl = session.serverUrl,
                pseudo = session.pseudo,
                fallbackCode = session.code
            )
            is LobbyResult.Failure -> {
                lobbySessionStore.clear()
                _uiState.update {
                    it.copy(
                        error = result.message,
                        screen = MultiplayerScreen.Menu,
                        lobbyCode = null,
                        isHost = false
                    )
                }
            }
        }
        joining = false
    }

    private fun handleEvent(event: ServerEvent) {
        when (event) {
            is ServerEvent.LobbyUpdate -> _uiState.update { state ->
                val hostOnline = event.members.find { it.isHost }?.connected != false
                state.copy(
                    members = event.members,
                    hostPaused = !state.isHost && state.inRound && !hostOnline
                )
            }
            is ServerEvent.LobbyClosed -> {
                lobbySessionStore.clear()
                connectionManager.disconnect()
                _uiState.update {
                    it.copy(
                        error = "Le salon a été fermé par l'hôte",
                        screen = MultiplayerScreen.Menu,
                        isHost = false,
                        lobbyCode = null,
                        hostPaused = false,
                        awaitingSync = false,
                        roundPlayerIds = emptySet()
                    )
                }
            }
            is ServerEvent.GameEvent -> handleGameEvent(event)
        }
    }

    private fun handleGameEvent(event: ServerEvent.GameEvent) {
        when (event.name) {
            Protocol.EVENT_PLAYER_PRESENCE -> {
                val playerId = event.data.optInt("playerId")
                val connected = event.data.optBoolean("connected")
                _uiState.update { state ->
                    val members = state.members.map {
                        if (it.playerId == playerId) it.copy(connected = connected) else it
                    }
                    val board = state.board?.let { snapshot ->
                        snapshot.copy(
                            players = snapshot.players.map { p ->
                                if (p.playerId == playerId) p.copy(connected = connected) else p
                            }
                        )
                    }
                    val hostPaused = if (playerId == Protocol.HOST_PLAYER_ID && !state.isHost && state.inRound) {
                        !connected
                    } else {
                        state.hostPaused
                    }
                    state.copy(members = members, board = board, hostPaused = hostPaused)
                }
            }
            else -> Unit
        }
        if (_uiState.value.isHost) return
        when (event.name) {
            Protocol.EVENT_GAME_START -> {
                val myId = _uiState.value.myPlayerId ?: return
                val playerIds = GameProtocol.parseStartPlayerIds(event.data).toSet()
                if (myId in playerIds) {
                    _uiState.update { it.copy(roundPlayerIds = playerIds) }
                    return
                }
                _uiState.update {
                    it.copy(
                        roundPlayerIds = playerIds,
                        inRound = true,
                        participating = false,
                        error = null,
                        screen = MultiplayerScreen.Waiting
                    )
                }
            }

            Protocol.EVENT_GAME_SYNC -> applySync(GameProtocol.parseSync(event.data))

            Protocol.EVENT_GAME_PRIVATE -> {
                val (role, word) = GameProtocol.parsePrivate(event.data) ?: return
                _uiState.update {
                    it.copy(
                        myRole = role,
                        myWord = word,
                        inRound = true,
                        participating = true,
                        wantsReplay = false,
                        revealAcked = 0,
                        revealTotal = 0,
                        board = null,
                        elimination = null,
                        guestResult = null,
                        hasVoted = false,
                        guessSubmitted = false,
                        unknownGuessCorrect = null,
                        revealConfirmed = false,
                        awaitingSync = false,
                        hostPaused = false,
                        screen = MultiplayerScreen.GuestReveal
                    )
                }
            }

            Protocol.EVENT_GAME_BOARD -> {
                if (!_uiState.value.inRound || !_uiState.value.participating) return
                val board = GameProtocol.parseBoard(event.data)
                _uiState.update {
                    val previous = it.board
                    val newVoteRound = previous == null ||
                        previous.votePhase != board.votePhase ||
                        previous.turnNumber != board.turnNumber ||
                        previous.roundNumber != board.roundNumber
                    val stayOnReveal = it.screen == MultiplayerScreen.GuestReveal &&
                        board.clueOrder.isEmpty() &&
                        board.votePhase == VotePhase.IDLE
                    val keepScreen = it.screen == MultiplayerScreen.GuestElimination ||
                        it.screen == MultiplayerScreen.GuestResult ||
                        stayOnReveal
                    it.copy(
                        board = board,
                        hasVoted = when {
                            board.votePhase == VotePhase.IDLE -> false
                            !newVoteRound -> it.hasVoted
                            else -> false
                        },
                        screen = if (keepScreen) it.screen else MultiplayerScreen.GuestBoard
                    )
                }
            }

            Protocol.EVENT_GAME_PHASE -> {
                if (!_uiState.value.inRound || !_uiState.value.participating) return
                if (event.data.optString("phase") != Protocol.PHASE_CLUE) return
                _uiState.update {
                    if (it.screen == MultiplayerScreen.GuestResult) return@update it
                    it.copy(
                        elimination = null,
                        guessSubmitted = false,
                        screen = MultiplayerScreen.GuestBoard
                    )
                }
            }

            Protocol.EVENT_GAME_ELIMINATION -> {
                if (!_uiState.value.inRound || !_uiState.value.participating) return
                val elimination = GameProtocol.parseElimination(event.data)
                if (elimination != null) {
                    _uiState.update {
                        it.copy(
                            elimination = elimination,
                            guestResult = null,
                            guessSubmitted = elimination.guessResolved || it.guessSubmitted,
                            unknownGuessCorrect = when {
                                elimination.guessCorrect -> true
                                elimination.guessResolved -> false
                                else -> it.unknownGuessCorrect
                            },
                            screen = MultiplayerScreen.GuestElimination
                        )
                    }
                }
            }

            Protocol.EVENT_GAME_RESULT -> {
                if (!_uiState.value.inRound || !_uiState.value.participating) return
                val result = GameProtocol.parseResult(event.data)
                _uiState.update {
                    val victory = result.victory
                    val found = victory is Victory.Unknown && victory.byGuess
                    val missedGuess = !found && it.elimination?.role == Role.UNKNOWN
                    it.copy(
                        guestResult = result,
                        inRound = false,
                        roundPlayerIds = emptySet(),
                        unknownGuessCorrect = when {
                            found -> true
                            missedGuess -> false
                            else -> it.unknownGuessCorrect
                        }
                    )
                }
            }

            Protocol.EVENT_GAME_REVEAL_ACK -> {
                _uiState.update {
                    it.copy(
                        revealAcked = event.data.optInt("acked"),
                        revealTotal = event.data.optInt("total")
                    )
                }
            }

            Protocol.EVENT_GAME_CANCELLED -> {
                val message = GameProtocol.cancelledMessage(
                    GameProtocol.parseCancelledReason(event.data)
                )
                _uiState.update {
                    it.copy(
                        screen = MultiplayerScreen.Waiting,
                        error = message,
                        inRound = false,
                        participating = false,
                        myRole = null,
                        myWord = null,
                        revealConfirmed = false,
                        board = null,
                        elimination = null,
                        guestResult = null,
                        hasVoted = false,
                        guessSubmitted = false,
                        unknownGuessCorrect = null,
                        wantsReplay = false,
                        awaitingSync = false,
                        hostPaused = false,
                        roundPlayerIds = emptySet()
                    )
                }
            }

            else -> Unit
        }
    }

    private fun applySync(sync: SyncSnapshot) {
        _uiState.update { state ->
            if (!sync.inRound) {
                return@update state.copy(
                    inRound = false,
                    participating = false,
                    awaitingSync = false,
                    hostPaused = false,
                    roundPlayerIds = emptySet(),
                    screen = if (state.screen == MultiplayerScreen.GuestResult) {
                        state.screen
                    } else {
                        MultiplayerScreen.Waiting
                    }
                )
            }
            if (!sync.participating) {
                val hostOffline = state.members.find { it.isHost }?.connected == false
                return@update state.copy(
                    inRound = true,
                    participating = false,
                    roundPlayerIds = sync.board?.players?.map { it.playerId }?.toSet()
                        ?: emptySet(),
                    awaitingSync = false,
                    myRole = null,
                    myWord = null,
                    board = sync.board,
                    elimination = sync.elimination,
                    guestResult = sync.result,
                    hostPaused = hostOffline,
                    error = null,
                    screen = MultiplayerScreen.Waiting
                )
            }
            val myId = state.myPlayerId
            val phaseScreen = when (sync.phase) {
                Protocol.PHASE_REVEAL -> MultiplayerScreen.GuestReveal
                Protocol.PHASE_ELIMINATION -> MultiplayerScreen.GuestElimination
                Protocol.PHASE_RESULT -> if (sync.result != null) {
                    MultiplayerScreen.GuestResult
                } else {
                    MultiplayerScreen.GuestElimination
                }
                else -> MultiplayerScreen.GuestBoard
            }
            val pendingMine = sync.pendingUnknownGuess && sync.elimination?.playerId == myId
            state.copy(
                inRound = true,
                participating = true,
                awaitingSync = false,
                hostPaused = false,
                myRole = sync.privateRole ?: state.myRole,
                myWord = sync.privateWord ?: state.myWord,
                board = sync.board ?: state.board,
                revealAcked = sync.revealAcked,
                revealTotal = sync.revealTotal,
                revealConfirmed = sync.alreadyRevealed,
                hasVoted = sync.alreadyVoted,
                elimination = sync.elimination,
                guestResult = sync.result,
                guessSubmitted = when {
                    pendingMine -> false
                    sync.elimination?.guessResolved == true -> true
                    else -> state.guessSubmitted
                },
                unknownGuessCorrect = when {
                    sync.elimination?.guessCorrect == true -> true
                    sync.elimination?.guessResolved == true -> false
                    else -> state.unknownGuessCorrect
                },
                screen = phaseScreen
            )
        }
    }
}

/** Factory manuelle : injecte les dépendances sans framework DI. */
class MultiplayerViewModelFactory(
    private val container: AppContainer
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MultiplayerViewModel::class.java)) {
            return MultiplayerViewModel(
                connectionManager = container.connectionManager,
                wordRepository = container.wordRepository,
                pseudoStore = container.pseudoStore,
                serverStore = container.serverStore,
                clientIdStore = container.clientIdStore,
                lobbySessionStore = container.lobbySessionStore
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
