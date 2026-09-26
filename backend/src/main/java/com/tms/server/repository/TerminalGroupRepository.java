package com.tms.server.repository;

import com.tms.server.domain.TerminalGroup;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TerminalGroupRepository extends JpaRepository<TerminalGroup, Long> {

    boolean existsByTemplateId(Long templateId);
}
