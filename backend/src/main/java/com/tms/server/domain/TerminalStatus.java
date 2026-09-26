package com.tms.server.domain;

public enum TerminalStatus {
    /** Pré-enregistré par un administrateur, pas encore enrôlé par l'agent. */
    REGISTERED,
    /** Enrôlé, dispose d'un jeton device valide. */
    ACTIVE,
    /** Désactivé : le jeton est refusé et l'enrôlement bloqué. */
    DISABLED
}
