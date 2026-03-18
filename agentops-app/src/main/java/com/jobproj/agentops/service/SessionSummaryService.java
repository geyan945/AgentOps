package com.jobproj.agentops.service;

import com.jobproj.agentops.dto.session.SessionSummaryResponse;
import com.jobproj.agentops.entity.AgentMessage;
import com.jobproj.agentops.repository.AgentMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SessionSummaryService {

    private final AgentMessageRepository agentMessageRepository;
    private final StringRedisTemplate stringRedisTemplate;

    @Value("${agent.cache.session-summary-ttl-seconds:600}")
    private long summaryTtlSeconds;

    @Value("${agent.cache.session-summary-max-messages:8}")
    private int summaryMaxMessages;

    public SessionSummaryResponse getOrBuildSummary(Long sessionId) {
        String cacheKey = buildKey(sessionId);
        try {
            String cached = stringRedisTemplate.opsForValue().get(cacheKey);
            if (StringUtils.hasText(cached)) {
                int messageCount = agentMessageRepository.findBySessionIdOrderByIdAsc(sessionId).size();
                return SessionSummaryResponse.builder().sessionId(sessionId).messageCount(messageCount).summary(cached).cacheHit(true).build();
            }
        } catch (Exception ignored) {
        }
        List<AgentMessage> messages = agentMessageRepository.findBySessionIdOrderByIdAsc(sessionId);
        String summary = buildSummary(messages);
        try {
            stringRedisTemplate.opsForValue().set(cacheKey, summary, Duration.ofSeconds(summaryTtlSeconds));
        } catch (Exception ignored) {
        }
        return SessionSummaryResponse.builder().sessionId(sessionId).messageCount(messages.size()).summary(summary).cacheHit(false).build();
    }

    public void evictSummary(Long sessionId) {
        try {
            stringRedisTemplate.delete(buildKey(sessionId));
        } catch (Exception ignored) {
        }
    }

    private String buildSummary(List<AgentMessage> messages) {
        if (messages.isEmpty()) {
            return "当前会话暂无历史消息。";
        }
        int start = Math.max(0, messages.size() - summaryMaxMessages);
        StringBuilder builder = new StringBuilder();
        builder.append("最近 ").append(messages.size() - start).append(" 条消息摘要：\n");
        for (int i = start; i < messages.size(); i++) {
            AgentMessage message = messages.get(i);
            builder.append("- ")
                    .append("user".equalsIgnoreCase(message.getRole()) ? "用户" : "assistant".equalsIgnoreCase(message.getRole()) ? "助手" : "工具")
                    .append("：")
                    .append(trim(message.getContent()))
                    .append("\n");
        }
        return builder.toString().trim();
    }

    private String trim(String content) {
        if (!StringUtils.hasText(content)) {
            return "";
        }
        return content.length() > 80 ? content.substring(0, 80) + "..." : content;
    }

    private String buildKey(Long sessionId) {
        return "agent:session:summary:" + sessionId;
    }
}