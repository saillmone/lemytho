// Machine à état du client web invité. Miroir de MultiplayerViewModel.handleGameEvent,
// restreint à la posture « invité » (pas de logique hôte, pas de règles de jeu).

import type {
  BoardSnapshot,
  EliminationSnapshot,
  LobbyMember,
  ResultSnapshot,
  Role,
} from "./protocol";
import {
  GAME_BOARD,
  GAME_CANCELLED,
  GAME_ELIMINATION,
  GAME_PRIVATE,
  GAME_RESULT,
  GAME_REVEAL_ACK,
  parseBoard,
  parseElimination,
  parsePrivate,
  parseResult,
} from "./protocol";

export type Screen =
  | "home"
  | "join"
  | "waiting"
  | "reveal"
  | "board"
  | "elimination"
  | "result";

export interface AppState {
  screen: Screen;
  connectionStatus: "disconnected" | "connecting" | "connected" | "error";
  serverUrl: string;
  pseudo: string;
  joinCode: string;
  myPlayerId: number | null;
  lobbyCode: string | null;
  members: LobbyMember[];
  error: string | null;

  myRole: Role | null;
  myWord: string | null;
  revealConfirmed: boolean;
  revealAcked: number;
  revealTotal: number;
  board: BoardSnapshot | null;
  elimination: EliminationSnapshot | null;
  guestResult: ResultSnapshot | null;
  hasVoted: boolean;
  guessSubmitted: boolean;
  unknownGuessCorrect: boolean | null;
  inRound: boolean;
  wantsReplay: boolean;
}

export function initialState(serverUrl: string, code: string | null): AppState {
  return {
    screen: code ? "join" : "home",
    connectionStatus: "disconnected",
    serverUrl,
    pseudo: "",
    joinCode: code ?? "",
    myPlayerId: null,
    lobbyCode: code,
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
  };
}

type Store = {
  state: AppState;
  listeners: Set<() => void>;
  set: (patch: Partial<AppState>) => void;
  subscribe: (fn: () => void) => () => void;
};

export function createStore(initial: AppState): Store {
  const store: Store = {
    state: initial,
    listeners: new Set(),
    set(patch) {
      store.state = { ...store.state, ...patch };
      for (const fn of store.listeners) fn();
    },
    subscribe(fn) {
      store.listeners.add(fn);
      return () => store.listeners.delete(fn);
    },
  };
  return store;
}

export function handleGameEvent(store: Store, name: string, data: unknown): void {
  switch (name) {
    case GAME_PRIVATE: {
      const parsed = parsePrivate(data);
      if (!parsed) return;
      store.set({
        myRole: parsed.role,
        myWord: parsed.word,
        inRound: true,
        wantsReplay: false,
        revealAcked: 0,
        revealTotal: 0,
        board: null,
        elimination: null,
        guestResult: null,
        hasVoted: false,
        guessSubmitted: false,
        unknownGuessCorrect: null,
        revealConfirmed: false,
        screen: "reveal",
      });
      break;
    }
    case GAME_BOARD: {
      if (!store.state.inRound) return;
      store.set({
        board: parseBoard(data),
        elimination: null,
        guestResult: null,
        hasVoted: false,
        guessSubmitted: false,
        revealConfirmed: false,
        screen: "board",
      });
      break;
    }
    case GAME_ELIMINATION: {
      if (!store.state.inRound) return;
      const elimination = parseElimination(data);
      if (elimination) {
        store.set({
          elimination,
          guestResult: null,
          guessSubmitted: elimination.guessResolved || store.state.guessSubmitted,
          unknownGuessCorrect: elimination.guessResolved
            ? false
            : store.state.unknownGuessCorrect,
          screen: "elimination",
        });
      }
      break;
    }
    case GAME_RESULT: {
      if (!store.state.inRound) return;
      // On mémorise le résultat mais on reste sur l'écran d'élimination :
      // l'invité choisit lui-même quand voir le score final.
      const parsed = parseResult(data);
      const found = parsed?.victory.type === "UNKNOWN" && parsed.victory.byGuess;
      const missedGuess =
        !found && store.state.elimination?.role === "UNKNOWN";
      store.set({
        guestResult: parsed,
        inRound: false,
        unknownGuessCorrect: found
          ? true
          : missedGuess
            ? false
            : store.state.unknownGuessCorrect,
      });
      break;
    }
    case GAME_REVEAL_ACK: {
      const o = (data ?? {}) as Record<string, unknown>;
      const acked = typeof o.acked === "number" ? o.acked : 0;
      const total = typeof o.total === "number" ? o.total : 0;
      store.set({ revealAcked: acked, revealTotal: total });
      break;
    }
    case GAME_CANCELLED: {
      // Plus assez de joueurs : retour au salon avec un message.
      store.set({
        screen: "waiting",
        error: "Partie annulée : plus assez de joueurs",
        inRound: false,
        myRole: null,
        myWord: null,
        revealConfirmed: false,
        board: null,
        elimination: null,
        guestResult: null,
        hasVoted: false,
        guessSubmitted: false,
        unknownGuessCorrect: null,
        wantsReplay: false,
      });
      break;
    }
    // game:start et game:phase n'ont pas d'action directe ici.
    default:
      break;
  }
}
