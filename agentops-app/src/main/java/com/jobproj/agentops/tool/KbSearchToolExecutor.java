package com.jobproj.agentops.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jobproj.agentops.integration.AiSmartQaReadService;
import com.jobproj.agentops.service.ToolCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Component
@RequiredArgsConstructor
public class KbSearchToolExecutor implements ToolExecutor {

    private final AiSmartQaReadService aiSmartQaReadService;
    private final ToolCacheService toolCacheService;

    @Value("${agent.cache.kb-search-ttl-seconds:300}")
    private long cacheTtlSeconds;

    @Override
    public String getName() { return "kb_search"; }

    @Override
    public String getDescription() { return "在 AISmartQA 的知识库 chunk 中检索相关内容"; }

    @Override
    public List<String> getArgumentNames() { return List.of("query", "topK", "knowledgeBaseId"); }

    @Override
    public ToolResult execute(JsonNode arguments, ToolContext context) {
        String query = arguments.path("query").asText("");
        int topK = Math.max(1, arguments.path("topK").asInt(5));
        Long knowledgeBaseId = arguments.hasNonNull("knowledgeBaseId") ? arguments.path("knowledgeBaseId").asLong() : null;
        JsonNode data = toolCacheService.getOrLoad(getName(), query + "::" + topK + "::" + knowledgeBaseId, Duration.ofSeconds(cacheTtlSeconds), () -> aiSmartQaReadService.searchChunks(query, topK, knowledgeBaseId));
        ObjectNode objectNode = (ObjectNode) data;
        return ToolResult.builder().toolName(getName()).success(true).summary("知识库检索完成，命中 " + objectNode.path("hits").size() + " 条").data(objectNode).build();
    }
}