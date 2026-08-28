// Point d'entrée du client web invité. Lit ?code= dans l'URL, monte l'app,
// relie le socket au store et pilote le rendu.

import "./styles.css";
import { GuestSocket } from "./socket";
import { createStore, handleGameEvent, initialState, type AppState } from "./state";
import type { Actions } from "./actions";
import { mount } from "./ui";
import { PLAYER_GUESS, PLAYER_REVEAL, PLAYER_VOTE } from "./protocol";
import { renderJoin } from "./screens/join";
import { renderHome } from "./screens/home";
import { renderWaiting } from "./screens/waiting";
import { renderReveal } from "./screens/reveal";
import { renderBoard } from "./screens/board";
import { renderElimination } from "./screens/elimination";
import { renderResult } from "./screens/result";

function resolveServerUrl(): string {
  const params = new URLSearchParams(window.location.search);
  const server = params.get("server");
  if (server) return server;
  return window.location.origin;
}

function resolveCode(): string | null {
  const params = new URLSearchParams(window.location.search);
  return params.get("code");
}

/** Identifiant stable du navigateur (sessionStorage) pour reprendre la connexion
 *  après un verrouillage d'écran ou une suspension d'onglet. */
function getClientId(): string {
  const key = "lemytho-client-id";
  let id = sessionStorage.getItem(key);
  if (!id) {
    id =
      typeof crypto.randomUUID === "function"
        ? crypto.randomUUID()
        : Math.random().toString(36).slice(2) + Date.now().toString(36);
    sessionStorage.setItem(key, id);
  }
  return id;
}

const clientId = getClientId();

/** Session active (salon rejoint), persistée en sessionStorage pour survivre à un
 *  rechargement de la page. sessionStorage est propre à l'onglet : il survit au
 *  rechargement mais est effacé à la fermeture de l'onglet. */
interface StoredSession {
  serverUrl: string;
  code: string;
  pseudo: string;
}

const SESSION_KEY = "lemytho-session";

function loadSession(): StoredSession | null {
  const raw = sessionStorage.getItem(SESSION_KEY);
  if (!raw) return null;
  try {
    const parsed = JSON.parse(raw) as StoredSession;
    if (typeof parsed.code === "string" && typeof parsed.pseudo === "string") {
      return parsed;
    }
  } catch {
    /* ignore */
  }
  sessionStorage.removeItem(SESSION_KEY);
  return null;
}

function saveSession(session: StoredSession): void {
  sessionStorage.setItem(SESSION_KEY, JSON.stringify(session));
}

function clearSession(): void {
  sessionStorage.removeItem(SESSION_KEY);
}

/** Salon actif (pour une reprise automatique après reconnexion ou rechargement). */
let activeSession: StoredSession | null = loadSession();

const rootElement = document.getElementById("app");
if (!rootElement) {
  throw new Error("Élément #app introuvable");
}
const root: HTMLElement = rootElement;

const serverUrl = resolveServerUrl();
const store = createStore(initialState(serverUrl, resolveCode()));

const socket = new GuestSocket({
  onStatus: (status) => store.set({ connectionStatus: status }),
  onLobbyUpdate: (members) => store.set({ members }),
  onLobbyClosed: () => {
    activeSession = null;
    clearSession();
    socket.disconnect();
    store.set({
      error: "Le salon a été fermé par l'hôte",
      screen: "join",
      myPlayerId: null,
      lobbyCode: null,
      members: [],
      inRound: false,
      wantsReplay: false,
    });
  },
  onGameEvent: (event) => handleGameEvent(store, event.name, event.data),
  onReconnected: () => {
    // Reprise automatique après une déconnexion brève : on rejoint le salon actif
    // avec le même clientId, ce qui préserve le playerId côté serveur.
    if (activeSession && store.state.myPlayerId != null) {
      rejoin();
    }
  },
});

/** Rejoint (ou rejoint à nouveau) le salon actif, puis restaure l'état local. */
function rejoin(): void {
  if (!activeSession) return;
  const session = activeSession;
  socket.joinLobby(session.code, session.pseudo, clientId).then((ack) => {
    if (ack.ok) {
      store.set({
        screen: "waiting",
        myPlayerId: ack.playerId ?? store.state.myPlayerId,
        lobbyCode: ack.code ?? session.code,
        joinCode: session.code,
        members: ack.members ?? [],
        error: null,
      });
    } else {
      activeSession = null;
      clearSession();
      store.set({
        screen: "join",
        myPlayerId: null,
        lobbyCode: null,
        members: [],
        error: ack.message ?? "Reconnexion impossible",
      });
    }
  });
}

// Retour au premier plan : force la reconnexion si l'onglet a été suspendu.
document.addEventListener("visibilitychange", () => {
  if (!document.hidden && activeSession && store.state.myPlayerId != null) {
    socket.reconnect();
  }
});

// Rechargement de page : le salon est restauré depuis sessionStorage, on se
// reconnecte et on rejoint automatiquement (le clientId est inchangé).
if (activeSession) {
  store.set({
    serverUrl: activeSession.serverUrl,
    joinCode: activeSession.code,
    pseudo: activeSession.pseudo,
    lobbyCode: activeSession.code,
    screen: "waiting",
  });
  socket.connect(activeSession.serverUrl);
  rejoin();
}

const actions: Actions = {
  showJoin() {
    store.set({ screen: "join" });
  },
  join(code, pseudo) {
    const normalized = code.trim().toUpperCase();
    const trimmedPseudo = pseudo.trim();
    // Mémorise la saisie pour la réafficher après une erreur (re-render).
    store.set({ joinCode: normalized, pseudo: trimmedPseudo });
    if (trimmedPseudo.length === 0) {
      store.set({ error: "Indique un pseudo pour rejoindre la partie" });
      return;
    }
    if (normalized.length === 0) {
      store.set({ error: "Renseigne le code du salon" });
      return;
    }
    socket.connect(store.state.serverUrl);
    socket.joinLobby(normalized, trimmedPseudo, clientId).then((ack) => {
      if (ack.ok) {
        activeSession = {
          code: normalized,
          pseudo: trimmedPseudo,
          serverUrl: store.state.serverUrl,
        };
        saveSession(activeSession);
        store.set({
          screen: "waiting",
          myPlayerId: ack.playerId ?? null,
          lobbyCode: ack.code ?? normalized,
          joinCode: normalized,
          members: ack.members ?? [],
          error: null,
        });
      } else {
        store.set({ error: ack.message ?? "Erreur inconnue" });
      }
    });
  },
  setReady(ready) {
    const myId = store.state.myPlayerId;
    if (myId == null) return;
    store.set({
      members: store.state.members.map((m) =>
        m.playerId === myId ? { ...m, ready } : m,
      ),
    });
    socket.setReady(ready);
  },
  quit() {
    activeSession = null;
    clearSession();
    socket.disconnect();
    store.set({
      screen: "home",
      myPlayerId: null,
      lobbyCode: null,
      members: [],
      error: null,
      myRole: null,
      myWord: null,
      revealConfirmed: false,
      revealAcked: 0,
      revealTotal: 0,
      board: null,
      elimination: null,
      guestResult: null,
      hasVoted: false,
      guessSubmitted: false,
      unknownGuessCorrect: null,
      inRound: false,
      wantsReplay: false,
    });
  },
  guestRevealDone() {
    const state = store.state;
    const myId = state.myPlayerId;
    if (myId == null) return;
    socket.sendToHost(PLAYER_REVEAL, { playerId: myId });
    store.set({ revealConfirmed: true });
  },
  guestCastVote(targetId) {
    const state = store.state;
    const myId = state.myPlayerId;
    if (myId == null) return;
    socket.sendToHost(PLAYER_VOTE, { playerId: myId, targetId });
    store.set({ hasVoted: true });
  },
  guestSubmitGuess(text) {
    const state = store.state;
    const myId = state.myPlayerId;
    const elimination = state.elimination;
    if (myId == null || elimination == null || state.guessSubmitted) return;
    if (elimination.playerId !== myId || elimination.role !== "UNKNOWN") return;
    if (elimination.guessResolved) return;
    const trimmed = text.trim().slice(0, 80);
    if (trimmed.length === 0) return;
    socket.sendToHost(PLAYER_GUESS, { playerId: myId, text: trimmed });
    store.set({ guessSubmitted: true });
  },
  guestSeeResults() {
    if (store.state.guestResult == null) return;
    store.set({ screen: "result" });
  },
  guestMarkReady() {
    const state = store.state;
    const myId = state.myPlayerId;
    if (state.wantsReplay || myId == null) return;
    socket.setReady(true);
    store.set({
      wantsReplay: true,
      members: state.members.map((m) =>
        m.playerId === myId ? { ...m, ready: true } : m,
      ),
      screen: "waiting",
    });
  },
};

function render(): void {
  const state: AppState = store.state;
  switch (state.screen) {
    case "home":
      mount(root, renderHome(actions));
      break;
    case "join":
      mount(root, renderJoin(state, actions));
      break;
    case "waiting":
      mount(root, renderWaiting(state, actions));
      break;
    case "reveal":
      mount(root, renderReveal(state, actions));
      break;
    case "board":
      mount(root, renderBoard(state, actions));
      break;
    case "elimination":
      mount(root, renderElimination(state, actions));
      break;
    case "result":
      mount(root, renderResult(state, actions));
      break;
  }
}

store.subscribe(render);
render();
