# Le Mytho

**Jeu de rôles cachés** — en local sur un seul écran, ou en ligne sur plusieurs
téléphones (et même sans app, via le navigateur).

Chaque joueur reçoit un rôle secret. Le but : démasquer les intrus par le débat
et le vote, sans révéler son propre mot.

## Les rôles

| Rôle | Ce qu'il reçoit | Son objectif |
| --- | --- | --- |
| **Citoyen** | Un mot secret, identique pour tous les Citoyens | Démasquer les intrus |
| **Imposteur** | Un mot secret, *proche* de celui des Citoyens | Se faire passer pour un Citoyen |
| **l'Inconnu** | Aucun mot | Deviner le mot des Citoyens sans se faire repérer |

> Citoyens et Imposteurs **ignorent leur rôle au départ** : ils ne le déduisent
> qu'en comparant les indices. Seul l'Inconnu connaît son rôle dès le début.

## Fonctionnalités

- **Partie locale** : tout le monde joue sur le même téléphone (passe le téléphone
  à chaque révélation).
- **Multijoueur en ligne** : un hôte crée une partie, les autres rejoignent avec
  un code de salon à 4 lettres.
- **Client web** : les invités peuvent jouer directement dans leur navigateur,
  sans installer l'app.
- **Deep link** : partage d'une invitation via un lien `lemytho://join?...`.

## Stack technique

| Couche | Techno |
| --- | --- |
| Application Android | Kotlin 2.1, Jetpack Compose (Material 3), MVVM + StateFlow, Room |
| Serveur relais | Node.js + TypeScript + Socket.IO |
| Client web invité | Vite + TypeScript (sans framework) |
| Déploiement | Docker, GitHub Actions |

## Architecture

Le serveur est un **simple relais** : il transporte les messages entre les
appareils mais ne contient **aucune logique de jeu**. C'est l'appareil de
**l'hôte** qui est autoritaire — il distribue les rôles, mène la manche et calcule
les scores. Ce choix garde le serveur minuscule, sans état, et facile à héberger.

```
Invité (app / web)  ⇄  Relais Socket.IO  ⇄  Hôte (app Android, autoritaire)
```

## Démarrage rapide (développement)

Voir [LOCAL_SETUP.md](LOCAL_SETUP.md) pour un guide pas-à-pas complet
(Android Studio, SDK, émulateur, premier build).

```powershell
# App Android
.\gradlew.bat assembleDebug

# Serveur relais (depuis server/)
npm install
npm run dev          # écoute sur http://localhost:3000

# Client web (depuis web/)
npm install
npm run dev
```

## Héberger son propre serveur

Le multijoueur a besoin d'un relais joignable par tous les téléphones. Pour une
partie en local (même Wi-Fi), un simple `npm run dev` suffit. Pour jouer à
distance (4G/5G), il faut un serveur accessible publiquement en **HTTPS/WSS**.

### Option A — Docker + Caddy (recommandée, tout-en-un)

Caddy fournit automatiquement le certificat HTTPS et le proxy WebSocket. Il faut
un **nom de domaine** pointant vers ton serveur (enregistrement DNS `A`).

```powershell
# À la racine du projet, avec Docker installé
$env:LEMYTHO_DOMAIN = "lemytho.exemple.com"
docker compose up -d --build
```

- Le relais est exposé uniquement en interne, Caddy fait le reverse proxy.
- L'APK téléchargeable est servi depuis `./apk` (monté en volume).

### Option B — Docker derrière un nginx existant

Si tu as déjà un nginx (avec certbot) sur ton serveur, utilise la config
allégée qui ne lance que le relais :

```powershell
docker compose -f docker-compose.prod.yml up -d --build
```

Puis ajoute dans nginx un `server_name` vers `127.0.0.1:3000` avec un certificat
Let's Encrypt. Le relais n'écoute que sur `127.0.0.1:3000`, donc il n'est pas
exposable directement.

### Option C — Node.js nu (sans Docker)

```powershell
cd server
npm ci
npm run build
PORT=3000 PUBLIC_DIR=../web/dist node dist/index.js
```

### Variables d'environnement du relais

| Variable | Défaut | Rôle |
| --- | --- | --- |
| `PORT` | `3000` | Port d'écoute |
| `PUBLIC_DIR` | `../web/dist` | Dossier des fichiers statiques (client web + APK) |
| `REJOIN_GRACE_MS` | `600000` | Délai (ms) pendant lequel un invité web déconnecté peut se reconnecter sans perdre sa place |

### Pointer l'app vers ton serveur

L'URL du relais est compilée dans l'APK via `BuildConfig.SERVER_URL`
(`app/build.gradle.kts`). Par défaut : `https://lemytho.duckdns.org`.

```powershell
# Surcharge au build
.\gradlew.bat assembleDebug -Plemytho.serverUrl=https://ton-domaine.com
```

Les joueurs peuvent aussi la modifier à la volée dans l'écran « Multijoueur »
(paramètres avancés).

## Déploiement CI/CD

Deux workflows GitHub Actions (dossier `.github/workflows`) :

- **`apk.yml`** : compile l'APK et l'envoie sur le VPS (`/apk/LeMytho-latest.apk`).
- **`deploy.yml`** : rebuild et redéploie le relais (serveur + client web) via Docker.

Les secrets attendus : `LEMYTHO_HOST`, `LEMYTHO_USER`, `LEMYTHO_SSH_KEY`.

## Licence

[GPL-3.0](LICENSE)
