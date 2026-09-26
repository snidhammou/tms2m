package com.tms.server.web.dto;

import com.tms.server.domain.TaskStatus;
import com.tms.server.domain.TaskType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Contrat d'API entre l'agent Android et le serveur ({@code /api/device/v1}). */
public final class DeviceDtos {

    private DeviceDtos() {
    }

    public record EnrollRequest(
            @NotBlank String serialNumber,
            @NotBlank String enrollmentKey,
            String manufacturer,
            String model,
            String osVersion,
            String firmwareVersion,
            String agentVersion) {
    }

    public record EnrollResponse(Long terminalId, String deviceToken, int pollIntervalSeconds) {
    }

    public record InstalledApp(String packageName, String versionName, long versionCode) {
    }

    public record HeartbeatRequest(
            Integer batteryLevel,
            String ipAddress,
            Double latitude,
            Double longitude,
            String osVersion,
            String firmwareVersion,
            String agentVersion,
            List<InstalledApp> installedApps) {
    }

    public record DeviceTask(Long id, TaskType type, Map<String, Object> payload) {
    }

    public record HeartbeatResponse(Instant serverTime, int pollIntervalSeconds, List<DeviceTask> tasks) {
    }

    public record TaskStatusUpdate(@NotNull TaskStatus status, String message) {
    }

    public record ParametersResponse(String packageName, Map<String, String> parameters) {
    }
}
