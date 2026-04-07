package com.jobproj.agentops.controller;

import com.jobproj.agentops.common.ApiResponse;
import com.jobproj.agentops.dto.agent.ToolInfoResponse;
import com.jobproj.agentops.mcp.InternalMcpToolCallRequest;
import com.jobproj.agentops.mcp.McpInitializeResponse;
import com.jobproj.agentops.mcp.McpServerService;
import com.jobproj.agentops.mcp.McpToolCallResponse;
import com.jobproj.agentops.runtime.InternalAccessService;
import com.jobproj.agentops.tool.ToolContext;
import com.jobproj.agentops.web.RequestIdHolder;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/internal/mcp")
@RequiredArgsConstructor
public class InternalMcpController {

    private final InternalAccessService internalAccessService;
    private final McpServerService mcpServerService;

    @PostMapping("/initialize")
    public ApiResponse<McpInitializeResponse> initialize(@RequestHeader("X-AgentOps-Internal-Key") String internalKey) {
        internalAccessService.assertAuthorized(internalKey);
        return ApiResponse.success(mcpServerService.initialize());
    }

    @GetMapping("/tools")
    public ApiResponse<List<ToolInfoResponse>> listTools(@RequestHeader("X-AgentOps-Internal-Key") String internalKey,
                                                         @RequestParam(required = false) Long userId,
                                                         @RequestParam(required = false) Long tenantId,
                                                         @RequestParam(required = false) Long sessionId,
                                                         @RequestParam(required = false) Long runId) {
        internalAccessService.assertAuthorized(internalKey);
        return ApiResponse.success(mcpServerService.listTools(ToolContext.builder()
                .userId(userId)
                .tenantId(tenantId)
                .sessionId(sessionId)
                .runId(runId)
                .source("INTERNAL_RUNTIME")
                .requestId(RequestIdHolder.current())
                .build()));
    }

    @PostMapping("/tools/call")
    public ApiResponse<McpToolCallResponse> callTool(@RequestHeader("X-AgentOps-Internal-Key") String internalKey,
                                                     @RequestBody @Valid InternalMcpToolCallRequest request) {
        internalAccessService.assertAuthorized(internalKey);
        return ApiResponse.success(mcpServerService.callTool(
                request.getToolName(),
                request.getArguments(),
                ToolContext.builder()
                        .userId(request.getUserId())
                        .tenantId(request.getTenantId())
                        .sessionId(request.getSessionId())
                        .runId(request.getRunId())
                        .source("INTERNAL_RUNTIME")
                        .requestId(RequestIdHolder.current())
                        .build()
        ));
    }
}
