package com.jobproj.agentops.agent;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class EvalCaseExecutionResult {

    private String decision;
    private String actualTool;
    private String answerText;
    private Long latencyMs;
    private Boolean success;
    private String errorMessage;
}