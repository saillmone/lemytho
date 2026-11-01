// Enveloppe Socket.IO côté navigateur (invité pur). Miroir de ConnectionManager.kt,
// réduit aux opérations dont un invité a besoin : join, ready, relay:toHost.

import { io, type Socket } from "socket.io-client";
import {
  LOBBY_CLOSED,
  LOBBY_HEARTBEAT,
  LOBBY_JOIN,
  LOBBY_LEAVE,
  LOBBY_READY,
  LOBBY_UPDATE,
  GAME_START,
  GAME_PRIVATE,
  GAME_BOARD,
  GAME_PHASE,
  GAME_ELIMINATION,
  GAME_RESULT,
  GAME_REVEAL_ACK,
  GAME_CANCELLED,
  GAME_SYNC,
  PLAYER_PRESENCE,
  parseMembers,
  type LobbyMember,
} from "./protocol";

export type ConnectionStatus =
  | "disconnected"
  | "connecting"
  | "connected"
  | "error";

export type GameEvent = { name: string; data: unknown };

export interface JoinAck {
  ok: boolean;
  code?: string;
  playerId?: number;
  members?: LobbyMember[];
  error?: string;
  message?: string;
}

type Listener = {
  onStatus: (status: ConnectionStatus) => void;
  onLobbyUpdate: (members: LobbyMember[]) => void;
  onLobbyClosed: () => void;
  onGameEvent: (event: GameEvent) => void;
  /** Déclenché à chaque (re)connexion de la socket. */
  onReconnected?: () => void;
};

const GAME_EVENTS = [
  GAME_START,
  GAME_PRIVATE,
  GAME_BOARD,
  GAME_PHASE,
  GAME_ELIMINATION,
  GAME_RESULT,
  GAME_REVEAL_ACK,
  GAME_CANCELLED,
  GAME_SYNC,
  PLAYER_PRESENCE,
];

export class GuestSocket {
  private socket: Socket | null = null;
  private connectedUrl: string | null = null;

  constructor(private readonly listeners: Listener) {}

  /** Ouvre une connexion vers [url] et attend CONNECT. */
  connect(url: string): Promise<boolean> {
    const current = this.socket;
    if (current && this.connectedUrl === url) {
      if (current.connected) return Promise.resolve(true);
      current.connect();
      return this.waitConnected(current);
    }
    this.disconnect();
    this.listeners.onStatus("connecting");
    this.connectedUrl = url;

    const socket = io(url, {
      reconnection: true,
      reconnectionAttempts: Infinity,
      reconnectionDelay: 1000,
      timeout: 10000,
    });

    socket.on("connect", () => {
      this.listeners.onStatus("connected");
      this.listeners.onReconnected?.();
    });
    socket.on("connect_error", () => this.listeners.onStatus("error"));
    socket.on("disconnect", () => this.listeners.onStatus("disconnected"));

    socket.on(LOBBY_UPDATE, (data: unknown) => {
      const o = (data ?? {}) as Record<string, unknown>;
      this.listeners.onLobbyUpdate(parseMembers(o.members));
    });
    socket.on(LOBBY_CLOSED, () => this.listeners.onLobbyClosed());

    for (const event of GAME_EVENTS) {
      socket.on(event, (data: unknown) => {
        this.listeners.onGameEvent({ name: event, data: data ?? {} });
      });
    }

    this.socket = socket;
    socket.connect();
    return this.waitConnected(socket);
  }

  disconnect(): void {
    if (this.socket) {
      this.socket.removeAllListeners();
      this.socket.disconnect();
      this.socket = null;
    }
    this.connectedUrl = null;
    this.listeners.onStatus("disconnected");
  }

  /** Force la reconnexion si la socket est tombée (ex. retour au premier plan). */
  reconnect(): void {
    if (this.socket && !this.socket.connected) {
      this.socket.connect();
    }
  }

  isConnected(): boolean {
    return this.socket?.connected === true;
  }

  heartbeat(): Promise<boolean> {
    const socket = this.socket;
    if (!socket?.connected) return Promise.resolve(false);
    return new Promise((resolve) => {
      const timer = setTimeout(() => resolve(false), 2000);
      socket.emit(LOBBY_HEARTBEAT, {}, () => {
        clearTimeout(timer);
        resolve(true);
      });
    });
  }

  leave(): Promise<void> {
    const socket = this.socket;
    if (!socket?.connected) return Promise.resolve();
    return new Promise((resolve) => {
      const timer = setTimeout(() => resolve(), 2000);
      socket.emit(LOBBY_LEAVE, {}, () => {
        clearTimeout(timer);
        resolve();
      });
    });
  }

  /** Rejoint un salon et attend la confirmation du serveur. */
  joinLobby(code: string, pseudo: string, clientId?: string): Promise<JoinAck> {
    const socket = this.socket;
    if (!socket) {
      return Promise.resolve({ ok: false, error: "NOT_CONNECTED", message: "Non connecté au serveur" });
    }
    if (!socket.connected) {
      return Promise.resolve({ ok: false, error: "NOT_CONNECTED", message: "Non connecté au serveur" });
    }
    return new Promise((resolve) => {
      let done = false;
      const finish = (ack: JoinAck) => {
        if (!done) {
          done = true;
          resolve(ack);
        }
      };
      const timer = setTimeout(
        () => finish({ ok: false, error: "TIMEOUT", message: "Délai de connexion dépassé" }),
        15000,
      );
      socket.emit(LOBBY_JOIN, { code, pseudo, clientId }, (ack: unknown) => {
        clearTimeout(timer);
        const o = (ack ?? {}) as Record<string, unknown>;
        if (o.ok === true) {
          finish({
            ok: true,
            code: typeof o.code === "string" ? o.code : undefined,
            playerId: typeof o.playerId === "number" ? o.playerId : undefined,
            members: parseMembers(o.members),
          });
        } else {
          finish({
            ok: false,
            error: typeof o.error === "string" ? o.error : "UNKNOWN",
            message: typeof o.message === "string" ? o.message : "Erreur inconnue",
          });
        }
      });
    });
  }

  setReady(ready: boolean): void {
    if (this.socket?.connected) {
      this.socket.emit(LOBBY_READY, { ready });
    }
  }

  /** Relais invité -> hôte. */
  sendToHost(event: string, data: Record<string, unknown> = {}): void {
    if (this.socket?.connected) {
      this.socket.emit("relay:toHost", { event, data });
    }
  }

  private waitConnected(socket: Socket): Promise<boolean> {
    if (socket.connected) return Promise.resolve(true);
    return new Promise((resolve) => {
      const timer = setTimeout(() => {
        socket.off("connect", onOk);
        resolve(socket.connected);
      }, 15000);
      const onOk = () => {
        clearTimeout(timer);
        resolve(true);
      };
      socket.once("connect", onOk);
    });
  }
}
