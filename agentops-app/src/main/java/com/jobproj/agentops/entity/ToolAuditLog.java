package com.jobproj.agentops.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "tool_audit_log")
public class ToolAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id")
    private Long tenantId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "session_id")
    private Long sessionId;

    @Column(name = "run_id")
    private Long runId;

    @Column(name = "tool_name", nullable = false, length = 64)
    private String toolName;

    @Column(name = "request_id", length = 64)
    private String requestId;

    @Column(nullable = false, length = 32)
    private String source;

    @Lob
    @Column(name = "arguments_summary", columnDefinition = "MEDIUMTEXT")
    private String argumentsSummary;

    @Column(nullable = false, length = 32)
    private String decision;

    @Column(nullable = false)
    private Boolean success = Boolean.FALSE;

    @Lob
    @Column(name = "response_summary", columnDefinition = "MEDIUMTEXT")
    private String responseSummary;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
