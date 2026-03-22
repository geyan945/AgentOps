package com.jobproj.agentops.dto.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentRunResponse {

    private Long id;
    private Long runId;
    private Long sessionId;
    private Long userId;
    private String status;
    private String runtimeType;
    private String executionMode;
    private String approvalPolicy;
    private String orchestrationMode;
    private String graphName;
    private String graphVersion;
    private String currentNode;
    private Boolean requiresHuman;
    private String resumeToken;
    private Integer checkpointVersion;
    private Integer lastEventSequence;
    private String finalAnswer;
    private String artifactsJson;
    private String citationsJson;
    private String costUsageJson;
    private String approvalReason;
    private Boolean replayRecovered;
    private Integer totalSteps;
    private Long totalLatencyMs;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime lastCheckpointAt;
    private LocalDateTime finishedAt;
    private List<AgentRunStepResponse> steps;
}
