package com.jobproj.agentops.controller;

import com.jobproj.agentops.common.ApiResponse;
import com.jobproj.agentops.dto.agent.ToolInfoResponse;
import com.jobproj.agentops.mcp.McpInitializeResponse;
import com.jobproj.agentops.mcp.McpServerService;
import com.jobproj.agentops.mcp.McpToolCallRequest;
import com.jobproj.agentops.mcp.McpToolCallResponse;
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
        return ApiResponse.success(mcpServerService.listTools());
    }

    @PostMapping("/tools/call")
    public ApiResponse<McpToolCallResponse> callTool(@RequestBody @Valid McpToolCallRequest request) {
        return ApiResponse.success(mcpServerService.callTool(request.getToolName(), request.getArguments()));
    }
}