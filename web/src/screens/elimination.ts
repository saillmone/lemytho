// Élimination vue par un invité (miroir de GuestEliminationScreen).

import { h, scrim } from "../ui";
import type { AppState } from "../state";
import type { Actions } from "../actions";
import type { EliminationSnapshot, Role } from "../protocol";

const MAX_GUESS_LENGTH = 80;

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

function guessVerdict(
  elimination: EliminationSnapshot,
  isMe: boolean,
  canTypeGuess: boolean,
  guessFound: boolean,
  hasResult: boolean,
): string | null {
  const missed =
    elimination.guessResolved ||
    (hasResult && elimination.role === "UNKNOWN" && !guessFound);
  if (canTypeGuess) {
    return "Tu as une dernière chance de deviner le mot des Citoyens.";
  }
  if (elimination.role === "UNKNOWN" && !elimination.guessResolved && !hasResult) {
    return `${elimination.pseudo} tente de deviner le mot des Citoyens…`;
  }
  if (guessFound) {
    return isMe
      ? "Tu as trouvé le mot des Citoyens !"
      : `${elimination.pseudo} a trouvé le mot des Citoyens !`;
  }
  if (missed) return "Ce n'était pas le mot des Citoyens.";
  if (hasResult) return null;
  return `Début du tour ${elimination.turnNumber}`;
}

export function renderElimination(state: AppState, actions: Actions): HTMLElement {
  const elimination = state.elimination;
  if (!elimination) return h("div", { class: "screen" }, "…");

  const isMe = elimination.playerId === state.myPlayerId;
  const hasResult = state.guestResult != null;
  const guessFound =
    state.guestResult?.victory.type === "UNKNOWN" &&
    state.guestResult.victory.byGuess === true;
  const canTypeGuess =
    isMe &&
    elimination.role === "UNKNOWN" &&
    !elimination.guessResolved &&
    !hasResult;

  const bgClass =
    elimination.role === "CITIZEN"
      ? "bg-elim-citizen"
      : elimination.role === "IMPOSTOR"
        ? "bg-elim-impostor"
        : "bg-elim-unknown";

  const nodes: (Node | null)[] = [
    h("div", { class: "spacer-lg" }),
    h("h1", { class: "center" }, scrim(eliminationPhrase(elimination, isMe))),
  ];

  const waitingForUnknownGuess =
    !canTypeGuess &&
    elimination.role === "UNKNOWN" &&
    !elimination.guessResolved &&
    !hasResult;

  const mid: (Node | null)[] = [];
  const preStepText = guessVerdict(
    elimination,
    isMe,
    canTypeGuess,
    guessFound,
    hasResult,
  );
  if (preStepText) {
    mid.push(h("div", { class: "center" }, scrim(preStepText)));
  }

  if (canTypeGuess) {
    const guessInput = h("input", {
      type: "text",
      placeholder: "Le mot des Citoyens",
      maxlength: MAX_GUESS_LENGTH,
      autocomplete: "off",
      disabled: state.guessSubmitted,
    }) as HTMLInputElement;

    const submit = h(
      "button",
      {
        class: "btn",
        disabled: true,
        onclick: () => {
          actions.guestSubmitGuess(guessInput.value);
        },
      },
      "Proposer le mot",
    ) as HTMLButtonElement;
    const syncSubmit = () => {
      submit.disabled = state.guessSubmitted || guessInput.value.trim().length === 0;
    };
    guessInput.addEventListener("input", syncSubmit);
    syncSubmit();

    mid.push(
      h("div", { class: "field" }, guessInput),
      submit,
    );
  }

  const raiseStatus = Boolean(preStepText);
  const midClass = raiseStatus
    ? "grow center-stack elim-status-raised"
    : "grow center-stack";
  nodes.push(h("div", { class: midClass }, ...mid));

  if (!canTypeGuess && !waitingForUnknownGuess) {
    nodes.push(
      h("div", { class: "spacer" }),
      h(
        "div",
        { class: "center" },
        scrim(hasResult ? "La partie est terminée." : "En attente du Maître du Jeu…"),
      ),
    );
  }

  if (hasResult) {
    nodes.push(
      h("div", { class: "spacer" }),
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
