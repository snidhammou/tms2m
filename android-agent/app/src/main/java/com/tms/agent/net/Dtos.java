package com.tms.agent.net;

import com.google.gson.JsonObject;

import java.util.List;
import java.util.Map;

/** Miroir du contrat {@code /api/device/v1} du serveur (DeviceDtos côté backend). */
public final class Dtos {

    private Dtos() {
    }

    public static class EnrollRequest {
        public String serialNumber;
        public String enrollmentKey;
        public String manufacturer;
        public String model;
        public String osVersion;
        public String firmwareVersion;
        public String agentVersion;
    }

    public static class EnrollResponse {
        public long terminalId;
        public String deviceToken;
        public int pollIntervalSeconds;
    }

    public static class InstalledApp {
        public String packageName;
        public String versionName;
        public long versionCode;

        public InstalledApp(String packageName, String versionName, long versionCode) {
            this.packageName = packageName;
            this.versionName = versionName;
            this.versionCode = versionCode;
        }
    }

    public static class HeartbeatRequest {
        public Integer batteryLevel;
        public String ipAddress;
        public Double latitude;
        public Double longitude;
        public String osVersion;
        public String firmwareVersion;
        public String agentVersion;
        public List<InstalledApp> installedApps;
        // Supervision
        public Long storageTotalBytes;
        public Long storageFreeBytes;
        public Long ramTotalBytes;
        public Long ramAvailBytes;
        public String networkType;
        public Long rxBytes;
        public Long txBytes;
        public Long uptimeSeconds;
        public Boolean deviceOwner;
        // Configuration appliquée
        public String autoRunPackage;
        public List<String> kioskPackages;
    }

    public static class DeviceTask {
        public long id;
        public String type;
        public JsonObject payload;

        public String payloadString(String key) {
            return payload != null && payload.has(key) && !payload.get(key).isJsonNull()
                    ? payload.get(key).getAsString() : null;
        }

        public long payloadLong(String key, long fallback) {
            return payload != null && payload.has(key) && !payload.get(key).isJsonNull()
                    ? payload.get(key).getAsLong() : fallback;
        }

        public List<String> payloadStringList(String key) {
            List<String> list = new java.util.ArrayList<>();
            if (payload != null && payload.has(key) && payload.get(key).isJsonArray()) {
                payload.getAsJsonArray(key).forEach(e -> list.add(e.getAsString()));
            }
            return list;
        }
    }

    public static class HeartbeatResponse {
        public String serverTime;
        public int pollIntervalSeconds;
        public List<DeviceTask> tasks;
    }

    public static class TaskStatusUpdate {
        public String status;
        public String message;
        /** Rapport structuré (diagnostic…), facultatif. */
        public Map<String, Object> result;

        public TaskStatusUpdate(String status, String message, Map<String, Object> result) {
            this.status = status;
            this.message = message;
            this.result = result;
        }
    }

    public static class ParametersResponse {
        public String packageName;
        public Map<String, String> parameters;
    }
}
