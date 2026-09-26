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

## Démarrage rapide

### Backend
```bash
cd backend
./gradlew bootRun
```
Console : http://localhost:8095 (`admin` / `admin123` en dev). Base H2 dans `backend/data/`.

Production (PostgreSQL) : `docker compose up --build`, puis changez `TMS_ADMIN_PASSWORD` et `TMS_ENROLLMENT_KEY`.

| Variable | Défaut |
|---|---|
| `TMS_ENROLLMENT_KEY` | `CHANGE-ME-ENROLL-KEY` |
| `TMS_AUTO_ACCEPT` | `true` |
| `TMS_POLL_INTERVAL` | `60` (secondes) |
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
| `newland` | `assembleNewlandDebug` / `Release` | + MESDK Newland (`app/libs/newland/`) : reboot via `NDK_SysReboot`, n° de série et firmware officiels |
| `pax` | `assemblePaxDebug` / `Release` | + NeptuneLite PAX (`app/libs/pax/`) : installation / désinstallation silencieuses, reboot, n° de série / modèle / firmware |
| `sunmi` | `assembleSunmiDebug` / `Release` | + PayLib Sunmi (`app/libs/sunmi/`) : reboot via `sysPowerManage`, n° de série / modèle officiels |

Le MESDK Newland et PayLib Sunmi **ne fournissent pas d'API d'installation d'APK**. Pour installer en silence sur ces marques, l'agent doit être signé avec la clé système du constructeur.

Chaque variante embarque le SDK de sa marque et, pour les autres constructeurs, l'implémentation sans SDK de `src/stubs/<marque>`. Quelle que soit la marque, l'agent ne déclare une installation réussie qu'après avoir vérifié la version réellement installée, et un reboot qu'après avoir constaté que le terminal a redémarré.

**Tester avec un terminal en USB** (sans Wi-Fi ni pare-feu) :
```bash
gradlew assembleNewlandDebug -PtmsServerUrl=http://127.0.0.1:8095
powershell -ExecutionPolicy Bypass -File tools\install-agent.ps1      # installe uniquement sur les terminaux Newland branchés
powershell -ExecutionPolicy Bypass -File tools\usb-tunnel.ps1         # rétablit adb reverse à chaque reconnexion USB
```
Les deux scripts ne ciblent que le constructeur demandé (`-Manufacturer`, `newland` par défaut) : un téléphone branché en même temps est ignoré.

> Sous Windows, placez le projet dans un chemin court (ex. `C:\dev\tms`) : `aidl.exe` échoue au-delà de 260 caractères.

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
`POST /enroll` · `POST /heartbeat` · `POST /tasks/{id}/status` · `GET /parameters?packageName=` · `GET /apps/{id}/download`

**Admin** (`/api/admin/v1`, HTTP Basic)
`/dashboard` · `/meta` · `/terminals[/{id}[/parameters]]` · `/merchants` · `/groups` · `/apps` · `/parameters` · `/tasks[/{id}/cancel]` · `POST /deployments`

## Pistes pour la production
- mTLS ou attestation constructeur à l'enrôlement (aujourd'hui : la clé partagée + le numéro de série suffisent) ;
- comptes admin multiples avec rôles, et journal d'audit ;
- migrations Flyway au lieu de `ddl-auto=update` ;
- distribution des APK via CDN et push (FCM/MQTT) en plus du polling.
