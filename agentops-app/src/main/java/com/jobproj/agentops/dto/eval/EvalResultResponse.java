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
    private String reason;
    private Long latencyMs;
    private LocalDateTime createdAt;
}