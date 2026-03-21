package com.jobproj.agentops.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "agent_human_task")
public class AgentHumanTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_id", nullable = false)
    private Long runId;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "task_type", nullable = false, length = 32)
    private String taskType;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(name = "current_node", length = 64)
    private String currentNode;

    @Lob
    @Column(columnDefinition = "MEDIUMTEXT")
    private String reason;

    @Lob
    @Column(name = "request_json", columnDefinition = "MEDIUMTEXT")
    private String requestJson;

    @Lob
    @Column(name = "response_json", columnDefinition = "MEDIUMTEXT")
    private String responseJson;

    @Column(nullable = false, length = 32)
    private String status = "PENDING";

    @Column(name = "decided_by")
    private Long decidedBy;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

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
