import { describe, expect, it } from "vitest";
import { LobbyError, LobbyRegistry } from "./lobby.js";

describe("LobbyRegistry", () => {
  it("crée un salon et place l'hôte en playerId 1", () => {
    const registry = new LobbyRegistry();
    const { code, playerId } = registry.createRoom("host-socket", "Alice", "client-alice");
    expect(code).toMatch(/^[ABCDEFGHJKMNPQRSTUVWXYZ]{4}$/);
    expect(playerId).toBe(1);
    expect(registry.isHost("host-socket")).toBe(true);
  });

  it("génère des codes uniques", () => {
    const registry = new LobbyRegistry();
    const codes = new Set<string>();
    for (let i = 0; i < 100; i += 1) {
      codes.add(registry.createRoom(`s${i}`, "H").code);
    }
    expect(codes.size).toBe(100);
  });

  it("fait rejoindre des invités avec des playerId croissants", () => {
    const registry = new LobbyRegistry();
    const { code } = registry.createRoom("host", "Alice");
    const first = registry.joinRoom(code, "Bob", "bob-socket");
    const second = registry.joinRoom(code, "Carla", "carla-socket");

    expect(first.playerId).toBe(2);
    expect(second.playerId).toBe(3);
    expect(first.members.map((m) => m.playerId)).toEqual([1, 2]);
    expect(second.members.map((m) => m.playerId)).toEqual([1, 2, 3]);
    expect(second.members.find((m) => m.playerId === 1)?.isHost).toBe(true);
  });

  it("refuse d'adhérer à un salon inexistant", () => {
    const registry = new LobbyRegistry();
    expect(() => registry.joinRoom("ZZZZ", "Bob", "bob-socket")).toThrowError(LobbyError);
  });

  it("refuse un pseudo déjà pris dans le salon (insensible à la casse)", () => {
    const registry = new LobbyRegistry();
    const { code } = registry.createRoom("host", "Alice");
    registry.joinRoom(code, "Bob", "bob-socket");

    try {
      registry.joinRoom(code, "bob", "carla-socket");
      throw new Error("devrait lever une LobbyError");
    } catch (err) {
      expect((err as LobbyError).code).toBe("PSEUDO_TAKEN");
      expect((err as LobbyError).message).toBe(
        "Ce pseudo est déjà pris dans ce salon, choisis-en un autre.",
      );
    }
  });

  it("reprend un invité après park sans changer son playerId", () => {
    const registry = new LobbyRegistry();
    const { code } = registry.createRoom("host", "Alice");
    registry.joinRoom(code, "Bob", "bob-socket", "client-bob");
    expect(registry.park("bob-socket")).not.toBeNull();

    const rejoin = registry.rejoinRoom(code, "client-bob", "bob-socket-2");
    expect(rejoin).not.toBeNull();
    expect(rejoin?.playerId).toBe(2);
    expect(rejoin?.resumed).toBe(true);
    expect(rejoin?.members.find((m) => m.playerId === 2)?.connected).toBe(true);
  });

  it("vole le siège même si l'ancienne socket est encore attachée", () => {
    const registry = new LobbyRegistry();
    const { code } = registry.createRoom("host", "Alice");
    registry.joinRoom(code, "Bob", "bob-old", "client-bob");

    const claimed = registry.claimSeat(code, "Bob", "bob-new", "client-bob");
    expect(claimed.playerId).toBe(2);
    expect(claimed.replacedSocketId).toBe("bob-old");
    expect(claimed.members.find((m) => m.playerId === 2)?.connected).toBe(true);
  });

  it("park de l'ancienne socket après vol est un no-op", () => {
    const registry = new LobbyRegistry();
    const { code } = registry.createRoom("host", "Alice");
    registry.joinRoom(code, "Bob", "bob-old", "client-bob");
    registry.claimSeat(code, "Bob", "bob-new", "client-bob");

    expect(registry.park("bob-old")).toBeNull();
    const room = registry.findRoomBySocket("bob-new");
    expect(room).toBeDefined();
    expect(room!.members.find((m) => m.playerId === 2)?.socketId).toBe("bob-new");
    expect(room!.members.find((m) => m.playerId === 2)?.socketId).not.toBe("");
  });

  it("park de l'hôte ne ferme pas le salon", () => {
    const registry = new LobbyRegistry();
    const { code } = registry.createRoom("host", "Alice", "client-alice");
    registry.joinRoom(code, "Bob", "bob-socket", "client-bob");

    const parked = registry.park("host");
    expect(parked?.wasHost).toBe(true);
    expect(registry.findRoomByCode(code)).toBeDefined();
    expect(registry.findRoomBySocket("bob-socket")).toBeDefined();
    expect(parked?.members.find((m) => m.playerId === 1)?.connected).toBe(false);
  });

  it("reprend le salon hôte par clientId au lieu d'en créer un autre", () => {
    const registry = new LobbyRegistry();
    const first = registry.createRoom("host-1", "Alice", "client-alice");
    registry.park("host-1");

    const resumed = registry.createRoom("host-2", "Alice", "client-alice");
    expect(resumed.resumed).toBe(true);
    expect(resumed.code).toBe(first.code);
    expect(resumed.playerId).toBe(1);
    expect(resumed.inRound).toBe(false);
    expect(registry.isHost("host-2")).toBe(true);
  });

  it("la reprise hôte conserve inRound", () => {
    const registry = new LobbyRegistry();
    const first = registry.createRoom("host-1", "Alice", "client-alice");
    registry.setInRound("host-1", true);
    registry.park("host-1");
    const resumed = registry.createRoom("host-2", "Alice", "client-alice");
    expect(resumed.resumed).toBe(true);
    expect(resumed.code).toBe(first.code);
    expect(resumed.inRound).toBe(true);
  });

  it("reclaim par pseudo hors ligne hors manche", () => {
    const registry = new LobbyRegistry();
    const { code } = registry.createRoom("host", "Alice");
    registry.joinRoom(code, "Bob", "bob-old", "client-bob");
    registry.park("bob-old");

    const claimed = registry.claimSeat(code, "bob", "bob-new", "client-bob-2");
    expect(claimed.playerId).toBe(2);
    expect(claimed.resumed).toBe(true);
    const room = registry.findRoomBySocket("bob-new")!;
    expect(room.members.find((m) => m.playerId === 2)?.clientId).toBe("client-bob-2");
  });

  it("refuse le reclaim par pseudo pendant une manche", () => {
    const registry = new LobbyRegistry();
    const created = registry.createRoom("host", "Alice", "client-alice");
    registry.joinRoom(created.code, "Bob", "bob-old", "client-bob");
    registry.park("bob-old");
    registry.setInRound("host", true);

    try {
      registry.claimSeat(created.code, "Bob", "eve", "client-eve");
      throw new Error("devrait lever une LobbyError");
    } catch (err) {
      expect((err as LobbyError).code).toBe("PSEUDO_TAKEN");
      expect((err as LobbyError).message).toBe(
        "Ce pseudo est déjà pris dans ce salon, choisis-en un autre.",
      );
    }
  });

  it("autorise le reclaim par clientId pendant une manche", () => {
    const registry = new LobbyRegistry();
    const created = registry.createRoom("host", "Alice", "client-alice");
    registry.joinRoom(created.code, "Bob", "bob-old", "client-bob");
    registry.park("bob-old");
    registry.setInRound("host", true);

    const claimed = registry.claimSeat(created.code, "Bob", "bob-new", "client-bob");
    expect(claimed.playerId).toBe(2);
    expect(claimed.resumed).toBe(true);
  });

  it("leaveByClientId est no-op pendant une manche", () => {
    const registry = new LobbyRegistry();
    const created = registry.createRoom("host", "Alice");
    registry.joinRoom(created.code, "Bob", "bob-socket", "client-bob");
    registry.park("bob-socket");
    registry.setInRound("host", true);

    expect(registry.leaveByClientId("client-bob")).toBeNull();
    expect(registry.findRoomByCode(created.code)?.members).toHaveLength(2);
  });

  it("retire un membre en attente de reprise hors manche", () => {
    const registry = new LobbyRegistry();
    const { code } = registry.createRoom("host", "Alice");
    registry.joinRoom(code, "Bob", "bob-socket", "client-bob");
    registry.joinRoom(code, "Carla", "carla-socket");
    registry.park("bob-socket");

    const result = registry.leaveByClientId("client-bob");
    expect(result?.wasHost).toBe(false);
    expect(result?.members.map((m) => m.pseudo)).toEqual(["Alice", "Carla"]);
  });

  it("retire un invité sans fermer le salon (départ franc)", () => {
    const registry = new LobbyRegistry();
    const { code } = registry.createRoom("host", "Alice");
    registry.joinRoom(code, "Bob", "bob-socket");

    const result = registry.leave("bob-socket");
    expect(result).not.toBeNull();
    expect(result?.wasHost).toBe(false);
    expect(result?.members.map((m) => m.pseudo)).toEqual(["Alice"]);
  });

  it("ferme le salon quand l'hôte quitte franchement", () => {
    const registry = new LobbyRegistry();
    const { code } = registry.createRoom("host", "Alice");
    registry.joinRoom(code, "Bob", "bob-socket");

    const result = registry.leave("host");
    expect(result?.wasHost).toBe(true);
    expect(result?.members).toEqual([]);
    expect(registry.findRoomBySocket("bob-socket")).toBeUndefined();
    expect(registry.findRoomByCode(code)).toBeUndefined();
  });

  it("résout playerId et socketId", () => {
    const registry = new LobbyRegistry();
    const { code } = registry.createRoom("host", "Alice");
    registry.joinRoom(code, "Bob", "bob-socket");

    expect(registry.playerIdFor("bob-socket")).toBe(2);
    const room = registry.findRoomBySocket("host");
    expect(room).toBeDefined();
    expect(registry.socketIdForPlayer(room!, 2)).toBe("bob-socket");
  });

  it("signale un inconnu (hors salon) comme non-hôte", () => {
    const registry = new LobbyRegistry();
    expect(registry.isHost("inconnu")).toBe(false);
    expect(registry.playerIdFor("inconnu")).toBeUndefined();
  });

  it("l'hôte est prêt par défaut, l'invité ne l'est pas", () => {
    const registry = new LobbyRegistry();
    const { code } = registry.createRoom("host", "Alice");
    const joined = registry.joinRoom(code, "Bob", "bob-socket");

    expect(joined.members.find((m) => m.playerId === 1)?.ready).toBe(true);
    expect(joined.members.find((m) => m.playerId === 2)?.ready).toBe(false);
  });

  it("bascule le statut prêt d'un membre", () => {
    const registry = new LobbyRegistry();
    const { code } = registry.createRoom("host", "Alice");
    registry.joinRoom(code, "Bob", "bob-socket");

    const updated = registry.setReady("bob-socket", true);
    expect(updated).not.toBeNull();
    expect(updated?.members.find((m) => m.playerId === 2)?.ready).toBe(true);

    const reverted = registry.setReady("bob-socket", false);
    expect(reverted?.members.find((m) => m.playerId === 2)?.ready).toBe(false);
  });

  it("resetReady ne s'appuie pas sur hostSocketId (hôte parqué)", () => {
    const registry = new LobbyRegistry();
    const { code } = registry.createRoom("host", "Alice", "client-alice");
    registry.joinRoom(code, "Bob", "bob-socket");
    registry.setReady("bob-socket", true);
    registry.park("host");
    const room = registry.findRoomByCode(code)!;
    registry.resetReady(room);
    expect(room.members.find((m) => m.playerId === 1)?.ready).toBe(true);
    expect(room.members.find((m) => m.playerId === 2)?.ready).toBe(false);
  });
});
