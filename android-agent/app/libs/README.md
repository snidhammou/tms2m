# SDK constructeurs (non versionnés)

Ces SDK sont des binaires propriétaires fournis par les constructeurs, souvent sous NDA.
Ils sont exclus de git (voir `.gitignore`) et doivent être copiés ici avant de compiler la
variante correspondante. La variante `universal` n'en a pas besoin.

| Variante | Fichier attendu | Source interne |
|---|---|---|
| `newland` | `newland/MESDK-3.10.81-RELEASE.aar` | projet ProjectEGATENPT : `inter_sdk_helper/libs/` |
| `pax` | `pax/NeptuneLiteApi_V3.27.00_20211103.jar` | projet Multibrand : `pax-sdk/libs/` |
| `sunmi` | `sunmi/PayLib-release-2.0.17.aar` | projet Multibrand : `multi-brand-sdk/libs/` |

Sinon, les demander au portail développeur ou au support du constructeur.

Pour changer de version, mettre à jour le nom de fichier dans `app/build.gradle.kts`
(`newlandImplementation`, `paxImplementation`, `sunmiImplementation`).
