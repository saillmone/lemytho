// Machine à état du client web invité. Miroir de MultiplayerViewModel.handleGameEvent,
// restreint à la posture « invité » (pas de logique hôte, pas de règles de jeu).

import type {
  BoardSnapshot,
  EliminationSnapshot,
  LobbyMember,
  ResultSnapshot,
  Role,
  SyncSnapshot,
} from "./protocol";
import {
  GAME_BOARD,
  GAME_CANCELLED,
  GAME_ELIMINATION,
  GAME_PHASE,
  GAME_PRIVATE,
  GAME_RESULT,
  GAME_REVEAL_ACK,
  GAME_START,
  GAME_SYNC,
  PHASE_CLUE,
  PHASE_ELIMINATION,
  PHASE_RESULT,
  PHASE_REVEAL,
  PLAYER_PRESENCE,
  cancelledMessage,
  parseBoard,
  parseCancelledReason,
  parseElimination,
  parsePrivate,
  parseResult,
  parseStartPlayerIds,
  parseSync,
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
  participating: boolean;
  roundPlayerIds: number[];
  wantsReplay: boolean;
  awaitingSync: boolean;
  hostPaused: boolean;
  wakeLockFailed: boolean;
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
    participating: false,
    roundPlayerIds: [],
    wantsReplay: false,
    awaitingSync: false,
    hostPaused: false,
    wakeLockFailed: false,
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
    case PLAYER_PRESENCE: {
      const o = (data ?? {}) as Record<string, unknown>;
      const playerId = typeof o.playerId === "number" ? o.playerId : -1;
      const connected = o.connected === true;
      const members = store.state.members.map((m) =>
        m.playerId === playerId ? { ...m, connected } : m,
      );
      const board = store.state.board
        ? {
            ...store.state.board,
            players: store.state.board.players.map((p) =>
              p.playerId === playerId ? { ...p, connected } : p,
            ),
          }
        : store.state.board;
      const hostPaused =
        playerId === 1 && store.state.inRound ? !connected : store.state.hostPaused;
      store.set({ members, board, hostPaused });
      break;
    }
    case GAME_START: {
      const myId = store.state.myPlayerId;
      if (myId == null) break;
      const playerIds = parseStartPlayerIds(data);
      if (playerIds.includes(myId)) {
        store.set({ roundPlayerIds: playerIds });
        break;
      }
      store.set({
        roundPlayerIds: playerIds,
        inRound: true,
        participating: false,
        error: null,
        screen: "waiting",
      });
      break;
    }
    case GAME_SYNC: {
      applySync(store, parseSync(data));
      break;
    }
    case GAME_PRIVATE: {
      const parsed = parsePrivate(data);
      if (!parsed) return;
      store.set({
        myRole: parsed.role,
        myWord: parsed.word,
        inRound: true,
        participating: true,
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
        awaitingSync: false,
        hostPaused: false,
        screen: "reveal",
      });
      break;
    }
    case GAME_BOARD: {
      if (!store.state.inRound || !store.state.participating) return;
      const board = parseBoard(data);
      const previous = store.state.board;
      const newVoteRound =
        previous == null ||
        previous.votePhase !== board.votePhase ||
        previous.turnNumber !== board.turnNumber ||
        previous.roundNumber !== board.roundNumber;
      const stayOnReveal =
        store.state.screen === "reveal" &&
        board.clueOrder.length === 0 &&
        board.votePhase === "IDLE";
      const keepScreen =
        store.state.screen === "elimination" ||
        store.state.screen === "result" ||
        stayOnReveal;
      store.set({
        board,
        hasVoted:
          board.votePhase === "IDLE" ? false : newVoteRound ? false : store.state.hasVoted,
        screen: keepScreen ? store.state.screen : "board",
      });
      break;
    }
    case GAME_PHASE: {
      if (!store.state.inRound || !store.state.participating) return;
      const o = (data ?? {}) as Record<string, unknown>;
      if (o.phase !== PHASE_CLUE) break;
      if (store.state.screen === "result") break;
      store.set({
        elimination: null,
        guessSubmitted: false,
        screen: "board",
      });
      break;
    }
    case GAME_ELIMINATION: {
      if (!store.state.inRound || !store.state.participating) return;
      const elimination = parseElimination(data);
      if (elimination) {
        store.set({
          elimination,
          guestResult: null,
          guessSubmitted: elimination.guessResolved || store.state.guessSubmitted,
          unknownGuessCorrect: elimination.guessCorrect
            ? true
            : elimination.guessResolved
              ? false
              : store.state.unknownGuessCorrect,
          screen: "elimination",
        });
      }
      break;
    }
    case GAME_RESULT: {
      if (!store.state.inRound || !store.state.participating) return;
      const parsed = parseResult(data);
      const found = parsed?.victory.type === "UNKNOWN" && parsed.victory.byGuess;
      const missedGuess =
        !found && store.state.elimination?.role === "UNKNOWN";
      store.set({
        guestResult: parsed,
        inRound: false,
        roundPlayerIds: [],
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
      store.set({
        screen: "waiting",
        error: cancelledMessage(parseCancelledReason(data)),
        inRound: false,
        participating: false,
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
        awaitingSync: false,
        hostPaused: false,
        roundPlayerIds: [],
      });
      break;
    }
    default:
      break;
  }
}

function applySync(store: Store, sync: SyncSnapshot): void {
  if (!sync.inRound) {
    store.set({
      inRound: false,
      participating: false,
      awaitingSync: false,
      hostPaused: false,
      roundPlayerIds: [],
      screen: store.state.screen === "result" ? "result" : "waiting",
    });
    return;
  }
  if (!sync.participating) {
    const hostOffline = store.state.members.find((m) => m.isHost)?.connected === false;
    store.set({
      inRound: true,
      participating: false,
      roundPlayerIds: sync.board?.players.map((p) => p.playerId) ?? [],
      awaitingSync: false,
      myRole: null,
      myWord: null,
      board: sync.board,
      elimination: sync.elimination,
      guestResult: sync.result,
      hostPaused: hostOffline,
      error: null,
      screen: "waiting",
    });
    return;
  }
  const myId = store.state.myPlayerId;
  const phaseScreen: Screen =
    sync.phase === PHASE_REVEAL
      ? "reveal"
      : sync.phase === PHASE_ELIMINATION
        ? "elimination"
        : sync.phase === PHASE_RESULT
          ? sync.result
            ? "result"
            : "elimination"
          : "board";
  const pendingMine = sync.pendingUnknownGuess && sync.elimination?.playerId === myId;
  store.set({
    inRound: true,
    participating: true,
    awaitingSync: false,
    hostPaused: false,
    myRole: sync.privateRole ?? store.state.myRole,
    myWord: sync.privateWord ?? store.state.myWord,
    board: sync.board ?? store.state.board,
    revealAcked: sync.revealAcked,
    revealTotal: sync.revealTotal,
    revealConfirmed: sync.alreadyRevealed,
    hasVoted: sync.alreadyVoted,
    elimination: sync.elimination,
    guestResult: sync.result,
    guessSubmitted: pendingMine
      ? false
      : sync.elimination?.guessResolved
        ? true
        : store.state.guessSubmitted,
    unknownGuessCorrect: sync.elimination?.guessCorrect
      ? true
      : sync.elimination?.guessResolved
        ? false
        : store.state.unknownGuessCorrect,
    screen: phaseScreen,
  });
}
