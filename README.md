# TMS multi-constructeur (Newland · PAX · Sunmi · …)

Terminal Management System composé de :

| Module | Techno | Rôle |
|---|---|---|
| `backend/` | Spring Boot 3.3 (Java 17), JPA, H2/PostgreSQL | API d'administration, API terminaux, dépôt d'APK, console web |
| `android-agent/` | Android Java (minSdk 22, targetSdk 34) | **TMS2M Agent**, installé sur chaque terminal : relie le terminal au serveur et les apps du terminal à l'agent (AIDL) |

> Le nom affiché de l'agent est « TMS2M Agent ». Son identifiant technique reste `com.tms.agent` : il sert à la liaison AIDL des applis clientes et à la mise à jour des agents déjà déployés.

```
 Console web ──(Basic auth)──► /api/admin/v1 ─┐
                                              │  Spring Boot + BDD + stockage APK
 Agent Android ─(X-Device-Token)► /api/device/v1 ─┘
      ▲
      │ AIDL (ITmsAgentService) + broadcast PARAMETERS_UPDATED
 Application de paiement
```

## Fonctionnalités

- **Parc multi-marques** : un seul APK agent, détection du constructeur au runtime (`DeviceManagerFactory`) → `PaxDeviceManager`, `NewlandDeviceManager`, `SunmiDeviceManager`, `GenericDeviceManager`.
- **Enrôlement** par clé partagée (auto-accept ou numéros de série pré-enregistrés), jeton device unique (seul son hash SHA-256 est stocké).
- **Supervision** : heartbeat (batterie, IP, versions OS/firmware/agent, inventaire des applications), statut en ligne/hors ligne.
- **Organisation** : marchands, groupes, TID.
- **Dépôt d'APK** : upload, lecture auto du manifeste (package, version), empreinte SHA-256 vérifiée par l'agent.
- **Déploiements** : `INSTALL_APP`, `UNINSTALL_APP`, `PUSH_PARAMS`, `REBOOT` ciblant des terminaux, un groupe, un marchand, un constructeur ou tout le parc. Les terminaux pré-enregistrés reçoivent leurs tâches dès l'enrôlement (staging).
- **Paramètres applicatifs** à 3 niveaux, GLOBAL → GROUPE → TERMINAL (le plus spécifique l'emporte).
- **Fiabilité de l'agent** : outbox persistante des statuts, reprise après auto-mise à jour, redémarrage au boot.
- **Temps réel** : l'agent garde une requête longue ouverte (`/api/device/v1/wait`) ; bouton **Synchroniser** dans la console et tâches délivrées immédiatement. Le polling reste le filet de sécurité.
- **Notifications** en bas à gauche de la console à la fin de chaque tâche (✅ / ❌ et message du terminal).
- **Icônes** : pictogramme de TPE aux couleurs de la marque dans la liste des terminaux ; icônes des applications (extraites des APK du dépôt ou envoyées par l'agent).
- **Désinstallation** d'une application depuis l'onglet Applications d'un terminal (l'agent TMS lui-même est protégé).
- **Mise à jour automatique de l'agent** : voir [Publier une nouvelle version de l'agent](#publier-une-nouvelle-version-de-lagent).

### Fonctions « TOMS » (phase 1)

| Module | Détail |
|---|---|
| **Organisations** | Hiérarchie (ex. acquéreur > région > agence), marchands rattachés. Filtrer ou déployer sur une organisation inclut ses sous-organisations |
| **Zéro contact** | *Modèles de déploiement* (applications, modèles de paramètres, démarrage auto, kiosque) liés à un **groupe** : appliqués automatiquement au pré-enregistrement, à l'enrôlement ou au changement de groupe. Pré-enregistrement **par lots** |
| **Modèles de paramètres** | Jeux clé/valeur réutilisables par application, applicables à un niveau (global / groupe / terminal) avec push |
| **Mise à jour planifiée** | Tâches « pas avant » une date et/ou dans une fenêtre quotidienne (ex. 22:00 → 06:00) |
| **Démarrage auto** | Application lancée au démarrage du terminal |
| **Mode kiosque** | Terminal verrouillé sur une liste d'applications (Device Owner requis) |
| **Supervision** | Stockage, mémoire, type de réseau, trafic, uptime, position ; graphiques sur 24 h (conservation configurable, 7 jours par défaut) ; alertes batterie < 20 % et stockage < 10 % |
| **Historique** | Journal des actions admin et des événements terminaux (enrôlement, applis installées / mises à jour / retirées, zéro contact) |
| **Assistance à distance** | Diagnostic (rapport d'état, joignabilité serveur, dérive d'horloge), extraction des logs par application et par plage horaire (jusqu'à 200 000 lignes, fichier .gz), extraction d'un fichier du terminal |

Limites connues : les logs des autres applications exigent la permission `READ_LOGS`, accordée une fois par adb (`adb shell pm grant com.tms.agent android.permission.READ_LOGS`), sinon seuls ceux de l'agent sont remontés ; le journal Android est circulaire (agrandir le tampon dans les options développeur) ; la position n'est disponible que si le terminal a un fournisseur de localisation actif ; le firmware, le bureau à distance et l'injection de clés (RKI) ne sont pas couverts.

## Ouvrir le projet (IDE)

Le dépôt contient **deux projets Gradle indépendants** : ouvrez chacun **dans son IDE, à partir de son sous-dossier** (la racine du dépôt n'est pas un projet Gradle).

| Dossier | Contenu | IDE conseillé |
|---|---|---|
| `backend/` | Serveur Spring Boot et console web (`src/main/resources/static`) | **IntelliJ IDEA** (Community suffit) |
| `android-agent/` | TMS2M Agent (4 variantes) et l'app de démo AIDL `sample-client` | **Android Studio** |
| `deploy/`, `tools/` | Scripts PowerShell, Docker Compose, Caddy | VS Code ou IntelliJ |

**Backend (IntelliJ IDEA)**
1. *File › Open* → `backend/` : le projet Gradle est importé automatiquement.
2. JDK **17** (*File › Project Structure › SDK*).
3. Lancer `TmsServerApplication` (ou la tâche Gradle `bootRun`) → console sur http://localhost:8095 (identifiants de développement ci-dessous).
4. Tests : tâche Gradle `test`, ou clic droit sur `src/test`. Base H2 locale dans `backend/data/` (non versionnée).

**Agent (Android Studio)**
1. *File › Open* → `android-agent/`.
2. Panneau *Build Variants* : choisir `newlandDebug`, `paxDebug`, `sunmiDebug` ou `universalDebug`.
3. Un *Run ▶* produit un agent de développement qui pointe par défaut sur `http://10.0.2.2:8095` (le PC vu depuis l'émulateur) ; l'URL reste modifiable dans l'écran de l'agent.
4. Les **APK de production** se construisent toujours avec `tools\build-agent-release.ps1` (voir plus bas).

**Fichiers non versionnés** à placer sur chaque poste (à recopier après un `git clone`) :

| Fichier | Rôle |
|---|---|
| `android-agent/local.properties` | Chemin du SDK Android (créé par Android Studio) |
| `android-agent/keystore.properties` + keystore `.jks` | Signature S2M (modèle : `keystore.properties.example`) |
| `android-agent/app/libs/newland/MESDK-*.aar`, `pax/NeptuneLiteApi_*.jar`, `sunmi/PayLib-*.aar` | SDK constructeurs |
| `deploy/.env` | Secrets de production (généré par `deploy/new-env.ps1`) |

**Particularités Windows**
- Gardez le projet dans un **chemin court** (ex. `C:\MyWorkspace\tms-multibrand`) : `aidl.exe` échoue au-delà de 260 caractères.
- Si Gradle échoue avec « Unable to establish loopback connection », définir la variable d'environnement :
  `JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=%USERPROFILE%\.gradle` (variable Windows ou configuration de lancement de l'IDE).

## Démarrage rapide

### Backend
```bash
cd backend
./gradlew bootRun
```
Console : http://localhost:8095 (`admin` / `admin123` en dev). Base H2 dans `backend/data/`.

Production : https://tms2m.com (VPS OVH, Docker Compose : Caddy + PostgreSQL + serveur). Mise à jour du serveur :
```bash
powershell -ExecutionPolicy Bypass -File deploy\deploy.ps1 -Server 146.59.225.67
```
Installation initiale et détails : [deploy/README.md](deploy/README.md).

| Variable | Défaut |
|---|---|
| `TMS_ENROLLMENT_KEY` | `CHANGE-ME-ENROLL-KEY` |
| `TMS_AUTO_ACCEPT` | `true` |
| `TMS_POLL_INTERVAL` | `60` (secondes) |
| `TMS_AGENT_AUTO_UPDATE` | `true` (mise à jour automatique de l'agent) |
| `TMS_ADMIN_USER` / `TMS_ADMIN_PASSWORD` | `admin` / `admin123` |
| `TMS_APK_DIR` | `./data/apks` |
| `SERVER_PORT` | `8095` |

Tests : `./gradlew test` (scénarios d'intégration enrôlement → heartbeat → tâche → statut, paramètres, sécurité).

### Agent Android
```bash
cd android-agent
./gradlew assembleDebug -PtmsServerUrl=http://192.168.1.10:8095 -PtmsEnrollmentKey=CHANGE-ME-ENROLL-KEY
```
L'URL et la clé sont intégrées à l'APK (staging de masse sans saisie). Elles restent modifiables dans l'écran de l'agent.

**Variantes de build** (une par SDK constructeur) :

| Variante | Tâche Gradle | Contenu |
|---|---|---|
| `universal` | `assembleUniversalDebug` / `Release` | API Android standard, pour tout terminal |
| `newland` | `assembleNewlandDebug` / `Release` | + MESDK Newland (`app/libs/newland/`) : n° de série et firmware officiels (le MESDK ne redémarre pas Android : `NDK_SysReboot` ne redémarre que le processeur sécurisé K21, et n'est donc pas utilisé) |
| `pax` | `assemblePaxDebug` / `Release` | + NeptuneLite PAX (`app/libs/pax/`) : installation / désinstallation silencieuses, reboot, n° de série / modèle / firmware |
| `sunmi` | `assembleSunmiDebug` / `Release` | + PayLib Sunmi (`app/libs/sunmi/`) : reboot via `sysPowerManage`, n° de série / modèle officiels |

### Publier une nouvelle version de l'agent

1. Construire les 4 APK de production (signés S2M, URL https://tms2m.com et clé d'enrôlement lues dans `deploy/.env`) :
   ```bash
   powershell -ExecutionPolicy Bypass -File tools\build-agent-release.ps1 -Version 1.0.5
   ```
   → `android-agent/dist/TMS2M-Agent-<variante>-1.0.5-prod.apk`. Le versionCode vaut `X*100000 + Y*1000 + Z*10 + variante` (0 universal, 1 newland, 2 pax, 3 sunmi) : toujours croissant et unique par variante.
2. Les publier dans la console : **Applications › Publier un APK** (sélection multiple possible).
3. Au heartbeat suivant, chaque terminal dont l'agent est plus ancien reçoit l'APK **de sa variante** (suffixe du versionName : `1.0.5-pax`, `1.0.5-newland`…). Une tâche par terminal et par version ; nouvel essai 6 h après un échec ou une confirmation restée sans réponse. Historique : « Mise à jour auto de l'agent ».

Installation silencieuse en Device Owner (N950S) ou via le SDK PAX. Sinon (Sunmi sans Device Owner), une notification « installation à confirmer » s'affiche sur le terminal, sans bloquer la synchronisation. La colonne **Agent** de la liste des terminaux indique qui est à jour. Désactivation : `TMS_AGENT_AUTO_UPDATE=false`.

Le MESDK Newland et PayLib Sunmi **ne fournissent pas d'API d'installation d'APK**. Pour installer en silence sur ces marques, l'agent doit être signé avec la clé système du constructeur. Il en va de même pour le **reboot sur Newland**.

### Mode Device Owner (reboot et installations silencieuses sans signature constructeur)

TMS2M Agent peut être défini comme **Device Owner** (propriétaire de l'appareil). Android lui ouvre alors `DevicePolicyManager.reboot()` et l'installation / désinstallation silencieuses, sur **toutes les marques** (Android 7+), sans signature plateforme. C'est la solution pour le reboot sur Newland et pour l'installation silencieuse sur Newland et Sunmi.

Activation, une fois par terminal, sur un terminal **sans compte** configuré :
```bash
adb shell dpm set-device-owner com.tms.agent/.admin.AgentDeviceAdmin
```
- L'écran de l'agent affiche « Device Owner : oui ». Le bouton « Retirer Device Owner » permet de le retirer en développement.
- Un agent Device Owner **ne peut pas être désinstallé** tant que ce statut est actif. Les mises à jour doivent être signées avec la même clé.
- En production, l'activation se fait au déploiement : adb en atelier, ou provisioning par QR code après réinitialisation.

Chaque variante embarque le SDK de sa marque et, pour les autres constructeurs, l'implémentation sans SDK de `src/stubs/<marque>`. Quelle que soit la marque, l'agent ne déclare une installation réussie qu'après avoir vérifié la version réellement installée, et un reboot qu'après avoir constaté que le terminal a redémarré.

**Tester avec un terminal en USB** (sans Wi-Fi ni pare-feu) :
```bash
gradlew assembleNewlandDebug -PtmsServerUrl=http://127.0.0.1:8095
powershell -ExecutionPolicy Bypass -File tools\install-agent.ps1      # installe uniquement sur les terminaux Newland branchés
powershell -ExecutionPolicy Bypass -File tools\usb-tunnel.ps1         # rétablit adb reverse à chaque reconnexion USB
```
**Tester en Wi-Fi local** (terminal et PC sur le même réseau) : compilez avec `-PtmsServerUrl=http://<IP-du-PC>:8095`, ou saisissez cette URL dans l'écran de l'agent. Le port 8095 doit être ouvert dans le pare-feu Windows pour le sous-réseau local :
```bash
New-NetFirewallRule -DisplayName "TMS2M 8095 (LAN)" -Direction Inbound -Protocol TCP -LocalPort 8095 -RemoteAddress LocalSubnet -Action Allow
```
En HTTP clair, c'est à réserver aux tests. En production, il faut un serveur HTTPS.

Les deux scripts ne ciblent que le constructeur demandé (`-Manufacturer`, `newland` par défaut) : un téléphone branché en même temps est ignoré.

## Signature par constructeur (important)

Sur les terminaux de production, Newland, PAX et Sunmi n'acceptent **que les APK signés avec leurs clés** (portails développeur constructeur). Pour une installation, une désinstallation ou un redémarrage **silencieux**, l'agent doit être :
- signé avec la clé plateforme/système du constructeur, **ou**
- relié au SDK du constructeur (points d'extension prévus dans `PaxDeviceManager`, `NewlandDeviceManager` et `SunmiDeviceManager` : déposer le jar/aar dans `app/libs/<marque>/` puis surcharger `vendorSerial()`, `installApk()` et `reboot()`).

Sans cela, l'agent fonctionne quand même : Android demande une confirmation à l'écran pour les installations, et les tâches REBOOT remontent en échec avec un message explicite.

En pratique, on produit une variante signée par marque à partir du même code source.

## Intégration d'une application de paiement

```java
Intent i = new Intent("com.tms.agent.action.BIND").setPackage("com.tms.agent");
bindService(i, new ServiceConnection() {
    public void onServiceConnected(ComponentName n, IBinder b) {
        ITmsAgentService tms = ITmsAgentService.Stub.asInterface(b);
        String params = tms.getParameters(getPackageName()); // JSON {"host":"…","port":"…"}
        String info   = tms.getTerminalInfo();                // SN, constructeur, modèle…
    }
    public void onServiceDisconnected(ComponentName n) { }
}, BIND_AUTO_CREATE);
```
Copier `android-agent/app/src/main/aidl/com/tms/agent/ITmsAgentService.aidl` dans l'application cliente. Une application ne peut lire **que ses propres** paramètres (contrôle par UID). Pour être notifiée d'une mise à jour, déclarer un receiver sur `com.tms.agent.action.PARAMETERS_UPDATED` (extras `packageName`, `parameters`).

Sur Android 11+, l'application cliente doit déclarer `<queries><package android:name="com.tms.agent"/></queries>`.

## API

**Terminal** (`/api/device/v1`, en-tête `X-Device-Token` sauf `/enroll`)
`POST /enroll` · `POST /heartbeat` · `GET /wait` (temps réel) · `POST /tasks/{id}/status` · `POST /tasks/{id}/artifact` · `POST /icons/{packageName}` · `GET /parameters?packageName=` · `GET /apps/{id}/download`

**Admin** (`/api/admin/v1`, HTTP Basic)
`/dashboard` · `/meta` · `/terminals[/{id}[/parameters|/metrics|/history]]` · `POST /terminals/{id}/sync` · `/organizations` · `/merchants` · `/groups` · `/apps[/icons/{packageName}]` · `/parameters` · `/deployment-templates` · `/parameter-templates` · `/tasks[/{id}/cancel|/artifact]` · `POST /deployments` · `/audit`

## Points ouverts (rappels)

- [ ] **Sunmi P3 de test (SN `P365P54QJ0423`) : réinitialisation usine à faire**, pour passer TMS2M Agent en Device Owner et débloquer l'installation silencieuse.
  - Le Device Owner actuel est un prototype S2M (`com.ma.s2m.nxp.tms.tmsapp`, signé avec la clé debug du PC d'un autre développeur, SHA-256 `a55c7a86…5ebff6`). Il ne sait pas rendre son statut, et ce statut ne se retire pas par adb.
  - Alternative sans effacement : récupérer le `debug.keystore` de son développeur, puis publier une mise à jour qui appelle `clearDeviceOwnerApp()`.
  - Avant : vérifier qu'aucune donnée ni clé de paiement utile n'est sur le terminal.
  - Après : Wi-Fi sans aucun compte, débogage USB, puis `tools/install-agent.ps1 -Manufacturer sunmi` et `adb shell dpm set-device-owner com.tms.agent/.admin.AgentDeviceAdmin`.
- [ ] **Newland** : demander la signature plateforme de TMS2M Agent (alternative au Device Owner, déjà actif sur le N950S de test).
- [ ] **PAX / Newland production** : faire signer TMS2M Agent par le constructeur (outil PAX / Certificate Management Newland).
- [ ] Désinstaller l'agent du téléphone Samsung (`RFCR60L95ZF`) installé par erreur lors des tests.

## Pistes pour la production
- mTLS ou attestation constructeur à l'enrôlement (aujourd'hui : la clé partagée + le numéro de série suffisent) ;
- comptes admin multiples avec rôles, et journal d'audit ;
- migrations Flyway au lieu de `ddl-auto=update` ;
- distribution des APK via CDN et push (FCM/MQTT) en plus du polling.
