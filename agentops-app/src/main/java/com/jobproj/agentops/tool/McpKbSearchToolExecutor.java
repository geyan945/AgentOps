package com.jobproj.agentops.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.jobproj.agentops.common.BusinessException;
import com.jobproj.agentops.common.ErrorCode;
import com.jobproj.agentops.mcp.McpHttpClient;
import com.jobproj.agentops.mcp.McpToolCallResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "agent.mcp.prefer-remote-tools", havingValue = "true")
public class McpKbSearchToolExecutor implements ToolExecutor {

    private final McpHttpClient mcpHttpClient;

    @Override
    public String getName() {
        return "kb_search_remote";
    }

    @Override
    public String getDescription() {
        return "通过 MCP 远程调用知识库检索工具";
    }

    @Override
    public List<String> getArgumentNames() {
        return List.of("query", "topK", "knowledgeBaseId");
    }

    @Override
    public ToolResult execute(JsonNode arguments, ToolContext context) {
        McpToolCallResponse response = mcpHttpClient.callTool("kb_search", arguments);
        if (response == null) {
            throw new BusinessException(ErrorCode.AGENT_EXECUTION_FAILED, "MCP kb_search 调用失败");
        }
        return ToolResult.builder()
                .toolName(getName())
                .success(response.isSuccess())
                .summary(response.getSummary())
                .data(response.getData())
                .build();
    }
}