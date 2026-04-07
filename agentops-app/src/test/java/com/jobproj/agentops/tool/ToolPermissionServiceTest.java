package com.jobproj.agentops.tool;

import com.jobproj.agentops.common.BusinessException;
import com.jobproj.agentops.entity.SysUser;
import com.jobproj.agentops.repository.SysUserRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ToolPermissionServiceTest {

    private final SysUserRepository userRepository = mock(SysUserRepository.class);
    private final ToolPermissionService service = new ToolPermissionService(userRepository);

    @Test
    void analystCanUseSqlQueryInsideSameTenant() {
        SysUser user = buildUser(7L, 101L, "ANALYST");
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));

        assertDoesNotThrow(() -> service.assertCanUse(7L, 101L, "sql_query", "ANALYST"));
    }

    @Test
    void tenantMismatchIsRejected() {
        SysUser user = buildUser(7L, 101L, "ADMIN");
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));

        assertThrows(BusinessException.class, () -> service.assertCanUse(7L, 202L, "kb_search", "USER"));
    }

    @Test
    void normalUserCannotUseHighRiskSqlQuery() {
        SysUser user = buildUser(7L, 101L, "USER");
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));

        assertFalse(service.canUse(7L, 101L, "sql_query", "ANALYST"));
    }

    private SysUser buildUser(Long userId, Long tenantId, String role) {
        SysUser user = new SysUser();
        user.setId(userId);
        user.setTenantId(tenantId);
        user.setRole(role);
        user.setStatus(1);
        user.setTokenVersion(0);
        user.setUsername("tester");
        user.setPasswordHash("hash");
        return user;
    }
}
