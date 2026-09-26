package com.tms.server.repository;

import com.tms.server.domain.DeploymentTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeploymentTemplateRepository extends JpaRepository<DeploymentTemplate, Long> {
}
