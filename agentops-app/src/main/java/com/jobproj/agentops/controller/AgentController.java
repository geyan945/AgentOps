package com.jobproj.agentops.controller;

import com.jobproj.agentops.agent.AgentRunService;
import com.jobproj.agentops.common.ApiResponse;
import com.jobproj.agentops.dto.agent.AgentRunRequest;
import com.jobproj.agentops.dto.agent.AgentRunResponse;
import com.jobproj.agentops.dto.agent.AgentRunStepResponse;
import com.jobproj.agentops.dto.agent.ToolInfoResponse;
import com.jobproj.agentops.security.SecurityUtils;
import com.jobproj.agentops.tool.ToolRegistry;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AgentController {

    private final AgentRunService agentRunService;
    private final ToolRegistry toolRegistry;

    @GetMapping("/tools")
    public ApiResponse<List<ToolInfoResponse>> listTools() {
        return ApiResponse.success(toolRegistry.listTools());
    }

    @PostMapping("/agent/runs")
    public ApiResponse<AgentRunResponse> execute(@RequestBody @Valid AgentRunRequest request) {
        return ApiResponse.success(agentRunService.execute(SecurityUtils.currentUserId(), request));
    }

    @GetMapping("/agent/runs/{id}")
    public ApiResponse<AgentRunResponse> getRun(@PathVariable Long id) {
        return ApiResponse.success(agentRunService.getRun(SecurityUtils.currentUserId(), id));
    }

    @GetMapping("/agent/runs/{id}/steps")
    public ApiResponse<List<AgentRunStepResponse>> listSteps(@PathVariable Long id) {
        return ApiResponse.success(agentRunService.listSteps(SecurityUtils.currentUserId(), id));
    }
}