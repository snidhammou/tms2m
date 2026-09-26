package com.tms.agent.device;

public final class OpResult {

    public final boolean success;
    public final String message;
    /**
     * Non nul : l'opération attend une confirmation sur l'écran du terminal (agent sans Device Owner).
     * Le résultat définitif arrivera plus tard via {@link InstallResults.Pending#onLateResult}.
     */
    public final InstallResults.Pending awaitingUser;

    private OpResult(boolean success, String message, InstallResults.Pending awaitingUser) {
        this.success = success;
        this.message = message;
        this.awaitingUser = awaitingUser;
    }

    public static OpResult ok(String message) {
        return new OpResult(true, message, null);
    }

    public static OpResult fail(String message) {
        return new OpResult(false, message, null);
    }

    static OpResult awaitingUser(String message, InstallResults.Pending pending) {
        return new OpResult(false, message, pending);
    }
}
