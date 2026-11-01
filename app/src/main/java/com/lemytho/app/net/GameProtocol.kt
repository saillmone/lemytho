package com.lemytho.app.net

import com.lemytho.app.data.model.Player
import com.lemytho.app.data.model.PlayerStatus
import com.lemytho.app.data.model.Role
import com.lemytho.app.engine.Victory
import com.lemytho.app.ui.NoElimination
import com.lemytho.app.ui.VotePhase
import com.lemytho.app.ui.VoteReveal
import org.json.JSONArray
import org.json.JSONObject

/** Joueur tel que vu par un invité (sans rôle ni mot tant que non révélés). */
data class PublicPlayer(
    val playerId: Int,
    val pseudo: String,
    val status: PlayerStatus,
    val role: Role? = null,
    val connected: Boolean = true
)

/** Photo de manche envoyée à un invité qui reprend sa chaise. */
data class SyncSnapshot(
    val inRound: Boolean,
    val participating: Boolean,
    val phase: String,
    val privateRole: Role? = null,
    val privateWord: String? = null,
    val board: BoardSnapshot? = null,
    val revealAcked: Int = 0,
    val revealTotal: Int = 0,
    val alreadyRevealed: Boolean = false,
    val alreadyVoted: Boolean = false,
    val elimination: EliminationSnapshot? = null,
    val pendingUnknownGuess: Boolean = false,
    val result: ResultSnapshot? = null
)

/** Instantané public du plateau, diffusé par l'hôte. */
data class BoardSnapshot(
    val players: List<PublicPlayer>,
    val clueOrder: List<Int>,
    val roundNumber: Int,
    val turnNumber: Int,
    val category: String?,
    val votePhase: VotePhase,
    val currentVoterId: Int?,
    val tiedCandidates: Set<Int>,
    val votedPlayerIds: List<Int> = emptyList(),
    val noElimination: NoElimination? = null
)

/** Instantané d'une élimination (rôle révélé publiquement). */
data class EliminationSnapshot(
    val playerId: Int,
    val pseudo: String,
    val role: Role,
    val turnNumber: Int,
    val guessResolved: Boolean = false,
    val guessCorrect: Boolean = false,
    val guessText: String? = null,
    val votes: List<VoteReveal> = emptyList()
)

/** Instantané du résultat final (rôles de tous + scores cumulés). */
data class ResultSnapshot(
    val victory: Victory,
    val players: List<Player>,
    val totalScores: Map<Int, Int>
)

/**
 * Sérialisation/désérialisation du protocole de jeu (JSON).
 *
 * La projection public/privé est critique : le mot secret d'un joueur n'est
 * jamais diffusé aux autres appareils. Toutes les fonctions de sérialisation
 * sont pures et couvertes par des tests unitaires.
 */
object GameProtocol {

    // --- Sérialisation (hôte -> invités) ---

    fun startPayload(category: String?, playerIds: List<Int> = emptyList()): JSONObject =
        JSONObject()
            .put("category", category ?: JSONObject.NULL)
            .put("playerIds", JSONArray(playerIds))

    fun cancelledPayload(reason: String): JSONObject =
        JSONObject().put("reason", reason)

    fun cancelledMessage(reason: String?): String = when (reason) {
        Protocol.CANCEL_NOT_ENOUGH_PLAYERS -> "Partie annulée : plus assez de joueurs"
        Protocol.CANCEL_HOST_CANCELLED -> "Manche annulée par le Maître du Jeu"
        Protocol.CANCEL_ROUND_LOST -> "La manche a été perdue (l'appli a été fermée)"
        else -> "Partie annulée"
    }

    fun parseCancelledReason(obj: JSONObject): String? =
        obj.optString("reason").takeIf { it.isNotBlank() }

    fun parseStartPlayerIds(obj: JSONObject): List<Int> =
        parseIntList(obj.optJSONArray("playerIds"))

    fun privatePayload(role: Role, word: String): JSONObject =
        JSONObject().put("role", role.name).put("word", word)

    fun phasePayload(phase: String): JSONObject =
        JSONObject().put("phase", phase)

    fun revealAckPayload(acked: Int, total: Int): JSONObject =
        JSONObject().put("acked", acked).put("total", total)

    fun boardPayload(
        players: List<Player>,
        clueOrder: List<Int>,
        roundNumber: Int,
        turnNumber: Int,
        category: String?,
        votePhase: VotePhase,
        currentVoterId: Int?,
        tiedCandidates: Set<Int>,
        offlinePlayerIds: Set<Int> = emptySet(),
        votedPlayerIds: List<Int> = emptyList(),
        noElimination: NoElimination? = null
    ): JSONObject {
        val playersArray = JSONArray()
        players.forEach { p ->
            playersArray.put(
                JSONObject()
                    .put("playerId", p.id)
                    .put("pseudo", p.pseudo)
                    .put("status", p.status.name)
                    .put("role", if (p.status == PlayerStatus.ELIMINATED) p.role.name else JSONObject.NULL)
                    .put("connected", p.id !in offlinePlayerIds)
            )
        }
        val payload = JSONObject()
            .put("players", playersArray)
            .put("clueOrder", JSONArray(clueOrder))
            .put("roundNumber", roundNumber)
            .put("turnNumber", turnNumber)
            .put("category", category ?: JSONObject.NULL)
            .put("votePhase", votePhase.name)
            .put("currentVoterId", currentVoterId ?: JSONObject.NULL)
            .put("tiedCandidates", JSONArray(tiedCandidates.toList()))
            .put("votedPlayerIds", JSONArray(votedPlayerIds))
        if (noElimination != null) {
            payload.put("noElimination", noElimination.name)
        }
        return payload
    }

    fun eliminationPayload(
        playerId: Int,
        pseudo: String,
        role: Role,
        turnNumber: Int,
        guessResolved: Boolean = false,
        guessCorrect: Boolean = false,
        guessText: String? = null,
        votes: List<VoteReveal> = emptyList()
    ): JSONObject {
        val votesArray = JSONArray()
        votes.forEach { v ->
            votesArray.put(
                JSONObject().put("voter", v.voterPseudo).put("target", v.targetPseudo)
            )
        }
        return JSONObject()
            .put("playerId", playerId)
            .put("pseudo", pseudo)
            .put("role", role.name)
            .put("turnNumber", turnNumber)
            .put("guessResolved", guessResolved)
            .put("guessCorrect", guessCorrect)
            .put("guessText", guessText?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
            .put("votes", votesArray)
    }

    fun resultPayload(
        players: List<Player>,
        victory: Victory,
        totalScores: Map<Int, Int>
    ): JSONObject {
        val playersArray = JSONArray()
        players.forEach { p ->
            playersArray.put(
                JSONObject()
                    .put("playerId", p.id)
                    .put("pseudo", p.pseudo)
                    .put("role", p.role.name)
            )
        }
        val scores = JSONObject()
        totalScores.forEach { (id, points) -> scores.put(id.toString(), points) }
        val (type, winnerIds, byGuess) = victory.toWire()
        return JSONObject()
            .put("victoryType", type)
            .put("winnerIds", JSONArray(winnerIds.toList()))
            .put("byGuess", byGuess)
            .put("players", playersArray)
            .put("totalScores", scores)
    }

    fun syncPayload(
        inRound: Boolean,
        participating: Boolean,
        phase: String,
        privateRole: Role? = null,
        privateWord: String? = null,
        board: JSONObject? = null,
        revealAcked: Int = 0,
        revealTotal: Int = 0,
        alreadyRevealed: Boolean = false,
        alreadyVoted: Boolean = false,
        elimination: JSONObject? = null,
        pendingUnknownGuess: Boolean = false,
        result: JSONObject? = null
    ): JSONObject {
        val payload = JSONObject()
            .put("inRound", inRound)
            .put("participating", participating)
            .put("phase", phase)
            .put("revealAcked", revealAcked)
            .put("revealTotal", revealTotal)
            .put("alreadyRevealed", alreadyRevealed)
            .put("alreadyVoted", alreadyVoted)
            .put("pendingUnknownGuess", pendingUnknownGuess)
            .put("board", board ?: JSONObject.NULL)
            .put("elimination", elimination ?: JSONObject.NULL)
            .put("result", result ?: JSONObject.NULL)
        if (participating && privateRole != null) {
            payload.put("private", privatePayload(privateRole, privateWord.orEmpty()))
        } else {
            payload.put("private", JSONObject.NULL)
        }
        return payload
    }

    // --- Désérialisation (invité) ---

    fun parsePrivate(obj: JSONObject): Pair<Role, String>? {
        val role = parseRole(obj.optString("role")) ?: return null
        return role to obj.optString("word", "")
    }

    fun parseBoard(obj: JSONObject): BoardSnapshot {
        val players = parsePublicPlayers(obj.optJSONArray("players"))
        return BoardSnapshot(
            players = players,
            clueOrder = parseIntList(obj.optJSONArray("clueOrder")),
            roundNumber = obj.optInt("roundNumber"),
            turnNumber = obj.optInt("turnNumber"),
            category = obj.optNullableString("category"),
            votePhase = parseVotePhase(obj.optString("votePhase")),
            currentVoterId = if (obj.isNull("currentVoterId")) null else obj.optInt("currentVoterId"),
            tiedCandidates = parseIntList(obj.optJSONArray("tiedCandidates")).toSet(),
            votedPlayerIds = parseIntList(obj.optJSONArray("votedPlayerIds")),
            noElimination = parseNoElimination(obj)
        )
    }

    fun parseElimination(obj: JSONObject): EliminationSnapshot? {
        val role = parseRole(obj.optString("role")) ?: return null
        return EliminationSnapshot(
            playerId = obj.optInt("playerId"),
            pseudo = obj.optString("pseudo"),
            role = role,
            turnNumber = obj.optInt("turnNumber"),
            guessResolved = obj.optBoolean("guessResolved"),
            guessCorrect = obj.optBoolean("guessCorrect"),
            guessText = obj.optNullableString("guessText"),
            votes = parseVoteReveals(obj.optJSONArray("votes"))
        )
    }

    fun parseResult(obj: JSONObject): ResultSnapshot {
        val players = buildList {
            val arr = obj.optJSONArray("players") ?: return@buildList
            for (i in 0 until arr.length()) {
                val p = arr.optJSONObject(i) ?: continue
                add(
                    Player(
                        id = p.optInt("playerId"),
                        pseudo = p.optString("pseudo"),
                        role = parseRole(p.optString("role")) ?: Role.CITIZEN,
                        assignedWord = ""
                    )
                )
            }
        }
        val scores = mutableMapOf<Int, Int>()
        obj.optJSONObject("totalScores")?.let { scoresObj ->
            val keys = scoresObj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                scores[key.toInt()] = scoresObj.optInt(key)
            }
        }
        val victory = victoryFromWire(
            type = obj.optString("victoryType"),
            winnerIds = parseIntList(obj.optJSONArray("winnerIds")).toSet(),
            byGuess = obj.optBoolean("byGuess")
        )
        return ResultSnapshot(victory = victory, players = players, totalScores = scores)
    }

    fun parseSync(obj: JSONObject): SyncSnapshot {
        val participating = obj.optBoolean("participating", false)
        val privateObj = if (obj.isNull("private")) null else obj.optJSONObject("private")
        val parsedPrivate = privateObj?.let { parsePrivate(it) }
        val boardObj = if (obj.isNull("board")) null else obj.optJSONObject("board")
        val elimObj = if (obj.isNull("elimination")) null else obj.optJSONObject("elimination")
        val resultObj = if (obj.isNull("result")) null else obj.optJSONObject("result")
        return SyncSnapshot(
            inRound = obj.optBoolean("inRound"),
            participating = participating,
            phase = obj.optString("phase"),
            privateRole = if (participating) parsedPrivate?.first else null,
            privateWord = if (participating) parsedPrivate?.second else null,
            board = boardObj?.let { parseBoard(it) },
            revealAcked = obj.optInt("revealAcked"),
            revealTotal = obj.optInt("revealTotal"),
            alreadyRevealed = obj.optBoolean("alreadyRevealed"),
            alreadyVoted = obj.optBoolean("alreadyVoted"),
            elimination = elimObj?.let { parseElimination(it) },
            pendingUnknownGuess = obj.optBoolean("pendingUnknownGuess"),
            result = resultObj?.let { parseResult(it) }
        )
    }

    // --- Helpers ---

    private fun parsePublicPlayers(arr: JSONArray?): List<PublicPlayer> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val p = arr.optJSONObject(i) ?: continue
                add(
                    PublicPlayer(
                        playerId = p.optInt("playerId"),
                        pseudo = p.optString("pseudo"),
                        status = parseStatus(p.optString("status")),
                        role = if (p.isNull("role")) null else parseRole(p.optString("role")),
                        connected = p.optBoolean("connected", true)
                    )
                )
            }
        }
    }

    private fun parseIntList(arr: JSONArray?): List<Int> {
        if (arr == null) return emptyList()
        return buildList { for (i in 0 until arr.length()) add(arr.optInt(i)) }
    }

    private fun parseVoteReveals(arr: JSONArray?): List<VoteReveal> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val voter = o.optString("voter")
                val target = o.optString("target")
                if (voter.isNotBlank() && target.isNotBlank()) {
                    add(VoteReveal(voterPseudo = voter, targetPseudo = target))
                }
            }
        }
    }

    private fun parseRole(value: String): Role? =
        runCatching { Role.valueOf(value) }.getOrNull()

    private fun parseStatus(value: String): PlayerStatus =
        runCatching { PlayerStatus.valueOf(value) }.getOrDefault(PlayerStatus.ACTIVE)

    private fun parseVotePhase(value: String): VotePhase =
        runCatching { VotePhase.valueOf(value) }.getOrDefault(VotePhase.IDLE)

    private fun parseNoElimination(obj: JSONObject): NoElimination? {
        if (!obj.has("noElimination") || obj.isNull("noElimination")) return null
        return runCatching { NoElimination.valueOf(obj.optString("noElimination")) }.getOrNull()
    }
}

// --- Conversions de Victory vers/depuis le format "filaire" ---

private fun Victory.toWire(): Triple<String, Set<Int>, Boolean> = when (this) {
    Victory.Ongoing -> Triple("ONGOING", emptySet(), false)
    Victory.Citizen -> Triple("CITIZEN", emptySet(), false)
    Victory.Impostor -> Triple("IMPOSTOR", emptySet(), false)
    is Victory.Unknown -> Triple("UNKNOWN", winnerIds, byGuess)
    Victory.Combined -> Triple("COMBINED", emptySet(), false)
}

private fun victoryFromWire(type: String, winnerIds: Set<Int>, byGuess: Boolean): Victory = when (type) {
    "CITIZEN" -> Victory.Citizen
    "IMPOSTOR" -> Victory.Impostor
    "UNKNOWN" -> Victory.Unknown(winnerIds, byGuess)
    "COMBINED" -> Victory.Combined
    else -> Victory.Ongoing
}

/** Retourne null si la clé est absente ou vaut JSONObject.NULL. */
private fun JSONObject.optNullableString(key: String): String? =
    if (isNull(key)) null else optString(key).ifBlank { null }
