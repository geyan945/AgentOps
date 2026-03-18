package com.jobproj.agentops.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobproj.agentops.common.BusinessException;
import com.jobproj.agentops.common.ErrorCode;
import com.jobproj.agentops.dto.agent.AgentRunRequest;
import com.jobproj.agentops.dto.agent.AgentRunResponse;
import com.jobproj.agentops.dto.agent.AgentRunStepResponse;
import com.jobproj.agentops.dto.agent.ToolInfoResponse;
import com.jobproj.agentops.entity.AgentRun;
import com.jobproj.agentops.entity.AgentRunStep;
import com.jobproj.agentops.entity.AgentSession;
import com.jobproj.agentops.repository.AgentRunRepository;
import com.jobproj.agentops.repository.AgentRunStepRepository;
import com.jobproj.agentops.service.RateLimitService;
import com.jobproj.agentops.service.SessionService;
import com.jobproj.agentops.tool.ToolContext;
import com.jobproj.agentops.tool.ToolExecutor;
import com.jobproj.agentops.tool.ToolPermissionService;
import com.jobproj.agentops.tool.ToolRegistry;
import com.jobproj.agentops.tool.ToolResult;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AgentRunService {

    private final SessionService sessionService;
    private final AgentRunRepository agentRunRepository;
    private final AgentRunStepRepository agentRunStepRepository;
    private final AgentPlannerService agentPlannerService;
    private final AgentAnswerService agentAnswerService;
    private final ToolRegistry toolRegistry;
    private final ToolPermissionService toolPermissionService;
    private final ObjectMapper objectMapper;
    private final RateLimitService rateLimitService;

    @Value("${agent.max-tool-calls:2}")
    private int maxToolCalls;

    @Transactional
    public AgentRunResponse execute(Long userId, AgentRunRequest request) {
        rateLimitService.checkRunAllowed(userId);
        AgentSession session = sessionService.getRequiredSession(userId, request.getSessionId());
        sessionService.saveMessage(session.getId(), "user", request.getMessage(), null);
        sessionService.touchSession(session, request.getMessage());

        AgentRun run = new AgentRun();
        run.setSessionId(session.getId());
        run.setUserInput(request.getMessage());
        run.setStatus("RUNNING");
        run = agentRunRepository.save(run);

        Instant runStart = Instant.now();
        List<ToolResult> toolResults = new ArrayList<>();
        int stepNo = 1;
        try {
            List<ToolInfoResponse> tools = toolRegistry.listTools();
            PlannerResult plannerResult = agentPlannerService.plan(request.getMessage(), tools);
            saveStep(run.getId(), stepNo++, "PLAN", null, plannerResult, plannerResult, true, null, 0L);

            if ("CALL_TOOLS".equalsIgnoreCase(plannerResult.getDecision()) && plannerResult.getToolCalls() != null) {
                int toolCallCount = 0;
                for (ToolCallPlan toolCallPlan : plannerResult.getToolCalls()) {
                    if (toolCallCount >= maxToolCalls) {
                        break;
                    }
                    if (!toolPermissionService.canUse(userId, toolCallPlan.getToolName())) {
                        throw new BusinessException(ErrorCode.FORBIDDEN, "当前用户无权限调用工具: " + toolCallPlan.getToolName());
                    }
                    ToolExecutor toolExecutor = toolRegistry.getRequired(toolCallPlan.getToolName());
                    Instant stepStart = Instant.now();
                    ToolResult toolResult = toolExecutor.execute(toolCallPlan.getArguments(), ToolContext.builder().userId(userId).sessionId(session.getId()).runId(run.getId()).build());
                    long latencyMs = Duration.between(stepStart, Instant.now()).toMillis();
                    saveStep(run.getId(), stepNo++, "TOOL_CALL", toolExecutor.getName(), toolCallPlan, toolResult, true, null, latencyMs);
                    toolResults.add(toolResult);
                    toolCallCount++;
                }
            }

            Instant answerStart = Instant.now();
            String finalAnswer = agentAnswerService.answer(request.getMessage(), toolResults);
            long answerLatency = Duration.between(answerStart, Instant.now()).toMillis();
            saveStep(run.getId(), stepNo++, "FINAL_ANSWER", null, request.getMessage(), finalAnswer, true, null, answerLatency);
            sessionService.saveMessage(session.getId(), "assistant", finalAnswer, null);

            run.setFinalAnswer(finalAnswer);
            run.setStatus("SUCCEEDED");
            run.setTotalSteps(stepNo - 1);
            run.setTotalLatencyMs(Duration.between(runStart, Instant.now()).toMillis());
            run.setFinishedAt(LocalDateTime.now());
            agentRunRepository.save(run);
            return getRun(userId, run.getId());
        } catch (Exception ex) {
            run.setStatus("FAILED");
            run.setErrorMessage(ex.getMessage());
            run.setTotalSteps(stepNo - 1);
            run.setTotalLatencyMs(Duration.between(runStart, Instant.now()).toMillis());
            run.setFinishedAt(LocalDateTime.now());
            agentRunRepository.save(run);
            if (ex instanceof BusinessException businessException) {
                throw businessException;
            }
            throw new BusinessException(ErrorCode.AGENT_EXECUTION_FAILED, ex.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public AgentRunResponse getRun(Long userId, Long runId) {
        AgentRun run = agentRunRepository.findById(runId).orElseThrow(() -> new BusinessException(ErrorCode.RUN_NOT_FOUND));
        sessionService.getRequiredSession(userId, run.getSessionId());
        List<AgentRunStepResponse> steps = agentRunStepRepository.findByRunIdOrderByStepNoAsc(runId).stream().map(this::toStepResponse).toList();
        return AgentRunResponse.builder()
                .id(run.getId())
                .sessionId(run.getSessionId())
                .status(run.getStatus())
                .finalAnswer(run.getFinalAnswer())
                .totalSteps(run.getTotalSteps())
                .totalLatencyMs(run.getTotalLatencyMs())
                .errorMessage(run.getErrorMessage())
                .createdAt(run.getCreatedAt())
                .finishedAt(run.getFinishedAt())
                .steps(steps)
                .build();
    }

    @Transactional(readOnly = true)
    public List<AgentRunStepResponse> listSteps(Long userId, Long runId) {
        AgentRun run = agentRunRepository.findById(runId).orElseThrow(() -> new BusinessException(ErrorCode.RUN_NOT_FOUND));
        sessionService.getRequiredSession(userId, run.getSessionId());
        return agentRunStepRepository.findByRunIdOrderByStepNoAsc(runId).stream().map(this::toStepResponse).toList();
    }

    private void saveStep(Long runId, int stepNo, String stepType, String toolName, Object input, Object output, boolean success, String errorMessage, long latencyMs) {
        AgentRunStep step = new AgentRunStep();
        step.setRunId(runId);
        step.setStepNo(stepNo);
        step.setStepType(stepType);
        step.setToolName(toolName);
        step.setInputJson(toJson(input));
        step.setOutputJson(toJson(output));
        step.setLatencyMs(latencyMs);
        step.setSuccess(success);
        step.setErrorMessage(errorMessage);
        agentRunStepRepository.save(step);
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return String.valueOf(value);
        }
    }

    private AgentRunStepResponse toStepResponse(AgentRunStep step) {
        return AgentRunStepResponse.builder()
                .id(step.getId())
                .stepNo(step.getStepNo())
                .stepType(step.getStepType())
                .toolName(step.getToolName())
                .inputJson(step.getInputJson())
                .outputJson(step.getOutputJson())
                .latencyMs(step.getLatencyMs())
                .success(step.getSuccess())
                .errorMessage(step.getErrorMessage())
                .createdAt(step.getCreatedAt())
                .build();
    }
}