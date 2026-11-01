/**
 * Registre des salons (logique pure, sans dépendance Socket.IO).
 *
 * Le serveur est un simple relais : il ne contient AUCUNE logique de jeu.
 * Il gère uniquement :
 *   - la création / reprise d'un salon (l'hôte, playerId = 1),
 *   - l'adhésion d'invités (playerId séquentiel),
 *   - la présence (park / claimSeat), distincte du départ franc,
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
  /** Identifiant stable de l'appareil : la chaise, pas le fil. */
  clientId?: string;
}

/** Représentation publique d'un membre, envoyée aux clients (sans socketId). */
export interface PublicMember {
  playerId: number;
  pseudo: string;
  isHost: boolean;
  ready: boolean;
  connected: boolean;
}

/** Salon : un hôte (playerId = 1) et une liste de membres. */
export interface Room {
  code: string;
  hostSocketId: string;
  hostClientId?: string;
  members: Member[];
  nextPlayerId: number;
  /** True entre lobby:start et lobby:roundEnded. */
  inRound: boolean;
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

/** Résultat d'un départ franc : `wasHost` vrai signifie que le salon est fermé. */
export interface LeaveResult {
  code: string;
  wasHost: boolean;
  playerId?: number;
  clientId?: string;
  members: PublicMember[];
}

/** Résultat d'un park (fil coupé, chaise gardée). */
export interface ParkResult {
  code: string;
  playerId: number;
  wasHost: boolean;
  clientId?: string;
  members: PublicMember[];
}

/** Prise ou reprise de chaise. */
export interface SeatClaim {
  playerId: number;
  members: PublicMember[];
  /** Ancienne socket à fermer (vol de siège). */
  replacedSocketId?: string;
  /** True si on a réoccupé une chaise existante (pas un nouvel insert). */
  resumed: boolean;
}

export interface CreateResult {
  code: string;
  playerId: number;
  members: PublicMember[];
  resumed: boolean;
  inRound: boolean;
  replacedSocketId?: string;
}

export class LobbyRegistry {
  private readonly rooms = new Map<string, Room>();

  /** Crée un salon, ou reprend celui dont l'hôte a ce clientId (parké ou encore relié). */
  createRoom(socketId: string, pseudo: string, clientId?: string): CreateResult {
    if (clientId) {
      const existing = this.findRoomByHostClientId(clientId);
      if (existing) {
        const host = existing.members.find((m) => m.playerId === 1);
        if (host) {
          const replaced = host.socketId !== "" && host.socketId !== socketId ? host.socketId : undefined;
          host.socketId = socketId;
          host.pseudo = pseudo;
          host.clientId = clientId;
          host.ready = true;
          existing.hostSocketId = socketId;
          existing.hostClientId = clientId;
          return {
            code: existing.code,
            playerId: 1,
            members: this.publicMembers(existing),
            resumed: true,
            inRound: existing.inRound,
            replacedSocketId: replaced,
          };
        }
      }
    }

    const code = this.generateCode();
    const room: Room = {
      code,
      hostSocketId: socketId,
      hostClientId: clientId,
      members: [{ playerId: 1, pseudo, socketId, ready: true, clientId }],
      nextPlayerId: 2,
      inRound: false,
    };
    this.rooms.set(code, room);
    return {
      code,
      playerId: 1,
      members: this.publicMembers(room),
      resumed: false,
      inRound: false,
    };
  }

  /**
   * Adhésion / reprise d'invité.
   * 1. même clientId → vol de siège ;
   * 2. hors manche, même pseudo hors ligne → reclaim ;
   * 3. sinon insert, ou PSEUDO_TAKEN.
   */
  claimSeat(
    code: string,
    pseudo: string,
    socketId: string,
    clientId?: string,
  ): SeatClaim {
    const room = this.rooms.get(code);
    if (!room) {
      throw new LobbyError("LOBBY_NOT_FOUND", "Salon introuvable");
    }

    if (clientId) {
      const byId = room.members.find((m) => m.clientId === clientId);
      if (byId) {
        return this.attachMember(room, byId, socketId, clientId, pseudo);
      }
    }

    const normalized = pseudo.trim().toLocaleLowerCase();
    const samePseudo = room.members.find(
      (m) => m.pseudo.trim().toLocaleLowerCase() === normalized,
    );
    if (samePseudo) {
      if (!room.inRound && samePseudo.socketId === "") {
        return this.attachMember(room, samePseudo, socketId, clientId, samePseudo.pseudo);
      }
      throw new LobbyError(
        "PSEUDO_TAKEN",
        "Ce pseudo est déjà pris dans ce salon, choisis-en un autre.",
      );
    }

    const playerId = room.nextPlayerId;
    room.nextPlayerId += 1;
    room.members.push({
      playerId,
      pseudo,
      socketId,
      ready: false,
      clientId,
    });
    return { playerId, members: this.publicMembers(room), resumed: false };
  }

  /** Compat tests / ancien nom : insert ou claim. */
  joinRoom(
    code: string,
    pseudo: string,
    socketId: string,
    clientId?: string,
  ): { playerId: number; members: PublicMember[] } {
    const claimed = this.claimSeat(code, pseudo, socketId, clientId);
    return { playerId: claimed.playerId, members: claimed.members };
  }

  /**
   * Reprise par clientId dans ce salon (vol de siège si la socket est encore là).
   * Retourne null si aucune chaise ne porte ce clientId.
   */
  rejoinRoom(
    code: string,
    clientId: string,
    socketId: string,
  ): SeatClaim | null {
    const room = this.rooms.get(code);
    if (!room) return null;
    const member = room.members.find((m) => m.clientId === clientId);
    if (!member) return null;
    return this.claimSeat(code, member.pseudo, socketId, clientId);
  }

  /**
   * Fil coupé : vide la socket si elle est encore celle du siège.
   * No-op si le siège a déjà une autre socket (vol de siège).
   */
  park(socketId: string): ParkResult | null {
    const room = this.findRoomBySocket(socketId);
    if (!room) return null;
    const member = room.members.find((m) => m.socketId === socketId);
    if (!member) return null;
    const wasHost = member.playerId === 1;
    member.socketId = "";
    if (wasHost) {
      room.hostSocketId = "";
    }
    return {
      code: room.code,
      playerId: member.playerId,
      wasHost,
      clientId: member.clientId,
      members: this.publicMembers(room),
    };
  }

  /**
   * Retire un membre parqué (grâce écoulée). No-op s'il s'est reconnecté
   * ou si une manche est en cours (l'appelant ne devrait pas appeler).
   */
  leaveByClientId(clientId: string): LeaveResult | null {
    for (const room of this.rooms.values()) {
      const member = room.members.find((m) => m.clientId === clientId && m.socketId === "");
      if (!member) continue;
      if (room.inRound) return null;
      return this.removeMember(room, member);
    }
    return null;
  }

  /** Ferme le salon si l'hôte est encore parqué (grâce hôte écoulée). */
  closeIfHostParked(code: string): LeaveResult | null {
    const room = this.rooms.get(code);
    if (!room) return null;
    const host = room.members.find((m) => m.playerId === 1);
    if (!host || host.socketId !== "") return null;
    this.rooms.delete(code);
    return {
      code,
      wasHost: true,
      playerId: 1,
      clientId: host.clientId,
      members: [],
    };
  }

  /** Retire un membre parqué identifié par salon + playerId. */
  leaveParkedPlayer(code: string, playerId: number): LeaveResult | null {
    const room = this.rooms.get(code);
    if (!room) return null;
    const member = room.members.find((m) => m.playerId === playerId && m.socketId === "");
    if (!member) return null;
    if (room.inRound) return null;
    return this.removeMember(room, member);
  }

  /** Départ franc (bouton Quitter) : retire le membre ; ferme le salon si hôte. */
  leave(socketId: string): LeaveResult | null {
    const room = this.findRoomBySocket(socketId);
    if (!room) return null;
    const member = room.members.find((m) => m.socketId === socketId);
    if (!member) return null;
    return this.removeMember(room, member);
  }

  setReady(socketId: string, ready: boolean): { code: string; members: PublicMember[] } | null {
    const room = this.findRoomBySocket(socketId);
    if (!room) return null;
    const member = room.members.find((m) => m.socketId === socketId);
    if (!member) return null;
    member.ready = ready;
    return { code: room.code, members: this.publicMembers(room) };
  }

  /** Remet les invités en « non prêt » (début de partie). L'hôte (playerId 1) reste prêt. */
  resetReady(room: Room): void {
    room.members.forEach((m) => {
      if (m.playerId !== 1) {
        m.ready = false;
      }
    });
  }

  setInRound(socketId: string, inRound: boolean): Room | null {
    const room = this.findRoomBySocket(socketId);
    if (!room) return null;
    room.inRound = inRound;
    return room;
  }

  parkedGuests(room: Room): Member[] {
    return room.members.filter((m) => m.playerId !== 1 && m.socketId === "" && m.clientId);
  }

  findRoomBySocket(socketId: string): Room | undefined {
    for (const room of this.rooms.values()) {
      if (room.members.some((m) => m.socketId === socketId)) {
        return room;
      }
    }
    return undefined;
  }

  findRoomByCode(code: string): Room | undefined {
    return this.rooms.get(code);
  }

  isHost(socketId: string): boolean {
    const room = this.findRoomBySocket(socketId);
    if (!room) return false;
    const member = room.members.find((m) => m.socketId === socketId);
    return member?.playerId === 1;
  }

  playerIdFor(socketId: string): number | undefined {
    const room = this.findRoomBySocket(socketId);
    return room?.members.find((m) => m.socketId === socketId)?.playerId;
  }

  socketIdForPlayer(room: Room, playerId: number): string | undefined {
    const id = room.members.find((m) => m.playerId === playerId)?.socketId;
    return id === "" ? undefined : id;
  }

  publicMembers(room: Room): PublicMember[] {
    return room.members.map((m) => ({
      playerId: m.playerId,
      pseudo: m.pseudo,
      isHost: m.playerId === 1,
      ready: m.ready,
      connected: m.socketId !== "",
    }));
  }

  private attachMember(
    room: Room,
    member: Member,
    socketId: string,
    clientId: string | undefined,
    pseudo: string,
  ): SeatClaim {
    const replaced =
      member.socketId !== "" && member.socketId !== socketId ? member.socketId : undefined;
    member.socketId = socketId;
    if (clientId) member.clientId = clientId;
    if (pseudo) member.pseudo = pseudo;
    if (member.playerId === 1) {
      room.hostSocketId = socketId;
      if (clientId) room.hostClientId = clientId;
    }
    return {
      playerId: member.playerId,
      members: this.publicMembers(room),
      replacedSocketId: replaced,
      resumed: true,
    };
  }

  private removeMember(room: Room, member: Member): LeaveResult {
    const wasHost = member.playerId === 1;
    room.members = room.members.filter((m) => m !== member);
    if (wasHost || room.members.length === 0) {
      this.rooms.delete(room.code);
      return {
        code: room.code,
        wasHost: true,
        playerId: member.playerId,
        clientId: member.clientId,
        members: [],
      };
    }
    return {
      code: room.code,
      wasHost: false,
      playerId: member.playerId,
      clientId: member.clientId,
      members: this.publicMembers(room),
    };
  }

  private findRoomByHostClientId(clientId: string): Room | undefined {
    for (const room of this.rooms.values()) {
      if (room.hostClientId === clientId) return room;
      const host = room.members.find((m) => m.playerId === 1 && m.clientId === clientId);
      if (host) return room;
    }
    return undefined;
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
