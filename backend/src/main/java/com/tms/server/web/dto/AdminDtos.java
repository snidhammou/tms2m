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
            Long merchantId, String merchantName, Long groupId, String groupName,
            Instant createdAt, Instant enrolledAt, Instant lastSeenAt,
            Integer batteryLevel, String ipAddress, Double latitude, Double longitude,
            List<DeviceDtos.InstalledApp> installedApps) {
    }

    public record TerminalCreateRequest(
            @NotBlank String serialNumber, Manufacturer manufacturer, String model,
            String tid, Long merchantId, Long groupId) {
    }

    public record TerminalUpdateRequest(
            String tid, Long merchantId, Long groupId, TerminalStatus status) {
    }

    // ---- Marchands / groupes ----

    public record MerchantDto(Long id, @NotBlank String code, @NotBlank String name, String city, String address) {
        public static MerchantDto of(Merchant m) {
            return new MerchantDto(m.getId(), m.getCode(), m.getName(), m.getCity(), m.getAddress());
        }
    }

    public record GroupDto(Long id, @NotBlank String name, String description) {
        public static GroupDto of(TerminalGroup g) {
            return new GroupDto(g.getId(), g.getName(), g.getDescription());
        }
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
            String deploymentId, Instant createdAt, Instant updatedAt) {
    }

    /**
     * Cible d'un déploiement : liste explicite OU filtres combinés (groupe, marchand, constructeur).
     * {@code all = true} est requis pour cibler tout le parc sans filtre (garde-fou).
     */
    public record DeploymentTarget(
            List<Long> terminalIds, Long groupId, Long merchantId, Manufacturer manufacturer, boolean all) {
    }

    public record DeploymentRequest(
            @NotNull TaskType type, Long appId, String packageName, @NotNull DeploymentTarget target) {
    }

    public record DeploymentResponse(String deploymentId, int taskCount) {
    }

    // ---- Tableau de bord ----

    public record DashboardDto(
            long terminals, long online, long offline,
            Map<String, Long> byManufacturer, Map<String, Long> byStatus, Map<String, Long> tasksByStatus,
            long merchants, long groups, long apps) {
    }

    public record MetaDto(
            List<Manufacturer> manufacturers, List<TerminalStatus> terminalStatuses,
            List<TaskType> taskTypes, List<TaskStatus> taskStatuses, List<ParameterScope> parameterScopes,
            int pollIntervalSeconds) {
    }
}
