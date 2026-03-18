package com.jobproj.agentops.controller;

import com.jobproj.agentops.common.ApiResponse;
import com.jobproj.agentops.dto.auth.AuthResponse;
import com.jobproj.agentops.dto.auth.CurrentUserResponse;
import com.jobproj.agentops.dto.auth.LoginRequest;
import com.jobproj.agentops.dto.auth.RegisterRequest;
import com.jobproj.agentops.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ApiResponse<Void> register(@RequestBody @Valid RegisterRequest request) {
        authService.register(request);
        return ApiResponse.success("注册成功", null);
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@RequestBody @Valid LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    @GetMapping("/me")
    public ApiResponse<CurrentUserResponse> me() {
        return ApiResponse.success(authService.currentUser());
    }
}