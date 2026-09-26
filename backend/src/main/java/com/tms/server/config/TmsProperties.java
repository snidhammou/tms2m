package com.tms.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tms")
public record TmsProperties(Enrollment enrollment, Device device, Storage storage, Admin admin) {

    public record Enrollment(String key, boolean autoAccept) {
    }

    public record Device(int pollIntervalSeconds) {
    }

    /**
     * @param artifactDir fichiers téléversés par les terminaux (logs, fichiers extraits)
     * @param metricsRetentionDays durée de conservation des points de supervision
     */
    public record Storage(String apkDir, String artifactDir, Integer metricsRetentionDays) {
        public String artifactDirOrDefault() {
            return artifactDir == null || artifactDir.isBlank() ? "./data/artifacts" : artifactDir;
        }

        public int retentionDaysOrDefault() {
            return metricsRetentionDays == null || metricsRetentionDays <= 0 ? 7 : metricsRetentionDays;
        }
    }

    public record Admin(String username, String password) {
    }
}
