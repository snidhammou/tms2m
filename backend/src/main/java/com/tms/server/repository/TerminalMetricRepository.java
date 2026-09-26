package com.tms.server.repository;

import com.tms.server.domain.TerminalMetric;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;

public interface TerminalMetricRepository extends JpaRepository<TerminalMetric, Long> {

    List<TerminalMetric> findByTerminalIdAndRecordedAtAfterOrderByRecordedAtAsc(Long terminalId, Instant since);

    @Modifying
    @Query("delete from TerminalMetric m where m.recordedAt < :before")
    int deleteOlderThan(Instant before);

    @Modifying
    @Query("delete from TerminalMetric m where m.terminalId = :terminalId")
    void deleteByTerminalId(Long terminalId);
}
