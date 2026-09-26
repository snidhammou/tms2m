package com.tms.server.domain;

public enum TaskStatus {
    PENDING, SENT, IN_PROGRESS, SUCCESS, FAILED, CANCELLED;

    public boolean isFinal() {
        return this == SUCCESS || this == FAILED || this == CANCELLED;
    }
}
