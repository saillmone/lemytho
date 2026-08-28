package com.lemytho.app.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WordGuessMatcherTest {

    @Test
    fun `accents ignores`() {
        assertTrue(WordGuessMatcher.matchesCitizenWord("cafe", "Café"))
        assertTrue(WordGuessMatcher.matchesCitizenWord("Café", "Cafe"))
    }

    @Test
    fun `articles initiaux ignores`() {
        assertTrue(WordGuessMatcher.matchesCitizenWord("le chat", "Chat"))
        assertTrue(WordGuessMatcher.matchesCitizenWord("l'ordinateur", "Ordinateur"))
        assertTrue(WordGuessMatcher.matchesCitizenWord("un soleil", "Soleil"))
    }

    @Test
    fun `tirets et espaces concatenes`() {
        assertTrue(WordGuessMatcher.matchesCitizenWord("sous marin", "Sous-marin"))
        assertTrue(WordGuessMatcher.matchesCitizenWord("sousmarin", "Sous-marin"))
        assertTrue(WordGuessMatcher.matchesCitizenWord("sac a dos", "Sac à dos"))
    }

    @Test
    fun `pluriel s accepte`() {
        assertTrue(WordGuessMatcher.matchesCitizenWord("chats", "Chat"))
        assertTrue(WordGuessMatcher.matchesCitizenWord("Chat", "chats"))
    }

    @Test
    fun `x final n est pas coupe`() {
        assertFalse(WordGuessMatcher.matchesCitizenWord("voi", "voix"))
        assertFalse(WordGuessMatcher.matchesCitizenWord("pri", "prix"))
    }

    @Test
    fun `mot court exige l exactitude`() {
        assertTrue(WordGuessMatcher.matchesCitizenWord("Chat", "Chat"))
        assertFalse(WordGuessMatcher.matchesCitizenWord("Chien", "Chat"))
        assertFalse(WordGuessMatcher.matchesCitizenWord("cha", "Chat"))
    }

    @Test
    fun `distance 1 acceptee sur 6 lettres`() {
        assertTrue(WordGuessMatcher.matchesCitizenWord("solei", "Soleil"))
        assertFalse(WordGuessMatcher.matchesCitizenWord("soleille", "Soleil"))
    }

    @Test
    fun `fragment d un mot compose refuse`() {
        assertFalse(WordGuessMatcher.matchesCitizenWord("soleil", "Lunettes de soleil"))
    }

    @Test
    fun `le mot des imposteurs ne suffit pas`() {
        assertFalse(WordGuessMatcher.matchesCitizenWord("Chien", "Chat"))
        assertFalse(WordGuessMatcher.matchesCitizenWord("Lune", "Soleil"))
        assertFalse(WordGuessMatcher.matchesCitizenWord("Lunettes de vue", "Lunettes de soleil"))
    }

    @Test
    fun `saisie vide refusee`() {
        assertFalse(WordGuessMatcher.matchesCitizenWord("", "Chat"))
        assertFalse(WordGuessMatcher.matchesCitizenWord("   ", "Chat"))
    }
}
