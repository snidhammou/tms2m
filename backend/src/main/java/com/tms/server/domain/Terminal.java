package com.tms.server.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "terminals", indexes = {
        @Index(name = "idx_terminal_token", columnList = "deviceTokenHash", unique = true)
})
public class Terminal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String serialNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Manufacturer manufacturer = Manufacturer.OTHER;

    private String model;
    private String osVersion;
    private String firmwareVersion;
    private String agentVersion;

    /** Terminal ID monétique (TID) affecté par l'acquéreur. */
    @Column(length = 32)
    private String tid;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TerminalStatus status = TerminalStatus.REGISTERED;

    @ManyToOne
    @JoinColumn(name = "merchant_id")
    private Merchant merchant;

    @ManyToOne
    @JoinColumn(name = "group_id")
    private TerminalGroup group;

    /** SHA-256 du jeton device (le jeton en clair n'est jamais stocké). */
    @Column(length = 64)
    private String deviceTokenHash;

    private Instant createdAt = Instant.now();
    private Instant enrolledAt;
    private Instant lastSeenAt;

    private Integer batteryLevel;
    private String ipAddress;
    private Double latitude;
    private Double longitude;

    /** Inventaire applicatif remonté au dernier heartbeat (JSON). */
    @Column(columnDefinition = "text")
    private String installedAppsJson;

    // --- Supervision (dernier heartbeat) ---
    private Long storageTotalBytes;
    private Long storageFreeBytes;
    private Long ramTotalBytes;
    private Long ramAvailBytes;
    @Column(length = 16)
    private String networkType;
    private Long uptimeSeconds;
    private Instant locationAt;
    /** L'agent est Device Owner (reboot et installations silencieux). */
    private Boolean deviceOwner;

    // --- Configuration appliquée (retour de l'agent) ---
    private String autoRunPackage;
    @Column(columnDefinition = "text")
    private String kioskPackagesJson;

    public Long getStorageTotalBytes() { return storageTotalBytes; }
    public void setStorageTotalBytes(Long v) { this.storageTotalBytes = v; }
    public Long getStorageFreeBytes() { return storageFreeBytes; }
    public void setStorageFreeBytes(Long v) { this.storageFreeBytes = v; }
    public Long getRamTotalBytes() { return ramTotalBytes; }
    public void setRamTotalBytes(Long v) { this.ramTotalBytes = v; }
    public Long getRamAvailBytes() { return ramAvailBytes; }
    public void setRamAvailBytes(Long v) { this.ramAvailBytes = v; }
    public String getNetworkType() { return networkType; }
    public void setNetworkType(String v) { this.networkType = v; }
    public Long getUptimeSeconds() { return uptimeSeconds; }
    public void setUptimeSeconds(Long v) { this.uptimeSeconds = v; }
    public Instant getLocationAt() { return locationAt; }
    public void setLocationAt(Instant v) { this.locationAt = v; }
    public Boolean getDeviceOwner() { return deviceOwner; }
    public void setDeviceOwner(Boolean v) { this.deviceOwner = v; }
    public String getAutoRunPackage() { return autoRunPackage; }
    public void setAutoRunPackage(String v) { this.autoRunPackage = v; }
    public String getKioskPackagesJson() { return kioskPackagesJson; }
    public void setKioskPackagesJson(String v) { this.kioskPackagesJson = v; }

    public Long getId() { return id; }
    public String getSerialNumber() { return serialNumber; }
    public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }
    public Manufacturer getManufacturer() { return manufacturer; }
    public void setManufacturer(Manufacturer manufacturer) { this.manufacturer = manufacturer; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getOsVersion() { return osVersion; }
    public void setOsVersion(String osVersion) { this.osVersion = osVersion; }
    public String getFirmwareVersion() { return firmwareVersion; }
    public void setFirmwareVersion(String firmwareVersion) { this.firmwareVersion = firmwareVersion; }
    public String getAgentVersion() { return agentVersion; }
    public void setAgentVersion(String agentVersion) { this.agentVersion = agentVersion; }
    public String getTid() { return tid; }
    public void setTid(String tid) { this.tid = tid; }
    public TerminalStatus getStatus() { return status; }
    public void setStatus(TerminalStatus status) { this.status = status; }
    public Merchant getMerchant() { return merchant; }
    public void setMerchant(Merchant merchant) { this.merchant = merchant; }
    public TerminalGroup getGroup() { return group; }
    public void setGroup(TerminalGroup group) { this.group = group; }
    public String getDeviceTokenHash() { return deviceTokenHash; }
    public void setDeviceTokenHash(String deviceTokenHash) { this.deviceTokenHash = deviceTokenHash; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getEnrolledAt() { return enrolledAt; }
    public void setEnrolledAt(Instant enrolledAt) { this.enrolledAt = enrolledAt; }
    public Instant getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(Instant lastSeenAt) { this.lastSeenAt = lastSeenAt; }
    public Integer getBatteryLevel() { return batteryLevel; }
    public void setBatteryLevel(Integer batteryLevel) { this.batteryLevel = batteryLevel; }
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }
    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }
    public String getInstalledAppsJson() { return installedAppsJson; }
    public void setInstalledAppsJson(String installedAppsJson) { this.installedAppsJson = installedAppsJson; }
}
