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
    private Integer attemptNo;
    private Long parentStepId;
    private Map<String, Object> input;
    private Map<String, Object> output;
    private Map<String, Object> stateBefore;
    private Map<String, Object> stateAfter;
    private Map<String, Object> observation;
    private Long latencyMs;
    private String modelName;
    private String promptVersion;
    private Boolean success;
    private String errorMessage;
}
