package com.jobproj.agentops.controller;

import com.jobproj.agentops.agent.AgentRunService;
import com.jobproj.agentops.common.ApiResponse;
import com.jobproj.agentops.dto.agent.AgentRunResumeRequest;
import com.jobproj.agentops.dto.agent.AgentRunResponse;
import com.jobproj.agentops.dto.human.HumanTaskDecisionRequest;
import com.jobproj.agentops.dto.human.HumanTaskResponse;
import com.jobproj.agentops.entity.AgentHumanTask;
import com.jobproj.agentops.runtime.AgentHumanTaskService;
import com.jobproj.agentops.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/human-tasks")
@RequiredArgsConstructor
public class HumanTaskController {

    private final AgentHumanTaskService humanTaskService;
    private final AgentRunService agentRunService;

    @GetMapping
    public ApiResponse<List<HumanTaskResponse>> listTasks() {
        return ApiResponse.success(humanTaskService.listTasks(SecurityUtils.currentUserId()));
    }

    @PostMapping("/{id}/decision")
    public ApiResponse<AgentRunResponse> decide(@PathVariable Long id, @RequestBody @Valid HumanTaskDecisionRequest request) {
        AgentHumanTask task = humanTaskService.getRequiredTask(SecurityUtils.currentUserId(), id);
        AgentRunResumeRequest resumeRequest = new AgentRunResumeRequest();
        resumeRequest.setDecision(request.getDecision());
        resumeRequest.setComment(request.getComment());
        return ApiResponse.success(agentRunService.resume(SecurityUtils.currentUserId(), task.getRunId(), resumeRequest));
    }
}
