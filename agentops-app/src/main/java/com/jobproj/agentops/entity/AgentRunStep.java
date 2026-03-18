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

    @Column(name = "tool_name", length = 64)
    private String toolName;

    @Lob
    @Column(name = "input_json", columnDefinition = "MEDIUMTEXT")
    private String inputJson;

    @Lob
    @Column(name = "output_json", columnDefinition = "MEDIUMTEXT")
    private String outputJson;

    @Column(name = "latency_ms")
    private Long latencyMs;

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
    }
}