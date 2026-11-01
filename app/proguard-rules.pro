# Règles R8/ProGuard spécifiques au projet.
# Socket.IO + OkHttp : le client Java s'appuie sur la réflexion.
-keep class io.socket.** { *; }
-keep class okhttp3.** { *; }
-keep class okio.** { *; }
-dontwarn io.socket.**
-dontwarn okhttp3.**
-dontwarn okio.**
