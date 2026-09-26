package com.tms.server.web.dto;

import com.tms.server.domain.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** DTOs de l'API d'administration ({@code /api/admin/v1}). */
public final class AdminDtos {

    private AdminDtos() {
    }

    // ---- Terminaux ----

    public record TerminalDto(
            Long id, String serialNumber, Manufacturer manufacturer, String model,
            String osVersion, String firmwareVersion, String agentVersion, String tid,
            TerminalStatus status, boolean online,
            Long merchantId, String merchantName, Long organizationId, String organizationName,
            Long groupId, String groupName,
            Instant createdAt, Instant enrolledAt, Instant lastSeenAt,
            Integer batteryLevel, String ipAddress, Double latitude, Double longitude, Instant locationAt,
            Long storageTotalBytes, Long storageFreeBytes, Long ramTotalBytes, Long ramAvailBytes,
            String networkType, Long uptimeSeconds, Boolean deviceOwner,
            String autoRunPackage, List<String> kioskPackages,
            List<DeviceDtos.InstalledApp> installedApps,
            /* canal temps réel ouvert : synchronisation et tâches instantanées */ boolean realtime) {
    }

    /** Résultat d'une synchronisation forcée ; {@code delivered} = terminal joint instantanément. */
    public record SyncResponse(boolean delivered, Instant requestedAt) {
    }

    public record TerminalCreateRequest(
            @NotBlank String serialNumber, Manufacturer manufacturer, String model,
            String tid, Long merchantId, Long groupId) {
    }

    /** Pré-enregistrement par lots : un n° de série par ligne. */
    public record BulkRegisterRequest(
            @NotBlank String serialNumbers, Manufacturer manufacturer, String model,
            Long merchantId, Long groupId) {
    }

    public record BulkRegisterResponse(int created, List<String> skipped) {
    }

    public record TerminalUpdateRequest(
            String tid, Long merchantId, Long groupId, TerminalStatus status) {
    }

    public record MetricPointDto(
            Instant recordedAt, Integer batteryLevel, Long storageFreeBytes, Long ramAvailBytes,
            Long rxBytes, Long txBytes, String networkType) {
        public static MetricPointDto of(TerminalMetric m) {
            return new MetricPointDto(m.getRecordedAt(), m.getBatteryLevel(), m.getStorageFreeBytes(),
                    m.getRamAvailBytes(), m.getRxBytes(), m.getTxBytes(), m.getNetworkType());
        }
    }

    // ---- Organisations / marchands / groupes ----

    public record OrganizationDto(Long id, @NotBlank String name, Long parentId, String parentName, String description) {
        public static OrganizationDto of(Organization o) {
            return new OrganizationDto(o.getId(), o.getName(),
                    o.getParent() != null ? o.getParent().getId() : null,
                    o.getParent() != null ? o.getParent().getName() : null,
                    o.getDescription());
        }
    }

    public record MerchantDto(Long id, @NotBlank String code, @NotBlank String name, String city, String address,
                              Long organizationId, String organizationName) {
        public static MerchantDto of(Merchant m) {
            Organization o = m.getOrganization();
            return new MerchantDto(m.getId(), m.getCode(), m.getName(), m.getCity(), m.getAddress(),
                    o != null ? o.getId() : null, o != null ? o.getName() : null);
        }
    }

    /** {@code applyToMembers} : applique aussi le modèle aux terminaux déjà dans le groupe. */
    public record GroupDto(Long id, @NotBlank String name, String description,
                           Long templateId, String templateName, Boolean applyToMembers) {
        public static GroupDto of(TerminalGroup g) {
            DeploymentTemplate t = g.getTemplate();
            return new GroupDto(g.getId(), g.getName(), g.getDescription(),
                    t != null ? t.getId() : null, t != null ? t.getName() : null, null);
        }
    }

    // ---- Modèles ----

    public record DeploymentTemplateDto(
            Long id, @NotBlank String name, String description,
            List<Long> appIds, List<Long> parameterTemplateIds,
            String autoRunPackage, List<String> kioskPackages, Instant updatedAt) {
    }

    public record ParameterTemplateDto(
            Long id, @NotBlank String name, @NotBlank String packageName,
            Map<String, String> values, Instant updatedAt) {
    }

    /** Applique un modèle de paramètres à un niveau (GLOBAL / GROUP / TERMINAL), avec push optionnel. */
    public record ApplyParameterTemplateRequest(
            @NotNull ParameterScope scope, Long scopeRef, boolean push) {
    }

    // ---- Applications ----

    public record AppPackageDto(
            Long id, String packageName, String label, String versionName, long versionCode,
            String originalFileName, long sizeBytes, String sha256, String description, Instant uploadedAt) {
        public static AppPackageDto of(AppPackage a) {
            return new AppPackageDto(a.getId(), a.getPackageName(), a.getLabel(), a.getVersionName(),
                    a.getVersionCode(), a.getOriginalFileName(), a.getSizeBytes(), a.getSha256(),
                    a.getDescription(), a.getUploadedAt());
        }
    }

    // ---- Paramètres ----

    public record ParameterDto(
            Long id, ParameterScope scope, Long scopeRef, String packageName,
            String key, String value, Instant updatedAt) {
        public static ParameterDto of(Parameter p) {
            return new ParameterDto(p.getId(), p.getScope(), p.getScopeRef(), p.getPackageName(),
                    p.getParamKey(), p.getParamValue(), p.getUpdatedAt());
        }
    }

    /**
     * Upsert d'un jeu de paramètres pour (scope, scopeRef, packageName).
     * Si {@code replace} est vrai, les clés absentes de {@code values} sont supprimées.
     */
    public record ParameterUpsertRequest(
            @NotNull ParameterScope scope, Long scopeRef, @NotBlank String packageName,
            @NotNull Map<String, String> values, boolean replace) {
    }

    // ---- Tâches / déploiements ----

    public record TaskDto(
            Long id, Long terminalId, String serialNumber, Manufacturer manufacturer,
            TaskType type, Map<String, Object> payload, TaskStatus status, String message,
            String deploymentId, Instant createdAt, Instant updatedAt,
            Instant notBefore, String windowStart, String windowEnd,
            Map<String, Object> result, String artifactName, Long artifactSize) {
    }

    /**
     * Cible d'un déploiement : liste explicite OU filtres combinés (organisation, groupe, marchand,
     * constructeur). {@code all = true} est requis pour cibler tout le parc sans filtre (garde-fou).
     */
    public record DeploymentTarget(
            List<Long> terminalIds, Long organizationId, Long groupId, Long merchantId,
            Manufacturer manufacturer, boolean all) {
    }

    /**
     * Planification optionnelle : {@code notBefore} (instant ISO) et fenêtre quotidienne
     * {@code windowStart}/{@code windowEnd} ("HH:mm", heure du serveur ; fenêtre de nuit possible).
     */
    public record Schedule(Instant notBefore, String windowStart, String windowEnd) {
    }

    /**
     * Paramètres selon le type : appId (INSTALL_APP), packageName (UNINSTALL_APP, PUSH_PARAMS,
     * SET_AUTORUN, et filtre facultatif pour EXTRACT_LOGS), kioskPackages (SET_KIOSK),
     * filePath (EXTRACT_FILE), logLines et logSinceMinutes (EXTRACT_LOGS).
     */
    public record DeploymentRequest(
            @NotNull TaskType type, Long appId, String packageName, List<String> kioskPackages,
            String filePath, Integer logLines, Integer logSinceMinutes, Instant logFrom, Instant logTo,
            @NotNull DeploymentTarget target, Schedule schedule) {
    }

    public record DeploymentResponse(String deploymentId, int taskCount) {
    }

    // ---- Historique ----

    public record AuditEventDto(Long id, Instant occurredAt, String actor, String action, Long terminalId,
                                String details) {
        public static AuditEventDto of(AuditEvent e) {
            return new AuditEventDto(e.getId(), e.getOccurredAt(), e.getActor(), e.getAction(), e.getTerminalId(),
                    e.getDetails());
        }
    }

    // ---- Tableau de bord ----

    public record DashboardDto(
            long terminals, long online, long offline,
            Map<String, Long> byManufacturer, Map<String, Long> byStatus, Map<String, Long> tasksByStatus,
            Map<String, Long> byNetworkType, long lowBattery, long lowStorage, long deviceOwners,
            long merchants, long groups, long apps, long organizations) {
    }

    public record MetaDto(
            List<Manufacturer> manufacturers, List<TerminalStatus> terminalStatuses,
            List<TaskType> taskTypes, List<TaskStatus> taskStatuses, List<ParameterScope> parameterScopes,
            int pollIntervalSeconds, String serverZone, boolean agentAutoUpdate) {
    }
}
