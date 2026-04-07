package com.jobproj.agentops.agent;

import com.jobproj.agentops.common.BusinessException;
import com.jobproj.agentops.common.ErrorCode;
import com.jobproj.agentops.dto.agent.AgentGraphResponse;
import com.jobproj.agentops.dto.agent.AgentRunEventResponse;
import com.jobproj.agentops.dto.agent.AgentRunReplayRequest;
import com.jobproj.agentops.dto.agent.AgentRunRequest;
import com.jobproj.agentops.dto.agent.AgentRunResponse;
import com.jobproj.agentops.dto.agent.AgentRunResumeRequest;
import com.jobproj.agentops.dto.agent.AgentRunStepResponse;
import com.jobproj.agentops.dto.runtime.RuntimeCheckpointResponse;
import com.jobproj.agentops.dto.runtime.RuntimeCommandResponse;
import com.jobproj.agentops.dto.runtime.RuntimeReplayRunRequest;
import com.jobproj.agentops.dto.runtime.RuntimeResumeRunRequest;
import com.jobproj.agentops.dto.runtime.RuntimeStartRunRequest;
import com.jobproj.agentops.entity.AgentHumanTask;
import com.jobproj.agentops.entity.AgentRun;
import com.jobproj.agentops.entity.AgentRunStep;
import com.jobproj.agentops.entity.AgentSession;
import com.jobproj.agentops.repository.AgentRunRepository;
import com.jobproj.agentops.repository.AgentRunStepRepository;
import com.jobproj.agentops.runtime.AgentGraphService;
import com.jobproj.agentops.runtime.AgentHumanTaskService;
import com.jobproj.agentops.runtime.AgentRunEventService;
import com.jobproj.agentops.runtime.RuntimeCheckpointService;
import com.jobproj.agentops.runtime.RuntimeClient;
import com.jobproj.agentops.service.RateLimitService;
import com.jobproj.agentops.service.SessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AgentRunService {

    private final SessionService sessionService;
    private final AgentRunRepository agentRunRepository;
    private final AgentRunStepRepository agentRunStepRepository;
    private final RateLimitService rateLimitService;
    private final RuntimeClient runtimeClient;
    private final AgentGraphService agentGraphService;
    private final AgentHumanTaskService humanTaskService;
    private final RuntimeCheckpointService runtimeCheckpointService;
    private final AgentRunEventService agentRunEventService;

    @Value("${agent.runtime.default-graph-name:enterprise-copilot}")
    private String defaultGraphName;

    @Value("${agent.runtime.default-graph-version:v2}")
    private String defaultGraphVersion;

    @Transactional
    public AgentRunResponse execute(Long userId, AgentRunRequest request) {
        rateLimitService.checkRunAllowed(userId);
        AgentSession session = sessionService.getRequiredSession(userId, request.getSessionId());
        sessionService.saveMessage(session.getId(), "user", request.getMessage(), null);
        sessionService.touchSession(session, request.getMessage());

        AgentRun run = new AgentRun();
        run.setSessionId(session.getId());
        run.setUserId(userId);
        run.setTenantId(session.getTenantId());
        run.setUserInput(request.getMessage());
        run.setRuntimeType("LANGGRAPH");
        run.setExecutionMode(normalizeOrDefault(request.getExecutionMode(), "USER"));
        run.setApprovalPolicy(normalizeOrDefault(request.getApprovalPolicy(), "MANUAL"));
        run.setOrchestrationMode(normalizeOrDefault(request.getOrchestrationMode(), "SINGLE_GRAPH"));
        run.setGraphName(defaultGraphName);
        run.setGraphVersion(defaultGraphVersion);
        run.setCurrentNode("intake_guardrail");
        run.setStatus("QUEUED");
        run = agentRunRepository.save(run);

        try {
            RuntimeCommandResponse response = runtimeClient.startRun(RuntimeStartRunRequest.builder()
                    .runId(run.getId())
                    .sessionId(run.getSessionId())
                    .userId(userId)
                    .tenantId(run.getTenantId())
                    .userInput(request.getMessage())
                    .executionMode(run.getExecutionMode())
                    .approvalPolicy(run.getApprovalPolicy())
                    .orchestrationMode(run.getOrchestrationMode())
                    .waitForCompletion(false)
                    .build());
            run.setStatus(response == null || !response.isAccepted() ? "RUNNING" : response.getStatus());
            run.setCurrentNode(response == null ? "intake_guardrail" : response.getCurrentNode());
            if (response != null && response.getOrchestrationMode() != null) {
                run.setOrchestrationMode(response.getOrchestrationMode());
            }
            agentRunRepository.save(run);
        } catch (Exception ex) {
            markRunFailed(run, "启动 runtime 失败: " + ex.getMessage());
        }
        return getRun(userId, run.getId());
    }

    @Transactional
    public AgentRunResponse resume(Long userId, Long runId, AgentRunResumeRequest request) {
        AgentRun run = getRequiredRun(userId, runId);
        AgentHumanTask task = humanTaskService.getPendingTaskByRunId(runId);
        if (!task.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权限处理当前人工任务");
        }
        runtimeCheckpointService.getCheckpoint(runId);
        humanTaskService.decideTask(task, userId, request.getDecision(), request.getComment());
        run.setStatus("RUNNING");
        run.setRequiresHuman(false);
        agentRunRepository.save(run);
        try {
            String resumeToken = request.getResumeToken() != null ? request.getResumeToken() : run.getResumeToken();
            Integer checkpointVersion = request.getCheckpointVersion() != null ? request.getCheckpointVersion() : run.getCheckpointVersion();
            if (resumeToken == null || checkpointVersion == null) {
                throw new BusinessException(ErrorCode.INVALID_RESUME_TOKEN, "current run has no resumable checkpoint");
            }
            RuntimeCommandResponse response = runtimeClient.resumeRun(RuntimeResumeRunRequest.builder()
                    .runId(runId)
                    .tenantId(run.getTenantId())
                    .decision(request.getDecision())
                    .comment(request.getComment())
                    .resumeToken(resumeToken)
                    .checkpointVersion(checkpointVersion)
                    .waitForCompletion(false)
                    .build());
            if (response != null) {
                run.setStatus(response.getStatus());
                run.setCurrentNode(response.getCurrentNode());
                agentRunRepository.save(run);
            }
        } catch (Exception ex) {
            markRunFailed(run, "恢复 runtime 失败: " + ex.getMessage());
        }
        return getRun(userId, runId);
    }

    @Transactional
    public AgentRunResponse replay(Long userId, Long runId, AgentRunReplayRequest request) {
        AgentRun run = getRequiredRun(userId, runId);
        RuntimeCheckpointResponse checkpoint = runtimeCheckpointService.getCheckpoint(runId);
        if (Boolean.TRUE.equals(checkpoint.getRequiresHuman())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "当前 run 正在等待人工审批，请使用 resume 而不是 replay");
        }
        if (request.getCheckpointVersion() != null && !request.getCheckpointVersion().equals(checkpoint.getCheckpointVersion())) {
            throw new BusinessException(ErrorCode.INVALID_RESUME_TOKEN, "checkpoint version 已变化，请刷新后重试");
        }
        run.setStatus("RUNNING");
        run.setRequiresHuman(false);
        run.setReplayRecovered(Boolean.TRUE);
        agentRunRepository.save(run);
        try {
            RuntimeCommandResponse response = runtimeClient.replayRun(RuntimeReplayRunRequest.builder()
                    .runId(runId)
                    .tenantId(run.getTenantId())
                    .checkpointVersion(checkpoint.getCheckpointVersion())
                    .waitForCompletion(false)
                    .build());
            if (response != null) {
                run.setStatus(response.getStatus());
                run.setCurrentNode(response.getCurrentNode());
                if (response.getCheckpointVersion() != null) {
                    run.setCheckpointVersion(response.getCheckpointVersion());
                }
                agentRunRepository.save(run);
            }
        } catch (Exception ex) {
            markRunFailed(run, "checkpoint replay 失败: " + ex.getMessage());
        }
        return getRun(userId, runId);
    }

    @Transactional(readOnly = true)
    public AgentRunResponse getRun(Long userId, Long runId) {
        AgentRun run = getRequiredRun(userId, runId);
        List<AgentRunStepResponse> steps = agentRunStepRepository.findByRunIdOrderByStepNoAsc(runId).stream().map(this::toStepResponse).toList();
        return AgentRunResponse.builder()
                .id(run.getId())
                .runId(run.getId())
                .sessionId(run.getSessionId())
                .userId(run.getUserId())
                .status(run.getStatus())
                .runtimeType(run.getRuntimeType())
                .executionMode(run.getExecutionMode())
                .approvalPolicy(run.getApprovalPolicy())
                .orchestrationMode(run.getOrchestrationMode())
                .graphName(run.getGraphName())
                .graphVersion(run.getGraphVersion())
                .currentNode(run.getCurrentNode())
                .requiresHuman(run.getRequiresHuman())
                .resumeToken(run.getResumeToken())
                .checkpointVersion(run.getCheckpointVersion())
                .lastEventSequence(run.getLastEventSequence())
                .finalAnswer(run.getFinalAnswer())
                .artifactsJson(run.getArtifactsJson())
                .citationsJson(run.getCitationsJson())
                .costUsageJson(run.getCostUsageJson())
                .approvalReason(run.getApprovalReason())
                .replayRecovered(run.getReplayRecovered())
                .totalSteps(run.getTotalSteps())
                .totalLatencyMs(run.getTotalLatencyMs())
                .errorMessage(run.getErrorMessage())
                .createdAt(run.getCreatedAt())
                .lastCheckpointAt(run.getLastCheckpointAt())
                .finishedAt(run.getFinishedAt())
                .steps(steps)
                .build();
    }

    @Transactional(readOnly = true)
    public List<AgentRunStepResponse> listSteps(Long userId, Long runId) {
        getRequiredRun(userId, runId);
        return agentRunStepRepository.findByRunIdOrderByStepNoAsc(runId).stream().map(this::toStepResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<AgentRunEventResponse> listEvents(Long userId, Long runId) {
        getRequiredRun(userId, runId);
        return agentRunEventService.listEvents(runId);
    }

    @Transactional(readOnly = true)
    public SseEmitter subscribeEvents(Long userId, Long runId) {
        getRequiredRun(userId, runId);
        return agentRunEventService.subscribe(userId, runId);
    }

    @Transactional(readOnly = true)
    public AgentGraphResponse getGraph(Long userId, Long runId) {
        AgentRun run = getRequiredRun(userId, runId);
        return agentGraphService.buildGraph(run, agentRunStepRepository.findByRunIdOrderByStepNoAsc(runId));
    }

    private AgentRun getRequiredRun(Long userId, Long runId) {
        return agentRunRepository.findByIdAndUserId(runId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RUN_NOT_FOUND));
    }

    private AgentRunStepResponse toStepResponse(AgentRunStep step) {
        return AgentRunStepResponse.builder()
                .id(step.getId())
                .stepNo(step.getStepNo())
                .stepType(step.getStepType())
                .nodeId(step.getNodeId())
                .nodeLabel(step.getNodeLabel())
                .toolName(step.getToolName())
                .eventSequence(step.getEventSequence())
                .attemptNo(step.getAttemptNo())
                .parentStepId(step.getParentStepId())
                .skillName(step.getSkillName())
                .skillType(step.getSkillType())
                .riskLevel(step.getRiskLevel())
                .approvalPolicy(step.getApprovalPolicy())
                .approvalReason(step.getApprovalReason())
                .retryReason(step.getRetryReason())
                .inputJson(step.getInputJson())
                .outputJson(step.getOutputJson())
                .stateBeforeJson(step.getStateBeforeJson())
                .stateAfterJson(step.getStateAfterJson())
                .observationJson(step.getObservationJson())
                .costUsageJson(step.getCostUsageJson())
                .latencyMs(step.getLatencyMs())
                .modelName(step.getModelName())
                .promptVersion(step.getPromptVersion())
                .success(step.getSuccess())
                .errorMessage(step.getErrorMessage())
                .createdAt(step.getCreatedAt())
                .build();
    }

    private void markRunFailed(AgentRun run, String errorMessage) {
        run.setStatus("FAILED");
        run.setErrorMessage(errorMessage);
        run.setFinishedAt(LocalDateTime.now());
        run.setRequiresHuman(false);
        run.setResumeToken(null);
        if (run.getCreatedAt() != null) {
            run.setTotalLatencyMs(Math.max(0L, Duration.between(run.getCreatedAt(), LocalDateTime.now()).toMillis()));
        }
        agentRunRepository.save(run);
    }

    private String normalizeOrDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim().toUpperCase();
    }
}
