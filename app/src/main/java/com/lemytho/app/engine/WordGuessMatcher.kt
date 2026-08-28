package com.lemytho.app.engine

import java.text.Normalizer

/**
 * Compare une saisie de l'Inconnu au mot des Citoyens.
 * Tolérance orthographique (accents, articles, faute de frappe), pas sémantique.
 */
object WordGuessMatcher {

    const val MAX_GUESS_LENGTH = 80

    private val combiningMarks = Regex("\\p{M}+")
    private val apostrophesAndHyphens = Regex("[\\u0027\\u2019\\u02BC\\-–—]")
    private val whitespace = Regex("\\s+")
    private val articles = setOf("le", "la", "les", "un", "une", "l", "d")

    fun matchesCitizenWord(guess: String, citizenWord: String): Boolean {
        val trimmed = guess.trim()
        if (trimmed.isEmpty()) return false
        val targetBase = concatenate(citizenWord)
        if (targetBase.isEmpty()) return false
        val threshold = distanceThreshold(targetBase.length)
        val guessVariants = variants(concatenate(trimmed))
        val targetVariants = variants(targetBase)
        for (guessForm in guessVariants) {
            for (targetForm in targetVariants) {
                if (levenshtein(guessForm, targetForm) <= threshold) return true
            }
        }
        return false
    }

    private fun concatenate(raw: String): String {
        val normalized = stripMarks(
            Normalizer.normalize(raw.lowercase(), Normalizer.Form.NFD)
        )
        val spaced = apostrophesAndHyphens.replace(normalized, " ")
        val tokens = spaced.split(whitespace).filter { it.isNotEmpty() && it !in articles }
        return tokens.joinToString("")
    }

    private fun stripMarks(nfd: String): String = combiningMarks.replace(nfd, "")

    private fun variants(base: String): List<String> = buildList {
        add(base)
        if (base.length > 3 && base.endsWith("s")) add(base.dropLast(1))
    }

    private fun distanceThreshold(length: Int): Int = when {
        length <= 4 -> 0
        length <= 8 -> 1
        else -> 2
    }

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        val prev = IntArray(b.length + 1) { it }
        val curr = IntArray(b.length + 1)
        for (i in 1..a.length) {
            curr[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = minOf(curr[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            for (j in prev.indices) prev[j] = curr[j]
        }
        return prev[b.length]
    }
}
