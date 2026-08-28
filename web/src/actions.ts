// Interface des actions exposées aux écrans. Implémentée dans main.ts, liée au store.

export interface Actions {
  showJoin(): void;
  join(code: string, pseudo: string): void;
  setReady(ready: boolean): void;
  quit(): void;
  guestRevealDone(): void;
  guestCastVote(targetId: number): void;
  guestSubmitGuess(text: string): void;
  guestSeeResults(): void;
  guestMarkReady(): void;
}
