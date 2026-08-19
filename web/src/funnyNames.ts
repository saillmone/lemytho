// Pseudos amusants (thème espion/enquête), miroir de FunnyNames.kt.

const NAMES = [
  "Sherlock",
  "Columbo",
  "Mata Hari",
  "Arsène",
  "Le Fouineur",
  "Hercule",
  "Miss Marple",
  "Le Corbeau",
  "Tête Brûlée",
  "L'Indic",
  "La Taupe",
  "Double Jeu",
  "Mr X",
  "La Silhouette",
  "L'Ombre",
  "Baron Noir",
  "Professeur",
  "La Belette",
  "Cervelle",
  "L'Espionne",
];

export function randomFunnyName(): string {
  return NAMES[Math.floor(Math.random() * NAMES.length)];
}
