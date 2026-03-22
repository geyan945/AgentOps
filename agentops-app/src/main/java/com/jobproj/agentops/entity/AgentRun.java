package com.jobproj.agentops.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "agent_run")
public class AgentRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Lob
    @Column(name = "user_input", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String userInput;

    @Lob
    @Column(name = "final_answer", columnDefinition = "MEDIUMTEXT")
    private String finalAnswer;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "runtime_type", nullable = false, length = 32)
    private String runtimeType;

    @Column(name = "execution_mode", nullable = false, length = 32)
    private String executionMode;

    @Column(name = "approval_policy", nullable = false, length = 32)
    private String approvalPolicy;

    @Column(name = "orchestration_mode", nullable = false, length = 32)
    private String orchestrationMode;

    @Column(name = "graph_name", length = 64)
    private String graphName;

    @Column(name = "graph_version", length = 32)
    private String graphVersion;

    @Column(name = "current_node", length = 64)
    private String currentNode;

    @Column(name = "requires_human", nullable = false)
    private Boolean requiresHuman = Boolean.FALSE;

    @Column(name = "resume_token", length = 128)
    private String resumeToken;

    @Column(name = "checkpoint_version")
    private Integer checkpointVersion = 0;

    @Column(name = "last_event_sequence")
    private Integer lastEventSequence = 0;

    @Column(name = "last_checkpoint_at")
    private LocalDateTime lastCheckpointAt;

    @Column(name = "total_steps", nullable = false)
    private Integer totalSteps = 0;

    @Column(name = "total_latency_ms")
    private Long totalLatencyMs;

    @Lob
    @Column(name = "artifacts_json", columnDefinition = "MEDIUMTEXT")
    private String artifactsJson;

    @Lob
    @Column(name = "citations_json", columnDefinition = "MEDIUMTEXT")
    private String citationsJson;

    @Lob
    @Column(name = "cost_usage_json", columnDefinition = "MEDIUMTEXT")
    private String costUsageJson;

    @Lob
    @Column(name = "approval_reason", columnDefinition = "MEDIUMTEXT")
    private String approvalReason;

    @Column(name = "replay_recovered")
    private Boolean replayRecovered = Boolean.FALSE;

    @Lob
    @Column(name = "error_message", columnDefinition = "MEDIUMTEXT")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        if (this.status == null) {
            this.status = "QUEUED";
        }
        if (this.runtimeType == null) {
            this.runtimeType = "LANGGRAPH";
        }
        if (this.executionMode == null) {
            this.executionMode = "USER";
        }
        if (this.approvalPolicy == null) {
            this.approvalPolicy = "MANUAL";
        }
        if (this.orchestrationMode == null) {
            this.orchestrationMode = "SINGLE_GRAPH";
        }
    }
}
