package com.jobproj.agentops.controller;

import com.jobproj.agentops.common.ApiResponse;
import com.jobproj.agentops.dto.agent.ToolInfoResponse;
import com.jobproj.agentops.mcp.McpInitializeResponse;
import com.jobproj.agentops.mcp.McpServerService;
import com.jobproj.agentops.mcp.McpToolCallRequest;
import com.jobproj.agentops.mcp.McpToolCallResponse;
import com.jobproj.agentops.security.JwtUserDetails;
import com.jobproj.agentops.security.SecurityUtils;
import com.jobproj.agentops.tool.ToolContext;
import com.jobproj.agentops.web.RequestIdHolder;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/mcp")
@RequiredArgsConstructor
public class McpController {

    private final McpServerService mcpServerService;

    @PostMapping("/initialize")
    public ApiResponse<McpInitializeResponse> initialize() {
        return ApiResponse.success(mcpServerService.initialize());
    }

    @GetMapping("/tools")
    public ApiResponse<List<ToolInfoResponse>> listTools() {
        JwtUserDetails user = SecurityUtils.currentUser();
        return ApiResponse.success(mcpServerService.listTools(publicContext(user)));
    }

    @PostMapping("/tools/call")
    public ApiResponse<McpToolCallResponse> callTool(@RequestBody @Valid McpToolCallRequest request) {
        JwtUserDetails user = SecurityUtils.currentUser();
        return ApiResponse.success(mcpServerService.callTool(request.getToolName(), request.getArguments(), publicContext(user)));
    }

    private ToolContext publicContext(JwtUserDetails user) {
        return ToolContext.builder()
                .userId(user.getId())
                .tenantId(user.getTenantId())
                .role(user.getRole())
                .source("PUBLIC_API")
                .requestId(RequestIdHolder.current())
                .build();
    }
}
