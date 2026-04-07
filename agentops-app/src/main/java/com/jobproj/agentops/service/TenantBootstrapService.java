package com.jobproj.agentops.service;

import com.jobproj.agentops.entity.SysUser;
import com.jobproj.agentops.entity.Tenant;
import com.jobproj.agentops.entity.TenantMembership;
import com.jobproj.agentops.repository.SysUserRepository;
import com.jobproj.agentops.repository.TenantMembershipRepository;
import com.jobproj.agentops.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class TenantBootstrapService {

    private final TenantRepository tenantRepository;
    private final TenantMembershipRepository tenantMembershipRepository;
    private final SysUserRepository sysUserRepository;

    @Transactional
    public SysUser ensureUserTenant(SysUser user) {
        if (user.getTenantId() != null) {
            tenantMembershipRepository.findByTenantIdAndUserId(user.getTenantId(), user.getId())
                    .orElseGet(() -> tenantMembershipRepository.save(buildMembership(user.getTenantId(), user.getId(), user.getRole())));
            return user;
        }
        Tenant tenant = new Tenant();
        tenant.setName(buildTenantName(user.getUsername()));
        tenant.setCode(buildTenantCode(user));
        tenantRepository.save(tenant);
        user.setTenantId(tenant.getId());
        sysUserRepository.save(user);
        tenantMembershipRepository.save(buildMembership(tenant.getId(), user.getId(), user.getRole()));
        return user;
    }

    private TenantMembership buildMembership(Long tenantId, Long userId, String role) {
        TenantMembership membership = new TenantMembership();
        membership.setTenantId(tenantId);
        membership.setUserId(userId);
        membership.setRole("ADMIN");
        membership.setStatus("ACTIVE");
        return membership;
    }

    private String buildTenantName(String username) {
        return (StringUtils.hasText(username) ? username : "agentops") + "-workspace";
    }

    private String buildTenantCode(SysUser user) {
        String base = StringUtils.hasText(user.getUsername()) ? user.getUsername().trim().toLowerCase().replaceAll("[^a-z0-9]+", "-") : "tenant";
        return base + "-" + user.getId();
    }
}
