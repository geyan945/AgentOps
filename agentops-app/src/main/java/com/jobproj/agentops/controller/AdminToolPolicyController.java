package com.jobproj.agentops.controller;

import com.jobproj.agentops.common.ApiResponse;
import com.jobproj.agentops.entity.ToolPolicy;
import com.jobproj.agentops.security.SecurityUtils;
import com.jobproj.agentops.tool.ToolGovernanceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/tool-policies")
@RequiredArgsConstructor
public class AdminToolPolicyController {

    private final ToolGovernanceService toolGovernanceService;

    @GetMapping
    public ApiResponse<List<ToolPolicy>> listPolicies() {
        return ApiResponse.success(toolGovernanceService.listPolicies(SecurityUtils.currentTenantId()));
    }

    @PostMapping
    public ApiResponse<ToolPolicy> savePolicy(@RequestBody @Valid ToolPolicy request) {
        return ApiResponse.success(toolGovernanceService.savePolicy(request, SecurityUtils.currentTenantId()));
    }
}
