package com.jobproj.agentops.repository;

import com.jobproj.agentops.entity.ToolAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ToolAuditLogRepository extends JpaRepository<ToolAuditLog, Long> {

    List<ToolAuditLog> findTop100ByTenantIdOrderByCreatedAtDesc(Long tenantId);
}
