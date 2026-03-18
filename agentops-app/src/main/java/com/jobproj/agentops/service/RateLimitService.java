package com.jobproj.agentops.service;

import com.jobproj.agentops.common.BusinessException;
import com.jobproj.agentops.common.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class RateLimitService {

    private static final DateTimeFormatter MINUTE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    private final StringRedisTemplate stringRedisTemplate;

    @Value("${agent.rate-limit.enabled:true}")
    private boolean enabled;

    @Value("${agent.rate-limit.run-per-minute:20}")
    private int runPerMinute;

    @Value("${agent.rate-limit.eval-per-minute:3}")
    private int evalPerMinute;

    public void checkRunAllowed(Long userId) {
        checkAllowed("run", userId, runPerMinute);
    }

    public void checkEvalAllowed(Long userId) {
        checkAllowed("eval", userId, evalPerMinute);
    }

    private void checkAllowed(String action, Long userId, int limit) {
        if (!enabled || limit <= 0) {
            return;
        }
        String timeBucket = LocalDateTime.now().format(MINUTE_FORMATTER);
        String key = "agent:rate_limit:" + action + ":user:" + userId + ":" + timeBucket;
        Long count = stringRedisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            stringRedisTemplate.expire(key, 70, TimeUnit.SECONDS);
        }
        if (count != null && count > limit) {
            throw new BusinessException(ErrorCode.RATE_LIMITED, "操作过于频繁，" + action + " 每分钟最多允许 " + limit + " 次");
        }
    }
}