package com.jobproj.agentops.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "eval_run")
public class EvalRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dataset_id", nullable = false)
    private Long datasetId;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "tenant_id")
    private Long tenantId;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "total_cases", nullable = false)
    private Integer totalCases = 0;

    @Column(name = "finished_cases", nullable = false)
    private Integer finishedCases = 0;

    @Column(name = "passed_cases", nullable = false)
    private Integer passedCases = 0;

    @Column(name = "avg_latency_ms")
    private Long avgLatencyMs;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        if (this.status == null) {
            this.status = "PENDING";
        }
    }
}
