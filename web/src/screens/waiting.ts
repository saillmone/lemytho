// Écran d'attente dans le salon (miroir de WaitingScreen), réservé à l'invité.

import { confirmDialog, h, scrim } from "../ui";
import type { AppState } from "../state";
import type { Actions } from "../actions";
import type { LobbyMember } from "../protocol";

function roleLabelForLobby(playingCount: number): string {
  if (playingCount === 3) return "2 Citoyens · 1 Inconnu ou 1 Imposteur";
  return roleDistributionLabel(playingCount);
}

function roleDistributionLabel(playerCount: number, threePlayerIsUnknown = true): string {
  if (playerCount === 3) {
    return threePlayerIsUnknown ? "2 Citoyens · 1 Inconnu" : "2 Citoyens · 1 Imposteur";
  }
  const intrus = Math.floor((playerCount + 1) / 3);
  const unknownCount = Math.floor((intrus + 1) / 3);
  const impostorCount = intrus - unknownCount;
  const citizenCount = playerCount - intrus;
  const parts = [citizenCount <= 1 ? "1 Citoyen" : `${citizenCount} Citoyens`];
  if (impostorCount > 0) {
    parts.push(impostorCount <= 1 ? "1 Imposteur" : `${impostorCount} Imposteurs`);
  }
  if (unknownCount > 0) {
    parts.push(unknownCount <= 1 ? "1 Inconnu" : `${unknownCount} Inconnus`);
  }
  return parts.join(" · ");
}

function memberRow(member: LobbyMember, isMe: boolean): HTMLElement {
  // Suffixe identique à l'app : « (toi) » a priorité sur « (Maître du Jeu) ».
  const suffix = isMe ? " (toi)" : member.isHost ? " (Maître du Jeu)" : "";
  const disconnected = !member.connected ? " (déconnecté)" : "";
  return h(
    "li",
    { class: "member" },
    h("span", { class: `dot ${member.ready ? "ready" : ""}` }),
    h("span", { class: "pseudo" }, `${member.pseudo}${suffix}${disconnected}`),
  );
}

export function renderWaiting(state: AppState, actions: Actions): HTMLElement {
  const readyCount = state.members.filter((m) => m.ready).length;
  const playingCount = state.members.length;
  const myMember = state.members.find((m) => m.playerId === state.myPlayerId);
  const myReady = myMember?.ready ?? false;

  const membersList = h(
    "ul",
    { class: "members" },
    ...state.members.map((m) => memberRow(m, m.playerId === state.myPlayerId)),
  );

  const readyButton = h(
    "button",
    {
      class: `btn ${myReady ? "secondary" : ""}`,
      onclick: () => actions.setReady(!myReady),
    },
    myReady ? "Je ne suis pas prêt" : "Je suis prêt",
  );

  const footer = h(
    "div",
    { class: "muted center" },
    readyCount < 3
      ? "En attente d'au moins 3 joueurs prêts…"
      : `Répartition : ${roleLabelForLobby(playingCount)}`,
  );

  const screen = h("div", { class: "screen bg-players" });

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

  const errorBlock = state.error
    ? h("div", {}, scrim(state.error, "error center"))
    : null;

  screen.append(
    h(
      "div",
      { class: "screen-inner" },
      h("h1", {}, "En attente…"),
      h("div", { class: "spacer" }),
      scrim(`Le Maître du Jeu va lancer la partie (salon ${state.lobbyCode ?? ""})`, "center"),
      h("div", { class: "spacer-lg" }),
      readyButton,
      h("div", { class: "spacer" }),
      footer,
      errorBlock,
      h("div", { class: "spacer-lg" }),
      h("h2", {}, `Joueurs (${state.members.length})`),
      h("div", { class: "spacer" }),
      membersList,
      h("div", { class: "grow" }),
      h("div", { class: "spacer-lg" }),
      quitButton,
    ),
  );

  return screen;
}
