package com.jobproj.agentops.tool;

import com.jobproj.agentops.common.BusinessException;
import com.jobproj.agentops.common.ErrorCode;
import com.jobproj.agentops.dto.agent.ToolInfoResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class ToolRegistry {

    private final Map<String, ToolExecutor> toolExecutorMap;

    @Value("${agent.mcp.prefer-remote-tools:false}")
    private boolean preferRemoteTools;

    public ToolRegistry(List<ToolExecutor> toolExecutors) {
        this.toolExecutorMap = toolExecutors.stream().collect(Collectors.toMap(ToolExecutor::getName, Function.identity()));
    }

    public ToolExecutor getRequired(String toolName) {
        ToolExecutor executor = toolExecutorMap.get(toolName);
        if (executor == null) {
            throw new BusinessException(ErrorCode.TOOL_NOT_FOUND, "工具不存在: " + toolName);
        }
        return executor;
    }

    public List<ToolInfoResponse> listTools() {
        return toolExecutorMap.values().stream()
                .filter(this::shouldExpose)
                .map(tool -> ToolInfoResponse.builder()
                        .name(tool.getName())
                        .description(tool.getDescription())
                        .argumentNames(tool.getArgumentNames())
                        .build())
                .toList();
    }

    private boolean shouldExpose(ToolExecutor toolExecutor) {
        if (!preferRemoteTools) {
            return !toolExecutor.getName().endsWith("_remote");
        }
        if ("kb_search".equals(toolExecutor.getName()) || "sql_query".equals(toolExecutor.getName())) {
            return false;
        }
        return true;
    }
}