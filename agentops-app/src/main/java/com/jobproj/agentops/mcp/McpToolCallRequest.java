package com.jobproj.agentops.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class McpToolCallRequest {

    @NotBlank
    private String toolName;

    private JsonNode arguments;
}