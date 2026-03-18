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

    @Lob
    @Column(name = "user_input", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String userInput;

    @Lob
    @Column(name = "final_answer", columnDefinition = "MEDIUMTEXT")
    private String finalAnswer;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "total_steps", nullable = false)
    private Integer totalSteps = 0;

    @Column(name = "total_latency_ms")
    private Long totalLatencyMs;

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
            this.status = "RUNNING";
        }
    }
}