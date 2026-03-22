package com.jobproj.agentops.agent;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class EvalCaseExecutionResult {

    private String decision;
    private String actualTool;
    private String answerText;
    private Long latencyMs;
    private Boolean success;
    private String errorMessage;
    private Integer retryCount;
    private Integer citationCount;
    private String route;
    private Boolean approvalTriggered;
    private String approvalDecision;
    private String orchestrationMode;
    private List<String> nodePath;
    private List<String> artifactTypes;
    private List<String> toolTrace;
    private List<String> skillsUsed;
    private Boolean replayRecovered;
    private java.util.Map<String, Object> costUsage;
}
