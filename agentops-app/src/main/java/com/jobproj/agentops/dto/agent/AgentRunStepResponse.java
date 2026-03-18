package com.jobproj.agentops.dto.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
public class AgentRunStepResponse {

    private Long id;
    private Integer stepNo;
    private String stepType;
    private String toolName;
    private String inputJson;
    private String outputJson;
    private Long latencyMs;
    private Boolean success;
    private String errorMessage;
    private LocalDateTime createdAt;
}