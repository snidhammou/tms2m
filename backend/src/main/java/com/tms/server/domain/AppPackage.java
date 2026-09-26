package com.tms.server.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "app_packages", uniqueConstraints = {
        @UniqueConstraint(name = "uk_app_pkg_version", columnNames = {"packageName", "versionCode"})
})
public class AppPackage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String packageName;

    private String label;
    private String versionName;

    @Column(nullable = false)
    private long versionCode;

    /** Nom du fichier dans le répertoire de stockage des APK. */
    @Column(nullable = false)
    private String storedFileName;

    private String originalFileName;
    private long sizeBytes;

    @Column(nullable = false, length = 64)
    private String sha256;

    private String description;
    private Instant uploadedAt = Instant.now();

    public Long getId() { return id; }
    public String getPackageName() { return packageName; }
    public void setPackageName(String packageName) { this.packageName = packageName; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public String getVersionName() { return versionName; }
    public void setVersionName(String versionName) { this.versionName = versionName; }
    public long getVersionCode() { return versionCode; }
    public void setVersionCode(long versionCode) { this.versionCode = versionCode; }
    public String getStoredFileName() { return storedFileName; }
    public void setStoredFileName(String storedFileName) { this.storedFileName = storedFileName; }
    public String getOriginalFileName() { return originalFileName; }
    public void setOriginalFileName(String originalFileName) { this.originalFileName = originalFileName; }
    public long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(long sizeBytes) { this.sizeBytes = sizeBytes; }
    public String getSha256() { return sha256; }
    public void setSha256(String sha256) { this.sha256 = sha256; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Instant getUploadedAt() { return uploadedAt; }
}
