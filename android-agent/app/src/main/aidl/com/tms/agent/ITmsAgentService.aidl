package com.tms.agent;

/**
 * Interface exposée par l'agent TMS aux applications du terminal (ex. application de paiement).
 *
 * Liaison : Intent("com.tms.agent.action.BIND").setPackage("com.tms.agent")
 * Les paramètres ne sont délivrés qu'au package appelant lui-même (contrôle par UID).
 */
interface ITmsAgentService {

    /** JSON : serialNumber, manufacturer, model, firmwareVersion, terminalId, enrolled, lastSyncAt. */
    String getTerminalInfo();

    /** JSON objet clé/valeur des paramètres TMS du package appelant ("{}" si aucun). */
    String getParameters(String packageName);

    /** Valeur d'un paramètre, ou null. */
    String getParameter(String packageName, String key);

    /** Déclenche une synchronisation immédiate avec le serveur TMS. */
    void requestSync();
}
