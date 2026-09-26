package com.tms.server.repository;

import com.tms.server.domain.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

    List<AuditEvent> findTop300ByOrderByIdDesc();

    List<AuditEvent> findTop300ByTerminalIdOrderByIdDesc(Long terminalId);
}
