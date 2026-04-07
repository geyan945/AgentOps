package com.jobproj.agentops.tool;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class ToolContext {

    private Long userId;
    private Long tenantId;
    private Long sessionId;
    private Long runId;
    private String role;
    private String source;
    private String requestId;
}
