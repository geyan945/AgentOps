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
public class DocFetchToolExecutor implements ToolExecutor {

    private final AiSmartQaReadService aiSmartQaReadService;
    private final ToolCacheService toolCacheService;

    @Value("${agent.cache.doc-fetch-ttl-seconds:600}")
    private long cacheTtlSeconds;

    @Override
    public String getName() { return "doc_fetch"; }

    @Override
    public String getDescription() { return "根据 documentId 获取文档正文与基础元信息"; }

    @Override
    public List<String> getArgumentNames() { return List.of("documentId"); }

    @Override
    public int getTimeoutBudgetMs() { return 2_000; }

    @Override
    public ToolResult execute(JsonNode arguments, ToolContext context) {
        Long documentId = arguments.path("documentId").asLong();
        JsonNode data = toolCacheService.getOrLoad(getName(), String.valueOf(documentId), Duration.ofSeconds(cacheTtlSeconds), () -> aiSmartQaReadService.fetchDocument(documentId));
        ObjectNode objectNode = (ObjectNode) data;
        return ToolResult.builder().toolName(getName()).success(true).summary("已读取文档 " + documentId).data(objectNode).build();
    }
}
