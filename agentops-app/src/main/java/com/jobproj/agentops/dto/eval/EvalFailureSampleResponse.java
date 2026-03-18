package com.jobproj.agentops.dto.eval;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
public class EvalFailureSampleResponse {

    private Long resultId;
    private Long runId;
    private Long caseId;
    private String question;
    private String actualTool;
    private Double score;
    private String reason;
    private Long latencyMs;
    private LocalDateTime createdAt;
}