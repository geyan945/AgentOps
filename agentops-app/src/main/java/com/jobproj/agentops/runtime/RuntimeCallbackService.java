package com.jobproj.agentops.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobproj.agentops.common.BusinessException;
import com.jobproj.agentops.common.ErrorCode;
import com.jobproj.agentops.dto.agent.AgentRunStepResponse;
import com.jobproj.agentops.dto.runtime.RuntimeContextResponse;
import com.jobproj.agentops.dto.runtime.RuntimeMessageResponse;
import com.jobproj.agentops.dto.runtime.RuntimeStatusCallbackRequest;
import com.jobproj.agentops.dto.runtime.RuntimeStepCallbackRequest;
import com.jobproj.agentops.entity.AgentMessage;
import com.jobproj.agentops.entity.AgentRun;
import com.jobproj.agentops.entity.AgentRunStep;
import com.jobproj.agentops.entity.AgentSession;
import com.jobproj.agentops.repository.AgentMessageRepository;
import com.jobproj.agentops.repository.AgentRunRepository;
import com.jobproj.agentops.repository.AgentRunStepRepository;
import com.jobproj.agentops.repository.SysUserRepository;
import com.jobproj.agentops.service.SessionService;
import com.jobproj.agentops.tool.ToolContext;
import com.jobproj.agentops.tool.ToolGovernanceService;
import com.jobproj.agentops.web.RequestIdHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RuntimeCallbackService {

    private final AgentRunRepository agentRunRepository;
    private final AgentRunStepRepository agentRunStepRepository;
    private final AgentMessageRepository agentMessageRepository;
    private final SysUserRepository sysUserRepository;
    private final SessionService sessionService;
    private final ToolGovernanceService toolGovernanceService;
    private final AgentMemoryService agentMemoryService;
    private final AgentHumanTaskService humanTaskService;
    private final AgentRunEventService agentRunEventService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public RuntimeContextResponse buildContext(Long sessionId) {
        AgentSession session = sessionService.getRequiredSessionById(sessionId);
        AgentRun run = agentRunRepository.findBySessionIdOrderByIdDesc(sessionId).stream().findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.RUN_NOT_FOUND, "session has no runtime record"));
        var user = sysUserRepository.findById(session.getUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        List<RuntimeMessageResponse> messages = agentMessageRepository.findBySessionIdOrderByIdAsc(sessionId).stream()
                .map(this::toRuntimeMessage)
                .toList();
        return RuntimeContextResponse.builder()
                .runId(run.getId())
                .sessionId(sessionId)
                .userId(session.getUserId())
                .tenantId(session.getTenantId())
                .role(user.getRole())
                .userInput(run.getUserInput())
                .status(run.getStatus())
                .conversationSummary(sessionService.getSessionSummary(session.getUserId(), sessionId).getSummary())
                .messages(messages)
                .memoryFacts(agentMemoryService.listBySessionId(sessionId))
                .tools(toolGovernanceService.listTools(ToolContext.builder()
                        .userId(session.getUserId())
                        .tenantId(session.getTenantId())
                        .sessionId(sessionId)
                        .runId(run.getId())
                        .role(user.getRole())
                        .source("INTERNAL_RUNTIME")
                        .requestId(RequestIdHolder.current())
                        .build()))
                .build();
    }

    @Transactional
    public AgentRunStepResponse saveStep(Long runId, RuntimeStepCallbackRequest request) {
        AgentRun run = getRequiredRun(runId);
        AgentRunStep step = new AgentRunStep();
        step.setRunId(runId);
        step.setStepNo(request.getStepNo() == null ? (int) agentRunStepRepository.countByRunId(runId) + 1 : request.getStepNo());
        step.setStepType(request.getStepType());
        step.setNodeId(request.getNodeId());
        step.setNodeLabel(request.getNodeLabel());
        step.setToolName(request.getToolName());
        step.setEventSequence(request.getEventSequence() == null ? agentRunEventService.nextEventSequence(runId) : request.getEventSequence());
        step.setAttemptNo(request.getAttemptNo());
        step.setParentStepId(request.getParentStepId());
        step.setSkillName(request.getSkillName());
        step.setSkillType(request.getSkillType());
        step.setRiskLevel(request.getRiskLevel());
        step.setApprovalPolicy(request.getApprovalPolicy());
        step.setApprovalReason(request.getApprovalReason());
        step.setRetryReason(request.getRetryReason());
        step.setInputJson(writeJson(request.getInput()));
        step.setOutputJson(writeJson(request.getOutput()));
        step.setStateBeforeJson(writeJson(request.getStateBefore()));
        step.setStateAfterJson(writeJson(request.getStateAfter()));
        step.setObservationJson(writeJson(request.getObservation()));
        step.setCostUsageJson(writeJson(request.getCostUsage()));
        step.setLatencyMs(request.getLatencyMs());
        step.setModelName(request.getModelName());
        step.setPromptVersion(request.getPromptVersion());
        step.setSuccess(Boolean.TRUE.equals(request.getSuccess()));
        step.setErrorMessage(request.getErrorMessage());
        AgentRunStep saved = agentRunStepRepository.save(step);
        run.setTotalSteps((int) agentRunStepRepository.countByRunId(runId));
        run.setCurrentNode(request.getNodeId());
        run.setLastEventSequence(saved.getEventSequence());
        agentRunRepository.save(run);
        agentRunEventService.publishStepEvent(run, saved, request);

        if ("HUMAN_TASK".equalsIgnoreCase(request.getStepType())) {
            humanTaskService.createPendingTask(
                    run.getId(),
                    run.getSessionId(),
                    run.getUserId(),
                    run.getTenantId(),
                    request.getNodeId(),
                    "APPROVAL",
                    "需要人工确认",
                    request.getErrorMessage() == null ? "运行进入人工审批" : request.getErrorMessage(),
                    request.getObservation()
            );
        }
        return AgentRunStepResponse.builder()
                .id(saved.getId())
                .stepNo(saved.getStepNo())
                .stepType(saved.getStepType())
                .nodeId(saved.getNodeId())
                .nodeLabel(saved.getNodeLabel())
                .toolName(saved.getToolName())
                .eventSequence(saved.getEventSequence())
                .attemptNo(saved.getAttemptNo())
                .parentStepId(saved.getParentStepId())
                .skillName(saved.getSkillName())
                .skillType(saved.getSkillType())
                .riskLevel(saved.getRiskLevel())
                .approvalPolicy(saved.getApprovalPolicy())
                .approvalReason(saved.getApprovalReason())
                .retryReason(saved.getRetryReason())
                .inputJson(saved.getInputJson())
                .outputJson(saved.getOutputJson())
                .stateBeforeJson(saved.getStateBeforeJson())
                .stateAfterJson(saved.getStateAfterJson())
                .observationJson(saved.getObservationJson())
                .costUsageJson(saved.getCostUsageJson())
                .latencyMs(saved.getLatencyMs())
                .modelName(saved.getModelName())
                .promptVersion(saved.getPromptVersion())
                .success(saved.getSuccess())
                .errorMessage(saved.getErrorMessage())
                .createdAt(saved.getCreatedAt())
                .build();
    }

    @Transactional
    public void updateStatus(Long runId, RuntimeStatusCallbackRequest request) {
        AgentRun run = getRequiredRun(runId);
        run.setStatus(request.getStatus());
        run.setCurrentNode(request.getCurrentNode());
        run.setGraphName(request.getGraphName());
        run.setGraphVersion(request.getGraphVersion());
        if (request.getOrchestrationMode() != null) {
            run.setOrchestrationMode(request.getOrchestrationMode());
        }
        run.setRequiresHuman(Boolean.TRUE.equals(request.getRequiresHuman()));
        run.setResumeToken(request.getResumeToken());
        if (request.getCheckpointVersion() != null) {
            run.setCheckpointVersion(request.getCheckpointVersion());
            run.setLastCheckpointAt(LocalDateTime.now());
        }
        if (request.getEventSequence() != null) {
            run.setLastEventSequence(request.getEventSequence());
        }
        run.setFinalAnswer(request.getFinalAnswer());
        run.setArtifactsJson(writeJson(request.getArtifacts()));
        run.setCitationsJson(writeJson(request.getCitations()));
        run.setCostUsageJson(writeJson(request.getCostUsage()));
        run.setApprovalReason(request.getApprovalReason());
        run.setReplayRecovered(Boolean.TRUE.equals(request.getReplayRecovered()));
        run.setErrorMessage(request.getErrorMessage());
        if (isTerminalStatus(request.getStatus())) {
            run.setFinishedAt(LocalDateTime.now());
            run.setTotalSteps((int) agentRunStepRepository.countByRunId(runId));
            if (run.getCreatedAt() != null) {
                run.setTotalLatencyMs(Math.max(0L, java.time.Duration.between(run.getCreatedAt(), LocalDateTime.now()).toMillis()));
            }
            run.setRequiresHuman(false);
            run.setResumeToken(null);
        }
        agentRunRepository.save(run);
        agentRunEventService.publishStatusEvent(run, request);
        if (request.getFinalAnswer() != null && !request.getFinalAnswer().isBlank()) {
            sessionService.saveMessage(run.getSessionId(), "assistant", request.getFinalAnswer(), writeJson(request.getCitations()));
        }
        if (request.getMemoryFacts() != null) {
            agentMemoryService.replaceFacts(run.getTenantId(), run.getUserId(), run.getSessionId(), runId, request.getMemoryFacts());
        }
    }

    private AgentRun getRequiredRun(Long runId) {
        return agentRunRepository.findById(runId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RUN_NOT_FOUND));
    }

    private RuntimeMessageResponse toRuntimeMessage(AgentMessage message) {
        return RuntimeMessageResponse.builder()
                .id(message.getId())
                .role(message.getRole())
                .content(message.getContent())
                .metadataJson(message.getMetadataJson())
                .createdAt(message.getCreatedAt())
                .build();
    }

    private boolean isTerminalStatus(String status) {
        return "SUCCEEDED".equalsIgnoreCase(status)
                || "FAILED".equalsIgnoreCase(status)
                || "CANCELLED".equalsIgnoreCase(status);
    }

    private String writeJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return String.valueOf(value);
        }
    }
}
