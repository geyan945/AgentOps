package com.jobproj.agentops.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jobproj.agentops.common.BusinessException;
import com.jobproj.agentops.common.ErrorCode;
import com.jobproj.agentops.dto.agent.ToolInfoResponse;
import com.jobproj.agentops.integration.AiSmartQaReadService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class McpServerService {

    private final AiSmartQaReadService aiSmartQaReadService;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final ObjectMapper objectMapper;

    @Value("${agent.kb.default-knowledge-base-id:1}")
    private Long defaultKnowledgeBaseId;

    public McpInitializeResponse initialize() {
        return McpInitializeResponse.builder()
                .serverName("agentops-local-mcp")
                .version("v1")
                .protocolVersion("2026-03-18")
                .description("AgentOps local MCP server for kb_search, doc_fetch and sql_query")
                .build();
    }

    public List<ToolInfoResponse> listTools() {
        return List.of(
                ToolInfoResponse.builder().name("kb_search").description("在 AISmartQA 的知识库 chunk 中检索相关内容").argumentNames(List.of("query", "topK", "knowledgeBaseId")).build(),
                ToolInfoResponse.builder().name("doc_fetch").description("读取指定文档的完整内容").argumentNames(List.of("documentId")).build(),
                ToolInfoResponse.builder().name("sql_query").description("执行只读白名单 SQL 模板查询，用于统计和报表场景").argumentNames(List.of("queryType", "knowledgeBaseId")).build()
        );
    }

    public McpToolCallResponse callTool(String toolName, JsonNode arguments) {
        return switch (toolName) {
            case "kb_search" -> executeKbSearch(arguments);
            case "doc_fetch" -> executeDocFetch(arguments);
            case "sql_query" -> executeSqlQuery(arguments);
            default -> throw new BusinessException(ErrorCode.TOOL_NOT_FOUND, "MCP 工具不存在: " + toolName);
        };
    }

    private McpToolCallResponse executeKbSearch(JsonNode arguments) {
        String query = arguments.path("query").asText("");
        int topK = Math.max(1, arguments.path("topK").asInt(5));
        Long knowledgeBaseId = arguments.hasNonNull("knowledgeBaseId") ? arguments.path("knowledgeBaseId").asLong() : defaultKnowledgeBaseId;
        JsonNode data = aiSmartQaReadService.searchChunks(query, topK, knowledgeBaseId);
        return McpToolCallResponse.builder().toolName("kb_search").success(true).summary("MCP 知识库检索完成，命中 " + data.path("hits").size() + " 条").data(data).build();
    }

    private McpToolCallResponse executeSqlQuery(JsonNode arguments) {
        String queryType = arguments.path("queryType").asText("");
        Long knowledgeBaseId = arguments.hasNonNull("knowledgeBaseId") ? arguments.path("knowledgeBaseId").asLong() : defaultKnowledgeBaseId;
        ObjectNode data = objectMapper.createObjectNode();
        switch (queryType) {
            case "SESSION_COUNT_BY_USER" -> {
                Long userId = arguments.hasNonNull("userId") ? arguments.path("userId").asLong() : 1L;
                Long count = namedParameterJdbcTemplate.queryForObject("select count(*) from agent_session where user_id = :userId", new MapSqlParameterSource("userId", userId), Long.class);
                data.put("userId", userId);
                data.put("sessionCount", count == null ? 0 : count);
            }
            case "RUN_COUNT_BY_STATUS" -> {
                List<Map<String, Object>> rows = namedParameterJdbcTemplate.queryForList("select status, count(*) total from agent_run group by status order by status", new MapSqlParameterSource());
                data.putPOJO("items", rows);
            }
            case "KB_DOCUMENT_COUNT" -> {
                Long count = namedParameterJdbcTemplate.queryForObject("select count(*) from ai_qa_system.sys_document where knowledge_base_id = :knowledgeBaseId", new MapSqlParameterSource("knowledgeBaseId", knowledgeBaseId), Long.class);
                data.put("knowledgeBaseId", knowledgeBaseId);
                data.put("documentCount", count == null ? 0 : count);
            }
            default -> throw new BusinessException(ErrorCode.INVALID_SQL_QUERY_TYPE, "MCP 不支持的 queryType: " + queryType);
        }
        data.put("queryType", queryType);
        return McpToolCallResponse.builder().toolName("sql_query").success(true).summary("MCP SQL 模板查询完成: " + queryType).data(data).build();
    }

    private McpToolCallResponse executeDocFetch(JsonNode arguments) {
        Long documentId = arguments.path("documentId").asLong();
        JsonNode data = aiSmartQaReadService.fetchDocument(documentId);
        return McpToolCallResponse.builder()
                .toolName("doc_fetch")
                .success(true)
                .summary("MCP 文档读取完成: " + documentId)
                .data(data)
                .build();
    }
}
