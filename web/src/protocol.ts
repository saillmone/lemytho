// Miroir TypeScript du contrat de protocole (app/net/Protocol.kt et GameProtocol.kt).
// Le serveur est un simple relais : il route des événements nommés en JSON.

export type Role = "CITIZEN" | "IMPOSTOR" | "UNKNOWN";
export type PlayerStatus = "ACTIVE" | "ELIMINATED";
export type VotePhase = "IDLE" | "VOTING" | "SECOND_ROUND";
export type NoElimination = "TIE" | "NO_VOTES";
export type VictoryType = "CITIZEN" | "IMPOSTOR" | "UNKNOWN" | "COMBINED" | "ONGOING";

// --- Noms d'événements (serveur -> client) ---
export const GAME_START = "game:start";
export const GAME_PRIVATE = "game:private";
export const GAME_BOARD = "game:board";
export const GAME_PHASE = "game:phase";
export const GAME_ELIMINATION = "game:elimination";
export const GAME_RESULT = "game:result";
export const GAME_REVEAL_ACK = "game:revealAck";
export const GAME_CANCELLED = "game:cancelled";
export const GAME_SYNC = "game:sync";

export const CANCEL_NOT_ENOUGH_PLAYERS = "not_enough_players";
export const CANCEL_HOST_CANCELLED = "host_cancelled";
export const CANCEL_ROUND_LOST = "round_lost";

// --- Noms d'événements (client -> hôte via relay:toHost) ---
export const PLAYER_REVEAL = "player:reveal";
export const PLAYER_VOTE = "player:vote";
export const PLAYER_READY = "player:ready";
/** Texte de la devinette de l'Inconnu ({ playerId, text }). */
export const PLAYER_GUESS = "player:guess";
export const PLAYER_PRESENCE = "player:presence";
export const PLAYER_LEFT = "player:left";

// --- Lobby ---
export const LOBBY_CREATE = "lobby:create";
export const LOBBY_JOIN = "lobby:join";
export const LOBBY_READY = "lobby:ready";
export const LOBBY_START = "lobby:start";
export const LOBBY_UPDATE = "lobby:update";
export const LOBBY_CLOSED = "lobby:closed";
export const LOBBY_LEAVE = "lobby:leave";
export const LOBBY_ROUND_ENDED = "lobby:roundEnded";
export const LOBBY_HEARTBEAT = "lobby:heartbeat";

export const PHASE_REVEAL = "REVEAL";
export const PHASE_CLUE = "CLUE";
export const PHASE_VOTE = "VOTE";
export const PHASE_ELIMINATION = "ELIMINATION";
export const PHASE_RESULT = "RESULT";

// --- Types de données ---
export interface LobbyMember {
  playerId: number;
  pseudo: string;
  isHost: boolean;
  ready: boolean;
  connected: boolean;
}

export interface PublicPlayer {
  playerId: number;
  pseudo: string;
  status: PlayerStatus;
  role: Role | null;
  connected: boolean;
}

export interface BoardSnapshot {
  players: PublicPlayer[];
  clueOrder: number[];
  roundNumber: number;
  turnNumber: number;
  category: string | null;
  votePhase: VotePhase;
  currentVoterId: number | null;
  tiedCandidates: number[];
  votedPlayerIds: number[];
  noElimination: NoElimination | null;
}

export interface VoteReveal {
  voterPseudo: string;
  targetPseudo: string;
}

export interface EliminationSnapshot {
  playerId: number;
  pseudo: string;
  role: Role;
  turnNumber: number;
  guessResolved: boolean;
  guessCorrect: boolean;
  guessText: string | null;
  votes: VoteReveal[];
}

export interface ResultPlayer {
  playerId: number;
  pseudo: string;
  role: Role;
}

export interface Victory {
  type: VictoryType;
  winnerIds: number[];
  byGuess: boolean;
}

export interface ResultSnapshot {
  victory: Victory;
  players: ResultPlayer[];
  totalScores: Record<string, number>;
}

export interface SyncSnapshot {
  inRound: boolean;
  participating: boolean;
  phase: string;
  privateRole: Role | null;
  privateWord: string | null;
  board: BoardSnapshot | null;
  revealAcked: number;
  revealTotal: number;
  alreadyRevealed: boolean;
  alreadyVoted: boolean;
  elimination: EliminationSnapshot | null;
  pendingUnknownGuess: boolean;
  result: ResultSnapshot | null;
}

// --- Helpers de parsing ---
function asRecord(v: unknown): Record<string, unknown> {
  return typeof v === "object" && v !== null ? (v as Record<string, unknown>) : {};
}

function optString(obj: Record<string, unknown>, key: string): string {
  const v = obj[key];
  return typeof v === "string" ? v : "";
}

function optInt(obj: Record<string, unknown>, key: string): number {
  const v = obj[key];
  return typeof v === "number" ? v : 0;
}

function optBool(obj: Record<string, unknown>, key: string): boolean {
  return obj[key] === true;
}

function isNull(v: unknown): boolean {
  return v === null || v === undefined;
}

function optNullableString(obj: Record<string, unknown>, key: string): string | null {
  const v = obj[key];
  if (isNull(v)) return null;
  const s = typeof v === "string" ? v : "";
  return s.trim().length === 0 ? null : s;
}

function optNumberArray(obj: Record<string, unknown>, key: string): number[] {
  const v = obj[key];
  if (!Array.isArray(v)) return [];
  return v.filter((x): x is number => typeof x === "number");
}

function parseRole(value: string): Role | null {
  if (value === "CITIZEN" || value === "IMPOSTOR" || value === "UNKNOWN") return value;
  return null;
}

function parseStatus(value: string): PlayerStatus {
  return value === "ELIMINATED" ? "ELIMINATED" : "ACTIVE";
}

function parseVotePhase(value: string): VotePhase {
  if (value === "VOTING" || value === "SECOND_ROUND") return value;
  return "IDLE";
}

function parseNoElimination(value: unknown): NoElimination | null {
  return value === "TIE" || value === "NO_VOTES" ? value : null;
}

export function parseMembers(data: unknown): LobbyMember[] {
  const arr = Array.isArray(data) ? data : [];
  return arr.map((m) => {
    const o = asRecord(m);
    return {
      playerId: optInt(o, "playerId"),
      pseudo: optString(o, "pseudo"),
      isHost: optBool(o, "isHost"),
      ready: optBool(o, "ready"),
      connected: o.connected !== false,
    };
  });
}

export function parsePrivate(data: unknown): { role: Role; word: string } | null {
  const o = asRecord(data);
  const role = parseRole(optString(o, "role"));
  if (!role) return null;
  return { role, word: optString(o, "word") };
}

export function parseBoard(data: unknown): BoardSnapshot {
  const o = asRecord(data);
  const playersArr = Array.isArray(o.players) ? o.players : [];
  const players: PublicPlayer[] = playersArr.map((p) => {
    const po = asRecord(p);
    return {
      playerId: optInt(po, "playerId"),
      pseudo: optString(po, "pseudo"),
      status: parseStatus(optString(po, "status")),
      role: isNull(po.role) ? null : parseRole(optString(po, "role")),
      connected: po.connected !== false,
    };
  });
  return {
    players,
    clueOrder: optNumberArray(o, "clueOrder"),
    roundNumber: optInt(o, "roundNumber"),
    turnNumber: optInt(o, "turnNumber"),
    category: optNullableString(o, "category"),
    votePhase: parseVotePhase(optString(o, "votePhase")),
    currentVoterId: isNull(o.currentVoterId) ? null : optInt(o, "currentVoterId"),
    tiedCandidates: optNumberArray(o, "tiedCandidates"),
    votedPlayerIds: optNumberArray(o, "votedPlayerIds"),
    noElimination: parseNoElimination(o.noElimination),
  };
}

export function parseStartPlayerIds(data: unknown): number[] {
  return optNumberArray(asRecord(data), "playerIds");
}

export function parseCancelledReason(data: unknown): string | null {
  const reason = optString(asRecord(data), "reason");
  return reason.length > 0 ? reason : null;
}

export function cancelledMessage(reason: string | null | undefined): string {
  switch (reason) {
    case CANCEL_NOT_ENOUGH_PLAYERS:
      return "Partie annulée : plus assez de joueurs";
    case CANCEL_HOST_CANCELLED:
      return "Manche annulée par le Maître du Jeu";
    case CANCEL_ROUND_LOST:
      return "La manche a été perdue (l'appli a été fermée)";
    default:
      return "Partie annulée";
  }
}

export function parseElimination(data: unknown): EliminationSnapshot | null {
  const o = asRecord(data);
  const role = parseRole(optString(o, "role"));
  if (!role) return null;
  return {
    playerId: optInt(o, "playerId"),
    pseudo: optString(o, "pseudo"),
    role,
    turnNumber: optInt(o, "turnNumber"),
    guessResolved: optBool(o, "guessResolved"),
    guessCorrect: optBool(o, "guessCorrect"),
    guessText: optNullableString(o, "guessText"),
    votes: parseVoteReveals(o.votes),
  };
}

function parseVoteReveals(raw: unknown): VoteReveal[] {
  if (!Array.isArray(raw)) return [];
  return raw.flatMap((item) => {
    const vo = asRecord(item);
    const voter = optString(vo, "voter");
    const target = optString(vo, "target");
    if (!voter || !target) return [];
    return [{ voterPseudo: voter, targetPseudo: target }];
  });
}

export function parseSync(data: unknown): SyncSnapshot {
  const o = asRecord(data);
  const participating = optBool(o, "participating");
  const parsedPrivate = isNull(o.private) ? null : parsePrivate(o.private);
  return {
    inRound: optBool(o, "inRound"),
    participating,
    phase: optString(o, "phase"),
    privateRole: participating ? (parsedPrivate?.role ?? null) : null,
    privateWord: participating ? (parsedPrivate?.word ?? null) : null,
    board: isNull(o.board) ? null : parseBoard(o.board),
    revealAcked: optInt(o, "revealAcked"),
    revealTotal: optInt(o, "revealTotal"),
    alreadyRevealed: optBool(o, "alreadyRevealed"),
    alreadyVoted: optBool(o, "alreadyVoted"),
    elimination: isNull(o.elimination) ? null : parseElimination(o.elimination),
    pendingUnknownGuess: optBool(o, "pendingUnknownGuess"),
    result: isNull(o.result) ? null : parseResult(o.result),
  };
}

export function parseResult(data: unknown): ResultSnapshot {
  const o = asRecord(data);
  const playersArr = Array.isArray(o.players) ? o.players : [];
  const players: ResultPlayer[] = playersArr.map((p) => {
    const po = asRecord(p);
    return {
      playerId: optInt(po, "playerId"),
      pseudo: optString(po, "pseudo"),
      role: parseRole(optString(po, "role")) ?? "CITIZEN",
    };
  });
  const scoresObj = asRecord(o.totalScores);
  const totalScores: Record<string, number> = {};
  for (const key of Object.keys(scoresObj)) {
    const v = scoresObj[key];
    if (typeof v === "number") totalScores[key] = v;
  }
  return {
    victory: {
      type: asVictoryType(optString(o, "victoryType")),
      winnerIds: optNumberArray(o, "winnerIds"),
      byGuess: optBool(o, "byGuess"),
    },
    players,
    totalScores,
  };
}

function asVictoryType(value: string): VictoryType {
  if (
    value === "CITIZEN" ||
    value === "IMPOSTOR" ||
    value === "UNKNOWN" ||
    value === "COMBINED" ||
    value === "ONGOING"
  ) {
    return value;
  }
  return "ONGOING";
}

/** Retourne un objet « role » nettoyé pour l'affichage. */
export function roleLabel(role: Role | null): string {
  switch (role) {
    case "CITIZEN":
      return "Citoyen";
    case "IMPOSTOR":
      return "Imposteur";
    case "UNKNOWN":
      return "Inconnu";
    default:
      return "";
  }
}

export function roleCssClass(role: Role | null): string {
  switch (role) {
    case "CITIZEN":
      return "role-citizen";
    case "IMPOSTOR":
      return "role-impostor";
    case "UNKNOWN":
      return "role-unknown";
    default:
      return "";
  }
}
