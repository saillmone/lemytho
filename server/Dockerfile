# --- Stage 1 : compilation du serveur Node ---
FROM node:22-alpine AS server-build
WORKDIR /app
COPY server/package.json server/package-lock.json ./
RUN npm ci
COPY server/tsconfig.json ./
COPY server/src ./src
RUN npm run build

# --- Stage 2 : compilation du client web (Vite) ---
FROM node:22-alpine AS web-build
WORKDIR /web
COPY web/package.json web/package-lock.json ./
RUN npm ci
COPY web/ ./
RUN npm run build

# --- Stage 3 : image finale (relais + fichiers statiques) ---
FROM node:22-alpine
WORKDIR /app
ENV NODE_ENV=production
ENV PUBLIC_DIR=/app/public
COPY server/package.json server/package-lock.json ./
RUN npm ci --omit=dev
COPY --from=server-build /app/dist ./dist
COPY --from=web-build /web/dist ./public
EXPOSE 3000
CMD ["node", "dist/index.js"]
