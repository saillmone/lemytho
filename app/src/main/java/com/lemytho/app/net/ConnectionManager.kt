package com.lemytho.app.net

import io.socket.client.Ack
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject

/**
 * Enveloppe du client Socket.IO en coroutines/Flow.
 *
 * - [status] : état de connexion, observé par l'UI.
 * - [events] : événements entrants (lobby + jeu relayé), consommés par les ViewModels.
 *
 * Toutes les méthodes sont thread-safe : Socket.IO émet sur ses propres threads.
 */
class ConnectionManager {

    private var socket: Socket? = null

    private val _status = MutableStateFlow<ConnectionStatus>(ConnectionStatus.Disconnected)
    val status: StateFlow<ConnectionStatus> = _status.asStateFlow()

    private val _events = MutableSharedFlow<ServerEvent>(extraBufferCapacity = 256)
    val events: SharedFlow<ServerEvent> = _events.asSharedFlow()

    /** Ouvre une connexion vers [url] (ex. "http://10.0.2.2:3000"). */
    fun connect(url: String) {
        disconnect()

        _status.value = ConnectionStatus.Connecting
        val options = IO.Options().apply {
            reconnection = true
            reconnectionAttempts = 10
            reconnectionDelay = 1000
            timeout = 10_000
        }

        val newSocket = IO.socket(url, options)

        newSocket.on(Socket.EVENT_CONNECT) {
            _status.value = ConnectionStatus.Connected
        }
        newSocket.on(Socket.EVENT_CONNECT_ERROR) { args ->
            val detail = args.firstOrNull()?.toString() ?: "Erreur de connexion"
            _status.value = ConnectionStatus.Error(detail)
        }
        newSocket.on(Socket.EVENT_DISCONNECT) {
            _status.value = ConnectionStatus.Disconnected
        }

        // Événements de lobby.
        newSocket.on(ServerEventNames.LOBBY_UPDATE) { args -> handleLobbyUpdate(args) }
        newSocket.on(ServerEventNames.LOBBY_CLOSED) {
            _events.tryEmit(ServerEvent.LobbyClosed)
        }

        // Événements de jeu relayés par le serveur (invité ET hôte).
        Protocol.INCOMING_EVENTS.forEach { event ->
            newSocket.on(event) { args -> handleGameEvent(event, args) }
        }

        socket = newSocket
        newSocket.connect()
    }

    /** Ferme la connexion et libère les listeners. */
    fun disconnect() {
        socket?.off()
        socket?.disconnect()
        socket = null
        _status.value = ConnectionStatus.Disconnected
    }

    /** Crée un salon (l'hôte) et attend la confirmation du serveur. */
    suspend fun createLobby(pseudo: String): LobbyResult =
        emitWithAck("lobby:create", JSONObject().put("pseudo", pseudo)) { ackArgs ->
            parseLobbyAck(ackArgs)
        }

    /** Rejoint un salon (l'invité) et attend la confirmation du serveur. */
    suspend fun joinLobby(code: String, pseudo: String): LobbyResult =
        emitWithAck(
            "lobby:join",
            JSONObject().put("code", code).put("pseudo", pseudo)
        ) { ackArgs ->
            parseLobbyAck(ackArgs)
        }

    // --- Relais : invité -> hôte ---

    fun sendToHost(event: String, data: JSONObject = JSONObject()) {
        emitRelay("relay:toHost", JSONObject().put("event", event).put("data", data))
    }

    /** Bascule le statut « prêt » du joueur dans le salon. */
    fun setReady(ready: Boolean) {
        val s = socket ?: return
        if (s.connected()) {
            s.emit("lobby:ready", JSONObject().put("ready", ready))
        }
    }

    /** Signale au serveur que la partie démarre (réinitialise les statuts « prêt »). */
    fun notifyGameStarted() {
        val s = socket ?: return
        if (s.connected()) {
            s.emit("lobby:start")
        }
    }

    // --- Relais : hôte -> destinataires ---

    fun sendToPlayer(playerId: Int, event: String, data: JSONObject = JSONObject()) {
        emitRelay(
            "relay:toPlayer",
            JSONObject().put("playerId", playerId).put("event", event).put("data", data)
        )
    }

    fun broadcast(event: String, data: JSONObject = JSONObject()) {
        emitRelay("relay:broadcast", JSONObject().put("event", event).put("data", data))
    }

    fun sendToAll(event: String, data: JSONObject = JSONObject()) {
        emitRelay("relay:toAll", JSONObject().put("event", event).put("data", data))
    }

    // --- Interne ---

    private fun handleLobbyUpdate(args: Array<out Any>) {
        val obj = args.firstOrNull() as? JSONObject ?: return
        val members = parseMembers(obj.optJSONArray("members"))
        _events.tryEmit(ServerEvent.LobbyUpdate(members))
    }

    private fun handleGameEvent(name: String, args: Array<out Any>) {
        val data = args.firstOrNull() as? JSONObject ?: JSONObject()
        _events.tryEmit(ServerEvent.GameEvent(name, data))
    }

    private fun parseLobbyAck(args: Array<out Any>): LobbyResult {
        val obj = args.firstOrNull() as? JSONObject
            ?: return LobbyResult.Failure("UNKNOWN", "Réponse invalide du serveur")
        return if (obj.optBoolean("ok", false)) {
            LobbyResult.Success(
                code = obj.optString("code").ifBlank { null },
                playerId = obj.optInt("playerId", -1),
                members = parseMembers(obj.optJSONArray("members"))
            )
        } else {
            LobbyResult.Failure(
                error = obj.optString("error", "UNKNOWN"),
                message = obj.optString("message", "Erreur inconnue")
            )
        }
    }

    private fun emitRelay(channel: String, payload: JSONObject) {
        val s = socket ?: return
        if (s.connected()) {
            s.emit(channel, payload)
        }
    }

    private suspend fun emitWithAck(
        event: String,
        payload: JSONObject,
        parse: (Array<out Any>) -> LobbyResult
    ): LobbyResult {
        val s = socket ?: return LobbyResult.Failure("NOT_CONNECTED", "Non connecté au serveur")
        return suspendCancellableCoroutine { continuation ->
            // IMPORTANT : l'Ack doit être le DERNIER argument, sinon le client
            // Java Socket.IO ne le reconnaît pas et l'envoie comme donnée.
            s.emit(event, payload, Ack { args ->
                if (continuation.isActive) {
                    continuation.resumeWith(Result.success(parse(args)))
                }
            })
        }
    }
}

/** Noms d'événements de lobby émis par le serveur. */
private object ServerEventNames {
    const val LOBBY_UPDATE = "lobby:update"
    const val LOBBY_CLOSED = "lobby:closed"
}
