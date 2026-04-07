package com.jobproj.agentops.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.jobproj.agentops.dto.agent.ToolInfoResponse;
import com.jobproj.agentops.tool.ToolContext;
import com.jobproj.agentops.tool.ToolGovernanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class McpServerService {

    private final ToolGovernanceService toolGovernanceService;

    public McpInitializeResponse initialize() {
        return McpInitializeResponse.builder()
                .serverName("agentops-local-mcp")
                .version("v2")
                .protocolVersion("2026-04-07")
                .description("AgentOps governed MCP server for kb_search, doc_fetch and sql_query")
                .build();
    }

    public List<ToolInfoResponse> listTools(ToolContext context) {
        return toolGovernanceService.listTools(context);
    }

    public McpToolCallResponse callTool(String toolName, JsonNode arguments, ToolContext context) {
        return toolGovernanceService.callTool(toolName, arguments, context);
    }
}
