import { createReadStream, existsSync, statSync } from "node:fs";
import { extname, join, resolve, sep } from "node:path";
import type { IncomingMessage, ServerResponse } from "node:http";

// Serveur de fichiers statiques minimal (aucune dépendance) pour le client web
// et l'APK téléchargeable. Repli SPA sur index.html, /join -> /join/index.html.

const MIME: Record<string, string> = {
  ".html": "text/html; charset=utf-8",
  ".js": "text/javascript; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".json": "application/json; charset=utf-8",
  ".svg": "image/svg+xml",
  ".png": "image/png",
  ".jpg": "image/jpeg",
  ".webp": "image/webp",
  ".apk": "application/vnd.android.package-archive",
  ".ico": "image/x-icon",
  ".txt": "text/plain; charset=utf-8",
};

function serveFile(res: ServerResponse, filePath: string, contentType: string): void {
  res.writeHead(200, {
    "Content-Type": contentType,
    "Cache-Control": "no-cache",
  });
  createReadStream(filePath).pipe(res);
}

export function createStaticHandler(publicDir: string): (req: IncomingMessage, res: ServerResponse) => void {
  const root = resolve(publicDir);

  return (req, res) => {
    const url = new URL(req.url ?? "/", "http://localhost");
    let pathname: string;
    try {
      pathname = decodeURIComponent(url.pathname);
    } catch {
      res.writeHead(400);
      res.end();
      return;
    }

    if (pathname === "/" || pathname === "") {
      pathname = "/index.html";
    } else if (pathname === "/join" || pathname === "/join/") {
      pathname = "/join/index.html";
    }

    const filePath = join(root, pathname);

    // Sécurité : interdit toute sortie du répertoire public (path traversal).
    if (filePath !== root && !filePath.startsWith(root + sep)) {
      res.writeHead(403);
      res.end();
      return;
    }

    if (existsSync(filePath) && statSync(filePath).isFile()) {
      serveFile(res, filePath, MIME[extname(filePath)] ?? "application/octet-stream");
      return;
    }

    // Repli SPA : routes inconnues -> index.html (sauf /join qui n'existe pas).
    const fallback = join(root, "index.html");
    if (existsSync(fallback)) {
      serveFile(res, fallback, MIME[".html"]);
      return;
    }

    res.writeHead(404, { "Content-Type": "text/plain; charset=utf-8" });
    res.end("Not found");
  };
}
