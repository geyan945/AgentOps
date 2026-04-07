package com.jobproj.agentops.dto.runtime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuntimeStartRunRequest {

    private Long runId;
    private Long sessionId;
    private Long userId;
    private Long tenantId;
    private String userInput;
    private String executionMode;
    private String approvalPolicy;
    private String orchestrationMode;
    private boolean waitForCompletion;
}
