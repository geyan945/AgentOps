package com.jobproj.agentops.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "agent_run_step")
public class AgentRunStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_id", nullable = false)
    private Long runId;

    @Column(name = "step_no", nullable = false)
    private Integer stepNo;

    @Column(name = "step_type", nullable = false, length = 32)
    private String stepType;

    @Column(name = "node_id", length = 64)
    private String nodeId;

    @Column(name = "node_label", length = 128)
    private String nodeLabel;

    @Column(name = "tool_name", length = 64)
    private String toolName;

    @Column(name = "attempt_no")
    private Integer attemptNo;

    @Column(name = "parent_step_id")
    private Long parentStepId;

    @Lob
    @Column(name = "input_json", columnDefinition = "MEDIUMTEXT")
    private String inputJson;

    @Lob
    @Column(name = "output_json", columnDefinition = "MEDIUMTEXT")
    private String outputJson;

    @Lob
    @Column(name = "state_before_json", columnDefinition = "MEDIUMTEXT")
    private String stateBeforeJson;

    @Lob
    @Column(name = "state_after_json", columnDefinition = "MEDIUMTEXT")
    private String stateAfterJson;

    @Lob
    @Column(name = "observation_json", columnDefinition = "MEDIUMTEXT")
    private String observationJson;

    @Column(name = "latency_ms")
    private Long latencyMs;

    @Column(name = "model_name", length = 64)
    private String modelName;

    @Column(name = "prompt_version", length = 64)
    private String promptVersion;

    @Column(nullable = false)
    private Boolean success = Boolean.TRUE;

    @Lob
    @Column(name = "error_message", columnDefinition = "MEDIUMTEXT")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        if (this.attemptNo == null) {
            this.attemptNo = 1;
        }
    }
}
