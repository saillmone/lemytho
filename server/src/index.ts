import { createServer } from "node:http";
import { Server, type Socket } from "socket.io";
import { LobbyError, LobbyRegistry, type PublicMember } from "./lobby.js";
import { createStaticHandler } from "./static.js";

const PORT = Number(process.env.PORT ?? 3000);

const PUBLIC_DIR = process.env.PUBLIC_DIR ?? "../web/dist";
const staticHandler = createStaticHandler(PUBLIC_DIR);

const httpServer = createServer((req, res) => {
  if ((req.url ?? "").startsWith("/socket.io/")) {
    return;
  }
  staticHandler(req, res);
});

const io = new Server(httpServer, {
  cors: { origin: "*" },
});

const registry = new LobbyRegistry();

/** Grâce invité hors manche (écran noir). 30 min par défaut. */
const REJOIN_GRACE_MS = Number(process.env.REJOIN_GRACE_MS) || 30 * 60 * 1000;
/** Grâce hôte (y compris en manche). 10 min par défaut. */
const HOST_GRACE_MS = Number(process.env.HOST_GRACE_MS) || 10 * 60 * 1000;

const pendingGuestRemoval = new Map<string, ReturnType<typeof setTimeout>>();
/** Timers de grâce hôte, indexés par code de salon. */
const pendingHostClose = new Map<string, ReturnType<typeof setTimeout>>();

function clean(value: unknown, maxLength: number): string {
  if (typeof value !== "string") return "";
  return value.trim().slice(0, maxLength);
}

function normalizeCode(value: unknown): string {
  return clean(value, 8).toUpperCase().replace(/[^A-Z0-9]/g, "");
}

function readClientId(value: unknown): string | undefined {
  return typeof value === "string" && value.length > 0 ? value.slice(0, 64) : undefined;
}

function leavePreviousRoom(socket: Socket): void {
  const previousCode = socket.data.code;
  if (typeof previousCode === "string") {
    socket.leave(previousCode);
  }
}

function isFn(value: unknown): value is (...args: unknown[]) => void {
  return typeof value === "function";
}

function clearGuestTimer(clientId: string | undefined): void {
  if (!clientId) return;
  const timer = pendingGuestRemoval.get(clientId);
  if (timer) {
    clearTimeout(timer);
    pendingGuestRemoval.delete(clientId);
  }
}

function clearHostTimer(code: string | undefined): void {
  if (!code) return;
  const timer = pendingHostClose.get(code);
  if (timer) {
    clearTimeout(timer);
    pendingHostClose.delete(code);
  }
}

function disconnectReplaced(oldSocketId: string | undefined): void {
  if (!oldSocketId) return;
  const oldSocket = io.sockets.sockets.get(oldSocketId);
  if (!oldSocket) return;
  oldSocket.data.replaced = true;
  oldSocket.disconnect(true);
}

function bindSocketToRoom(socket: Socket, code: string, playerId: number): void {
  leavePreviousRoom(socket);
  socket.join(code);
  socket.data.code = code;
  socket.data.playerId = playerId;
}

function emitPresence(code: string, playerId: number, connected: boolean, members: PublicMember[]): void {
  io.to(code).emit("player:presence", { playerId, connected });
  io.to(code).emit("lobby:update", { members });
}

function scheduleGuestRemoval(clientId: string): void {
  clearGuestTimer(clientId);
  const timer = setTimeout(() => {
    pendingGuestRemoval.delete(clientId);
    const result = registry.leaveByClientId(clientId);
    if (!result) return;
    if (result.wasHost) {
      io.to(result.code).emit("lobby:closed", { code: result.code });
      return;
    }
    if (result.playerId != null) {
      io.to(result.code).emit("player:left", { playerId: result.playerId });
    }
    io.to(result.code).emit("lobby:update", { members: result.members });
  }, REJOIN_GRACE_MS);
  pendingGuestRemoval.set(clientId, timer);
}

function scheduleHostClose(code: string): void {
  clearHostTimer(code);
  const timer = setTimeout(() => {
    pendingHostClose.delete(code);
    const result = registry.closeIfHostParked(code);
    if (!result) return;
    io.to(code).emit("lobby:closed", { code });
  }, HOST_GRACE_MS);
  pendingHostClose.set(code, timer);
}

function restartGuestTimersAfterRound(code: string): void {
  const room = registry.findRoomByCode(code);
  if (!room) return;
  for (const guest of registry.parkedGuests(room)) {
    if (guest.clientId) scheduleGuestRemoval(guest.clientId);
  }
}

io.on("connection", (socket) => {
  socket.on("lobby:create", (payload: unknown, ack?: unknown) => {
    const data = payload as { pseudo?: unknown; clientId?: unknown };
    const pseudo = clean(data?.pseudo, 24) || "Hôte";
    const clientId = readClientId(data?.clientId);
    const created = registry.createRoom(socket.id, pseudo, clientId);
    disconnectReplaced(created.replacedSocketId);
    clearHostTimer(created.code);
    bindSocketToRoom(socket, created.code, created.playerId);
    if (isFn(ack)) {
      ack({
        ok: true,
        code: created.code,
        playerId: created.playerId,
        members: created.members,
        resumed: created.resumed,
        inRound: created.inRound,
      });
    }
    if (created.resumed) {
      emitPresence(created.code, 1, true, created.members);
    }
  });

  socket.on("lobby:join", (payload: unknown, ack?: unknown) => {
    const data = payload as { code?: unknown; pseudo?: unknown; clientId?: unknown };
    const code = normalizeCode(data?.code);
    const pseudo = clean(data?.pseudo, 24) || "Joueur";
    const clientId = readClientId(data?.clientId);
    try {
      const claimed = registry.claimSeat(code, pseudo, socket.id, clientId);
      disconnectReplaced(claimed.replacedSocketId);
      if (clientId) clearGuestTimer(clientId);
      bindSocketToRoom(socket, code, claimed.playerId);
      if (isFn(ack)) ack({ ok: true, code, playerId: claimed.playerId, members: claimed.members });
      if (claimed.resumed) {
        emitPresence(code, claimed.playerId, true, claimed.members);
      } else {
        socket.to(code).emit("lobby:update", { members: claimed.members });
      }
    } catch (err) {
      const error = err as LobbyError;
      if (isFn(ack)) ack({ ok: false, error: error.code, message: error.message });
    }
  });

  socket.on("lobby:leave", (_payload: unknown, ack?: unknown) => {
    if (socket.data.replaced) {
      if (isFn(ack)) ack({ ok: true });
      return;
    }
    const result = registry.leave(socket.id);
    socket.data.replaced = true;
    leavePreviousRoom(socket);
    socket.data.code = undefined;
    socket.data.playerId = undefined;
    if (result) {
      if (result.clientId) clearGuestTimer(result.clientId);
      if (result.wasHost) clearHostTimer(result.code);
      if (result.wasHost) {
        io.to(result.code).emit("lobby:closed", { code: result.code });
      } else {
        if (result.playerId != null) {
          io.to(result.code).emit("player:left", { playerId: result.playerId });
        }
        io.to(result.code).emit("lobby:update", { members: result.members });
      }
    }
    if (isFn(ack)) ack({ ok: true });
  });

  socket.on("lobby:ready", (payload: unknown, ack?: unknown) => {
    const ready = (payload as { ready?: unknown })?.ready === true;
    const room = registry.findRoomBySocket(socket.id);
    if (!room) return;
    const result = registry.setReady(socket.id, ready);
    if (!result) return;
    if (isFn(ack)) ack({ ok: true, members: result.members });
    io.to(result.code).emit("lobby:update", { members: result.members });
  });

  socket.on("lobby:start", () => {
    const room = registry.findRoomBySocket(socket.id);
    if (!room || !registry.isHost(socket.id)) return;
    registry.resetReady(room);
    registry.setInRound(socket.id, true);
    for (const guest of registry.parkedGuests(room)) {
      clearGuestTimer(guest.clientId);
    }
    io.to(room.code).emit("lobby:update", { members: registry.publicMembers(room) });
  });

  socket.on("lobby:roundEnded", () => {
    const room = registry.findRoomBySocket(socket.id);
    if (!room || !registry.isHost(socket.id)) return;
    registry.setInRound(socket.id, false);
    restartGuestTimersAfterRound(room.code);
  });

  socket.on("lobby:heartbeat", (_payload: unknown, ack?: unknown) => {
    if (isFn(ack)) ack({ ok: true });
  });

  socket.on("relay:toHost", (payload: unknown) => {
    const { event, data: relayData } = payload as { event?: unknown; data?: unknown };
    const room = registry.findRoomBySocket(socket.id);
    if (!room || typeof event !== "string") return;
    if (!room.hostSocketId) return;
    io.to(room.hostSocketId).emit(event, relayData);
  });

  socket.on("relay:toPlayer", (payload: unknown) => {
    if (!registry.isHost(socket.id)) return;
    const { playerId, event, data: relayData } = payload as {
      playerId?: unknown;
      event?: unknown;
      data?: unknown;
    };
    const room = registry.findRoomBySocket(socket.id);
    if (!room || typeof event !== "string" || typeof playerId !== "number") return;
    const targetSocketId = registry.socketIdForPlayer(room, playerId);
    if (targetSocketId) {
      io.to(targetSocketId).emit(event, relayData);
    }
  });

  socket.on("relay:broadcast", (payload: unknown) => {
    if (!registry.isHost(socket.id)) return;
    const { event, data: relayData } = payload as { event?: unknown; data?: unknown };
    const room = registry.findRoomBySocket(socket.id);
    if (!room || typeof event !== "string") return;
    socket.to(room.code).emit(event, relayData);
  });

  socket.on("relay:toAll", (payload: unknown) => {
    if (!registry.isHost(socket.id)) return;
    const { event, data: relayData } = payload as { event?: unknown; data?: unknown };
    const room = registry.findRoomBySocket(socket.id);
    if (!room || typeof event !== "string") return;
    io.to(room.code).emit(event, relayData);
  });

  socket.on("disconnect", () => {
    if (socket.data.replaced) return;
    const parked = registry.park(socket.id);
    if (!parked) return;

    emitPresence(parked.code, parked.playerId, false, parked.members);

    if (parked.wasHost) {
      scheduleHostClose(parked.code);
      return;
    }

    const room = registry.findRoomByCode(parked.code);
    if (room?.inRound) return;
    if (parked.clientId) {
      scheduleGuestRemoval(parked.clientId);
      return;
    }
    const dropped = registry.leaveParkedPlayer(parked.code, parked.playerId);
    if (!dropped) return;
    if (dropped.playerId != null) {
      io.to(dropped.code).emit("player:left", { playerId: dropped.playerId });
    }
    io.to(dropped.code).emit("lobby:update", { members: dropped.members });
  });
});

httpServer.listen(PORT, () => {
  console.log(`Le Mytho relay server listening on port ${PORT}`);
});

function shutdown(): void {
  io.close(() => process.exit(0));
  setTimeout(() => process.exit(0), 500).unref();
}
process.on("SIGINT", shutdown);
process.on("SIGTERM", shutdown);
