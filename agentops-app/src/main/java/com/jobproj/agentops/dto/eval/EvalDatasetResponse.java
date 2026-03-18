package com.jobproj.agentops.dto.eval;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class EvalDatasetResponse {

    private Long id;
    private String name;
    private String description;
    private Integer caseCount;
    private LocalDateTime createdAt;
    private List<EvalCaseResponse> cases;
}