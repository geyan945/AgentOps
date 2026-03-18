package com.jobproj.agentops.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
public class McpSqlQueryToolExecutor implements ToolExecutor {

    private final McpHttpClient mcpHttpClient;

    @Override
    public String getName() {
        return "sql_query_remote";
    }

    @Override
    public String getDescription() {
        return "通过 MCP 远程调用只读 SQL 模板工具";
    }

    @Override
    public List<String> getArgumentNames() {
        return List.of("queryType", "knowledgeBaseId");
    }

    @Override
    public ToolResult execute(JsonNode arguments, ToolContext context) {
        JsonNode actualArguments = arguments;
        if (arguments instanceof ObjectNode objectNode && !objectNode.hasNonNull("userId") && context.getUserId() != null) {
            objectNode.put("userId", context.getUserId());
            actualArguments = objectNode;
        }
        McpToolCallResponse response = mcpHttpClient.callTool("sql_query", actualArguments);
        if (response == null) {
            throw new BusinessException(ErrorCode.AGENT_EXECUTION_FAILED, "MCP sql_query 调用失败");
        }
        return ToolResult.builder()
                .toolName(getName())
                .success(response.isSuccess())
                .summary(response.getSummary())
                .data(response.getData())
                .build();
    }
}