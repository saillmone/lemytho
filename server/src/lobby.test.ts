import { describe, expect, it } from "vitest";
import { LobbyError, LobbyRegistry } from "./lobby.js";

describe("LobbyRegistry", () => {
  it("crée un salon et place l'hôte en playerId 1", () => {
    const registry = new LobbyRegistry();
    const { code, playerId } = registry.createRoom("host-socket", "Alice");
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
    }
  });

  it("retire un invité sans fermer le salon", () => {
    const registry = new LobbyRegistry();
    const { code } = registry.createRoom("host", "Alice");
    registry.joinRoom(code, "Bob", "bob-socket");

    const result = registry.leave("bob-socket");
    expect(result).not.toBeNull();
    expect(result?.wasHost).toBe(false);
    expect(result?.members.map((m) => m.pseudo)).toEqual(["Alice"]);
  });

  it("ferme le salon quand l'hôte se déconnecte", () => {
    const registry = new LobbyRegistry();
    const { code } = registry.createRoom("host", "Alice");
    registry.joinRoom(code, "Bob", "bob-socket");

    const result = registry.leave("host");
    expect(result?.wasHost).toBe(true);
    expect(result?.members).toEqual([]);
    expect(registry.findRoomBySocket("bob-socket")).toBeUndefined();
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
});
