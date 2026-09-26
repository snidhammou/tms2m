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
            List<InstalledApp> installedApps,
            // Supervision
            Long storageTotalBytes,
            Long storageFreeBytes,
            Long ramTotalBytes,
            Long ramAvailBytes,
            String networkType,
            Long rxBytes,
            Long txBytes,
            Long uptimeSeconds,
            Boolean deviceOwner,
            // Configuration effectivement appliquée sur le terminal
            String autoRunPackage,
            List<String> kioskPackages) {
    }

    public record DeviceTask(Long id, TaskType type, Map<String, Object> payload) {
    }

    /** {@code iconsWanted} : packages installés dont le serveur n'a pas encore l'icône. */
    public record HeartbeatResponse(Instant serverTime, int pollIntervalSeconds, List<DeviceTask> tasks,
                                    List<String> iconsWanted) {
    }

    /** {@code result} : rapport structuré (diagnostic…), stocké tel quel. */
    public record TaskStatusUpdate(@NotNull TaskStatus status, String message, Map<String, Object> result) {
    }

    public record ParametersResponse(String packageName, Map<String, String> parameters) {
    }
}
