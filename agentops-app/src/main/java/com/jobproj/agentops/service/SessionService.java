package com.jobproj.agentops.service;

import com.jobproj.agentops.common.BusinessException;
import com.jobproj.agentops.common.ErrorCode;
import com.jobproj.agentops.dto.session.CreateSessionRequest;
import com.jobproj.agentops.dto.session.MessageResponse;
import com.jobproj.agentops.dto.session.SessionResponse;
import com.jobproj.agentops.dto.session.SessionSummaryResponse;
import com.jobproj.agentops.entity.AgentMessage;
import com.jobproj.agentops.entity.AgentSession;
import com.jobproj.agentops.repository.AgentMessageRepository;
import com.jobproj.agentops.repository.AgentSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SessionService {

    private final AgentSessionRepository sessionRepository;
    private final AgentMessageRepository messageRepository;
    private final SessionSummaryService sessionSummaryService;

    @Transactional
    public SessionResponse createSession(Long userId, CreateSessionRequest request) {
        AgentSession session = new AgentSession();
        session.setUserId(userId);
        session.setTitle(StringUtils.hasText(request.getTitle()) ? request.getTitle() : "新会话");
        session.setStatus("ACTIVE");
        sessionRepository.save(session);
        return toSessionResponse(session);
    }

    public List<SessionResponse> listSessions(Long userId) {
        return sessionRepository.findByUserIdOrderByUpdatedAtDesc(userId).stream().map(this::toSessionResponse).toList();
    }

    public AgentSession getRequiredSession(Long userId, Long sessionId) {
        return sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));
    }

    public AgentSession getRequiredSessionById(Long sessionId) {
        return sessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));
    }

    public List<MessageResponse> listMessages(Long userId, Long sessionId) {
        getRequiredSession(userId, sessionId);
        return messageRepository.findBySessionIdOrderByIdAsc(sessionId).stream().map(this::toMessageResponse).toList();
    }

    public SessionSummaryResponse getSessionSummary(Long userId, Long sessionId) {
        getRequiredSession(userId, sessionId);
        return sessionSummaryService.getOrBuildSummary(sessionId);
    }

    @Transactional
    public AgentMessage saveMessage(Long sessionId, String role, String content, String metadataJson) {
        AgentMessage message = new AgentMessage();
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setContent(content);
        message.setMetadataJson(metadataJson);
        AgentMessage saved = messageRepository.save(message);
        sessionSummaryService.evictSummary(sessionId);
        return saved;
    }

    @Transactional
    public void touchSession(AgentSession session, String userMessage) {
        if (!StringUtils.hasText(session.getTitle()) || "新会话".equals(session.getTitle())) {
            String trimmed = userMessage.length() > 20 ? userMessage.substring(0, 20) + "..." : userMessage;
            session.setTitle(trimmed);
        }
        sessionRepository.save(session);
    }

    private SessionResponse toSessionResponse(AgentSession session) {
        return SessionResponse.builder().id(session.getId()).title(session.getTitle()).status(session.getStatus()).createdAt(session.getCreatedAt()).updatedAt(session.getUpdatedAt()).build();
    }

    private MessageResponse toMessageResponse(AgentMessage message) {
        return MessageResponse.builder().id(message.getId()).role(message.getRole()).content(message.getContent()).metadataJson(message.getMetadataJson()).createdAt(message.getCreatedAt()).build();
    }
}
