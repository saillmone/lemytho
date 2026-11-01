// Élimination vue par un invité (miroir de GuestEliminationScreen).

import { h, roleGlyph, scrim, sessionBanners } from "../ui";
import type { AppState } from "../state";
import type { Actions } from "../actions";
import { roleCssClass, type EliminationSnapshot, type Role } from "../protocol";

const MAX_GUESS_LENGTH = 80;

function voteParty(
  pseudo: string,
  elimination: EliminationSnapshot,
  myPseudo: string | null,
): HTMLElement {
  const eliminated = pseudo === elimination.pseudo;
  const label = myPseudo != null && pseudo === myPseudo ? `${pseudo} (moi)` : pseudo;
  const name = h(
    "span",
    { class: eliminated ? `vote-elim ${roleCssClass(elimination.role)}` : "" },
    label,
  );
  if (!eliminated) return h("span", { class: "vote-party" }, name);
  return h("span", { class: "vote-party" }, roleGlyph(elimination.role), name);
}

function voteListInline(
  elimination: EliminationSnapshot,
  myPseudo: string | null,
  compact: boolean,
): HTMLElement | null {
  if (elimination.votes.length === 0) return null;
  return h(
    "div",
    { class: compact ? "vote-list-inline compact" : "vote-list-inline" },
    ...elimination.votes.map((v) =>
      h(
        "div",
        { class: "vote-line" },
        voteParty(v.voterPseudo, elimination, myPseudo),
        " → ",
        voteParty(v.targetPseudo, elimination, myPseudo),
      ),
    ),
  );
}

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
  const myPseudo =
    state.board?.players.find((p) => p.playerId === state.myPlayerId)?.pseudo ??
    state.members.find((m) => m.playerId === state.myPlayerId)?.pseudo ??
    null;
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
    sessionBanners(state),
    h("h1", { class: "center" }, scrim(eliminationPhrase(elimination, isMe))),
  ];

  const waitingForUnknownGuess =
    !canTypeGuess &&
    elimination.role === "UNKNOWN" &&
    !elimination.guessResolved &&
    !hasResult;

  const preStepText = guessVerdict(
    elimination,
    isMe,
    canTypeGuess,
    guessFound,
    hasResult,
  );
  const followUp = turnFollowUp(
    canTypeGuess,
    waitingForUnknownGuess,
    hasResult,
    elimination.turnNumber,
  );
  const verdictNode = preStepText
    ? guessFound || elimination.guessCorrect
      ? sparkleBurst(scrim(preStepText))
      : h("div", { class: "center" }, scrim(preStepText))
    : null;

  const mid: (Node | null)[] = [voteListInline(elimination, myPseudo, canTypeGuess)];
  if (canTypeGuess) {
    if (verdictNode) mid.push(verdictNode);
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
    guessInput.addEventListener("focus", () => {
      requestAnimationFrame(() =>
        guessInput.scrollIntoView({ block: "nearest", behavior: "smooth" }),
      );
    });
    syncSubmit();

    mid.push(h("div", { class: "field" }, guessInput), submit);
  }

  nodes.push(
    h("div", { class: canTypeGuess ? "elim-mid has-guess" : "elim-mid" }, ...mid),
  );

  const footer: (Node | null)[] = [];
  if (!canTypeGuess) {
    if (verdictNode) footer.push(verdictNode);
    if (followUp) footer.push(h("div", { class: "center" }, scrim(followUp)));
    if (!waitingForUnknownGuess) {
      footer.push(
        h(
          "div",
          { class: "center" },
          scrim(hasResult ? "La partie est terminée." : "En attente du Maître du Jeu…"),
        ),
      );
    }
  }
  if (hasResult) {
    footer.push(
      h(
        "button",
        { class: "btn", onclick: () => actions.guestSeeResults() },
        "Voir les résultats",
      ),
    );
  }
  if (footer.length > 0) {
    nodes.push(h("div", { class: "elim-footer" }, ...footer));
  }

  return h(
    "div",
    { class: `screen ${bgClass}${canTypeGuess ? " has-ime" : ""}` },
    h("div", { class: "screen-inner" }, ...nodes),
  );
}
