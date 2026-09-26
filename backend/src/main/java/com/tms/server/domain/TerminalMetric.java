package com.tms.server.domain;

import jakarta.persistence.*;

import java.time.Instant;

/** Point de mesure remonté à chaque heartbeat (graphiques de supervision). */
@Entity
@Table(name = "terminal_metrics", indexes = {
        @Index(name = "idx_metric_terminal_time", columnList = "terminal_id, recordedAt")
})
public class TerminalMetric {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "terminal_id", nullable = false)
    private Long terminalId;

    @Column(nullable = false)
    private Instant recordedAt = Instant.now();

    private Integer batteryLevel;
    private Long storageFreeBytes;
    private Long ramAvailBytes;
    /** Octets reçus / envoyés depuis le démarrage du terminal (compteurs cumulés). */
    private Long rxBytes;
    private Long txBytes;
    @Column(length = 16)
    private String networkType;

    public Long getId() { return id; }
    public Long getTerminalId() { return terminalId; }
    public void setTerminalId(Long terminalId) { this.terminalId = terminalId; }
    public Instant getRecordedAt() { return recordedAt; }
    public Integer getBatteryLevel() { return batteryLevel; }
    public void setBatteryLevel(Integer batteryLevel) { this.batteryLevel = batteryLevel; }
    public Long getStorageFreeBytes() { return storageFreeBytes; }
    public void setStorageFreeBytes(Long storageFreeBytes) { this.storageFreeBytes = storageFreeBytes; }
    public Long getRamAvailBytes() { return ramAvailBytes; }
    public void setRamAvailBytes(Long ramAvailBytes) { this.ramAvailBytes = ramAvailBytes; }
    public Long getRxBytes() { return rxBytes; }
    public void setRxBytes(Long rxBytes) { this.rxBytes = rxBytes; }
    public Long getTxBytes() { return txBytes; }
    public void setTxBytes(Long txBytes) { this.txBytes = txBytes; }
    public String getNetworkType() { return networkType; }
    public void setNetworkType(String networkType) { this.networkType = networkType; }
}
