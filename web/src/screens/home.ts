// Page d'accueil du client web : présentation du jeu, règles des rôles, et
// accès au téléchargement de l'app ou au jeu dans le navigateur.

import { h, scrim } from "../ui";
import type { Actions } from "../actions";

function ruleCard(name: string, hint: string, goal: string, roleClass: string): HTMLElement {
  return h(
    "div",
    { class: "card rule-card" },
    h("div", { class: `name ${roleClass}` }, name),
    h("div", { class: "rule-hint" }, hint),
    h("div", { class: "rule-goal muted" }, goal),
  );
}

export function renderHome(actions: Actions): HTMLElement {
  const download = h(
    "button",
    {
      class: "btn",
      onclick: () => {
        window.location.href = "/apk/LeMytho-latest.apk";
      },
    },
    "Télécharger l'application (Android)",
  );

  const play = h(
    "button",
    { class: "btn secondary", onclick: () => actions.showJoin() },
    "Jouer dans le navigateur",
  );

  return h(
    "div",
    { class: "screen bg-home" },
    h(
      "div",
      { class: "screen-inner" },
      h("h1", { class: "center font-title home-title" }, "Le Mytho"),
      h("div", { class: "spacer" }),
      h("div", { class: "center" }, scrim("Jeu de rôles cachés")),
      h("div", { class: "spacer" }),
      h(
        "div",
        { class: "center muted" },
        "Démasque les intrus par le débat et le vote, sans jamais révéler ton mot.",
      ),
      h("div", { class: "spacer-lg" }),
      h("h2", { class: "center" }, "Les rôles"),
      h("div", { class: "spacer" }),
      ruleCard(
        "Citoyen",
        "Reçoit un mot secret, identique pour tous les Citoyens.",
        "Démasque les intrus.",
        "role-citizen",
      ),
      ruleCard(
        "Imposteur",
        "Reçoit un mot proche de celui des Citoyens.",
        "Reste caché jusqu'au bout.",
        "role-impostor",
      ),
      ruleCard(
        "l'Inconnu",
        "Ne reçoit aucun mot.",
        "Devine le mot des Citoyens.",
        "role-unknown",
      ),
      h("div", { class: "grow" }),
      h("div", { class: "spacer-lg" }),
      download,
      h("div", { class: "spacer" }),
      play,
      h("div", { class: "spacer" }),
      h("div", { class: "center" }, scrim("Pour jouer, demande le code du salon au Maître du Jeu.")),
      h("div", { class: "spacer-lg" }),
    ),
  );
}
