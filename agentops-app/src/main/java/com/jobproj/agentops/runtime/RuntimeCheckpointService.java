package com.jobproj.agentops.runtime;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobproj.agentops.common.BusinessException;
import com.jobproj.agentops.common.ErrorCode;
import com.jobproj.agentops.dto.runtime.RuntimeCheckpointRequest;
import com.jobproj.agentops.dto.runtime.RuntimeCheckpointResponse;
import com.jobproj.agentops.entity.AgentRun;
import com.jobproj.agentops.entity.AgentRuntimeCheckpoint;
import com.jobproj.agentops.repository.AgentRunRepository;
import com.jobproj.agentops.repository.AgentRuntimeCheckpointRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RuntimeCheckpointService {

    private final AgentRuntimeCheckpointRepository checkpointRepository;
    private final AgentRunRepository agentRunRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public RuntimeCheckpointResponse getCheckpoint(Long runId) {
        AgentRuntimeCheckpoint checkpoint = checkpointRepository.findByRunId(runId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RUNTIME_CHECKPOINT_NOT_FOUND));
        return toResponse(checkpoint);
    }

    @Transactional
    public RuntimeCheckpointResponse saveCheckpoint(Long runId, RuntimeCheckpointRequest request) {
        AgentRun run = agentRunRepository.findById(runId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RUN_NOT_FOUND));
        AgentRuntimeCheckpoint checkpoint = checkpointRepository.findByRunId(runId).orElseGet(AgentRuntimeCheckpoint::new);
        checkpoint.setRunId(runId);
        checkpoint.setSessionId(request.getSessionId() == null ? run.getSessionId() : request.getSessionId());
        checkpoint.setStatus(request.getStatus());
        checkpoint.setCurrentNode(request.getCurrentNode());
        checkpoint.setOrchestrationMode(request.getOrchestrationMode());
        checkpoint.setCheckpointVersion(nextVersion(checkpoint.getCheckpointVersion()));
        checkpoint.setResumeToken(request.getResumeToken());
        checkpoint.setRequiresHuman(Boolean.TRUE.equals(request.getRequiresHuman()));
        checkpoint.setHumanTaskId(request.getHumanTaskId());
        checkpoint.setResumeAfterNode(request.getResumeAfterNode());
        checkpoint.setEventSequence(request.getEventSequence());
        checkpoint.setLoopCount(defaultInt(request.getLoopCount()));
        checkpoint.setToolLoopCount(defaultInt(request.getToolLoopCount()));
        checkpoint.setReviewCount(defaultInt(request.getReviewCount()));
        checkpoint.setStateJson(writeJson(request.getState()));
        checkpoint.setLastError(request.getLastError());
        AgentRuntimeCheckpoint saved = checkpointRepository.save(checkpoint);

        run.setCheckpointVersion(saved.getCheckpointVersion());
        run.setLastCheckpointAt(saved.getUpdatedAt() == null ? LocalDateTime.now() : saved.getUpdatedAt());
        if (request.getCurrentNode() != null) {
            run.setCurrentNode(request.getCurrentNode());
        }
        if (request.getOrchestrationMode() != null) {
            run.setOrchestrationMode(request.getOrchestrationMode());
        }
        if (request.getStatus() != null) {
            run.setStatus(request.getStatus());
        }
        if (request.getEventSequence() != null) {
            run.setLastEventSequence(request.getEventSequence());
        }
        run.setRequiresHuman(Boolean.TRUE.equals(request.getRequiresHuman()));
        if (request.getResumeToken() != null || !Boolean.TRUE.equals(request.getRequiresHuman())) {
            run.setResumeToken(request.getResumeToken());
        }
        agentRunRepository.save(run);
        return toResponse(saved);
    }

    @Transactional
    public void deleteCheckpoint(Long runId) {
        checkpointRepository.deleteByRunId(runId);
        agentRunRepository.findById(runId).ifPresent(run -> {
            run.setResumeToken(null);
            run.setRequiresHuman(false);
            agentRunRepository.save(run);
        });
    }

    private RuntimeCheckpointResponse toResponse(AgentRuntimeCheckpoint checkpoint) {
        return RuntimeCheckpointResponse.builder()
                .runId(checkpoint.getRunId())
                .sessionId(checkpoint.getSessionId())
                .status(checkpoint.getStatus())
                .currentNode(checkpoint.getCurrentNode())
                .orchestrationMode(checkpoint.getOrchestrationMode())
                .checkpointVersion(checkpoint.getCheckpointVersion())
                .resumeToken(checkpoint.getResumeToken())
                .requiresHuman(checkpoint.getRequiresHuman())
                .humanTaskId(checkpoint.getHumanTaskId())
                .resumeAfterNode(checkpoint.getResumeAfterNode())
                .eventSequence(checkpoint.getEventSequence())
                .loopCount(checkpoint.getLoopCount())
                .toolLoopCount(checkpoint.getToolLoopCount())
                .reviewCount(checkpoint.getReviewCount())
                .state(readJson(checkpoint.getStateJson()))
                .lastError(checkpoint.getLastError())
                .createdAt(checkpoint.getCreatedAt())
                .updatedAt(checkpoint.getUpdatedAt())
                .build();
    }

    private Integer nextVersion(Integer currentVersion) {
        return currentVersion == null || currentVersion < 1 ? 1 : currentVersion + 1;
    }

    private int defaultInt(Integer value) {
        return value == null ? 0 : value;
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

    private Map<String, Object> readJson(String value) {
        if (value == null || value.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(value, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ex) {
            return Map.of("raw", value);
        }
    }
}
