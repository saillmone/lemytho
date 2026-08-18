#!/usr/bin/env bash
# =============================================================================
# Configuration nginx + HTTPS (certbot) pour OpenCover sur le VPS.
# À exécuter UNE SEULE FOIS, avec sudo, une fois le domaine DuckDNS créé.
#
# Usage : sudo bash nginx-setup.sh [domaine] [email]
#   ex.  : sudo bash nginx-setup.sh opencover.duckdns.org toi@exemple.com
# =============================================================================
set -euo pipefail

DOMAIN="${1:-opencover.duckdns.org}"
EMAIL="${2:-}"

echo "==> Domaine : $DOMAIN"

# 0. Vérifie que le domaine pointe bien vers ce serveur (sinon certbot échouera).
RESOLVED=$(getent hosts "$DOMAIN" | awk '{print $1}' | head -n 1 || true)
if [ -z "$RESOLVED" ]; then
  echo "!! Le domaine $DOMAIN ne résout pas encore." >&2
  echo "!! Crée-le d'abord sur DuckDNS (IP : 152.228.232.87) et attends 2-3 min." >&2
  exit 1
fi
echo "==> $DOMAIN résout vers $RESOLVED"

# 1. Vhost HTTP (certbot ajoutera lui-même la partie HTTPS ensuite).
echo "==> Création du vhost nginx"
cat > "/etc/nginx/sites-available/opencover" <<EOF
server {
    listen 80;
    listen [::]:80;
    server_name $DOMAIN;

    location / {
        proxy_pass http://127.0.0.1:3000;
        proxy_http_version 1.1;
        proxy_set_header Upgrade \$http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
        proxy_read_timeout 300;
        proxy_send_timeout 300;
    }
}
EOF

ln -sf "/etc/nginx/sites-available/opencover" "/etc/nginx/sites-enabled/opencover"

nginx -t

# 2. Installation de certbot si absent.
if ! command -v certbot >/dev/null 2>&1; then
  echo "==> Installation de certbot"
  apt-get update
  DEBIAN_FRONTEND=noninteractive apt-get install -y certbot python3-certbot-nginx
fi

# 3. Obtention du certificat et activation HTTPS.
echo "==> Obtention du certificat HTTPS"
if [ -n "$EMAIL" ]; then
  certbot --nginx -d "$DOMAIN" --non-interactive --agree-tos -m "$EMAIL" --redirect
else
  certbot --nginx -d "$DOMAIN" --non-interactive --agree-tos --register-unsafely-without-email --redirect
fi

echo "==> Rechargement nginx"
systemctl reload nginx

echo "==> Terminé. https://$DOMAIN doit maintenant répondre."
