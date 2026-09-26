package com.tms.server.repository;

import com.tms.server.domain.Parameter;
import com.tms.server.domain.ParameterScope;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ParameterRepository extends JpaRepository<Parameter, Long> {

    List<Parameter> findByScopeAndScopeRefIsNullAndPackageName(ParameterScope scope, String packageName);

    List<Parameter> findByScopeAndScopeRefAndPackageName(ParameterScope scope, Long scopeRef, String packageName);

    List<Parameter> findAllByOrderByPackageNameAscScopeAscParamKeyAsc();

    void deleteByScopeAndScopeRef(ParameterScope scope, Long scopeRef);
}
