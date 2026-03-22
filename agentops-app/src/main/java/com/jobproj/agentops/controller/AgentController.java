package com.jobproj.agentops.controller;

import com.jobproj.agentops.agent.AgentRunService;
import com.jobproj.agentops.common.ApiResponse;
import com.jobproj.agentops.dto.agent.AgentGraphResponse;
import com.jobproj.agentops.dto.agent.AgentRunEventResponse;
import com.jobproj.agentops.dto.agent.AgentRunRequest;
import com.jobproj.agentops.dto.agent.AgentRunReplayRequest;
import com.jobproj.agentops.dto.agent.AgentRunResponse;
import com.jobproj.agentops.dto.agent.AgentRunResumeRequest;
import com.jobproj.agentops.dto.agent.AgentRunStepResponse;
import com.jobproj.agentops.dto.agent.ToolInfoResponse;
import com.jobproj.agentops.security.SecurityUtils;
import com.jobproj.agentops.tool.ToolRegistry;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
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

    @GetMapping("/agent/runs/{id}/graph")
    public ApiResponse<AgentGraphResponse> getGraph(@PathVariable Long id) {
        return ApiResponse.success(agentRunService.getGraph(SecurityUtils.currentUserId(), id));
    }

    @GetMapping("/agent/runs/{id}/steps")
    public ApiResponse<List<AgentRunStepResponse>> listSteps(@PathVariable Long id) {
        return ApiResponse.success(agentRunService.listSteps(SecurityUtils.currentUserId(), id));
    }

    @GetMapping("/agent/runs/{id}/events/history")
    public ApiResponse<List<AgentRunEventResponse>> listEvents(@PathVariable Long id) {
        return ApiResponse.success(agentRunService.listEvents(SecurityUtils.currentUserId(), id));
    }

    @GetMapping(path = "/agent/runs/{id}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribeEvents(@PathVariable Long id) {
        return agentRunService.subscribeEvents(SecurityUtils.currentUserId(), id);
    }

    @PostMapping("/agent/runs/{id}/resume")
    public ApiResponse<AgentRunResponse> resume(@PathVariable Long id, @RequestBody @Valid AgentRunResumeRequest request) {
        return ApiResponse.success(agentRunService.resume(SecurityUtils.currentUserId(), id, request));
    }

    @PostMapping("/agent/runs/{id}/replay")
    public ApiResponse<AgentRunResponse> replay(@PathVariable Long id, @RequestBody(required = false) AgentRunReplayRequest request) {
        return ApiResponse.success(agentRunService.replay(SecurityUtils.currentUserId(), id, request == null ? new AgentRunReplayRequest() : request));
    }
}
