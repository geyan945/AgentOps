package com.jobproj.agentops.dto.eval;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class EvalResultResponse {

    private Long id;
    private Long caseId;
    private String actualTool;
    private String answerText;
    private Boolean success;
    private Double score;
    private Double routeScore;
    private Double groundingScore;
    private Double citationScore;
    private Double finalScore;
    private Integer retryCount;
    private String reason;
    private String toolTraceJson;
    private String nodePathJson;
    private Boolean approvalTriggered;
    private String approvalDecision;
    private String judgeModel;
    private String judgeReason;
    private Long latencyMs;
    private LocalDateTime createdAt;
}
