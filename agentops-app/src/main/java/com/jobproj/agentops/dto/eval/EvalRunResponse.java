package com.jobproj.agentops.dto.eval;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class EvalRunResponse {

    private Long id;
    private Long datasetId;
    private String status;
    private Integer totalCases;
    private Integer finishedCases;
    private Integer passedCases;
    private Long avgLatencyMs;
    private LocalDateTime createdAt;
    private LocalDateTime finishedAt;
}