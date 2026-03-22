package com.jobproj.agentops.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "agent_runtime_checkpoint")
public class AgentRuntimeCheckpoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_id", nullable = false, unique = true)
    private Long runId;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "current_node", length = 64)
    private String currentNode;

    @Column(name = "orchestration_mode", length = 32)
    private String orchestrationMode;

    @Column(name = "checkpoint_version", nullable = false)
    private Integer checkpointVersion;

    @Column(name = "resume_token", length = 128)
    private String resumeToken;

    @Column(name = "requires_human", nullable = false)
    private Boolean requiresHuman = Boolean.FALSE;

    @Column(name = "human_task_id")
    private Long humanTaskId;

    @Column(name = "resume_after_node", length = 64)
    private String resumeAfterNode;

    @Column(name = "event_sequence")
    private Integer eventSequence;

    @Column(name = "loop_count")
    private Integer loopCount;

    @Column(name = "tool_loop_count")
    private Integer toolLoopCount;

    @Column(name = "review_count")
    private Integer reviewCount;

    @Lob
    @Column(name = "state_json", columnDefinition = "MEDIUMTEXT")
    private String stateJson;

    @Lob
    @Column(name = "last_error", columnDefinition = "MEDIUMTEXT")
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.checkpointVersion == null) {
            this.checkpointVersion = 1;
        }
        if (this.status == null) {
            this.status = "RUNNING";
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
