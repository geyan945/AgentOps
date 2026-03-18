package com.jobproj.agentops.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobproj.agentops.integration.GeminiClient;
import com.jobproj.agentops.tool.ToolResult;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AgentAnswerService {

    private final GeminiClient geminiClient;
    private final ObjectMapper objectMapper;

    @Value("${agent.answer.provider:rule}")
    private String answerProvider;

    @Value("${agent.answer.prompt-version:answer-v1}")
    private String promptVersion;

    public String answer(String userMessage, List<ToolResult> toolResults) {
        if ("gemini".equalsIgnoreCase(answerProvider) && geminiClient.isAvailable()) {
            try {
                return geminiClient.generateText(buildAnswerPrompt(userMessage, toolResults));
            } catch (Exception ignored) {
            }
        }
        return buildFallbackAnswer(userMessage, toolResults);
    }

    private String buildAnswerPrompt(String userMessage, List<ToolResult> toolResults) {
        StringBuilder builder = new StringBuilder();
        builder.append("你是企业级 Agent 的回答生成器。请严格依据工具结果回答，不要编造。\n");
        builder.append("当前 promptVersion=").append(promptVersion).append("\n");
        builder.append("用户问题：").append(userMessage).append("\n\n工具结果：\n");
        for (ToolResult result : toolResults) {
            builder.append("工具=").append(result.getToolName()).append("\n摘要=").append(result.getSummary()).append("\n");
            try {
                builder.append("数据=").append(objectMapper.writeValueAsString(result.getData())).append("\n\n");
            } catch (Exception ex) {
                builder.append("数据序列化失败\n\n");
            }
        }
        builder.append("请输出简洁、结构化的中文答案，并说明使用了哪些工具。\n");
        return builder.toString();
    }

    private String buildFallbackAnswer(String userMessage, List<ToolResult> toolResults) {
        if (toolResults == null || toolResults.isEmpty()) {
            return "已收到你的问题：" + userMessage + "。当前无需调用工具，建议补充更多上下文。";
        }
        StringBuilder builder = new StringBuilder("我已根据问题调用相关工具完成分析。\n");
        for (ToolResult result : toolResults) {
            builder.append("- 工具 ").append(result.getToolName()).append("：").append(result.getSummary()).append("\n");
            try {
                String compactJson = objectMapper.writeValueAsString(result.getData());
                if (compactJson.length() > 220) {
                    compactJson = compactJson.substring(0, 220) + "...";
                }
                builder.append("  关键结果：").append(compactJson).append("\n");
            } catch (Exception ignored) {
            }
        }
        builder.append("如需更细的原始结果，可以继续查看对应 run 的 step 明细。\n");
        return builder.toString();
    }
}