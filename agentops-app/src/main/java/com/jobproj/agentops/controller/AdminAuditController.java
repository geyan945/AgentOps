package com.jobproj.agentops.controller;

import com.jobproj.agentops.common.ApiResponse;
import com.jobproj.agentops.entity.ApprovalAuditLog;
import com.jobproj.agentops.entity.ToolAuditLog;
import com.jobproj.agentops.repository.ApprovalAuditLogRepository;
import com.jobproj.agentops.repository.ToolAuditLogRepository;
import com.jobproj.agentops.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/audit")
@RequiredArgsConstructor
public class AdminAuditController {

    private final ToolAuditLogRepository toolAuditLogRepository;
    private final ApprovalAuditLogRepository approvalAuditLogRepository;

    @GetMapping("/tool-calls")
    public ApiResponse<List<ToolAuditLog>> listToolCalls() {
        return ApiResponse.success(toolAuditLogRepository.findTop100ByTenantIdOrderByCreatedAtDesc(SecurityUtils.currentTenantId()));
    }

    @GetMapping("/approvals")
    public ApiResponse<List<ApprovalAuditLog>> listApprovals() {
        return ApiResponse.success(approvalAuditLogRepository.findTop100ByTenantIdOrderByCreatedAtDesc(SecurityUtils.currentTenantId()));
    }
}
