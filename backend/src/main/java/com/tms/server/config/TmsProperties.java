package com.tms.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tms")
public record TmsProperties(Enrollment enrollment, Device device, Storage storage, Admin admin) {

    public record Enrollment(String key, boolean autoAccept) {
    }

    public record Device(int pollIntervalSeconds) {
    }

    public record Storage(String apkDir) {
    }

    public record Admin(String username, String password) {
    }
}
