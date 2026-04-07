package com.jobproj.agentops.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.jobproj.agentops.common.BusinessException;
import com.jobproj.agentops.common.ErrorCode;
import com.jobproj.agentops.dto.agent.ToolInfoResponse;
import com.jobproj.agentops.entity.ToolPolicy;
import com.jobproj.agentops.mcp.McpToolCallResponse;
import com.jobproj.agentops.repository.ToolPolicyRepository;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ToolGovernanceService {

    private final ToolRegistry toolRegistry;
    private final ToolPermissionService toolPermissionService;
    private final ToolPolicyRepository toolPolicyRepository;
    private final ToolAuditLogService toolAuditLogService;

    public List<ToolInfoResponse> listTools(ToolContext context) {
        return toolRegistry.listExecutors().stream()
                .map(executor -> toResponse(executor, resolvePolicy(context.getTenantId(), executor)))
                .filter(response -> Boolean.TRUE.equals(response.getEnabled()))
                .filter(response -> context.getUserId() != null && toolPermissionService.canUse(context.getUserId(), context.getTenantId(), response.getName(), response.getRequiredRole()))
                .toList();
    }

    public McpToolCallResponse callTool(String toolName, JsonNode arguments, ToolContext context) {
        ToolExecutor executor = toolRegistry.getRequired(toolName);
        EffectiveToolPolicy policy = resolvePolicy(context.getTenantId(), executor);
        if (!policy.enabled) {
            toolAuditLogService.log(context, toolName, arguments, "DENY_DISABLED", false, "tool disabled by policy");
            throw new BusinessException(ErrorCode.TOOL_NOT_FOUND, "tool disabled by policy");
        }
        toolPermissionService.assertCanUse(context.getUserId(), context.getTenantId(), toolName, policy.requiredRole);
        if ("PUBLIC_API".equalsIgnoreCase(context.getSource()) && "HUMAN_REVIEW".equalsIgnoreCase(policy.approvalPolicy)) {
            toolAuditLogService.log(context, toolName, arguments, "DENY_APPROVAL_REQUIRED", false, "public api cannot bypass approval workflow");
            throw new BusinessException(ErrorCode.FORBIDDEN, "tool requires runtime approval workflow");
        }
        try {
            ToolResult result = executor.execute(arguments, context);
            toolAuditLogService.log(context, toolName, arguments, "ALLOW", result.isSuccess(), result.getSummary());
            return McpToolCallResponse.builder()
                    .toolName(toolName)
                    .success(result.isSuccess())
                    .summary(result.getSummary())
                    .data(result.getData())
                    .build();
        } catch (RuntimeException ex) {
            toolAuditLogService.log(context, toolName, arguments, "DENY_EXECUTION_FAILED", false, ex.getMessage());
            throw ex;
        }
    }

    public List<ToolPolicy> listPolicies(Long tenantId) {
        return toolPolicyRepository.findByTenantIdOrderByToolNameAsc(tenantId);
    }

    public ToolPolicy savePolicy(ToolPolicy request, Long tenantId) {
        ToolPolicy existing = toolPolicyRepository.findByTenantIdAndToolName(tenantId, request.getToolName()).orElse(null);
        ToolPolicy target = existing == null ? new ToolPolicy() : existing;
        target.setTenantId(tenantId);
        target.setToolName(request.getToolName());
        target.setEnabled(request.getEnabled() == null ? Boolean.TRUE : request.getEnabled());
        target.setRequiredRole(request.getRequiredRole());
        target.setApprovalPolicyOverride(request.getApprovalPolicyOverride());
        target.setRiskLevelOverride(request.getRiskLevelOverride());
        target.setTimeoutBudgetMs(request.getTimeoutBudgetMs());
        target.setRetryPolicy(request.getRetryPolicy());
        target.setAuditRequired(request.getAuditRequired());
        target.setQuotaPerMinute(request.getQuotaPerMinute());
        return toolPolicyRepository.save(target);
    }

    private ToolInfoResponse toResponse(ToolExecutor executor, EffectiveToolPolicy policy) {
        return ToolInfoResponse.builder()
                .name(executor.getName())
                .description(executor.getDescription())
                .argumentNames(executor.getArgumentNames())
                .riskLevel(policy.riskLevel)
                .approvalPolicy(policy.approvalPolicy)
                .idempotent(executor.isIdempotent())
                .timeoutBudgetMs(policy.timeoutBudgetMs)
                .retryPolicy(policy.retryPolicy)
                .auditRequired(policy.auditRequired)
                .enabled(policy.enabled)
                .requiredRole(policy.requiredRole)
                .build();
    }

    private EffectiveToolPolicy resolvePolicy(Long tenantId, ToolExecutor executor) {
        ToolPolicy policy = tenantId == null ? null : toolPolicyRepository.findByTenantIdAndToolName(tenantId, executor.getName()).orElse(null);
        return EffectiveToolPolicy.builder()
                .enabled(policy == null || policy.getEnabled() == null ? Boolean.TRUE : policy.getEnabled())
                .requiredRole(policy == null || !StringUtils.hasText(policy.getRequiredRole()) ? defaultRequiredRole(executor) : policy.getRequiredRole().trim().toUpperCase())
                .approvalPolicy(policy == null || !StringUtils.hasText(policy.getApprovalPolicyOverride()) ? executor.getApprovalPolicy() : policy.getApprovalPolicyOverride().trim().toUpperCase())
                .riskLevel(policy == null || !StringUtils.hasText(policy.getRiskLevelOverride()) ? executor.getRiskLevel() : policy.getRiskLevelOverride().trim().toUpperCase())
                .timeoutBudgetMs(policy == null || policy.getTimeoutBudgetMs() == null ? executor.getTimeoutBudgetMs() : policy.getTimeoutBudgetMs())
                .retryPolicy(policy == null || !StringUtils.hasText(policy.getRetryPolicy()) ? executor.getRetryPolicy() : policy.getRetryPolicy())
                .auditRequired(policy == null || policy.getAuditRequired() == null ? executor.isAuditRequired() : policy.getAuditRequired())
                .build();
    }

    private String defaultRequiredRole(ToolExecutor executor) {
        return "HUMAN_REVIEW".equalsIgnoreCase(executor.getApprovalPolicy()) || "HIGH".equalsIgnoreCase(executor.getRiskLevel())
                ? "ANALYST"
                : "USER";
    }

    @Value
    @Builder
    private static class EffectiveToolPolicy {
        boolean enabled;
        String requiredRole;
        String approvalPolicy;
        String riskLevel;
        Integer timeoutBudgetMs;
        String retryPolicy;
        Boolean auditRequired;
    }
}
