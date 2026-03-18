package com.jobproj.agentops.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "eval_result")
public class EvalResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "eval_run_id", nullable = false)
    private Long evalRunId;

    @Column(name = "case_id", nullable = false)
    private Long caseId;

    @Column(name = "actual_tool", length = 256)
    private String actualTool;

    @Lob
    @Column(name = "answer_text", columnDefinition = "MEDIUMTEXT")
    private String answerText;

    @Column(nullable = false)
    private Boolean success = Boolean.FALSE;

    @Column(nullable = false)
    private Double score = 0D;

    @Lob
    @Column(columnDefinition = "MEDIUMTEXT")
    private String reason;

    @Column(name = "latency_ms")
    private Long latencyMs;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}