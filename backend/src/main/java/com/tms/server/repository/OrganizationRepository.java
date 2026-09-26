package com.tms.server.repository;

import com.tms.server.domain.Organization;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationRepository extends JpaRepository<Organization, Long> {

    boolean existsByParentId(Long parentId);
}
