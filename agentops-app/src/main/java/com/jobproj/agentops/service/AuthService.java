package com.jobproj.agentops.service;

import com.jobproj.agentops.common.BusinessException;
import com.jobproj.agentops.common.ErrorCode;
import com.jobproj.agentops.dto.auth.AuthResponse;
import com.jobproj.agentops.dto.auth.CurrentUserResponse;
import com.jobproj.agentops.dto.auth.LoginRequest;
import com.jobproj.agentops.dto.auth.RegisterRequest;
import com.jobproj.agentops.entity.SysUser;
import com.jobproj.agentops.repository.SysUserRepository;
import com.jobproj.agentops.security.JwtTokenProvider;
import com.jobproj.agentops.security.JwtUserDetails;
import com.jobproj.agentops.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final SysUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Value("${jwt.expire-seconds}")
    private long expireSeconds;

    @Transactional
    public void register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BusinessException(ErrorCode.USER_ALREADY_EXISTS);
        }
        SysUser user = new SysUser();
        user.setUsername(request.getUsername());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRole("USER");
        user.setStatus(1);
        userRepository.save(user);
    }

    public AuthResponse login(LoginRequest request) {
        SysUser user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_CREDENTIALS));
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.BAD_CREDENTIALS);
        }
        JwtUserDetails userDetails = new JwtUserDetails(user.getId(), user.getUsername(), user.getPasswordHash(), user.getRole(), user.getStatus());
        return new AuthResponse(jwtTokenProvider.generateToken(userDetails), expireSeconds);
    }

    public CurrentUserResponse currentUser() {
        JwtUserDetails user = SecurityUtils.currentUser();
        return new CurrentUserResponse(user.getId(), user.getUsername(), user.getRole());
    }
}