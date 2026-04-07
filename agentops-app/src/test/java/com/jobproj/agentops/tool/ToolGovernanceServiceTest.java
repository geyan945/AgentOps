package com.jobproj.agentops.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jobproj.agentops.common.BusinessException;
import com.jobproj.agentops.entity.ToolPolicy;
import com.jobproj.agentops.repository.ToolPolicyRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ToolGovernanceServiceTest {

    private final ToolRegistry toolRegistry = mock(ToolRegistry.class);
    private final ToolPermissionService toolPermissionService = mock(ToolPermissionService.class);
    private final ToolPolicyRepository toolPolicyRepository = mock(ToolPolicyRepository.class);
    private final ToolAuditLogService toolAuditLogService = mock(ToolAuditLogService.class);
    private final ToolGovernanceService service = new ToolGovernanceService(toolRegistry, toolPermissionService, toolPolicyRepository, toolAuditLogService);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void publicApiCannotBypassHumanReviewTool() {
        ToolExecutor sqlExecutor = mock(ToolExecutor.class);
        when(sqlExecutor.getName()).thenReturn("sql_query");
        when(sqlExecutor.getDescription()).thenReturn("sql");
        when(sqlExecutor.getArgumentNames()).thenReturn(List.of("queryType"));
        when(sqlExecutor.getRiskLevel()).thenReturn("HIGH");
        when(sqlExecutor.getApprovalPolicy()).thenReturn("HUMAN_REVIEW");
        when(sqlExecutor.getTimeoutBudgetMs()).thenReturn(4000);
        when(sqlExecutor.getRetryPolicy()).thenReturn("NO_RETRY");
        when(sqlExecutor.isAuditRequired()).thenReturn(true);
        when(toolRegistry.getRequired("sql_query")).thenReturn(sqlExecutor);
        when(toolPolicyRepository.findByTenantIdAndToolName(101L, "sql_query")).thenReturn(Optional.empty());
        doNothing().when(toolPermissionService).assertCanUse(7L, 101L, "sql_query", "ANALYST");

        ToolContext context = ToolContext.builder()
                .userId(7L)
                .tenantId(101L)
                .source("PUBLIC_API")
                .requestId("req-1")
                .build();
        ObjectNode args = objectMapper.createObjectNode().put("queryType", "RUN_COUNT_BY_STATUS");

        BusinessException ex = assertThrows(BusinessException.class, () -> service.callTool("sql_query", args, context));
        assertEquals(4003, ex.getErrorCode().getCode());
        verify(toolAuditLogService).log(any(), any(), any(), any(), any(Boolean.class), any());
        verify(sqlExecutor, never()).execute(any(), any());
    }

    @Test
    void tenantPolicyCanDisableToolListing() {
        ToolExecutor kbExecutor = mock(ToolExecutor.class);
        when(kbExecutor.getName()).thenReturn("kb_search");
        when(kbExecutor.getDescription()).thenReturn("kb");
        when(kbExecutor.getArgumentNames()).thenReturn(List.of("query"));
        when(kbExecutor.getRiskLevel()).thenReturn("LOW");
        when(kbExecutor.getApprovalPolicy()).thenReturn("NONE");
        when(kbExecutor.getTimeoutBudgetMs()).thenReturn(2500);
        when(kbExecutor.getRetryPolicy()).thenReturn("LINEAR_BACKOFF");
        when(kbExecutor.isAuditRequired()).thenReturn(false);
        when(toolRegistry.listExecutors()).thenReturn(List.of(kbExecutor));
        when(toolPolicyRepository.findByTenantIdAndToolName(101L, "kb_search")).thenReturn(Optional.of(disabledPolicy()));
        when(toolPermissionService.canUse(7L, 101L, "kb_search", "USER")).thenReturn(true);

        ToolContext context = ToolContext.builder().userId(7L).tenantId(101L).source("PUBLIC_API").build();

        assertEquals(0, service.listTools(context).size());
    }

    private ToolPolicy disabledPolicy() {
        ToolPolicy policy = new ToolPolicy();
        policy.setTenantId(101L);
        policy.setToolName("kb_search");
        policy.setEnabled(false);
        return policy;
    }
}
