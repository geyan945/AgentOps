package com.jobproj.agentops.mcp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class McpInitializeResponse {

    private String serverName;
    private String version;
    private String protocolVersion;
    private String description;
}