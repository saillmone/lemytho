import { defineConfig } from "vite";

// Le client est servi en same-origin par le relais Node : base relative pour
// que les assets soient résolus quel que soit le chemin de déploiement.
export default defineConfig({
  base: "./",
  build: {
    outDir: "dist",
    emptyOutDir: true,
  },
});
