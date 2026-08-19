// Élimination vue par un invité (miroir de GuestEliminationScreen).

import { h, scrim } from "../ui";
import type { AppState } from "../state";
import type { Actions } from "../actions";
import type { EliminationSnapshot, Role } from "../protocol";

function rolePhrase(role: Role): string {
  switch (role) {
    case "CITIZEN":
      return "un Citoyen";
    case "IMPOSTOR":
      return "un Imposteur";
    case "UNKNOWN":
      return "l'Inconnu";
  }
}

function eliminationPhrase(elimination: EliminationSnapshot, isMe: boolean): string {
  const role = rolePhrase(elimination.role);
  return isMe
    ? `Tu étais ${role}, tu as été éliminé !`
    : `${elimination.pseudo} était ${role}, il a été éliminé !`;
}

export function renderElimination(state: AppState, actions: Actions): HTMLElement {
  const elimination = state.elimination;
  if (!elimination) return h("div", { class: "screen" }, "…");

  const isMe = elimination.playerId === state.myPlayerId;
  const hasResult = state.guestResult != null;

  const bgClass =
    elimination.role === "CITIZEN"
      ? "bg-elim-citizen"
      : elimination.role === "IMPOSTOR"
        ? "bg-elim-impostor"
        : "bg-elim-unknown";

  const nodes: (Node | null)[] = [
    h("div", { class: "spacer-lg" }),
    h("h1", { class: "center" }, scrim(eliminationPhrase(elimination, isMe))),
    h("div", { class: "grow" }),
  ];

  if (!hasResult) {
    const preStepText =
      elimination.guessResolved || elimination.role !== "UNKNOWN"
        ? `Début du tour ${elimination.turnNumber}`
        : isMe
          ? "Tu as une dernière chance de deviner le mot des Citoyens."
          : `${elimination.pseudo} a une dernière chance de deviner le mot des Citoyens.`;
    nodes.push(
      h("div", { class: "center" }, scrim(preStepText)),
      h("div", { class: "spacer" }),
    );
  }

  nodes.push(
    h(
      "div",
      { class: "center" },
      scrim(hasResult ? "La partie est terminée." : "En attente du Maître du Jeu…"),
    ),
  );

  if (hasResult) {
    nodes.push(
      h("div", { class: "spacer-lg" }),
      h(
        "button",
        { class: "btn", onclick: () => actions.guestSeeResults() },
        "Voir les résultats",
      ),
    );
  }

  return h(
    "div",
    { class: `screen ${bgClass}` },
    h("div", { class: "screen-inner" }, ...nodes),
  );
}
