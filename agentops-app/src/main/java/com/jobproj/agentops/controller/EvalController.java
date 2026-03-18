package com.jobproj.agentops.controller;

import com.jobproj.agentops.common.ApiResponse;
import com.jobproj.agentops.dto.eval.CreateEvalDatasetRequest;
import com.jobproj.agentops.dto.eval.CreateEvalRunRequest;
import com.jobproj.agentops.dto.eval.EvalDashboardResponse;
import com.jobproj.agentops.dto.eval.EvalDatasetResponse;
import com.jobproj.agentops.dto.eval.EvalFailureSampleResponse;
import com.jobproj.agentops.dto.eval.EvalResultResponse;
import com.jobproj.agentops.dto.eval.EvalRunResponse;
import com.jobproj.agentops.security.SecurityUtils;
import com.jobproj.agentops.service.EvalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/evals")
@RequiredArgsConstructor
public class EvalController {

    private final EvalService evalService;

    @PostMapping("/datasets")
    public ApiResponse<EvalDatasetResponse> createDataset(@RequestBody @Valid CreateEvalDatasetRequest request) {
        return ApiResponse.success(evalService.createDataset(SecurityUtils.currentUserId(), request));
    }

    @GetMapping("/datasets")
    public ApiResponse<List<EvalDatasetResponse>> listDatasets() {
        return ApiResponse.success(evalService.listDatasets(SecurityUtils.currentUserId()));
    }

    @GetMapping("/datasets/{id}")
    public ApiResponse<EvalDatasetResponse> getDataset(@PathVariable Long id) {
        return ApiResponse.success(evalService.getDataset(SecurityUtils.currentUserId(), id));
    }

    @PostMapping("/runs")
    public ApiResponse<EvalRunResponse> createRun(@RequestBody @Valid CreateEvalRunRequest request) {
        return ApiResponse.success(evalService.createRun(SecurityUtils.currentUserId(), request));
    }

    @GetMapping("/runs/{id}")
    public ApiResponse<EvalRunResponse> getRun(@PathVariable Long id) {
        return ApiResponse.success(evalService.getRun(SecurityUtils.currentUserId(), id));
    }

    @GetMapping("/runs/{id}/results")
    public ApiResponse<List<EvalResultResponse>> listResults(@PathVariable Long id) {
        return ApiResponse.success(evalService.listResults(SecurityUtils.currentUserId(), id));
    }

    @GetMapping("/dashboard")
    public ApiResponse<EvalDashboardResponse> dashboard() {
        return ApiResponse.success(evalService.getDashboard(SecurityUtils.currentUserId()));
    }

    @GetMapping("/failures")
    public ApiResponse<List<EvalFailureSampleResponse>> listFailures(@RequestParam(defaultValue = "10") int limit) {
        return ApiResponse.success(evalService.listFailedSamples(SecurityUtils.currentUserId(), limit));
    }
}