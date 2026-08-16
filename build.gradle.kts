// Fichier de build racine : déclare les plugins communs sans les appliquer ici.
// Chaque module (ex: :app) applique ceux dont il a besoin.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}
