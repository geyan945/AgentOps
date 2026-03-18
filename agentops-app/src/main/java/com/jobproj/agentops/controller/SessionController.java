package com.jobproj.agentops.controller;

import com.jobproj.agentops.common.ApiResponse;
import com.jobproj.agentops.dto.session.CreateSessionRequest;
import com.jobproj.agentops.dto.session.MessageResponse;
import com.jobproj.agentops.dto.session.SessionResponse;
import com.jobproj.agentops.dto.session.SessionSummaryResponse;
import com.jobproj.agentops.security.SecurityUtils;
import com.jobproj.agentops.service.SessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final SessionService sessionService;

    @PostMapping
    public ApiResponse<SessionResponse> createSession(@RequestBody @Valid CreateSessionRequest request) {
        return ApiResponse.success(sessionService.createSession(SecurityUtils.currentUserId(), request));
    }

    @GetMapping
    public ApiResponse<List<SessionResponse>> listSessions() {
        return ApiResponse.success(sessionService.listSessions(SecurityUtils.currentUserId()));
    }

    @GetMapping("/{id}/messages")
    public ApiResponse<List<MessageResponse>> listMessages(@PathVariable Long id) {
        return ApiResponse.success(sessionService.listMessages(SecurityUtils.currentUserId(), id));
    }

    @GetMapping("/{id}/summary")
    public ApiResponse<SessionSummaryResponse> getSummary(@PathVariable Long id) {
        return ApiResponse.success(sessionService.getSessionSummary(SecurityUtils.currentUserId(), id));
    }
}