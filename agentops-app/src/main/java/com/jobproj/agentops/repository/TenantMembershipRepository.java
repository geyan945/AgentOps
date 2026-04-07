package com.jobproj.agentops.repository;

import com.jobproj.agentops.entity.TenantMembership;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TenantMembershipRepository extends JpaRepository<TenantMembership, Long> {

    Optional<TenantMembership> findByTenantIdAndUserId(Long tenantId, Long userId);

    List<TenantMembership> findByUserIdAndStatus(Long userId, String status);
}
