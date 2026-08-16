import { createServer } from "node:http";
import { Server, type Socket } from "socket.io";
import { LobbyError, LobbyRegistry, type PublicMember } from "./lobby.js";

const PORT = Number(process.env.PORT ?? 3000);

const httpServer = createServer((_req, res) => {
  res.writeHead(200, { "Content-Type": "text/plain; charset=utf-8" });
  res.end("OpenCover relay server");
});

const io = new Server(httpServer, {
  // Clients mobiles natifs : pas d'origine navigateur à restreindre.
  cors: { origin: "*" },
});

const registry = new LobbyRegistry();

/** Nettoie une chaîne utilisateur (pseudo, code) en entrée. */
function clean(value: unknown, maxLength: number): string {
  if (typeof value !== "string") return "";
  return value.trim().slice(0, maxLength);
}

/** Code de salon normalisé (majuscules, sans espaces). */
function normalizeCode(value: unknown): string {
  return clean(value, 8).toUpperCase().replace(/[^A-Z0-9]/g, "");
}

/** Quitte la room Socket.IO précédente de la socket (évite les adhésions fantômes). */
function leavePreviousRoom(socket: Socket): void {
  const previousCode = socket.data.code;
  if (typeof previousCode === "string") {
    socket.leave(previousCode);
  }
}

/** Vrai si [value] est une fonction (guard pour les callbacks ack). */
function isFn(value: unknown): value is (...args: unknown[]) => void {
  return typeof value === "function";
}

io.on("connection", (socket) => {
  // --- Création de salon (hôte) ---
  socket.on("lobby:create", (payload: unknown, ack?: unknown) => {
    const pseudo = clean((payload as { pseudo?: unknown })?.pseudo, 24) || "Hôte";
    const { code, playerId } = registry.createRoom(socket.id, pseudo);
    socket.join(code);
    socket.data.code = code;
    socket.data.playerId = playerId;
    const members: PublicMember[] = [{ playerId, pseudo, isHost: true, ready: true }];
    if (isFn(ack)) ack({ ok: true, code, playerId, members });
  });

  // --- Adhésion d'un invité ---
  socket.on("lobby:join", (payload: unknown, ack?: unknown) => {
    const data = payload as { code?: unknown; pseudo?: unknown };
    const code = normalizeCode(data?.code);
    const pseudo = clean(data?.pseudo, 24) || "Joueur";
    try {
      const { playerId, members } = registry.joinRoom(code, pseudo, socket.id);
      leavePreviousRoom(socket);
      socket.join(code);
      socket.data.code = code;
      socket.data.playerId = playerId;
      if (isFn(ack)) ack({ ok: true, code, playerId, members });
      socket.to(code).emit("lobby:update", { members });
    } catch (err) {
      const error = err as LobbyError;
      if (isFn(ack)) ack({ ok: false, error: error.code, message: error.message });
    }
  });

  // --- Statut « prêt » d'un membre ---
  socket.on("lobby:ready", (payload: unknown, ack?: unknown) => {
    const ready = (payload as { ready?: unknown })?.ready === true;
    const room = registry.findRoomBySocket(socket.id);
    if (!room) return;
    const result = registry.setReady(socket.id, ready);
    if (!result) return;
    if (isFn(ack)) ack({ ok: true, members: result.members });
    io.to(result.code).emit("lobby:update", { members: result.members });
  });

  // --- Début de partie : remet tous les membres en « non prêt » ---
  socket.on("lobby:start", () => {
    const room = registry.findRoomBySocket(socket.id);
    if (!room || !registry.isHost(socket.id)) return;
    registry.resetReady(room);
    io.to(room.code).emit("lobby:update", { members: registry.publicMembers(room) });
  });

  // --- Relais : invité -> hôte ---
  socket.on(
    "relay:toHost",
    (payload: unknown) => {
      const { event, data: relayData } = payload as { event?: unknown; data?: unknown };
      const room = registry.findRoomBySocket(socket.id);
      if (!room || typeof event !== "string") return;
      io.to(room.hostSocketId).emit(event, relayData);
    },
  );

  // --- Relais : hôte -> invité précis ---
  socket.on(
    "relay:toPlayer",
    (payload: unknown) => {
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
    },
  );

  // --- Relais : hôte -> tous les invités (sauf l'hôte) ---
  socket.on(
    "relay:broadcast",
    (payload: unknown) => {
      if (!registry.isHost(socket.id)) return;
      const { event, data: relayData } = payload as { event?: unknown; data?: unknown };
      const room = registry.findRoomBySocket(socket.id);
      if (!room || typeof event !== "string") return;
      socket.to(room.code).emit(event, relayData);
    },
  );

  // --- Relais : hôte -> tout le salon (hôte compris) ---
  socket.on(
    "relay:toAll",
    (payload: unknown) => {
      if (!registry.isHost(socket.id)) return;
      const { event, data: relayData } = payload as { event?: unknown; data?: unknown };
      const room = registry.findRoomBySocket(socket.id);
      if (!room || typeof event !== "string") return;
      io.to(room.code).emit(event, relayData);
    },
  );

  // --- Déconnexion ---
  socket.on("disconnect", () => {
    const result = registry.leave(socket.id);
    if (!result) return;
    if (result.wasHost) {
      io.to(result.code).emit("lobby:closed", { code: result.code });
    } else {
      io.to(result.code).emit("lobby:update", { members: result.members });
    }
  });
});

httpServer.listen(PORT, () => {
  console.log(`OpenCover relay server listening on port ${PORT}`);
});

// Fermeture propre (reload tsx watch / arrêt manuel) : libère le port avant
// de relancer, évitant les erreurs EADDRINUSE lors des redémarrages à chaud.
function shutdown(): void {
  io.close(() => process.exit(0));
  setTimeout(() => process.exit(0), 500).unref();
}
process.on("SIGINT", shutdown);
process.on("SIGTERM", shutdown);
