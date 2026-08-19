// Écran de saisie pseudo + code (miroir de JoinLobbyScreen).
// Champs non contrôlés : on lit les valeurs à la soumission pour éviter une
// re-création du DOM (et la perte de focus) à chaque frappe.

import { h } from "../ui";
import { randomFunnyName } from "../funnyNames";
import type { AppState } from "../state";
import type { Actions } from "../actions";

export function renderJoin(state: AppState, actions: Actions): HTMLElement {
  const pseudoInput = h("input", {
    type: "text",
    placeholder: "Ton pseudo",
    maxlength: 24,
    autocomplete: "off",
    value: state.pseudo,
  }) as HTMLInputElement;

  const randomPseudo = h(
    "button",
    {
      class: "link small",
      onclick: () => {
        pseudoInput.value = randomFunnyName();
      },
    },
    "Pseudo aléatoire",
  );

  const codeInput = h("input", {
    type: "text",
    placeholder: "Code du salon",
    maxlength: 4,
    autocapitalize: "characters",
    autocomplete: "off",
    value: state.joinCode,
  }) as HTMLInputElement;

  const submit = h(
    "button",
    {
      class: "btn",
      onclick: () => actions.join(codeInput.value, pseudoInput.value),
    },
    "Rejoindre",
  ) as HTMLButtonElement;

  const syncSubmit = () => {
    submit.disabled = codeInput.value.length !== 4;
  };
  codeInput.addEventListener("input", () => {
    codeInput.value = codeInput.value.toUpperCase().replace(/[^A-Z0-9]/g, "").slice(0, 4);
    syncSubmit();
  });
  syncSubmit();

  const back = h(
    "button",
    { class: "link", onclick: () => actions.quit() },
    "Retour",
  );

  const error = state.error ? h("div", { class: "error center" }, state.error) : null;

  return h(
    "div",
    { class: "screen bg-players" },
    h(
      "div",
      { class: "screen-inner" },
      h("h1", {}, "Rejoindre une partie"),
      h("div", { class: "spacer-lg" }),
      h("div", { class: "field" }, h("label", {}, "Ton pseudo"), pseudoInput, randomPseudo),
      h("div", { class: "field" }, h("label", {}, "Code du salon"), codeInput),
      error,
      h("div", { class: "spacer-lg" }),
      submit,
      h("div", { class: "grow" }),
      h("div", { class: "spacer-lg" }),
      back,
    ),
  );
}
