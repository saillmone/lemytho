// Mini helper de rendu DOM sans framework : h(tag, props, ...children).

export type VNodeChild = Node | string | null | undefined | false;

export function h<K extends keyof HTMLElementTagNameMap>(
  tag: K,
  props: Record<string, unknown> = {},
  ...children: VNodeChild[]
): HTMLElementTagNameMap[K] {
  const el = document.createElement(tag);
  for (const [key, value] of Object.entries(props)) {
    if (value == null || value === false) continue;
    if (key === "class") {
      el.className = String(value);
    } else if (key === "dataset") {
      for (const [dk, dv] of Object.entries(value as Record<string, string>)) {
        el.dataset[dk] = dv;
      }
    } else if (key.startsWith("on") && typeof value === "function") {
      const eventName = key.slice(2).toLowerCase();
      el.addEventListener(eventName, value as EventListener);
    } else if (key in el) {
      (el as unknown as Record<string, unknown>)[key] = value;
    } else {
      el.setAttribute(key, String(value));
    }
  }
  for (const child of children) {
    if (child == null || child === false) continue;
    el.append(child);
  }
  return el;
}

/** Racine de rendu : remplace tout le contenu du conteneur par [node]. */
export function mount(root: HTMLElement, node: Node): void {
  root.replaceChildren(node);
}

export function scrim(text: string, cls = ""): HTMLElement {
  return h("span", { class: `scrim ${cls}`.trim() }, text);
}

/** Options d'une modale de confirmation (miroir de AlertDialog). */
export interface ConfirmDialogOptions {
  title: string;
  text: string;
  confirmLabel: string;
  cancelLabel: string;
  onConfirm: () => void;
  onCancel: () => void;
}

/** Modale de confirmation non fermable par l'extérieur. Se retire à l'annulation. */
export function confirmDialog(options: ConfirmDialogOptions): HTMLElement {
  const backdrop = h("div", { class: "modal-backdrop" });
  const modal = h(
    "div",
    { class: "modal confirm" },
    h("h2", {}, options.title),
    h("div", { class: "spacer" }),
    h("div", { class: "muted" }, options.text),
    h("div", { class: "spacer-lg" }),
    h(
      "button",
      {
        class: "btn",
        onclick: () => {
          options.onConfirm();
        },
      },
      options.confirmLabel,
    ),
    h("div", { class: "spacer" }),
    h(
      "button",
      {
        class: "link",
        onclick: () => {
          backdrop.remove();
          options.onCancel();
        },
      },
      options.cancelLabel,
    ),
  );
  backdrop.append(modal);
  return backdrop;
}
