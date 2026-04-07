package com.jobproj.agentops.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "tool_policy", uniqueConstraints = {
        @UniqueConstraint(name = "uk_tool_policy_tenant_tool", columnNames = {"tenant_id", "tool_name"})
})
public class ToolPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id")
    private Long tenantId;

    @Column(name = "tool_name", nullable = false, length = 64)
    private String toolName;

    @Column(nullable = false)
    private Boolean enabled = Boolean.TRUE;

    @Column(name = "required_role", length = 32)
    private String requiredRole;

    @Column(name = "approval_policy_override", length = 32)
    private String approvalPolicyOverride;

    @Column(name = "risk_level_override", length = 32)
    private String riskLevelOverride;

    @Column(name = "timeout_budget_ms")
    private Integer timeoutBudgetMs;

    @Column(name = "retry_policy", length = 32)
    private String retryPolicy;

    @Column(name = "audit_required")
    private Boolean auditRequired;

    @Column(name = "quota_per_minute")
    private Integer quotaPerMinute;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
