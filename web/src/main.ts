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

/** Identifiant stable du navigateur (localStorage) : la chaise, pas l'onglet. */
function getClientId(): string {
  const key = "lemytho-client-id";
  let id = localStorage.getItem(key);
  if (!id) {
    id =
      typeof crypto.randomUUID === "function"
        ? crypto.randomUUID()
        : Math.random().toString(36).slice(2) + Date.now().toString(36);
    localStorage.setItem(key, id);
  }
  return id;
}

const clientId = getClientId();

interface StoredSession {
  serverUrl: string;
  code: string;
  pseudo: string;
  inRound?: boolean;
}

const SESSION_KEY = "lemytho-session";

function loadSession(): StoredSession | null {
  const raw = localStorage.getItem(SESSION_KEY);
  if (!raw) return null;
  try {
    const parsed = JSON.parse(raw) as StoredSession;
    if (typeof parsed.code === "string" && typeof parsed.pseudo === "string") {
      return parsed;
    }
  } catch {
    /* ignore */
  }
  localStorage.removeItem(SESSION_KEY);
  return null;
}

function saveSession(session: StoredSession): void {
  localStorage.setItem(SESSION_KEY, JSON.stringify(session));
}

function clearSession(): void {
  localStorage.removeItem(SESSION_KEY);
}

let activeSession: StoredSession | null = loadSession();
let joining = false;
type WakeLockHandle = {
  release: () => Promise<void>;
  addEventListener: (type: string, listener: () => void) => void;
};
let wakeLock: WakeLockHandle | null = null;

const rootElement = document.getElementById("app");
if (!rootElement) {
  throw new Error("Élément #app introuvable");
}
const root: HTMLElement = rootElement;

const serverUrl = resolveServerUrl();
const store = createStore(initialState(serverUrl, resolveCode()));

const socket = new GuestSocket({
  onStatus: (status) => store.set({ connectionStatus: status }),
  onLobbyUpdate: (members) => {
    const hostOnline = members.find((m) => m.isHost)?.connected !== false;
    store.set({
      members,
      hostPaused: store.state.inRound && !hostOnline,
    });
  },
  onLobbyClosed: () => {
    activeSession = null;
    clearSession();
    releaseWakeLock();
    socket.disconnect();
    store.set({
      error: "Le salon a été fermé par l'hôte",
      screen: "join",
      myPlayerId: null,
      lobbyCode: null,
      members: [],
      inRound: false,
      participating: false,
      wantsReplay: false,
      awaitingSync: false,
      hostPaused: false,
      roundPlayerIds: [],
    });
  },
  onGameEvent: (event) => {
    handleGameEvent(store, event.name, event.data);
    persistRoundFlag();
  },
  onReconnected: () => {
    if (activeSession && store.state.myPlayerId != null && !joining) {
      rejoin();
    }
  },
});

function persistRoundFlag(): void {
  if (!activeSession) return;
  const inRound = store.state.inRound;
  if (activeSession.inRound !== inRound) {
    activeSession = { ...activeSession, inRound };
    saveSession(activeSession);
  }
}

async function requestWakeLock(): Promise<void> {
  const nav = navigator as Navigator & { wakeLock?: { request: (type: "screen") => Promise<WakeLockSentinel> } };
  if (!nav.wakeLock) {
    store.set({ wakeLockFailed: true });
    return;
  }
  try {
    wakeLock = (await nav.wakeLock.request("screen")) as WakeLockHandle;
    wakeLock.addEventListener("release", () => {
      wakeLock = null;
    });
    store.set({ wakeLockFailed: false });
  } catch {
    store.set({ wakeLockFailed: true });
  }
}

function releaseWakeLock(): void {
  void wakeLock?.release();
  wakeLock = null;
}

/** Rejoint le salon actif sans réinitialiser l'écran de manche. */
function rejoin(): void {
  if (!activeSession || joining) return;
  const session = activeSession;
  joining = true;
  socket
    .connect(session.serverUrl)
    .then((ok) => {
      if (!ok) {
        joining = false;
        store.set({ error: "Reconnexion impossible" });
        return;
      }
      return socket.joinLobby(session.code, session.pseudo, clientId);
    })
    .then((ack) => {
      joining = false;
      if (!ack) return;
      if (ack.ok) {
        const keepRound = store.state.inRound || session.inRound === true;
        store.set({
          myPlayerId: ack.playerId ?? store.state.myPlayerId,
          lobbyCode: ack.code ?? session.code,
          joinCode: session.code,
          members: ack.members ?? [],
          error: null,
          awaitingSync: keepRound,
        });
      } else {
        activeSession = null;
        clearSession();
        releaseWakeLock();
        store.set({
          screen: "join",
          myPlayerId: null,
          lobbyCode: null,
          members: [],
          error: ack.message ?? "Reconnexion impossible",
          awaitingSync: false,
        });
      }
    });
}

async function onForeground(): Promise<void> {
  if (!activeSession) return;
  await requestWakeLock();
  if (socket.isConnected()) {
    const ok = await socket.heartbeat();
    if (!ok) {
      socket.disconnect();
      rejoin();
    }
  } else {
    rejoin();
  }
}

document.addEventListener("visibilitychange", () => {
  if (!document.hidden) void onForeground();
});
window.addEventListener("pageshow", () => {
  void onForeground();
});
window.addEventListener("online", () => {
  void onForeground();
});
window.addEventListener("focus", () => {
  void onForeground();
});

if (activeSession) {
  store.set({
    serverUrl: activeSession.serverUrl,
    joinCode: activeSession.code,
    pseudo: activeSession.pseudo,
    lobbyCode: activeSession.code,
    screen: "waiting",
    awaitingSync: activeSession.inRound === true,
  });
  void requestWakeLock();
  rejoin();
}

const actions: Actions = {
  showJoin() {
    store.set({ screen: "join" });
  },
  join(code, pseudo) {
    const normalized = code.trim().toUpperCase();
    const trimmedPseudo = pseudo.trim();
    store.set({ joinCode: normalized, pseudo: trimmedPseudo });
    if (trimmedPseudo.length === 0) {
      store.set({ error: "Indique un pseudo pour rejoindre la partie" });
      return;
    }
    if (normalized.length === 0) {
      store.set({ error: "Renseigne le code du salon" });
      return;
    }
    joining = true;
    socket.connect(store.state.serverUrl).then((ok) => {
      if (!ok) {
        joining = false;
        store.set({ error: "Délai de connexion dépassé" });
        return;
      }
      return socket.joinLobby(normalized, trimmedPseudo, clientId);
    }).then((ack) => {
      joining = false;
      if (!ack) return;
      if (ack.ok) {
        activeSession = {
          code: normalized,
          pseudo: trimmedPseudo,
          serverUrl: store.state.serverUrl,
          inRound: false,
        };
        saveSession(activeSession);
        void requestWakeLock();
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
    releaseWakeLock();
    void socket.leave().then(() => socket.disconnect());
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
      participating: false,
      wantsReplay: false,
      awaitingSync: false,
      hostPaused: false,
      wakeLockFailed: false,
      roundPlayerIds: [],
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
