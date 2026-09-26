package com.tms.server.repository;

import com.tms.server.domain.AppPackage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AppPackageRepository extends JpaRepository<AppPackage, Long> {

    boolean existsByPackageNameAndVersionCode(String packageName, long versionCode);

    List<AppPackage> findAllByOrderByPackageNameAscVersionCodeDesc();
}
