package com.lemytho.app.net

import com.lemytho.app.data.model.Player
import com.lemytho.app.data.model.PlayerStatus
import com.lemytho.app.data.model.Role
import com.lemytho.app.engine.Victory
import com.lemytho.app.ui.VotePhase
import org.json.JSONArray
import org.json.JSONObject

/** Joueur tel que vu par un invité (sans rôle ni mot tant que non révélés). */
data class PublicPlayer(
    val playerId: Int,
    val pseudo: String,
    val status: PlayerStatus,
    val role: Role? = null
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
    val tiedCandidates: Set<Int>
)

/** Instantané d'une élimination (rôle révélé publiquement). */
data class EliminationSnapshot(
    val playerId: Int,
    val pseudo: String,
    val role: Role,
    val turnNumber: Int,
    val guessResolved: Boolean = false
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

    fun startPayload(category: String?): JSONObject =
        JSONObject().put("category", category ?: JSONObject.NULL)

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
        tiedCandidates: Set<Int>
    ): JSONObject {
        val playersArray = JSONArray()
        players.forEach { p ->
            playersArray.put(
                JSONObject()
                    .put("playerId", p.id)
                    .put("pseudo", p.pseudo)
                    .put("status", p.status.name)
                    .put("role", if (p.status == PlayerStatus.ELIMINATED) p.role.name else JSONObject.NULL)
            )
        }
        return JSONObject()
            .put("players", playersArray)
            .put("clueOrder", JSONArray(clueOrder))
            .put("roundNumber", roundNumber)
            .put("turnNumber", turnNumber)
            .put("category", category ?: JSONObject.NULL)
            .put("votePhase", votePhase.name)
            .put("currentVoterId", currentVoterId ?: JSONObject.NULL)
            .put("tiedCandidates", JSONArray(tiedCandidates.toList()))
    }

    fun eliminationPayload(
        playerId: Int,
        pseudo: String,
        role: Role,
        turnNumber: Int,
        guessResolved: Boolean = false
    ): JSONObject =
        JSONObject()
            .put("playerId", playerId)
            .put("pseudo", pseudo)
            .put("role", role.name)
            .put("turnNumber", turnNumber)
            .put("guessResolved", guessResolved)

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
            tiedCandidates = parseIntList(obj.optJSONArray("tiedCandidates")).toSet()
        )
    }

    fun parseElimination(obj: JSONObject): EliminationSnapshot? {
        val role = parseRole(obj.optString("role")) ?: return null
        return EliminationSnapshot(
            playerId = obj.optInt("playerId"),
            pseudo = obj.optString("pseudo"),
            role = role,
            turnNumber = obj.optInt("turnNumber"),
            guessResolved = obj.optBoolean("guessResolved")
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
                        role = if (p.isNull("role")) null else parseRole(p.optString("role"))
                    )
                )
            }
        }
    }

    private fun parseIntList(arr: JSONArray?): List<Int> {
        if (arr == null) return emptyList()
        return buildList { for (i in 0 until arr.length()) add(arr.optInt(i)) }
    }

    private fun parseRole(value: String): Role? =
        runCatching { Role.valueOf(value) }.getOrNull()

    private fun parseStatus(value: String): PlayerStatus =
        runCatching { PlayerStatus.valueOf(value) }.getOrDefault(PlayerStatus.ACTIVE)

    private fun parseVotePhase(value: String): VotePhase =
        runCatching { VotePhase.valueOf(value) }.getOrDefault(VotePhase.IDLE)
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
