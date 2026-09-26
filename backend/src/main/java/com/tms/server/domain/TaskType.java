package com.tms.server.domain;

public enum TaskType {
    INSTALL_APP,
    UNINSTALL_APP,
    PUSH_PARAMS,
    REBOOT,
    /** Définit (ou retire, packageName vide) l'application lancée au démarrage. */
    SET_AUTORUN,
    /** Active le mode kiosque sur une liste d'applications (liste vide = désactivé). */
    SET_KIOSK,
    /** Diagnostic à distance : l'agent renvoie un rapport d'état. */
    DIAGNOSE,
    /** Extraction des logs de l'agent (fichier téléversé). */
    EXTRACT_LOGS,
    /** Extraction d'un fichier du terminal (chemin dans le payload). */
    EXTRACT_FILE
}
