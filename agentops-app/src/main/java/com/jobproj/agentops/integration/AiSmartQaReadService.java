package com.jobproj.agentops.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jobproj.agentops.common.BusinessException;
import com.jobproj.agentops.common.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AiSmartQaReadService {

    private final ObjectMapper objectMapper;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Value("${agent.kb.es.enabled:true}")
    private boolean esEnabled;

    @Value("${agent.kb.es.base-url}")
    private String esBaseUrl;

    @Value("${agent.kb.es.index-name}")
    private String indexName;

    public JsonNode fetchDocument(Long documentId) {
        String sql = """
                select d.id as document_id, d.knowledge_base_id, d.file_name, d.file_type, d.parse_status, d.vector_status,
                       c.content as content
                from ai_qa_system.sys_document d
                left join ai_qa_system.sys_document_content c on c.document_id = d.id
                where d.id = :documentId
                """;
        List<Map<String, Object>> rows = namedParameterJdbcTemplate.queryForList(sql, new MapSqlParameterSource("documentId", documentId));
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND, "文档不存在: " + documentId);
        }
        Map<String, Object> row = rows.get(0);
        ObjectNode node = objectMapper.createObjectNode();
        node.put("documentId", ((Number) row.get("document_id")).longValue());
        node.put("knowledgeBaseId", ((Number) row.get("knowledge_base_id")).longValue());
        node.put("fileName", String.valueOf(row.get("file_name")));
        node.put("fileType", String.valueOf(row.get("file_type")));
        node.put("parseStatus", row.get("parse_status") == null ? 0 : ((Number) row.get("parse_status")).intValue());
        node.put("vectorStatus", row.get("vector_status") == null ? 0 : ((Number) row.get("vector_status")).intValue());
        node.put("content", row.get("content") == null ? "" : String.valueOf(row.get("content")));
        return node;
    }

    public JsonNode searchChunks(String query, int topK, Long knowledgeBaseId) {
        if (esEnabled) {
            try {
                JsonNode result = searchChunksByEs(query, topK, knowledgeBaseId);
                if (result.path("hits").isArray() && result.path("hits").size() > 0) {
                    return result;
                }
            } catch (Exception ignored) {
            }
        }
        return searchChunksByDbFallback(query, topK, knowledgeBaseId);
    }

    private JsonNode searchChunksByEs(String query, int topK, Long knowledgeBaseId) {
        RestClient client = RestClient.builder().baseUrl(esBaseUrl).build();
        Map<String, Object> matchQuery = new HashMap<>();
        matchQuery.put("match", Map.of("content", Map.of("query", query, "operator", "or")));
        Object queryBody = knowledgeBaseId == null
                ? matchQuery
                : Map.of("bool", Map.of("must", List.of(matchQuery), "filter", List.of(Map.of("term", Map.of("knowledgeBaseId", knowledgeBaseId)))));
        Map<String, Object> body = Map.of(
                "size", topK,
                "query", queryBody,
                "highlight", Map.of("fields", Map.of("content", Map.of()))
        );
        JsonNode response = client.post()
                .uri("/" + indexName + "/_search")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);
        ArrayNode hits = objectMapper.createArrayNode();
        for (JsonNode hit : response.path("hits").path("hits")) {
            JsonNode source = hit.path("_source");
            ObjectNode item = objectMapper.createObjectNode();
            item.put("documentId", source.path("documentId").asLong());
            item.put("chunkId", source.path("chunkId").asLong());
            item.put("chunkIndex", source.path("chunkIndex").asInt());
            item.put("content", source.path("content").asText(""));
            item.put("score", hit.path("_score").asDouble(0D));
            JsonNode fragments = hit.path("highlight").path("content");
            if (fragments.isArray() && !fragments.isEmpty()) {
                item.put("highlight", fragments.get(0).asText());
            }
            hits.add(item);
        }
        ObjectNode result = objectMapper.createObjectNode();
        result.put("strategy", "es");
        result.set("hits", hits);
        return result;
    }

    private JsonNode searchChunksByDbFallback(String query, int topK, Long knowledgeBaseId) {
        String sql = """
                select c.id as chunk_id, c.document_id, c.chunk_index, c.content
                from ai_qa_system.sys_document_chunk c
                join ai_qa_system.sys_document d on d.id = c.document_id
                where (:knowledgeBaseId is null or d.knowledge_base_id = :knowledgeBaseId)
                  and c.content like :pattern
                order by c.id desc
                limit :limit
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("knowledgeBaseId", knowledgeBaseId)
                .addValue("pattern", "%" + query + "%")
                .addValue("limit", topK);
        List<Map<String, Object>> rows = namedParameterJdbcTemplate.queryForList(sql, params);
        ArrayNode hits = objectMapper.createArrayNode();
        for (Map<String, Object> row : rows) {
            ObjectNode item = objectMapper.createObjectNode();
            item.put("documentId", ((Number) row.get("document_id")).longValue());
            item.put("chunkId", ((Number) row.get("chunk_id")).longValue());
            item.put("chunkIndex", ((Number) row.get("chunk_index")).intValue());
            String content = String.valueOf(row.get("content"));
            item.put("content", content);
            item.put("highlight", buildHighlight(content, query));
            item.put("score", countMatches(content, query));
            hits.add(item);
        }
        ObjectNode result = objectMapper.createObjectNode();
        result.put("strategy", "db_fallback");
        result.set("hits", hits);
        return result;
    }

    private String buildHighlight(String content, String query) {
        if (!StringUtils.hasText(content) || !StringUtils.hasText(query)) {
            return content;
        }
        return content.replace(query, "<em>" + query + "</em>");
    }

    private int countMatches(String content, String query) {
        if (!StringUtils.hasText(content) || !StringUtils.hasText(query)) {
            return 0;
        }
        int count = 0;
        int index = 0;
        while ((index = content.indexOf(query, index)) >= 0) {
            count++;
            index += query.length();
        }
        return count;
    }
}