package com.jobproj.agentops.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class InternalMcpToolCallRequest {

    @NotBlank
    private String toolName;

    @NotNull
    private Long userId;

    @NotNull
    private Long tenantId;

    private Long sessionId;

    private Long runId;

    private JsonNode arguments;
}
