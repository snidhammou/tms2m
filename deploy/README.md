# Déploiement de production TMS2M

Architecture : **Caddy** (HTTPS Let's Encrypt automatique) → **serveur TMS** → **PostgreSQL**, en conteneurs Docker.
Seuls les ports 22 (SSH), 80 et 443 sont ouverts.

## 1. DNS (Namecheap → Advanced DNS)
Supprimer les enregistrements de parking, puis ajouter :

| Type | Host | Valeur | TTL |
|---|---|---|---|
| A | `@` | IP du VPS | Automatic |
| A | `www` | IP du VPS | Automatic |

## 2. Préparer le serveur (une fois)
```bash
scp -i ~/.ssh/tms2m_ed25519 deploy/server-setup.sh ubuntu@IP:/tmp/
ssh -i ~/.ssh/tms2m_ed25519 ubuntu@IP "sudo bash /tmp/server-setup.sh"
```

## 3. Secrets de production (une fois, sur le PC)
```bash
powershell -ExecutionPolicy Bypass -File deploy\new-env.ps1 -Domain tms2m.com -Email vous@exemple.com
```
Crée `deploy/.env` (non versionné) avec mot de passe admin, clé d'enrôlement et mot de passe de base aléatoires.
**Conservez ce fichier** dans un gestionnaire de mots de passe.

## 4. Déployer / mettre à jour
```bash
powershell -ExecutionPolicy Bypass -File deploy\deploy.ps1 -Server IP -User ubuntu
```
La console est ensuite disponible sur `https://tms2m.com`.

## 5. Agents
Compiler les agents avec l'URL et la clé d'enrôlement de production :
```bash
gradlew assembleNewlandRelease -PtmsServerUrl=https://tms2m.com -PtmsEnrollmentKey=<TMS_ENROLLMENT_KEY>
```

## Exploitation
- Logs : `ssh … "cd /opt/tms2m/deploy && sudo docker compose logs -f tms-server"`
- Sauvegarde de la base : `sudo docker compose exec postgres pg_dump -U tms tms > tms-$(date +%F).sql`
- Les données (base, APK, fichiers remontés) sont dans des volumes Docker persistants.
