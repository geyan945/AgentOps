package com.jobproj.agentops.dto.runtime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuntimeStepCallbackRequest {

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
    private Map<String, Object> input;
    private Map<String, Object> output;
    private Map<String, Object> stateBefore;
    private Map<String, Object> stateAfter;
    private Map<String, Object> observation;
    private Map<String, Object> costUsage;
    private Long latencyMs;
    private String modelName;
    private String promptVersion;
    private Boolean success;
    private String errorMessage;
}
