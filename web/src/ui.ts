import { roleCssClass, type Role } from "./protocol";

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

const ROLE_SVGS: Record<Role, string> = {
  CITIZEN:
    '<svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor" aria-hidden="true"><path d="M12 12c2.7 0 5-2.3 5-5s-2.3-5-5-5-5 2.3-5 5 2.3 5 5 5zm0 2c-3.3 0-10 1.7-10 5v3h20v-3c0-3.3-6.7-5-10-5z"/></svg>',
  IMPOSTOR:
    '<svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor" aria-hidden="true"><path d="M21.99 8.89c0-1.08-.9-1.86-1.9-1.86-.28 0-.54.05-.78.14C18.73 5.25 16.82 4 14.5 4c-1.18 0-2.24.4-3.05 1.07C10.64 4.4 9.58 4 8.4 4 6.08 4 4.17 5.25 3.59 7.17c-.24-.09-.5-.14-.78-.14-1 0-1.9.78-1.9 1.86 0 3.22 3.16 6.11 9.54 8.27 6.38-2.16 9.54-5.05 9.54-8.27zM9 11.5c-.83 0-1.5-.67-1.5-1.5S8.17 8.5 9 8.5s1.5.67 1.5 1.5-.67 1.5-1.5 1.5zm6 0c-.83 0-1.5-.67-1.5-1.5s.67-1.5 1.5-1.5 1.5.67 1.5 1.5-.67 1.5-1.5 1.5z"/></svg>',
  UNKNOWN:
    '<svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor" aria-hidden="true"><path d="M12 6c3.79 0 7.17 2.13 8.82 5.5-.59 1.22-1.42 2.27-2.42 3.12L21 17.27 19.73 18.5 2.81 1.59 1.39 3l3.53 3.53C3.08 8.3 1.78 10.02 1.18 11.5 2.83 14.87 6.21 17 10 17c.76 0 1.5-.1 2.2-.28l.9.9c-.99.25-2.03.38-3.1.38-4.52 0-8.32-2.75-10-6.5C1.73 8.12 5.81 5 12 5c1.4 0 2.74.25 3.98.7L14.12 7.56C13.45 6.6 12.77 6 12 6zm0 3c.55 0 1.05.15 1.5.4L10.4 12.5c-.25-.45-.4-.95-.4-1.5 0-1.66 1.34-3 3-3z"/></svg>',
};

/** Icône de rôle (optionnellement suivie d'un libellé). */
export function roleGlyph(role: Role, label?: string): HTMLElement {
  const wrap = h("span", { class: `role-icon ${roleCssClass(role)}` });
  wrap.innerHTML = ROLE_SVGS[role] ?? "";
  if (label) wrap.append(h("span", { class: "role-name" }, label));
  return wrap;
}

export function sessionBanners(opts: {
  connectionStatus: string;
  hostPaused: boolean;
  awaitingSync: boolean;
  wakeLockFailed?: boolean;
}): HTMLElement {
  const nodes: HTMLElement[] = [];
  if (opts.connectionStatus !== "connected") {
    nodes.push(h("div", { class: "banner" }, "Hors ligne — retourne sur le jeu…"));
  }
  if (opts.hostPaused) {
    nodes.push(h("div", { class: "banner" }, "Maître du Jeu hors ligne — partie en pause"));
  }
  if (opts.awaitingSync) {
    nodes.push(h("div", { class: "banner" }, "Reconnexion…"));
  }
  if (opts.wakeLockFailed) {
    nodes.push(
      h(
        "div",
        { class: "banner" },
        "Laisse l'écran allumé ; si tu pars, reviens sur l'onglet, tu reprendras ta place.",
      ),
    );
  }
  return h("div", { class: "banners" }, ...nodes);
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
