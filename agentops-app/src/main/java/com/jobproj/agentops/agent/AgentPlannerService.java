package com.jobproj.agentops.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jobproj.agentops.dto.agent.ToolInfoResponse;
import com.jobproj.agentops.integration.GeminiClient;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class AgentPlannerService {

    private static final Pattern DOCUMENT_ID_PATTERN = Pattern.compile("(?:documentId|文档|doc(?:ument)?)[^0-9]{0,6}(\\d+)", Pattern.CASE_INSENSITIVE);

    private final GeminiClient geminiClient;
    private final ObjectMapper objectMapper;

    @Value("${agent.planner.provider:rule}")
    private String plannerProvider;

    @Value("${agent.max-tool-calls:2}")
    private int maxToolCalls;

    @Value("${agent.kb.default-knowledge-base-id:1}")
    private Long defaultKnowledgeBaseId;

    @Value("${agent.mcp.prefer-remote-tools:false}")
    private boolean preferRemoteTools;

    @Value("${agent.planner.prompt-version:planner-v1}")
    private String promptVersion;

    public PlannerResult plan(String userMessage, List<ToolInfoResponse> tools) {
        if ("gemini".equalsIgnoreCase(plannerProvider) && geminiClient.isAvailable()) {
            PlannerResult plannerResult = tryGeminiPlan(userMessage, tools);
            if (plannerResult != null) {
                return plannerResult;
            }
        }
        return ruleBasedPlan(userMessage);
    }

    private PlannerResult tryGeminiPlan(String userMessage, List<ToolInfoResponse> tools) {
        try {
            String prompt = buildPlannerPrompt(userMessage, tools);
            String raw = geminiClient.generateText(prompt);
            String json = normalizeJson(raw);
            PlannerResult plannerResult = objectMapper.readValue(json, PlannerResult.class);
            if (plannerResult.getToolCalls() != null && plannerResult.getToolCalls().size() > maxToolCalls) {
                plannerResult.setToolCalls(plannerResult.getToolCalls().subList(0, maxToolCalls));
            }
            return plannerResult;
        } catch (Exception ignored) {
            return null;
        }
    }

    private PlannerResult ruleBasedPlan(String userMessage) {
        String text = userMessage == null ? "" : userMessage.toLowerCase();
        if (text.contains("你好") || text.contains("hello") || text.contains("hi")) {
            return PlannerResult.builder().decision("ANSWER_DIRECTLY").reason("问候语不需要调用工具").build();
        }
        String sqlToolName = preferRemoteTools ? "sql_query_remote" : "sql_query";
        String kbToolName = preferRemoteTools ? "kb_search_remote" : "kb_search";
        if (text.contains("统计") || text.contains("count") || text.contains("多少") || text.contains("数量")) {
            String queryType = text.contains("会话") || text.contains("session") ? "SESSION_COUNT_BY_USER"
                    : text.contains("文档") || text.contains("knowledge") || text.contains("知识库") ? "KB_DOCUMENT_COUNT"
                    : "RUN_COUNT_BY_STATUS";
            ObjectNode args = JsonNodeFactory.instance.objectNode();
            args.put("queryType", queryType);
            if ("KB_DOCUMENT_COUNT".equals(queryType)) {
                args.put("knowledgeBaseId", defaultKnowledgeBaseId);
            }
            return PlannerResult.builder()
                    .decision("CALL_TOOLS")
                    .reason("统计类问题优先使用 sql_query")
                    .toolCalls(List.of(ToolCallPlan.builder().toolName(sqlToolName).arguments(args).build()))
                    .build();
        }
        Matcher matcher = DOCUMENT_ID_PATTERN.matcher(userMessage == null ? "" : userMessage);
        if (matcher.find()) {
            ObjectNode args = JsonNodeFactory.instance.objectNode();
            args.put("documentId", Long.parseLong(matcher.group(1)));
            return PlannerResult.builder()
                    .decision("CALL_TOOLS")
                    .reason("用户明确指定了文档 id，优先读取文档")
                    .toolCalls(List.of(ToolCallPlan.builder().toolName("doc_fetch").arguments(args).build()))
                    .build();
        }
        ObjectNode args = JsonNodeFactory.instance.objectNode();
        args.put("query", userMessage);
        args.put("topK", 5);
        args.put("knowledgeBaseId", defaultKnowledgeBaseId);
        return PlannerResult.builder()
                .decision("CALL_TOOLS")
                .reason("默认走知识库检索工具")
                .toolCalls(List.of(ToolCallPlan.builder().toolName(kbToolName).arguments(args).build()))
                .build();
    }

    private String buildPlannerPrompt(String userMessage, List<ToolInfoResponse> tools) {
        String toolText = tools.stream().map(tool -> "- " + tool.getName() + ": " + tool.getDescription() + ", 参数=" + tool.getArgumentNames()).reduce((a, b) -> a + "\n" + b).orElse("无工具");
        return "你是 Agent Planner。请根据用户问题决定是直接回答还是调用工具。\n" +
                "当前 promptVersion=" + promptVersion + "。如果需要调用工具，只能从下面工具中选择，且最多调用 " + maxToolCalls + " 个工具。\n" +
                "工具列表:\n" + toolText + "\n\n" +
                "输出必须是 JSON，结构如下：{\"decision\":\"ANSWER_DIRECTLY|CALL_TOOLS\",\"reason\":\"...\",\"toolCalls\":[{\"toolName\":\"...\",\"arguments\":{}}]}\n" +
                "对统计问题优先选择 sql_query/sql_query_remote，对明确给出 documentId 的问题优先选择 doc_fetch，其他知识问答优先选择 kb_search/kb_search_remote。\n" +
                "用户问题：" + userMessage;
    }

    private String normalizeJson(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "{}";
        }
        String text = raw.trim();
        if (text.startsWith("```")) {
            text = text.replaceFirst("^```json", "").replaceFirst("^```", "").replaceFirst("```$", "").trim();
        }
        return text;
    }
}