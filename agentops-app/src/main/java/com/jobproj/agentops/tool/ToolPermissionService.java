package com.jobproj.agentops.tool;

import com.jobproj.agentops.common.BusinessException;
import com.jobproj.agentops.common.ErrorCode;
import com.jobproj.agentops.entity.SysUser;
import com.jobproj.agentops.repository.SysUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ToolPermissionService {

    private static final Map<String, Set<String>> ROLE_TOOLS = Map.of(
            "USER", Set.of("kb_search", "doc_fetch"),
            "ANALYST", Set.of("kb_search", "doc_fetch", "sql_query"),
            "ADMIN", Set.of("kb_search", "doc_fetch", "sql_query", "kb_search_remote", "sql_query_remote"),
            "INTERNAL", Set.of("kb_search", "doc_fetch", "sql_query", "kb_search_remote", "sql_query_remote")
    );

    private static final List<String> ROLE_ORDER = List.of("USER", "ANALYST", "ADMIN", "INTERNAL");

    private final SysUserRepository userRepository;

    public boolean canUse(Long userId, Long tenantId, String toolName, String requiredRole) {
        try {
            assertCanUse(userId, tenantId, toolName, requiredRole);
            return true;
        } catch (BusinessException ex) {
            return false;
        }
    }

    public void assertCanUse(Long userId, Long tenantId, String toolName, String requiredRole) {
        SysUser user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "user not found"));
        if (tenantId != null && user.getTenantId() != null && !tenantId.equals(user.getTenantId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "tenant scope mismatch");
        }
        if (user.getStatus() != null && user.getStatus() != 1) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "user disabled");
        }
        Set<String> allowedTools = ROLE_TOOLS.getOrDefault(normalizeRole(user.getRole()), ROLE_TOOLS.get("USER"));
        if (!allowedTools.contains(toolName)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "tool not allowed for current role");
        }
        if (StringUtils.hasText(requiredRole) && !hasRoleAtLeast(normalizeRole(user.getRole()), normalizeRole(requiredRole))) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "role not sufficient for tool policy");
        }
    }

    private boolean hasRoleAtLeast(String actual, String required) {
        int actualIndex = ROLE_ORDER.indexOf(actual);
        int requiredIndex = ROLE_ORDER.indexOf(required);
        if (actualIndex < 0 || requiredIndex < 0) {
            return actual.equals(required);
        }
        return actualIndex >= requiredIndex;
    }

    private String normalizeRole(String role) {
        return role == null ? "USER" : role.trim().toUpperCase();
    }
}
