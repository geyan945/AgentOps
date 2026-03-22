package com.jobproj.agentops.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "agent_run_event")
public class AgentRunEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_id", nullable = false)
    private Long runId;

    @Column(name = "event_sequence", nullable = false)
    private Integer eventSequence;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "step_id")
    private Long stepId;

    @Column(name = "node_id", length = 64)
    private String nodeId;

    @Column(name = "status", length = 32)
    private String status;

    @Lob
    @Column(name = "payload_json", columnDefinition = "MEDIUMTEXT")
    private String payloadJson;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
