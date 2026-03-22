package com.jobproj.agentops.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jobproj.agentops.common.BusinessException;
import com.jobproj.agentops.common.ErrorCode;
import com.jobproj.agentops.service.ToolCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class SqlQueryToolExecutor implements ToolExecutor {

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final ObjectMapper objectMapper;
    private final ToolCacheService toolCacheService;

    @Value("${agent.cache.sql-query-ttl-seconds:60}")
    private long cacheTtlSeconds;

    @Override
    public String getName() { return "sql_query"; }

    @Override
    public String getDescription() { return "执行只读白名单 SQL 模板查询，用于统计和报表场景"; }

    @Override
    public List<String> getArgumentNames() { return List.of("queryType", "knowledgeBaseId"); }

    @Override
    public String getRiskLevel() { return "HIGH"; }

    @Override
    public String getApprovalPolicy() { return "HUMAN_REVIEW"; }

    @Override
    public int getTimeoutBudgetMs() { return 4_000; }

    @Override
    public String getRetryPolicy() { return "NO_RETRY"; }

    @Override
    public boolean isAuditRequired() { return true; }

    @Override
    public ToolResult execute(JsonNode arguments, ToolContext context) {
        String queryType = arguments.path("queryType").asText("");
        Long knowledgeBaseId = arguments.hasNonNull("knowledgeBaseId") ? arguments.path("knowledgeBaseId").asLong() : null;
        JsonNode data = toolCacheService.getOrLoad(getName(), queryType + "::" + knowledgeBaseId + "::" + context.getUserId(), Duration.ofSeconds(cacheTtlSeconds), () -> executeTemplate(queryType, knowledgeBaseId, context));
        return ToolResult.builder().toolName(getName()).success(true).summary("SQL 模板查询完成: " + queryType).data(data).build();
    }

    private JsonNode executeTemplate(String queryType, Long knowledgeBaseId, ToolContext context) {
        ObjectNode data = objectMapper.createObjectNode();
        switch (queryType) {
            case "SESSION_COUNT_BY_USER" -> {
                Long count = namedParameterJdbcTemplate.queryForObject("select count(*) from agent_session where user_id = :userId", new MapSqlParameterSource("userId", context.getUserId()), Long.class);
                data.put("queryType", queryType);
                data.put("userId", context.getUserId());
                data.put("sessionCount", count == null ? 0 : count);
            }
            case "RUN_COUNT_BY_STATUS" -> {
                List<Map<String, Object>> rows = namedParameterJdbcTemplate.queryForList("select status, count(*) total from agent_run group by status order by status", new MapSqlParameterSource());
                ArrayNode items = objectMapper.createArrayNode();
                for (Map<String, Object> row : rows) {
                    ObjectNode item = objectMapper.createObjectNode();
                    item.put("status", String.valueOf(row.get("status")));
                    item.put("total", ((Number) row.get("total")).longValue());
                    items.add(item);
                }
                data.put("queryType", queryType);
                data.set("items", items);
            }
            case "KB_DOCUMENT_COUNT" -> {
                Long effectiveKnowledgeBaseId = knowledgeBaseId == null ? 1L : knowledgeBaseId;
                Long count = namedParameterJdbcTemplate.queryForObject("select count(*) from ai_qa_system.sys_document where knowledge_base_id = :knowledgeBaseId", new MapSqlParameterSource("knowledgeBaseId", effectiveKnowledgeBaseId), Long.class);
                data.put("queryType", queryType);
                data.put("knowledgeBaseId", effectiveKnowledgeBaseId);
                data.put("documentCount", count == null ? 0 : count);
            }
            default -> throw new BusinessException(ErrorCode.INVALID_SQL_QUERY_TYPE, "不支持的 queryType: " + queryType);
        }
        return data;
    }
}
