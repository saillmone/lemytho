package com.lemytho.app.ui

/** Pseudos amusants (thème espion/enquête), partagés entre jeu local et multijoueur. */
object FunnyNames {
    val NAMES = listOf(
        "Sherlock", "Columbo", "Mata Hari", "Arsène", "Le Fouineur",
        "Hercule", "Miss Marple", "Le Corbeau", "Tête Brûlée", "L'Indic",
        "La Taupe", "Double Jeu", "Mr X", "La Silhouette", "L'Ombre",
        "Baron Noir", "Professeur", "La Belette", "Cervelle", "L'Espionne"
    )

    fun random(): String = NAMES.random()
}
