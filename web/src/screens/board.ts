// Plateau public vu par un invité (miroir de GuestBoardScreen) + dialogue de vote.

import { h, scrim } from "../ui";
import type { AppState } from "../state";
import type { Actions } from "../actions";
import { roleCssClass, roleLabel, type BoardSnapshot, type PublicPlayer, type VotePhase } from "../protocol";

function shouldVote(board: BoardSnapshot, myId: number): boolean {
  switch (board.votePhase) {
    case "IDLE":
      return false;
    case "VOTING":
      return board.players.some((p) => p.playerId === myId && p.status === "ACTIVE");
    case "SECOND_ROUND":
      return !board.tiedCandidates.includes(myId);
  }
}

function statusText(votePhase: VotePhase, hasVoted: boolean, canVote: boolean): string {
  if (hasVoted) return "Vote enregistré, en attente des autres…";
  if (votePhase === "IDLE") return "Donnez vos indices dans l'ordre, discutez… puis votez !";
  if (canVote && votePhase === "SECOND_ROUND") return "Égalité : re-vote pour départager.";
  if (canVote) return "À toi de voter !";
  return "Vote en cours…";
}

function playerCard(player: PublicPlayer, order: number | undefined, isMe: boolean): HTMLElement {
  const eliminated = player.status === "ELIMINATED";
  const label = `${order != null ? `${order}. ` : ""}${player.pseudo}${isMe ? " (toi)" : ""}`;
  const roleLine =
    eliminated && player.role
      ? h("div", { class: `role ${roleCssClass(player.role)}` }, roleLabel(player.role))
      : null;
  return h(
    "div",
    { class: `card ${eliminated ? "eliminated" : ""}` },
    h("div", { class: "name" }, label),
    roleLine,
  );
}

export function renderBoard(state: AppState, actions: Actions): HTMLElement {
  const board = state.board;
  if (!board) return h("div", { class: "screen" }, "Chargement du plateau…");

  const myId = state.myPlayerId ?? -1;
  const rankById = new Map(board.clueOrder.map((id, index) => [id, index + 1]));
  const orderedPlayers = [...board.players].sort((a, b) => {
    const ra = a.status === "ACTIVE" ? rankById.get(a.playerId) ?? Number.MAX_SAFE_INTEGER : Number.MAX_SAFE_INTEGER + a.playerId;
    const rb = b.status === "ACTIVE" ? rankById.get(b.playerId) ?? Number.MAX_SAFE_INTEGER : Number.MAX_SAFE_INTEGER + b.playerId;
    return ra - rb;
  });

  const canVote = shouldVote(board, myId);

  const grid = h(
    "div",
    { class: "grid" },
    ...orderedPlayers.map((p) => playerCard(p, rankById.get(p.playerId), p.playerId === myId)),
  );

  const root = h(
    "div",
    { class: "screen bg-board" },
    h(
      "div",
      { class: "screen-inner" },
      h("h1", {}, scrim("Plateau de jeu")),
      h("div", { class: "spacer" }),
      h(
        "div",
        { class: "row" },
        scrim(`Manche ${board.roundNumber}`),
        scrim(`Tour ${board.turnNumber}`),
      ),
      board.category ? h("div", { class: "spacer" }, scrim(`Catégorie : ${board.category}`)) : null,
      h("div", { class: "spacer-lg" }),
      scrim(statusText(board.votePhase, state.hasVoted, canVote)),
      h("div", { class: "spacer" }),
      grid,
    ),
  );

  if (canVote && !state.hasVoted) {
    const targets =
      board.votePhase === "SECOND_ROUND"
        ? board.players.filter((p) => board.tiedCandidates.includes(p.playerId) && p.playerId !== myId)
        : board.players.filter((p) => p.status === "ACTIVE" && p.playerId !== myId);

    const isSecondRound = board.votePhase === "SECOND_ROUND";
    const modal = h(
      "div",
      { class: "modal-backdrop" },
      h(
        "div",
        { class: "modal" },
        h("h2", {}, isSecondRound ? "Égalité !" : "Vote"),
        isSecondRound ? h("div", { class: "muted" }, "Re-votez pour départager les ex æquo.") : null,
        h("div", { class: "spacer" }),
        h("div", { class: "muted" }, "Qui n'est pas un Citoyen d'après toi ?"),
        h("div", { class: "spacer" }),
        ...targets.map((t) =>
          h(
            "button",
            { class: "vote-target", onclick: () => actions.guestCastVote(t.playerId) },
            t.pseudo,
          ),
        ),
      ),
    );
    root.append(modal);
  }

  return root;
}
