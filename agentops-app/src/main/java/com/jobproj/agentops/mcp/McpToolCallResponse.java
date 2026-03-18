package com.jobproj.agentops.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class McpToolCallResponse {

    private String toolName;
    private boolean success;
    private String summary;
    private JsonNode data;
}