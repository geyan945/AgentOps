package com.jobproj.agentops.repository;

import com.jobproj.agentops.entity.ApprovalAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApprovalAuditLogRepository extends JpaRepository<ApprovalAuditLog, Long> {

    List<ApprovalAuditLog> findTop100ByTenantIdOrderByCreatedAtDesc(Long tenantId);
}
