package com.tms.agent.device;

public final class OpResult {

    public final boolean success;
    public final String message;

    private OpResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public static OpResult ok(String message) {
        return new OpResult(true, message);
    }

    public static OpResult fail(String message) {
        return new OpResult(false, message);
    }
}
