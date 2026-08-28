// Résultat final vu par un invité (miroir de GuestResultScreen).

import { confirmDialog, h, scrim } from "../ui";
import type { AppState } from "../state";
import type { Actions } from "../actions";
import { roleCssClass, type ResultSnapshot, type Victory } from "../protocol";

function score(result: ResultSnapshot, playerId: number): number {
  return result.totalScores[String(playerId)] ?? 0;
}

function victoryTitle(result: Victory, players: ResultSnapshot["players"]): string {
  switch (result.type) {
    case "ONGOING":
      return "Partie en cours";
    case "CITIZEN": {
      const n = players.filter((p) => p.role === "CITIZEN").length;
      return n <= 1 ? "Victoire du Citoyen" : "Victoire des Citoyens";
    }
    case "IMPOSTOR": {
      const n = players.filter((p) => p.role === "IMPOSTOR").length;
      return n <= 1 ? "Victoire de l'Imposteur" : "Victoire des Imposteurs";
    }
    case "UNKNOWN":
      return "Victoire de l'Inconnu";
    case "COMBINED": {
      const nI = players.filter((p) => p.role === "IMPOSTOR").length;
      const prefix = nI <= 1 ? "Victoire de l'Imposteur" : "Victoire des Imposteurs";
      return `${prefix} et de l'Inconnu`;
    }
  }
}

function victorySubtitle(
  result: Victory,
  players: ResultSnapshot["players"],
  unknownGuessCorrect: boolean | null,
): string {
  let base = "";
  switch (result.type) {
    case "ONGOING":
      base = "";
      break;
    case "CITIZEN":
      base = "Tous les Imposteurs et les Inconnus ont été éliminés.";
      break;
    case "IMPOSTOR":
      base = "Au moins un Imposteur a survécu jusqu'à la fin.";
      break;
    case "COMBINED": {
      const impostors = players.filter((p) => p.role === "IMPOSTOR").length;
      const unknowns = players.filter((p) => p.role === "UNKNOWN").length;
      const impostorLabel = impostors <= 1 ? "l'Imposteur" : "les Imposteurs";
      const unknownLabel = unknowns <= 1 ? "l'Inconnu" : "les Inconnus";
      base = `Les Citoyens sont éliminés : ${impostorLabel} et ${unknownLabel} gagnent ensemble.`;
      break;
    }
    case "UNKNOWN":
      if (result.byGuess) {
        const winner = players.find((p) => p.playerId === result.winnerIds[0]);
        base = `${winner?.pseudo ?? "l'Inconnu"} a deviné le mot exact !`;
      } else {
        base = "l'Inconnu a survécu jusqu'à la fin.";
      }
      break;
  }
  if (unknownGuessCorrect === false) {
    const missed = "L'Inconnu n'a pas trouvé le mot des Citoyens.";
    return base.length === 0 ? missed : `${base} ${missed}`;
  }
  return base;
}

function rankClass(rank: number): string {
  if (rank === 1) return "rank gold";
  if (rank === 2) return "rank silver";
  if (rank === 3) return "rank bronze";
  return "rank none";
}

export function renderResult(state: AppState, actions: Actions): HTMLElement {
  const result = state.guestResult;
  if (!result) return h("div", { class: "screen" }, "…");

  const ranked = [...result.players].sort(
    (a, b) => score(result, b.playerId) - score(result, a.playerId),
  );

  const rows = ranked.map((player, index) => {
    const points = score(result, player.playerId);
    return h(
      "div",
      { class: "score-row" },
      h("span", { class: rankClass(index + 1) }, index < 3 ? `${index + 1}` : ""),
      h("span", { class: `role-dot ${roleCssClass(player.role)}` }),
      h("span", { class: "pseudo" }, player.pseudo),
      h("span", {}, points > 0 ? `+${points}` : "+0"),
    );
  });

  const replayArea = state.wantsReplay
    ? scrim("En attente du Maître du Jeu…")
    : h(
        "button",
        { class: "btn", onclick: () => actions.guestMarkReady() },
        "Prêt pour la prochaine manche",
      );

  const screen = h("div", { class: "screen bg-results" });

  const quitButton = h("button", { class: "link" }, "Quitter");
  quitButton.addEventListener("click", () => {
    screen.append(
      confirmDialog({
        title: "Quitter le salon ?",
        text: "Tu vas quitter le salon et être retiré de la partie.",
        confirmLabel: "Quitter",
        cancelLabel: "Annuler",
        onConfirm: () => actions.quit(),
        onCancel: () => {},
      }),
    );
  });

  screen.append(
    h(
      "div",
      { class: "screen-inner" },
      h("h1", { class: "center" }, scrim(victoryTitle(result.victory, result.players))),
      h("div", { class: "spacer" }),
      h("div", { class: "center" }, scrim(victorySubtitle(result.victory, result.players, state.unknownGuessCorrect))),
      h("div", { class: "spacer-lg" }),
      h("div", { class: "center" }, scrim("Score total")),
      h("div", { class: "spacer" }),
      h("div", { class: "grow" }, h("div", { class: "members" }, ...rows)),
      h("div", { class: "spacer-lg" }),
      replayArea,
      h("div", { class: "spacer" }),
      quitButton,
    ),
  );

  return screen;
}
