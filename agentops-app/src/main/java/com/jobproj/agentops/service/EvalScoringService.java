package com.jobproj.agentops.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobproj.agentops.agent.EvalCaseExecutionResult;
import com.jobproj.agentops.entity.EvalCase;
import com.jobproj.agentops.integration.GeminiClient;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class EvalScoringService {

    private final ObjectMapper objectMapper;
    private final GeminiClient geminiClient;

    @Value("${agent.eval.pass-score:0.6}")
    private double passScore;

    public ScoreOutcome score(EvalCase evalCase, EvalCaseExecutionResult executionResult) {
        double routeScore = scoreRoute(evalCase, executionResult);
        GroundingJudgeOutcome judgeOutcome = judgeGrounding(evalCase, executionResult);
        double groundingScore = judgeOutcome.getScore();
        double citationScore = scoreCitation(evalCase, executionResult);
        double approvalScore = scoreApproval(evalCase, executionResult);

        List<String> reasons = new ArrayList<>();
        reasons.add(explainRoute(evalCase, executionResult, routeScore));
        reasons.add(judgeOutcome.getReason());
        reasons.add(explainCitation(evalCase, executionResult, citationScore));
        reasons.add(explainApproval(evalCase, executionResult, approvalScore));

        boolean nodePathMatched = nodePathMatches(readStringList(evalCase.getExpectedNodePathJson()), executionResult.getNodePath());
        boolean artifactsMatched = artifactsMatch(readStringList(evalCase.getExpectedArtifactTypesJson()), executionResult.getArtifactTypes());
        boolean retryWithinBound = executionResult.getRetryCount() == null || executionResult.getRetryCount() <= 2;

        if (!nodePathMatched) {
            reasons.add("节点路径未命中预期");
        }
        if (!artifactsMatched) {
            reasons.add("artifact 类型未命中预期");
        }
        if (!retryWithinBound) {
            reasons.add("retry 次数超过上限: " + executionResult.getRetryCount());
        }

        double finalScore = Math.min(1D, Math.max(0D, routeScore * 0.25D + groundingScore * 0.45D + citationScore * 0.20D + approvalScore * 0.10D));
        boolean success = finalScore >= passScore && nodePathMatched && artifactsMatched && retryWithinBound;
        if (!Boolean.TRUE.equals(executionResult.getSuccess())) {
            success = false;
            reasons.add("执行阶段报错: " + executionResult.getErrorMessage());
        }

        return ScoreOutcome.builder()
                .score(finalScore)
                .routeScore(routeScore)
                .groundingScore(groundingScore)
                .citationScore(citationScore)
                .finalScore(finalScore)
                .success(success)
                .reason(String.join("；", reasons))
                .judgeModel(judgeOutcome.getJudgeModel())
                .judgeReason(judgeOutcome.getReason())
                .build();
    }

    private double scoreRoute(EvalCase evalCase, EvalCaseExecutionResult executionResult) {
        if (StringUtils.hasText(evalCase.getExpectedRoute())) {
            return evalCase.getExpectedRoute().equalsIgnoreCase(executionResult.getRoute()) ? 1D : 0D;
        }
        if (!StringUtils.hasText(evalCase.getExpectedTool())) {
            return 1D;
        }
        return containsIgnoreCase(executionResult.getActualTool(), evalCase.getExpectedTool()) ? 1D : 0D;
    }

    private String explainRoute(EvalCase evalCase, EvalCaseExecutionResult executionResult, double routeScore) {
        if (StringUtils.hasText(evalCase.getExpectedRoute())) {
            return routeScore > 0D
                    ? "命中预期 route: " + evalCase.getExpectedRoute()
                    : "route 不匹配，期望=" + evalCase.getExpectedRoute() + "，实际=" + executionResult.getRoute();
        }
        if (!StringUtils.hasText(evalCase.getExpectedTool())) {
            return "未设置预期 route/tool，路由分默认给满";
        }
        return routeScore > 0D
                ? "命中预期工具: " + evalCase.getExpectedTool()
                : "工具不匹配，期望=" + evalCase.getExpectedTool() + "，实际=" + executionResult.getActualTool();
    }

    private double scoreCitation(EvalCase evalCase, EvalCaseExecutionResult executionResult) {
        Integer expectedCitationMin = evalCase.getExpectedCitationMin();
        if (expectedCitationMin == null || expectedCitationMin <= 0) {
            return 1D;
        }
        return executionResult.getCitationCount() != null && executionResult.getCitationCount() >= expectedCitationMin ? 1D : 0D;
    }

    private String explainCitation(EvalCase evalCase, EvalCaseExecutionResult executionResult, double citationScore) {
        Integer expectedCitationMin = evalCase.getExpectedCitationMin();
        if (expectedCitationMin == null || expectedCitationMin <= 0) {
            return "未设置最小引用数，引用分默认给满";
        }
        return citationScore > 0D
                ? "引用数达标: " + executionResult.getCitationCount()
                : "引用数不足，期望至少 " + expectedCitationMin + "，实际 " + executionResult.getCitationCount();
    }

    private double scoreApproval(EvalCase evalCase, EvalCaseExecutionResult executionResult) {
        if (!StringUtils.hasText(evalCase.getExpectedApprovalPolicy())) {
            return 1D;
        }
        return approvalMatches(evalCase.getExpectedApprovalPolicy(), executionResult) ? 1D : 0D;
    }

    private String explainApproval(EvalCase evalCase, EvalCaseExecutionResult executionResult, double approvalScore) {
        if (!StringUtils.hasText(evalCase.getExpectedApprovalPolicy())) {
            return "未设置预期审批策略，审批分默认给满";
        }
        return approvalScore > 0D
                ? "审批策略符合预期: " + evalCase.getExpectedApprovalPolicy()
                : "审批策略不匹配，期望=" + evalCase.getExpectedApprovalPolicy() + "，实际 decision=" + executionResult.getApprovalDecision();
    }

    private GroundingJudgeOutcome judgeGrounding(EvalCase evalCase, EvalCaseExecutionResult executionResult) {
        if (geminiClient != null && geminiClient.isAvailable()) {
            try {
                String raw = geminiClient.generateText(buildJudgePrompt(evalCase, executionResult));
                Map<String, Object> parsed = objectMapper.readValue(normalizeJson(raw), new TypeReference<Map<String, Object>>() {});
                double score = asDouble(parsed.get("score"));
                String reason = String.valueOf(parsed.getOrDefault("reason", "Gemini judge completed"));
                return GroundingJudgeOutcome.builder()
                        .score(score)
                        .reason(reason)
                        .judgeModel(geminiClient.currentModel())
                        .build();
            } catch (Exception ignored) {
                // fall back to deterministic keyword matching
            }
        }

        List<String> expectedKeywords = readStringList(evalCase.getExpectedKeywordsJson());
        if (expectedKeywords.isEmpty()) {
            return GroundingJudgeOutcome.builder()
                    .score(1D)
                    .reason("未设置预期关键词，grounding 分默认给满")
                    .judgeModel("rule-keyword")
                    .build();
        }
        String answer = executionResult.getAnswerText() == null ? "" : executionResult.getAnswerText().toLowerCase(Locale.ROOT);
        long matched = expectedKeywords.stream()
                .filter(StringUtils::hasText)
                .map(keyword -> keyword.toLowerCase(Locale.ROOT))
                .filter(answer::contains)
                .count();
        return GroundingJudgeOutcome.builder()
                .score(matched * 1.0D / expectedKeywords.size())
                .reason("关键词命中 " + matched + "/" + expectedKeywords.size())
                .judgeModel("rule-keyword")
                .build();
    }

    private String buildJudgePrompt(EvalCase evalCase, EvalCaseExecutionResult executionResult) {
        return "你是 AgentOps Eval Judge。请评估回答是否真正基于工具证据和问题，不要关注文风。\n"
                + "输出必须是 JSON，格式为 {\"score\":0.0-1.0,\"reason\":\"...\"}。\n"
                + "Question: " + nullSafe(evalCase.getQuestion()) + "\n"
                + "ExpectedKeywords: " + readStringList(evalCase.getExpectedKeywordsJson()) + "\n"
                + "Route: " + nullSafe(executionResult.getRoute()) + "\n"
                + "ToolTrace: " + nullSafe(String.valueOf(executionResult.getToolTrace())) + "\n"
                + "NodePath: " + nullSafe(String.valueOf(executionResult.getNodePath())) + "\n"
                + "Answer: " + nullSafe(executionResult.getAnswerText());
    }

    private boolean approvalMatches(String expectedApprovalPolicy, EvalCaseExecutionResult executionResult) {
        String normalized = expectedApprovalPolicy.trim().toUpperCase(Locale.ROOT);
        if ("NONE".equals(normalized)) {
            return !Boolean.TRUE.equals(executionResult.getApprovalTriggered());
        }
        if ("AUTO_APPROVE".equals(normalized) || "APPROVE".equals(normalized)) {
            return Boolean.TRUE.equals(executionResult.getApprovalTriggered()) && "APPROVE".equalsIgnoreCase(executionResult.getApprovalDecision());
        }
        if ("AUTO_REJECT".equals(normalized) || "REJECT".equals(normalized)) {
            return Boolean.TRUE.equals(executionResult.getApprovalTriggered()) && "REJECT".equalsIgnoreCase(executionResult.getApprovalDecision());
        }
        return normalized.equalsIgnoreCase(executionResult.getApprovalDecision());
    }

    private boolean nodePathMatches(List<String> expectedPath, List<String> actualPath) {
        if (expectedPath.isEmpty()) {
            return true;
        }
        if (actualPath == null || actualPath.isEmpty()) {
            return false;
        }
        int cursor = 0;
        for (String actual : actualPath) {
            if (expectedPath.get(cursor).equalsIgnoreCase(actual)) {
                cursor++;
                if (cursor >= expectedPath.size()) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean artifactsMatch(List<String> expectedArtifacts, List<String> actualArtifacts) {
        if (expectedArtifacts.isEmpty()) {
            return true;
        }
        if (actualArtifacts == null || actualArtifacts.isEmpty()) {
            return false;
        }
        return expectedArtifacts.stream().allMatch(expected ->
                actualArtifacts.stream().anyMatch(actual -> expected.equalsIgnoreCase(actual)));
    }

    private boolean containsIgnoreCase(String actualValue, String expectedValue) {
        return actualValue != null && expectedValue != null
                && actualValue.toLowerCase(Locale.ROOT).contains(expectedValue.toLowerCase(Locale.ROOT));
    }

    private double asDouble(Object value) {
        if (value instanceof Number number) {
            return Math.max(0D, Math.min(1D, number.doubleValue()));
        }
        try {
            return Math.max(0D, Math.min(1D, Double.parseDouble(String.valueOf(value))));
        } catch (Exception ex) {
            return 0D;
        }
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

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private List<String> readStringList(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception ignored) {
            return List.of();
        }
    }

    @Data
    @Builder
    public static class ScoreOutcome {
        private double score;
        private double routeScore;
        private double groundingScore;
        private double citationScore;
        private double finalScore;
        private boolean success;
        private String reason;
        private String judgeModel;
        private String judgeReason;
    }

    @Data
    @Builder
    private static class GroundingJudgeOutcome {
        private double score;
        private String reason;
        private String judgeModel;
    }
}
