package com.jobproj.agentops.dto.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
public class AgentRunResponse {

    private Long id;
    private Long sessionId;
    private String status;
    private String finalAnswer;
    private Integer totalSteps;
    private Long totalLatencyMs;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime finishedAt;
    private List<AgentRunStepResponse> steps;
}