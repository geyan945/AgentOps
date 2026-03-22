package com.jobproj.agentops.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobproj.agentops.common.BusinessException;
import com.jobproj.agentops.common.ErrorCode;
import com.jobproj.agentops.dto.agent.AgentRunEventResponse;
import com.jobproj.agentops.dto.runtime.RuntimeStatusCallbackRequest;
import com.jobproj.agentops.dto.runtime.RuntimeStepCallbackRequest;
import com.jobproj.agentops.entity.AgentRun;
import com.jobproj.agentops.entity.AgentRunEvent;
import com.jobproj.agentops.entity.AgentRunStep;
import com.jobproj.agentops.repository.AgentRunEventRepository;
import com.jobproj.agentops.repository.AgentRunRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
@RequiredArgsConstructor
public class AgentRunEventService {

    private final AgentRunRepository agentRunRepository;
    private final AgentRunEventRepository agentRunEventRepository;
    private final ObjectMapper objectMapper;

    private final Map<Long, CopyOnWriteArrayList<SseEmitter>> emitterStore = new ConcurrentHashMap<>();

    public SseEmitter subscribe(Long userId, Long runId) {
        agentRunRepository.findByIdAndUserId(runId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RUN_NOT_FOUND));
        SseEmitter emitter = new SseEmitter(30L * 60L * 1000L);
        emitterStore.computeIfAbsent(runId, key -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> removeEmitter(runId, emitter));
        emitter.onTimeout(() -> removeEmitter(runId, emitter));
        emitter.onError(ex -> removeEmitter(runId, emitter));
        try {
            emitter.send(SseEmitter.event().name("connected").data(Map.of("runId", runId)));
            for (AgentRunEventResponse event : listEvents(runId)) {
                emitter.send(SseEmitter.event().name(event.getEventType()).data(event));
            }
        } catch (IOException ex) {
            removeEmitter(runId, emitter);
            throw new BusinessException(ErrorCode.RUNTIME_UNAVAILABLE, "建立事件流失败");
        }
        return emitter;
    }

    public List<AgentRunEventResponse> listEvents(Long runId) {
        return agentRunEventRepository.findByRunIdOrderByEventSequenceAsc(runId).stream()
                .map(this::toResponse)
                .toList();
    }

    public int nextEventSequence(Long runId) {
        return (int) agentRunEventRepository.countByRunId(runId) + 1;
    }

    public void publishStepEvent(AgentRun run, AgentRunStep step, RuntimeStepCallbackRequest request) {
        int sequence = request.getEventSequence() == null ? nextEventSequence(run.getId()) : request.getEventSequence();
        AgentRunEvent event = new AgentRunEvent();
        event.setRunId(run.getId());
        event.setEventSequence(sequence);
        event.setEventType("STEP");
        event.setStepId(step.getId());
        event.setNodeId(step.getNodeId());
        event.setStatus(run.getStatus());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("stepNo", step.getStepNo());
        payload.put("stepType", step.getStepType());
        payload.put("nodeId", step.getNodeId());
        payload.put("nodeLabel", step.getNodeLabel());
        payload.put("toolName", step.getToolName());
        payload.put("skillName", step.getSkillName());
        payload.put("skillType", step.getSkillType());
        payload.put("riskLevel", step.getRiskLevel());
        payload.put("approvalPolicy", step.getApprovalPolicy());
        payload.put("approvalReason", step.getApprovalReason());
        payload.put("retryReason", step.getRetryReason());
        payload.put("observation", request.getObservation());
        payload.put("costUsage", request.getCostUsage());
        payload.put("latencyMs", step.getLatencyMs());
        event.setPayloadJson(writeJson(payload));
        AgentRunEvent saved = agentRunEventRepository.save(event);
        run.setLastEventSequence(sequence);
        agentRunRepository.save(run);
        broadcast(saved);
    }

    public void publishStatusEvent(AgentRun run, RuntimeStatusCallbackRequest request) {
        int sequence = request.getEventSequence() == null ? nextEventSequence(run.getId()) : request.getEventSequence();
        AgentRunEvent event = new AgentRunEvent();
        event.setRunId(run.getId());
        event.setEventSequence(sequence);
        event.setEventType("STATUS");
        event.setNodeId(request.getCurrentNode());
        event.setStatus(request.getStatus());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", request.getStatus());
        payload.put("currentNode", request.getCurrentNode());
        payload.put("graphName", request.getGraphName());
        payload.put("graphVersion", request.getGraphVersion());
        payload.put("orchestrationMode", request.getOrchestrationMode());
        payload.put("requiresHuman", request.getRequiresHuman());
        payload.put("checkpointVersion", request.getCheckpointVersion());
        payload.put("resumeTokenPresent", request.getResumeToken() != null);
        payload.put("approvalReason", request.getApprovalReason());
        payload.put("replayRecovered", request.getReplayRecovered());
        payload.put("costUsage", request.getCostUsage());
        event.setPayloadJson(writeJson(payload));
        AgentRunEvent saved = agentRunEventRepository.save(event);
        run.setLastEventSequence(sequence);
        agentRunRepository.save(run);
        broadcast(saved);
    }

    private void broadcast(AgentRunEvent event) {
        List<SseEmitter> emitters = emitterStore.get(event.getRunId());
        if (emitters == null || emitters.isEmpty()) {
            return;
        }
        AgentRunEventResponse payload = toResponse(event);
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name(event.getEventType()).data(payload));
            } catch (IOException ex) {
                removeEmitter(event.getRunId(), emitter);
            }
        }
    }

    private void removeEmitter(Long runId, SseEmitter emitter) {
        List<SseEmitter> emitters = emitterStore.get(runId);
        if (emitters == null) {
            return;
        }
        emitters.remove(emitter);
        if (emitters.isEmpty()) {
            emitterStore.remove(runId);
        }
    }

    private AgentRunEventResponse toResponse(AgentRunEvent event) {
        return AgentRunEventResponse.builder()
                .id(event.getId())
                .runId(event.getRunId())
                .eventSequence(event.getEventSequence())
                .eventType(event.getEventType())
                .stepId(event.getStepId())
                .nodeId(event.getNodeId())
                .status(event.getStatus())
                .payloadJson(event.getPayloadJson())
                .createdAt(event.getCreatedAt())
                .build();
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return String.valueOf(value);
        }
    }
}
