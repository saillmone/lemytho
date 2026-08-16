# LOCAL_SETUP — Installer et lancer OpenCover sur ta machine

Ce guide t'amène d'une machine "vierge" (aucun outil Android installé) jusqu'au premier build
réussi de l'application. Suis les étapes dans l'ordre.

> État constaté de ta machine au moment de la rédaction : **Git installé**, mais **pas de Java,
> pas de Gradle, pas d'Android SDK, pas d'Android Studio**.

---

## Sommaire

1. Installer Android Studio
2. Installer le SDK Android + l'émulateur
3. Ouvrir le projet et synchroniser Gradle
4. Créer un émulateur (AVD)
5. Compiler l'APK en ligne de commande
6. Serveur relais (multijoueur)
7. Premier commit + premier push (Git)
8. Dépannage
9. Versions utilisées par le projet

---

## 1. Installer Android Studio

Android Studio fournit **tout ce qui manque** : le JDK (Java 17, inclus), le SDK Android,
l'émulateur et les outils de build. Tu n'as donc **rien à installer séparément** pour Java.

### Option A — ligne de commande (recommandé, via winget)

Ouvre **PowerShell** et tape :

```powershell
winget install --id Google.AndroidStudio -e
```

### Option B — téléchargement manuel

1. Va sur <https://developer.android.com/studio>
2. Télécharge l'installateur Windows (`.exe`)
3. Lance-le et laisse les options par défaut.

À la fin de l'installation, lance Android Studio. **Ne crée pas de nouveau projet** : on va
ouvrir le projet OpenCover existant à l'étape 3.

---

## 2. Installer le SDK Android + l'émulateur

Au premier lancement, Android Studio affiche un assistant ("Setup Wizard"). Laisse-le installer
le SDK par défaut, puis vérifie les composants :

1. Ouvre **File > Settings > Languages & Frameworks > Android SDK** (ou "SDK Manager").
2. Dans l'onglet **SDK Platforms**, coche **Android 15 (API level 35)**.
3. Dans l'onglet **SDK Tools**, vérifie que sont cochés :
   - **Android SDK Build-Tools 35**
   - **Android SDK Platform-Tools**
   - **Android Emulator**
   - **Android SDK Command-line Tools (latest)**
4. Clique sur **Apply** / **OK** pour télécharger ce qui manque.

> Le chemin du SDK par défaut est `%LOCALAPPDATA%\Android\Sdk`.
> C'est ce chemin qu'Android Studio écrira automatiquement dans `local.properties`
> (fichier local, non versionné — il est déjà dans le `.gitignore`).

---

## 3. Ouvrir le projet et synchroniser Gradle

1. Dans Android Studio : **File > Open**, puis sélectionne le dossier `opencover`
   (celui qui contient `settings.gradle.kts`).
2. Android Studio détecte `gradle/wrapper/gradle-wrapper.properties` et **télécharge Gradle 8.9**
   automatiquement. Il génère aussi les fichiers du wrapper (`gradlew`, `gradlew.bat`,
   `gradle-wrapper.jar`) au premier sync.
3. Accepte la synchronisation ("Sync Now").
4. Si un popup te propose d'installer des composants SDK manquants, accepte.

Première synchronisation = téléchargement de Gradle + des dépendances → peut prendre plusieurs
minutes. C'est normal.

> **Point de contrôle** : en bas, l'onglet "Build" doit afficher `BUILD SUCCESSFUL`
> (ou aucun message d'erreur rouge).

---

## 4. Créer un émulateur (AVD)

1. **Tools > Device Manager** (icône téléphone).
2. Clique sur **Create Virtual Device**.
3. Choisis un appareil (ex : **Pixel 8**), puis **Next**.
4. Choisis une image système **Android 15 (API 35)**, télécharge-la si besoin, puis **Finish**.
5. Clique sur le triangle ▶ pour lancer l'émulateur.

> L'émulateur est le seul moyen de "voir" l'app tant qu'on n'a pas d'appareil branché.
> Un téléphone physique Android en mode débogage USB marche aussi.

---

## 5. Compiler l'APK en ligne de commande

Ouvre un terminal **dans le dossier `opencover`**, puis tape (Windows) :

```powershell
.\gradlew.bat assembleDebug
```

- `assembleDebug` : construit l'APK de développement (non signé).
- L'APK généré se trouve dans : `app/build/outputs/apk/debug/app-debug.apk`.

> Si `.\gradlew.bat` n'existe pas encore, c'est que le wrapper n'a pas été généré (étape 3).
> Relance la synchronisation Android Studio, ou génère-le depuis le terminal Android Studio
> ("View > Tool Windows > Terminal") avec :
> ```powershell
> gradle wrapper --gradle-version 8.9
> ```

---

## 6. Serveur relais (multijoueur)

Le mode multijoueur repose sur un **serveur relais** (Node.js + Socket.IO) qui ne fait que
transporter les messages entre les appareils. Aucune logique de jeu ne tourne côté serveur :
c'est l'hôte qui mène la partie.

### 6.1. Prérequis

Installe **Node.js 20 LTS** (ou plus récent) depuis <https://nodejs.org> (l'installateur ajoute
`npm`).

### 6.2. Lancer le serveur en local

Dans un terminal, depuis le dossier `server/` :

```powershell
npm install
npm run dev
```

Le serveur écoute sur `http://localhost:3000`.

### 6.3. Adresse du serveur dans l'application

L'URL du serveur est compilée dans l'APK via `BuildConfig.SERVER_URL` (définie dans
`app/build.gradle.kts`). Valeur par défaut :

- **émulateur Android** : `http://10.0.2.2:3000` (alias du `localhost` de la machine hôte) ;
- **téléphone physique** : l'IP locale de la machine, ex. `http://192.168.1.20:3000`.

Tu peux aussi la modifier à la volée dans l'écran « Multijoueur » de l'app.

> En développement, les connexions non chiffrées (HTTP) sont autorisées
> (`android:usesCleartextTraffic="true"` dans le manifeste). En production, on passe par
> **HTTPS/WSS** via le reverse proxy Caddy (voir ci-dessous).

### 6.4. Déploiement en production (VPS + Docker + Caddy)

À la racine du projet, un `docker-compose.yml` orchestre :

- `server` : le serveur relais (image Node.js) ;
- `caddy` : reverse proxy qui fournit automatiquement le **HTTPS** et le **WSS**
  (nécessaires pour un téléphone connecté en 4G/5G).

```powershell
# À la racine du projet, avec Docker installé
$env:OPENCOVER_DOMAIN = "opencover.example.com"
docker compose up -d --build
```

Le domaine doit pointer vers l'IP du VPS (enregistrement DNS `A`). Caddy obtient le certificat
Let's Encrypt tout seul. L'app doit alors être configurée avec l'URL
`https://opencover.example.com`.

### 6.5. Tester le multijoueur

Le test multi-appareils nécessite deux appareils (ou un émulateur + un téléphone) :

1. L'hôte crée une partie : l'écran affiche un **code de salon** (4 lettres).
2. Les invités rejoignent avec ce code.
3. L'hôte choisit la catégorie et lance la partie.
4. Chaque joueur voit son rôle/mot sur son propre écran, puis vote depuis son appareil.

---

## 7. Premier commit + premier push (Git)

Les commandes ci-dessous initialisent le dépôt et poussent le code vers GitHub.

> Remplace `TON_UTILISATEUR` par ton nom d'utilisateur GitHub. Crée d'abord un dépôt **vide**
> nommé `opencover` sur <https://github.com/new> (sans README, sans .gitignore).

```powershell
git init
git add .
git commit -m "Initialisation du projet OpenCover (structure Gradle + Compose)"
git branch -M main
git remote add origin https://github.com/TON_UTILISATEUR/opencover.git
git push -u origin main
```

---

## 8. Dépannage

| Problème | Solution |
| --- | --- |
| `JAVA_HOME` non défini / "java non reconnu" | Normal : Android Studio embarque son propre JDK. Passe par Android Studio pour builder, ou ajoute le JDK d'Android Studio au PATH. |
| `gradlew.bat` introuvable | Le wrapper n'a pas été généré. Voir étape 5 (commande `gradle wrapper`). |
| Erreur "SDK location not found" | Le fichier `local.properties` manque. Ouvre Android Studio, il le crée automatiquement au sync. |
| Sync très long la première fois | Normal (téléchargement Gradle + dépendances). Attends la fin. |
| L'émulateur ne démarre pas | Vérifie que la **virtualisation** est activée dans le BIOS (Hyper-V / Intel VT-x / AMD-V). |

---

## 9. Versions utilisées par le projet

| Outil / librairie | Version |
| --- | --- |
| Kotlin | 2.1.0 |
| Android Gradle Plugin (AGP) | 8.7.2 |
| Gradle | 8.9 |
| JDK | 17 |
| compileSdk / targetSdk | 35 (Android 15) |
| minSdk | 24 (Android 7.0) |
| Compose BOM | 2024.12.01 (Compose 1.7.6) |
| Material 3 | 1.3.1 (piloté par le BOM) |
| Room | 2.6.1 |
| KSP | 2.1.0-1.0.29 |
| Coroutines | 1.8.1 |
| JUnit | 4.13.2 |
