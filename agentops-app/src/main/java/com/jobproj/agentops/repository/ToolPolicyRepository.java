package com.jobproj.agentops.repository;

import com.jobproj.agentops.entity.ToolPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ToolPolicyRepository extends JpaRepository<ToolPolicy, Long> {

    Optional<ToolPolicy> findByTenantIdAndToolName(Long tenantId, String toolName);

    List<ToolPolicy> findByTenantIdOrderByToolNameAsc(Long tenantId);
}
