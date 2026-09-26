#!/usr/bin/env bash
# Préparation d'un VPS Ubuntu 24.04 neuf pour TMS2M (à lancer une seule fois, en root ou via sudo).
set -euo pipefail

echo "== Mise à jour du système"
apt-get update -y
DEBIAN_FRONTEND=noninteractive apt-get upgrade -y

echo "== Docker (paquets Ubuntu officiels)"
DEBIAN_FRONTEND=noninteractive apt-get install -y docker.io docker-compose-v2 ufw unattended-upgrades
systemctl enable --now docker

echo "== Pare-feu : SSH, HTTP, HTTPS uniquement"
ufw allow OpenSSH
ufw allow 80/tcp
ufw allow 443/tcp
ufw --force enable

echo "== Mises à jour de sécurité automatiques"
dpkg-reconfigure -f noninteractive unattended-upgrades

echo "== Dossier de l'application"
mkdir -p /opt/tms2m
echo "Serveur prêt."
