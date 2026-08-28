// Écran de révélation individuelle (miroir de RevealOwnScreen). Maintenir pour révéler.

import { h, scrim } from "../ui";
import type { AppState } from "../state";
import type { Actions } from "../actions";

export function renderReveal(state: AppState, actions: Actions): HTMLElement {
  const isUnknown = state.myRole === "UNKNOWN";
  const waiting = state.revealConfirmed;

  const screen = h("div", { class: "screen bg-reveal" });
  const inner = h("div", { class: "screen-inner" });

  let content: HTMLElement;
  if (waiting) {
    const count =
      state.revealTotal > 0
        ? h("div", {}, scrim(`Prêts : ${state.revealAcked} / ${state.revealTotal}`))
        : null;
    content = h(
      "div",
      { class: "reveal-zone" },
      h(
        "div",
        {},
        scrim("En attente des autres joueurs…"),
        count ?? null,
      ),
    );
  } else {
    const revealZone = h("div", { class: "reveal-zone" }, scrim("Touche l'écran pour révéler"));
    const wordClass = "reveal-word font-title";
    // Maintenir pour révéler (souris + tactile). Le fond de l'Inconnu n'apparaît
    // en plein écran que pendant l'appui (miroir de RevealOwnScreen).
    const show = () => {
      if (isUnknown) {
        screen.classList.remove("bg-reveal");
        screen.classList.add("bg-unknown");
      }
      revealZone.replaceChildren(
        isUnknown
          ? h(
              "div",
              { class: "reveal-unknown" },
              scrim("Tu es l'Inconnu", "reveal-unknown-title"),
              h("div", { class: "spacer" }),
              scrim("Tu ne reçois pas de mot.\nTu connais ton rôle dès le départ : devine le mot des Citoyens sans te faire repérer."),
            )
          : h(
              "div",
              {},
              scrim("Ton mot secret :"),
              h("div", { class: "spacer" }),
              scrim(state.myWord ?? "", wordClass),
            ),
      );
    };
    const hide = () => {
      if (isUnknown) {
        screen.classList.remove("bg-unknown");
        screen.classList.add("bg-reveal");
      }
      revealZone.replaceChildren(scrim("Touche l'écran pour révéler"));
    };
    revealZone.addEventListener("pointerdown", (e) => {
      e.preventDefault();
      show();
    });
    revealZone.addEventListener("pointerup", hide);
    revealZone.addEventListener("pointercancel", hide);
    revealZone.addEventListener("pointerleave", hide);
    content = revealZone;
  }

  const doneButton = h(
    "button",
    {
      class: "btn",
      disabled: waiting,
      onclick: () => actions.guestRevealDone(),
    },
    "Je suis prêt pour la suite",
  );

  inner.append(
    h("h1", { class: "center" }, scrim("Révélation")),
    h("div", { class: "spacer" }),
    h("div", { class: "center" }, scrim(state.pseudo)),
    content,
    h("div", { class: "spacer" }),
    doneButton,
  );
  screen.append(inner);
  return screen;
}
