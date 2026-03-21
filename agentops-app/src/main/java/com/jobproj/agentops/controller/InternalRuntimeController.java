package com.jobproj.agentops.controller;

import com.jobproj.agentops.common.ApiResponse;
import com.jobproj.agentops.dto.agent.AgentRunStepResponse;
import com.jobproj.agentops.dto.runtime.RuntimeCheckpointRequest;
import com.jobproj.agentops.dto.runtime.RuntimeCheckpointResponse;
import com.jobproj.agentops.dto.runtime.RuntimeContextResponse;
import com.jobproj.agentops.dto.runtime.RuntimeStatusCallbackRequest;
import com.jobproj.agentops.dto.runtime.RuntimeStepCallbackRequest;
import com.jobproj.agentops.runtime.InternalAccessService;
import com.jobproj.agentops.runtime.RuntimeCallbackService;
import com.jobproj.agentops.runtime.RuntimeCheckpointService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/runtime")
@RequiredArgsConstructor
public class InternalRuntimeController {

    private final InternalAccessService internalAccessService;
    private final RuntimeCallbackService runtimeCallbackService;
    private final RuntimeCheckpointService runtimeCheckpointService;

    @GetMapping("/sessions/{sessionId}/context")
    public ApiResponse<RuntimeContextResponse> getContext(@RequestHeader("X-AgentOps-Internal-Key") String internalKey,
                                                          @PathVariable Long sessionId) {
        internalAccessService.assertAuthorized(internalKey);
        return ApiResponse.success(runtimeCallbackService.buildContext(sessionId));
    }

    @PostMapping("/runs/{runId}/steps")
    public ApiResponse<AgentRunStepResponse> saveStep(@RequestHeader("X-AgentOps-Internal-Key") String internalKey,
                                                      @PathVariable Long runId,
                                                      @RequestBody @Valid RuntimeStepCallbackRequest request) {
        internalAccessService.assertAuthorized(internalKey);
        return ApiResponse.success(runtimeCallbackService.saveStep(runId, request));
    }

    @PostMapping("/runs/{runId}/status")
    public ApiResponse<Void> updateStatus(@RequestHeader("X-AgentOps-Internal-Key") String internalKey,
                                          @PathVariable Long runId,
                                          @RequestBody @Valid RuntimeStatusCallbackRequest request) {
        internalAccessService.assertAuthorized(internalKey);
        runtimeCallbackService.updateStatus(runId, request);
        return ApiResponse.success(null);
    }

    @GetMapping("/runs/{runId}/checkpoint")
    public ApiResponse<RuntimeCheckpointResponse> getCheckpoint(@RequestHeader("X-AgentOps-Internal-Key") String internalKey,
                                                                @PathVariable Long runId) {
        internalAccessService.assertAuthorized(internalKey);
        return ApiResponse.success(runtimeCheckpointService.getCheckpoint(runId));
    }

    @PutMapping("/runs/{runId}/checkpoint")
    public ApiResponse<RuntimeCheckpointResponse> saveCheckpoint(@RequestHeader("X-AgentOps-Internal-Key") String internalKey,
                                                                 @PathVariable Long runId,
                                                                 @RequestBody RuntimeCheckpointRequest request) {
        internalAccessService.assertAuthorized(internalKey);
        return ApiResponse.success(runtimeCheckpointService.saveCheckpoint(runId, request));
    }

    @DeleteMapping("/runs/{runId}/checkpoint")
    public ApiResponse<Void> deleteCheckpoint(@RequestHeader("X-AgentOps-Internal-Key") String internalKey,
                                              @PathVariable Long runId) {
        internalAccessService.assertAuthorized(internalKey);
        runtimeCheckpointService.deleteCheckpoint(runId);
        return ApiResponse.success(null);
    }
}
