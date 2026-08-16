# **Cahier des charges : Projet OpenCover Android (V9 \- Final Complet)**

## **1\. Rôle et Mode de Fonctionnement (Pour l'IA Cursor)**

Tu es l'Agent Architecte/Lead Developer. L'utilisateur est débutant. Tu dois le guider pas à pas. Ne génère pas tout le code d'un coup. Demande toujours la validation avant d'exécuter des commandes terminal complexes. Tu dois concevoir une application Android open-source (clone de jeu de rôles cachés).

## **2\. Contraintes Techniques & Initialisation**

> * **Langage & Build :** Kotlin 1.9+ avec Gradle (Kotlin DSL : build.gradle.kts).  
> * **UI :** Jetpack Compose (utiliser le Compose BOM le plus récent).  
> * **Architecture :** MVVM strict avec StateFlow.  
> * **Dépendances imposées :** Room (avec plugin KSP) pour la base locale, Lifecycle ViewModel Compose, Coroutines.  
> * **Qualité :** Implémentation obligatoire de **Tests Unitaires (JUnit)** pour valider la logique mathématique du GameEngine avant de coder l'UI.

## **3\. Conformité F-Droid (Critique)**

> * Interdiction absolue de SDK propriétaires (Pas de Google Play Services, Firebase, Crashlytics).  
> * Génération d'un fichier LICENSE (GPLv3).  
> * Création du dossier fastlane/metadata/android/ pour les descriptions du store.

## **4\. Modèles de données**

> * Player : ID, Pseudo, Rôle (Civil, Infiltré, Mr White), Mot assigné, Statut, Score.  
> * WordPair : Id, Catégorie, Mot Civil, Mot Infiltré, Version.  
> * WordRepository : Gère l'accès via Room. Initialisé par le fichier app/src/main/assets/words\_seed.json.

## **5\. Règles Métier & Cas Limites (Anti-bugs)**

> * **Répartition (3 à 20 joueurs) :** Civils \= Arrondi\_Supérieur(Total / 2). Mr White \= 1 (3-10), 2 (11-16), 3 (17-20). Infiltré \= Reste. (Pour 3 joueurs : Forcer 1 Infiltré OU 1 Mr White).  
> * **Élimination :** Le rôle exact est révélé publiquement post-élimination.  
> * **Égalité (Vote) :** Second tour restreint aux ex æquo. Si persistance, élimination aléatoire.  
> * **Élimination de Mr White :** La partie ne s'arrête pas immédiatement. Il dispose d'une ultime et unique tentative pour deviner le mot exact des Civils (validation orale par le groupe, AUCUN champ texte). S'il trouve, il vole la victoire à lui seul.  
> * **Victoire des Civils :** tous les Infiltrés et tous les Mr White sont éliminés.  
> * **Victoire des Infiltrés :** au moins l'un d'eux survit jusqu'à la fin (duel final 1 Civil face à 1 Infiltré, ou tous les Civils éliminés).  
> * **Victoire de Mr White :** il survit jusqu'à la fin (duel final 1 Civil face à 1 Mr White), ou il devine le mot exact lors de son élimination.  
> * **Victoire partagée :** les Civils sont éliminés et il reste à la fois des Infiltrés et Mr White en jeu.  
> * **Scoring :** Civils +2 par Civil vivant ; Infiltrés +10 par Infiltré vivant ; Mr White +6 (devinette ou survie) ; victoire partagée +10 par Infiltré vivant ET +6 par Mr White vivant.

## **6\. Directives UI (Design System Minimaliste)**

> * UI 100% Stateless (Material Design 3). Mode Sombre strict.  
> * Couleurs : Fond gris anthracite. Rouge (destructeur/Infiltré), Cyan (progression/Civil), Blanc (Mr White).  
> * **Écrans :** Home, Setup, Players, Reveal (Bouton "Maintenir pour voir mon mot"), Game Board (Grille \+ Modale de vote).

## **7\. DevOps, Environnement & VPS (Phase 2\)**

> * **LOCAL\_SETUP.md :** Guide pour l'utilisateur sur l'installation de Java/Android SDK et le démarrage de l'émulateur.  
> * **Commandes Git :** Fournir les commandes pour le premier push.  
> * **Infra VPS :** Pour la phase 2, fournir un docker-compose.yml incluant le backend ET un reverse proxy (Caddy ou Traefik) pour générer automatiquement les certificats SSL (HTTPS/WSS obligatoire pour Android).  
> * **CI/CD :** GitHub Actions pour compiler l'APK.

## **8\. Séquence d'exécution (À respecter par l'IA)**

> 1. Générer la structure Gradle et le LOCAL\_SETUP.md.  
> 2. Générer les Data Classes et le WordRepository (avec Room).  
> 3. Générer le GameEngine ET ses tests unitaires.  
> 4. *Attendre la validation de l'utilisateur pour lancer les tests.*  
> 5. Générer les écrans UI (Jetpack Compose).  
> 6. Préparer les fichiers DevOps pour le VPS.

## **9\. Base de données initiale (words\_seed.json)**

À placer dans app/src/main/assets/words\_seed.json :  
\[  
  {"id": 1, "category": "Animaux", "civilWord": "Chat", "undercoverWord": "Chien", "version": 1},  
  {"id": 2, "category": "Nature", "civilWord": "Soleil", "undercoverWord": "Lune", "version": 1},  
  {"id": 3, "category": "Lieux", "civilWord": "Plage", "undercoverWord": "Montagne", "version": 1},  
  {"id": 4, "category": "Nourriture", "civilWord": "Pizza", "undercoverWord": "Burger", "version": 1},  
  {"id": 5, "category": "Lieux", "civilWord": "Cinéma", "undercoverWord": "Théâtre", "version": 1},  
  {"id": 6, "category": "Technologie", "civilWord": "Ordinateur", "undercoverWord": "Tablette", "version": 1},  
  {"id": 7, "category": "Transports", "civilWord": "Avion", "undercoverWord": "Hélicoptère", "version": 1},  
  {"id": 8, "category": "Transports", "civilWord": "Bateau", "undercoverWord": "Sous-marin", "version": 1},  
  {"id": 9, "category": "Nature", "civilWord": "Fleuve", "undercoverWord": "Rivière", "version": 1},  
  {"id": 10, "category": "Métiers", "civilWord": "Docteur", "undercoverWord": "Infirmier", "version": 1}  
\]

## **10\. Prompts des images (mémo)**

> Fichiers déposés dans `app/src/main/res/drawable/`. Contraintes communes : portrait 9:19.5 (1080×2340), aucune lettre/chiffre/texte, fond anthracite (#1E1E1E → #2A2A2A), zone centrale sombre et vide réservée à l'overlay, détails sur les bords.

### `home_bg.jpg`

```text
Full-screen mobile game home screen background, portrait orientation 9:19.5 ratio (1080x2340),
cute and fun cartoon illustration style, flat colors with soft cel shading, playful and charming.
Mood: a mysterious secret-identity party game about to begin, cozy spy theme, inviting and lighthearted.
A small group of cute cartoon characters gathered in a circle at the bottom edge, seen from behind,
heads tilted as if whispering secrets, a few tiny spy gadgets (magnifying glass, hat, mask) doodled
around the corners, soft glowing cyan (#4DD0E1) and white (#FFFFFF) accents, floating sparkles
and doodle stars, subtle dark vignette.
Composition: large dark, uncluttered empty area in the upper half and center of the frame reserved
for an app title and buttons overlay, all characters and details kept low near the bottom and edges.
Dark anthracite background (#1E1E1E to #2A2A2A) to keep it readable with white text on top.
No text, no letters, no words, no numbers, no typography, no watermark, no logo.
High resolution, crisp vector-like edges, vibrant but soft palette, ultra detailed.
```

### `reveal_word_bg.jpg`

```text
Full-screen mobile game background, portrait orientation 9:19.5 ratio (1080x2340),
cute and fun cartoon illustration style, flat colors with soft cel shading, playful and charming.
Mood: a secret word about to be revealed, mysterious but lighthearted, cozy spy theme.
A cute cartoon magnifying glass and a tiny sneaky silhouette of a character peeking from the corner,
soft glowing cyan (#4DD0E1) accents, floating sparkles and small doodle stars, subtle dark vignette.
Composition: dark, uncluttered empty area in the center of the frame reserved for overlay text,
the fun details concentrated around the edges and corners.
Dark anthracite background (#1E1E1E to #2A2A2A) to keep it readable with white text on top.
No text, no letters, no words, no numbers, no typography, no watermark, no logo.
High resolution, crisp vector-like edges, vibrant but soft palette, ultra detailed.
```

### `mr_white_bg.jpg`

```text
Full-screen mobile game background, portrait orientation 9:19.5 ratio (1080x2340),
cute and fun cartoon illustration style, flat colors with soft cel shading, playful and charming.
Mood: an unpredictable and sneaky hidden-role villain, funny and mischievous, lighthearted spy theme.
A cute cartoon character wearing a white hood and a sly grin, winking, holding two big glowing
white chevrons (<< >>) above his head like a secret badge, white light (#FFFFFF) with a soft halo,
small floating question marks and doodle stars around, subtle dark vignette.
Composition: dark, uncluttered empty area in the center of the frame reserved for overlay text,
with the character placed slightly below or at the edge so text can overlay cleanly.
Dark anthracite background (#1E1E1E to #2A2A2A) to keep it readable with white text on top.
No text, no letters, no words, no numbers, no typography, no watermark, no logo.
High resolution, crisp vector-like edges, vibrant but soft palette, ultra detailed.
```

### `setup_bg.jpg`

```text
Full-screen mobile app background, portrait orientation 9:19.5 ratio (1080x2340),
cute and fun cartoon illustration style, flat colors with soft cel shading, playful and charming.
Mood: a group of friends deciding the rules before a secret party game, cozy spy theme.
A row of cute cartoon characters at the bottom edge holding up fingers to count players
(one, two, three fingers), a few tiny floating category icons doodled around the corners
(a leaf, a paw print, a pizza slice, a car), soft glowing cyan (#4DD0E1) accents,
floating sparkles and doodle stars, subtle dark vignette.
Composition: large dark, uncluttered empty area in the upper half and center reserved for
buttons and a dropdown overlay, all characters and details kept low near the bottom and edges.
Dark anthracite background (#1E1E1E to #2A2A2A).
No text, no letters, no words, no numbers, no typography, no watermark, no logo.
High resolution, crisp vector-like edges, vibrant but soft palette, ultra detailed.
```

### `players_bg.jpg`

```text
Full-screen mobile app background, portrait orientation 9:19.5 ratio (1080x2340),
cute and fun cartoon illustration style, flat colors with soft cel shading, playful and charming.
Mood: characters writing their secret identities on little cards before the game, cozy spy theme.
A group of cute cartoon characters at the bottom edge each holding a blank name card and a
pencil, one character winking, a few blank speech bubbles doodled around the corners,
soft glowing cyan (#4DD0E1) accents, floating sparkles and doodle stars, subtle dark vignette.
Composition: large dark, uncluttered empty area in the upper half and center reserved for
text input fields overlay, all characters and details kept low near the bottom and edges.
Dark anthracite background (#1E1E1E to #2A2A2A).
No text, no letters, no words, no numbers, no typography, no watermark, no logo.
High resolution, crisp vector-like edges, vibrant but soft palette, ultra detailed.
```

### `game_board_bg.jpg`

```text
Full-screen mobile app background, portrait orientation 9:19.5 ratio (1080x2340),
cute and fun cartoon illustration style, flat colors with soft cel shading, playful and charming.
Mood: a secret meeting around a round table, suspicion and hidden roles, lighthearted spy theme.
Cute cartoon characters seated around a round table at the bottom edge, seen from above-behind,
one holding a small magnifying glass, a few small voting cards and tiny masks doodled around
the corners, soft glowing cyan (#4DD0E1) and white (#FFFFFF) accents, floating sparkles and
doodle stars, subtle dark vignette.
Composition: large dark, uncluttered empty area in the upper half and center reserved for a
grid of player cards overlay, all characters and details kept low near the bottom and edges.
Dark anthracite background (#1E1E1E to #2A2A2A).
No text, no letters, no words, no numbers, no typography, no watermark, no logo.
High resolution, crisp vector-like edges, vibrant but soft palette, ultra detailed.
```

### `results_bg.jpg`

```text
Full-screen mobile app background, portrait orientation 9:19.5 ratio (1080x2340),
cute and fun cartoon illustration style, flat colors with soft cel shading, playful and charming.
Mood: end of a secret game, victory celebration and friendly suspense, cozy spy theme.
A small podium with cute cartoon characters celebrating at the bottom edge, confetti and
tiny golden trophy doodles around the corners, soft glowing cyan (#4DD0E1) and warm gold
(#FFD54F) accents, floating sparkles and doodle stars, subtle dark vignette.
Composition: large dark, uncluttered empty area in the upper half and center reserved for
a results list and scores overlay, all characters and details kept low near the bottom and edges.
Dark anthracite background (#1E1E1E to #2A2A2A).
No text, no letters, no words, no numbers, no typography, no watermark, no logo.
High resolution, crisp vector-like edges, vibrant but soft palette, ultra detailed.
```

### `eliminated_civil.jpg`

```text
Full-screen mobile game background, portrait orientation 9:19.5 ratio (1080x2340),
cute and fun cartoon illustration style, flat colors with soft cel shading, playful and charming.
Mood: a friendly civilian character eliminated from a secret party game, funny and lighthearted,
not scary, cozy spy theme, gentle slapstick humor.
A cute cartoon character lying on their back in a classic silly "knocked out" pose, simple X eyes
and tongue slightly out in a goofy way, a small toy-like knife stuck harmlessly in the ground beside
them (no blood, no gore, no wounds), a few stars spinning above their head, soft glowing cyan
(#4DD0E1) spotlight fading over them, subtle dark vignette.
Composition: dark, uncluttered empty area in the upper third of the frame reserved for overlay text,
the character placed slightly below center so the text can overlay cleanly, fun details around the edges.
Dark anthracite background (#1E1E1E to #2A2A2A) to keep it readable with white text on top.
No text, no letters, no words, no numbers, no typography, no watermark, no logo.
High resolution, crisp vector-like edges, vibrant but soft palette, ultra detailed.
```

### `eliminated_undercover.jpg`

```text
Full-screen mobile game background, portrait orientation 9:19.5 ratio (1080x2340),
cute and fun cartoon illustration style, flat colors with soft cel shading, playful and charming.
Mood: a sneaky undercover infiltrator character caught and eliminated, funny and mischievous,
lighthearted spy theme, gentle slapstick humor, no gore.
A cute cartoon spy character lying on their back in a silly "knocked out" pose with simple X eyes,
their domino mask half-fallen off and slipping sideways, a small toy-like knife stuck harmlessly in
the ground beside them (no blood, no gore), a tiny secret badge and torn papers scattered around,
soft glowing red (#FF5252) spotlight fading over them, floating sparkles, subtle dark vignette.
Composition: dark, uncluttered empty area in the upper third reserved for overlay text, the character
placed slightly below center, fun details around the edges.
Dark anthracite background (#1E1E1E to #2A2A2A).
No text, no letters, no words, no numbers, no typography, no watermark, no logo.
High resolution, crisp vector-like edges, vibrant but soft palette, ultra detailed.
```

### `eliminated_mrwhite.jpg`

```text
Full-screen mobile game background, portrait orientation 9:19.5 ratio (1080x2340),
cute and fun cartoon illustration style, flat colors with soft cel shading, playful and charming.
Mood: the mysterious white-hooded character eliminated but still scheming, funny and mischievous,
lighthearted spy theme, gentle humor, a hint of "last chance" suspense.
A cute cartoon character in a white hood lying on their back in a silly "knocked out" pose with one
eye peeking open and a sly smirk, a small toy-like knife stuck harmlessly in the ground beside them
(no blood, no gore), several small glowing question marks floating above their head, soft white
(#FFFFFF) spotlight with a gentle halo, floating sparkles, subtle dark vignette.
Composition: dark, uncluttered empty area in the upper third reserved for overlay text, the character
placed slightly below center, fun details around the edges.
Dark anthracite background (#1E1E1E to #2A2A2A).
No text, no letters, no words, no numbers, no typography, no watermark, no logo.
High resolution, crisp vector-like edges, vibrant but soft palette, ultra detailed.
```