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

function unknownGuessVerdict(
  guessText: string | null,
  correct: boolean,
  isMe: boolean,
  pseudo: string,
): string {
  const win = isMe ? "tu gagnes la partie !" : "il gagne la partie !";
  const quoted = guessText?.trim() ? guessText : null;
  if (quoted && correct) return `"${quoted}" était le mot des Citoyens : ${win}`;
  if (quoted) return `"${quoted}" n'était pas le mot des Citoyens.`;
  if (correct && isMe) return `Tu as trouvé le mot des Citoyens : ${win}`;
  if (correct) return `${pseudo} a trouvé le mot des Citoyens : ${win}`;
  return "Ce n'était pas le mot des Citoyens.";
}

function guessVerdict(
  elimination: EliminationSnapshot,
  isMe: boolean,
  canTypeGuess: boolean,
  guessFound: boolean,
  hasResult: boolean,
): string | null {
  const missed =
    (elimination.guessResolved && !elimination.guessCorrect) ||
    (hasResult &&
      elimination.role === "UNKNOWN" &&
      !guessFound &&
      !elimination.guessCorrect);
  if (canTypeGuess) {
    return "Tu as une dernière chance de deviner le mot des Citoyens.";
  }
  if (elimination.role === "UNKNOWN" && !elimination.guessResolved && !hasResult) {
    return `${elimination.pseudo} tente de deviner le mot des Citoyens…`;
  }
  if (guessFound || elimination.guessCorrect) {
    return unknownGuessVerdict(
      elimination.guessText,
      true,
      isMe,
      elimination.pseudo,
    );
  }
  if (missed) {
    return unknownGuessVerdict(elimination.guessText, false, isMe, elimination.pseudo);
  }
  return null;
}

function turnFollowUp(
  canTypeGuess: boolean,
  waitingForUnknownGuess: boolean,
  hasResult: boolean,
  turnNumber: number,
): string | null {
  if (canTypeGuess || waitingForUnknownGuess || hasResult) return null;
  return `Début du tour ${turnNumber}`;
}

function sparkleBurst(inner: HTMLElement): HTMLElement {
  const sparks = Array.from({ length: 16 }, (_, i) =>
    h("span", { class: `sparkle sparkle-${i}` }),
  );
  return h("div", { class: "sparkle-burst" }, inner, ...sparks);
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
    const pill = scrim(preStepText);
    const guessWin = guessFound || elimination.guessCorrect;
    mid.push(
      guessWin ? sparkleBurst(pill) : h("div", { class: "center" }, pill),
    );
  }
  const followUp = turnFollowUp(
    canTypeGuess,
    waitingForUnknownGuess,
    hasResult,
    elimination.turnNumber,
  );
  if (followUp) {
    mid.push(h("div", { class: "center" }, scrim(followUp)));
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

  const raiseStatus = Boolean(preStepText || followUp);
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
