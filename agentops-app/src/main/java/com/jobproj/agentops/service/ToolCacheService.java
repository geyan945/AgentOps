package com.jobproj.agentops.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class ToolCacheService {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${agent.cache.tool.enabled:true}")
    private boolean enabled;

    public JsonNode getOrLoad(String namespace, String rawKey, Duration ttl, Supplier<JsonNode> loader) {
        if (!enabled) {
            return loader.get();
        }
        String cacheKey = buildCacheKey(namespace, rawKey);
        try {
            String cached = stringRedisTemplate.opsForValue().get(cacheKey);
            if (StringUtils.hasText(cached)) {
                return objectMapper.readTree(cached);
            }
        } catch (Exception ignored) {
        }
        JsonNode loaded = loader.get();
        if (loaded == null) {
            return null;
        }
        try {
            stringRedisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(loaded), ttl);
        } catch (Exception ignored) {
        }
        return loaded;
    }

    private String buildCacheKey(String namespace, String rawKey) {
        String digest = DigestUtils.md5DigestAsHex(rawKey.getBytes(StandardCharsets.UTF_8));
        return "agent:tool:" + namespace + ":" + digest;
    }
}