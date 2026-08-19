/**
 * Registre des salons (logique pure, sans dépendance Socket.IO).
 *
 * Le serveur est un simple relais : il ne contient AUCUNE logique de jeu.
 * Il gère uniquement :
 *   - la création d'un salon (l'hôte, playerId = 1),
 *   - l'adhésion d'invités (playerId séquentiel),
 *   - la présence (départ / déconnexion),
 *   - la résolution socketId <-> playerId pour le routage des messages.
 *
 * Cette classe est volontairement isolée de Socket.IO pour être testable en unitaire.
 */

/** Membre d'un salon (vu côté serveur : contient le socketId, jamais diffusé tel quel). */
export interface Member {
  playerId: number;
  pseudo: string;
  socketId: string;
  ready: boolean;
  /** Identifiant stable du navigateur : permet de reprendre la connexion après un verrouillage d'écran. */
  clientId?: string;
}

/** Représentation publique d'un membre, envoyée aux clients (sans socketId). */
export interface PublicMember {
  playerId: number;
  pseudo: string;
  isHost: boolean;
  ready: boolean;
  /** Faux pendant une déconnexion brève (invité web en attente de reprise). */
  connected: boolean;
}

/** Salon : un hôte (playerId = 1) et une liste de membres. */
export interface Room {
  code: string;
  hostSocketId: string;
  members: Member[];
  nextPlayerId: number;
}

/** Erreur métier avec code stable pour le client. */
export class LobbyError extends Error {
  readonly code: string;

  constructor(code: string, message: string) {
    super(message);
    this.code = code;
  }
}

/** Alphabet sans caractères ambigus (I, L, O) pour des codes lisibles. */
const CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ";
const CODE_LENGTH = 4;

/** Résultat d'un départ : `wasHost` vrai signifie que le salon est fermé. */
export interface LeaveResult {
  code: string;
  wasHost: boolean;
  members: PublicMember[];
}

export class LobbyRegistry {
  private readonly rooms = new Map<string, Room>();

  /** Crée un salon et y place l'hôte (playerId = 1). */
  createRoom(socketId: string, pseudo: string): { code: string; playerId: number } {
    const code = this.generateCode();
    const room: Room = {
      code,
      hostSocketId: socketId,
      members: [{ playerId: 1, pseudo, socketId, ready: true }],
      nextPlayerId: 2,
    };
    this.rooms.set(code, room);
    return { code, playerId: 1 };
  }

  /** Fait rejoindre un invité. Lève [LobbyError] si le salon n'existe pas ou si le pseudo est déjà pris. */
  joinRoom(
    code: string,
    pseudo: string,
    socketId: string,
    clientId?: string,
  ): { playerId: number; members: PublicMember[] } {
    const room = this.rooms.get(code);
    if (!room) {
      throw new LobbyError("LOBBY_NOT_FOUND", "Salon introuvable");
    }
    const normalized = pseudo.trim().toLocaleLowerCase();
    if (room.members.some((m) => m.pseudo.trim().toLocaleLowerCase() === normalized)) {
      throw new LobbyError("PSEUDO_TAKEN", "Ce pseudo est déjà pris dans ce salon");
    }
    const playerId = room.nextPlayerId;
    room.nextPlayerId += 1;
    room.members.push({ playerId, pseudo, socketId, ready: false, clientId });
    return { playerId, members: this.publicMembers(room) };
  }

  /**
   * Reprend la connexion d'un invité après une déconnexion brève (écran verrouillé,
   * onglet suspendu). Ré-associe la nouvelle socket au membre existant identifié par
   * [clientId], en conservant son playerId. Retourne null si aucune reprise possible.
   */
  rejoinRoom(
    code: string,
    clientId: string,
    socketId: string,
  ): { playerId: number; members: PublicMember[] } | null {
    const room = this.rooms.get(code);
    if (!room) return null;
    const member = room.members.find((m) => m.clientId === clientId && m.socketId === "");
    if (!member) return null;
    member.socketId = socketId;
    return { playerId: member.playerId, members: this.publicMembers(room) };
  }

  /**
   * Retire définitivement un membre en attente de reprise (délai écoulé sans
   * reconnexion). Ne cible que les membres dont la socket est déjà déconnectée.
   */
  leaveByClientId(clientId: string): LeaveResult | null {
    for (const room of this.rooms.values()) {
      const member = room.members.find((m) => m.clientId === clientId && m.socketId === "");
      if (!member) continue;
      room.members = room.members.filter((m) => m !== member);
      if (room.members.length === 0) {
        this.rooms.delete(room.code);
        return { code: room.code, wasHost: true, members: [] };
      }
      return { code: room.code, wasHost: false, members: this.publicMembers(room) };
    }
    return null;
  }

  /** Met à jour le statut « prêt » d'un membre. Retourne le salon mis à jour, ou null. */
  setReady(socketId: string, ready: boolean): { code: string; members: PublicMember[] } | null {
    const room = this.findRoomBySocket(socketId);
    if (!room) return null;
    const member = room.members.find((m) => m.socketId === socketId);
    if (!member) return null;
    member.ready = ready;
    return { code: room.code, members: this.publicMembers(room) };
  }

  /** Remet les invités en « non prêt » (début de partie). L'hôte reste toujours prêt. */
  resetReady(room: Room): void {
    room.members.forEach((m) => {
      if (m.socketId !== room.hostSocketId) {
        m.ready = false;
      }
    });
  }

  /** Retire un membre (déconnexion). Ferme le salon si l'hôte part ou s'il est vide. */
  leave(socketId: string): LeaveResult | null {
    const room = this.findRoomBySocket(socketId);
    if (!room) {
      return null;
    }
    const wasHost = room.hostSocketId === socketId;
    room.members = room.members.filter((m) => m.socketId !== socketId);

    if (wasHost || room.members.length === 0) {
      this.rooms.delete(room.code);
      return { code: room.code, wasHost: true, members: [] };
    }
    return { code: room.code, wasHost: false, members: this.publicMembers(room) };
  }

  /** Salon auquel appartient une socket, ou `undefined`. */
  findRoomBySocket(socketId: string): Room | undefined {
    for (const room of this.rooms.values()) {
      if (room.members.some((m) => m.socketId === socketId)) {
        return room;
      }
    }
    return undefined;
  }

  /** Vrai si la socket est l'hôte de son salon. */
  isHost(socketId: string): boolean {
    const room = this.findRoomBySocket(socketId);
    return room !== undefined && room.hostSocketId === socketId;
  }

  /** playerId de la socket, ou `undefined`. */
  playerIdFor(socketId: string): number | undefined {
    const room = this.findRoomBySocket(socketId);
    return room?.members.find((m) => m.socketId === socketId)?.playerId;
  }

  /** socketId du membre [playerId] dans [room], ou `undefined`. */
  socketIdForPlayer(room: Room, playerId: number): string | undefined {
    return room.members.find((m) => m.playerId === playerId)?.socketId;
  }

  /** Membres publics (sans socketId) d'un salon. */
  publicMembers(room: Room): PublicMember[] {
    return room.members.map((m) => ({
      playerId: m.playerId,
      pseudo: m.pseudo,
      isHost: m.socketId === room.hostSocketId,
      ready: m.ready,
      connected: m.socketId !== "",
    }));
  }

  private generateCode(): string {
    let code = "";
    do {
      code = Array.from(
        { length: CODE_LENGTH },
        () => CODE_ALPHABET[Math.floor(Math.random() * CODE_ALPHABET.length)],
      ).join("");
    } while (this.rooms.has(code));
    return code;
  }
}
