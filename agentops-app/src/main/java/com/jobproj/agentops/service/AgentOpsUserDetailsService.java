package com.jobproj.agentops.service;

import com.jobproj.agentops.common.BusinessException;
import com.jobproj.agentops.common.ErrorCode;
import com.jobproj.agentops.repository.SysUserRepository;
import com.jobproj.agentops.security.JwtUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AgentOpsUserDetailsService implements UserDetailsService {

    private final SysUserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .map(user -> new JwtUserDetails(
                        user.getId(),
                        user.getTenantId(),
                        user.getUsername(),
                        user.getPasswordHash(),
                        user.getRole(),
                        user.getStatus(),
                        user.getTokenVersion()
                ))
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
