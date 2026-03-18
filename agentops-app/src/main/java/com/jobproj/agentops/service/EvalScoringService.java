package com.jobproj.agentops.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobproj.agentops.agent.EvalCaseExecutionResult;
import com.jobproj.agentops.entity.EvalCase;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class EvalScoringService {

    private final ObjectMapper objectMapper;

    @Value("${agent.eval.pass-score:0.6}")
    private double passScore;

    public ScoreOutcome score(EvalCase evalCase, EvalCaseExecutionResult executionResult) {
        double score = 0D;
        List<String> reasons = new ArrayList<>();

        String expectedTool = evalCase.getExpectedTool();
        if (!StringUtils.hasText(expectedTool)) {
            score += 0.4D;
            reasons.add("未设置预期工具，工具分默认给满");
        } else if (expectedTool.equalsIgnoreCase(executionResult.getActualTool())) {
            score += 0.4D;
            reasons.add("命中预期工具: " + expectedTool);
        } else {
            reasons.add("工具不匹配，期望=" + expectedTool + "，实际=" + executionResult.getActualTool());
        }

        List<String> expectedKeywords = readExpectedKeywords(evalCase.getExpectedKeywordsJson());
        if (expectedKeywords.isEmpty()) {
            score += 0.6D;
            reasons.add("未设置预期关键词，关键词分默认给满");
        } else {
            String answer = executionResult.getAnswerText() == null ? "" : executionResult.getAnswerText().toLowerCase(Locale.ROOT);
            long matched = expectedKeywords.stream().filter(StringUtils::hasText).map(keyword -> keyword.toLowerCase(Locale.ROOT)).filter(answer::contains).count();
            double keywordScore = 0.6D * matched / expectedKeywords.size();
            score += keywordScore;
            reasons.add("关键词命中 " + matched + "/" + expectedKeywords.size());
        }

        score = Math.min(1D, Math.max(0D, score));
        boolean success = score >= passScore;
        if (!Boolean.TRUE.equals(executionResult.getSuccess())) {
            success = false;
            reasons.add("执行阶段报错: " + executionResult.getErrorMessage());
        }
        return ScoreOutcome.builder().score(score).success(success).reason(String.join("；", reasons)).build();
    }

    private List<String> readExpectedKeywords(String json) {
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
        private boolean success;
        private String reason;
    }
}