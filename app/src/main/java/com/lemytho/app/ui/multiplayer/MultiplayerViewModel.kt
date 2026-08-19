package com.lemytho.app.ui.multiplayer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lemytho.app.BuildConfig
import com.lemytho.app.data.local.WordRepository
import com.lemytho.app.di.AppContainer
import com.lemytho.app.di.PseudoStore
import com.lemytho.app.di.ServerStore
import com.lemytho.app.net.ConnectionManager
import com.lemytho.app.net.GameProtocol
import com.lemytho.app.net.LobbyResult
import com.lemytho.app.net.Protocol
import com.lemytho.app.net.ServerEvent
import com.lemytho.app.ui.FunnyNames
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
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
    private val serverStore: ServerStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(MultiplayerUiState())
    val uiState: StateFlow<MultiplayerUiState> = _uiState.asStateFlow()

    init {
        // Pré-remplit le pseudo et l'URL du serveur avec les dernières valeurs.
        _uiState.update {
            it.copy(
                myPseudo = pseudoStore.load(),
                serverUrl = serverStore.load() ?: it.serverUrl
            )
        }
        viewModelScope.launch {
            connectionManager.status.collect { status ->
                _uiState.update { it.copy(connectionStatus = status) }
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
            it.copy(screen = MultiplayerScreen.HostLobby, isHost = true, error = message)
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
        connectionManager.connect(url)
        viewModelScope.launch {
            val result = withTimeoutOrNull(15_000) {
                connectionManager.createLobby(state.myPseudo.trim())
            }
            when (result) {
                is LobbyResult.Success -> _uiState.update {
                    it.copy(
                        screen = MultiplayerScreen.HostLobby,
                        isHost = true,
                        myPlayerId = result.playerId,
                        lobbyCode = result.code,
                        members = result.members,
                        error = null
                    )
                }
                is LobbyResult.Failure -> _uiState.update { it.copy(error = result.message) }
                null -> _uiState.update { it.copy(error = "Délai de connexion dépassé") }
            }
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
        connectionManager.connect(url)
        viewModelScope.launch {
            val result = withTimeoutOrNull(15_000) {
                connectionManager.joinLobby(normalizedCode, state.myPseudo.trim())
            }
            when (result) {
                is LobbyResult.Success -> _uiState.update {
                    it.copy(
                        screen = MultiplayerScreen.Waiting,
                        isHost = false,
                        myPlayerId = result.playerId,
                        lobbyCode = result.code ?: normalizedCode,
                        members = result.members,
                        error = null
                    )
                }
                is LobbyResult.Failure -> _uiState.update { it.copy(error = result.message) }
                null -> _uiState.update { it.copy(error = "Délai de connexion dépassé") }
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
        // Source de vérité unique : on passe par lobby:ready (serveur), qui met
        // à jour le salon ET relaie player:ready à l'hôte. On renvoie ensuite le
        // joueur au salon pour qu'il voie les pastilles et puisse se rétracter.
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

    /** Déconnecte et réinitialise l'état multijoueur, en conservant pseudo et URL mémorisés. */
    fun quit() {
        connectionManager.disconnect()
        _uiState.value = MultiplayerUiState(
            myPseudo = pseudoStore.load(),
            serverUrl = serverStore.load() ?: BuildConfig.SERVER_URL
        )
    }

    private fun handleEvent(event: ServerEvent) {
        when (event) {
            is ServerEvent.LobbyUpdate -> _uiState.update { it.copy(members = event.members) }
            is ServerEvent.LobbyClosed -> {
                // Coupe la socket pour que le statut repasse à « Hors ligne » et
                // ne reste pas affiché « Connecté » à côté du message de fermeture.
                connectionManager.disconnect()
                _uiState.update {
                    it.copy(
                        error = "Le salon a été fermé par l'hôte",
                        screen = MultiplayerScreen.Menu,
                        isHost = false,
                        lobbyCode = null
                    )
                }
            }
            is ServerEvent.GameEvent -> handleGameEvent(event)
        }
    }

    private fun handleGameEvent(event: ServerEvent.GameEvent) {
        // L'hôte gère le jeu via GameViewModel : il ignore la projection publique.
        if (_uiState.value.isHost) return
        when (event.name) {
            Protocol.EVENT_GAME_PRIVATE -> {
                val (role, word) = GameProtocol.parsePrivate(event.data) ?: return
                _uiState.update {
                    it.copy(
                        myRole = role,
                        myWord = word,
                        inRound = true,
                        wantsReplay = false,
                        revealAcked = 0,
                        revealTotal = 0,
                        board = null,
                        elimination = null,
                        guestResult = null,
                        hasVoted = false,
                        revealConfirmed = false,
                        screen = MultiplayerScreen.GuestReveal
                    )
                }
            }

            Protocol.EVENT_GAME_BOARD -> {
                // Hors manche (non sélectionné pour rejouer) : on ignore le plateau.
                if (!_uiState.value.inRound) return
                val board = GameProtocol.parseBoard(event.data)
                _uiState.update {
                    it.copy(
                        board = board,
                        elimination = null,
                        guestResult = null,
                        hasVoted = false,
                        revealConfirmed = false,
                        screen = MultiplayerScreen.GuestBoard
                    )
                }
            }

            Protocol.EVENT_GAME_ELIMINATION -> {
                if (!_uiState.value.inRound) return
                val elimination = GameProtocol.parseElimination(event.data)
                if (elimination != null) {
                    _uiState.update {
                        it.copy(
                            elimination = elimination,
                            guestResult = null,
                            screen = MultiplayerScreen.GuestElimination
                        )
                    }
                }
            }

            Protocol.EVENT_GAME_RESULT -> {
                if (!_uiState.value.inRound) return
                val result = GameProtocol.parseResult(event.data)
                // On mémorise le résultat mais on RESTE sur l'écran d'élimination :
                // chaque invité choisit lui-même quand voir le score final. La manche
                // est terminée pour cet invité jusqu'à la prochaine relance.
                _uiState.update { it.copy(guestResult = result, inRound = false) }
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
                // Plus assez de joueurs : retour au salon avec un message.
                _uiState.update {
                    it.copy(
                        screen = MultiplayerScreen.Waiting,
                        error = "Partie annulée : plus assez de joueurs",
                        inRound = false,
                        myRole = null,
                        myWord = null,
                        revealConfirmed = false,
                        board = null,
                        elimination = null,
                        guestResult = null,
                        hasVoted = false,
                        wantsReplay = false
                    )
                }
            }

            // game:start et game:phase n'ont pas d'action directe ici.
            else -> Unit
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
                serverStore = container.serverStore
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
