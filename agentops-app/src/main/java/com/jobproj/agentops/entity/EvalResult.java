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

    @Column(name = "route_score")
    private Double routeScore;

    @Column(name = "grounding_score")
    private Double groundingScore;

    @Column(name = "citation_score")
    private Double citationScore;

    @Column(name = "final_score")
    private Double finalScore;

    @Column(name = "retry_count")
    private Integer retryCount;

    @Lob
    @Column(columnDefinition = "MEDIUMTEXT")
    private String reason;

    @Lob
    @Column(name = "tool_trace_json", columnDefinition = "MEDIUMTEXT")
    private String toolTraceJson;

    @Lob
    @Column(name = "node_path_json", columnDefinition = "MEDIUMTEXT")
    private String nodePathJson;

    @Column(name = "approval_triggered")
    private Boolean approvalTriggered;

    @Column(name = "approval_decision", length = 64)
    private String approvalDecision;

    @Lob
    @Column(name = "skills_used_json", columnDefinition = "MEDIUMTEXT")
    private String skillsUsedJson;

    @Column(name = "replay_recovered")
    private Boolean replayRecovered;

    @Lob
    @Column(name = "cost_usage_json", columnDefinition = "MEDIUMTEXT")
    private String costUsageJson;

    @Column(name = "judge_model", length = 128)
    private String judgeModel;

    @Lob
    @Column(name = "judge_reason", columnDefinition = "MEDIUMTEXT")
    private String judgeReason;

    @Column(name = "latency_ms")
    private Long latencyMs;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
