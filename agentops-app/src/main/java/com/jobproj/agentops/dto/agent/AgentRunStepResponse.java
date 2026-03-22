package com.jobproj.agentops.dto.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentRunStepResponse {

    private Long id;
    private Integer stepNo;
    private String stepType;
    private String nodeId;
    private String nodeLabel;
    private String toolName;
    private Integer eventSequence;
    private Integer attemptNo;
    private Long parentStepId;
    private String skillName;
    private String skillType;
    private String riskLevel;
    private String approvalPolicy;
    private String approvalReason;
    private String retryReason;
    private String inputJson;
    private String outputJson;
    private String stateBeforeJson;
    private String stateAfterJson;
    private String observationJson;
    private String costUsageJson;
    private Long latencyMs;
    private String modelName;
    private String promptVersion;
    private Boolean success;
    private String errorMessage;
    private LocalDateTime createdAt;
}
