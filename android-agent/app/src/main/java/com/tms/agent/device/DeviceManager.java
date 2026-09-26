package com.tms.agent.device;

import java.io.File;

/**
 * Abstraction des opérations dépendantes du constructeur. Chaque marque (PAX, Newland,
 * Sunmi…) fournit une implémentation qui s'appuie sur son SDK propriétaire lorsqu'il est
 * disponible, et retombe sinon sur les API Android standard.
 *
 * Les méthodes d'action sont bloquantes : elles sont appelées depuis le thread de l'agent.
 */
public interface DeviceManager {

    /** Nom du constructeur envoyé au serveur (NEWLAND, PAX, SUNMI, …). */
    String vendor();

    String serialNumber();

    String model();

    String firmwareVersion();

    OpResult installApk(File apk, String packageName);

    OpResult uninstall(String packageName);

    /** Vrai si l'agent dispose des droits pour redémarrer le terminal. */
    boolean canReboot();

    OpResult reboot();
}
