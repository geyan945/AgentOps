package com.jobproj.agentops.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobproj.agentops.dto.runtime.RuntimeMemoryFactResponse;
import com.jobproj.agentops.entity.AgentMemoryFact;
import com.jobproj.agentops.repository.AgentMemoryFactRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AgentMemoryService {

    private final AgentMemoryFactRepository memoryFactRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<RuntimeMemoryFactResponse> listBySessionId(Long sessionId) {
        return memoryFactRepository.findBySessionIdOrderByUpdatedAtDesc(sessionId).stream()
                .map(this::toRuntimeResponse)
                .toList();
    }

    @Transactional
    public void replaceFacts(Long userId, Long sessionId, Long sourceRunId, List<Map<String, Object>> facts) {
        memoryFactRepository.deleteBySourceRunId(sourceRunId);
        if (facts == null || facts.isEmpty()) {
            return;
        }
        List<AgentMemoryFact> entities = new ArrayList<>();
        for (Map<String, Object> fact : facts) {
            AgentMemoryFact entity = new AgentMemoryFact();
            entity.setUserId(userId);
            entity.setSessionId(sessionId);
            entity.setSourceRunId(sourceRunId);
            entity.setFactType(String.valueOf(fact.getOrDefault("factType", "NOTE")));
            entity.setFactKey(String.valueOf(fact.getOrDefault("factKey", "")));
            entity.setFactValue(writeJsonOrString(fact.get("factValue")));
            entity.setStatus("ACTIVE");
            entities.add(entity);
        }
        memoryFactRepository.saveAll(entities);
    }

    private RuntimeMemoryFactResponse toRuntimeResponse(AgentMemoryFact fact) {
        return RuntimeMemoryFactResponse.builder()
                .id(fact.getId())
                .factType(fact.getFactType())
                .factKey(fact.getFactKey())
                .factValue(fact.getFactValue())
                .build();
    }

    private String writeJsonOrString(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof String text) {
            return text;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return String.valueOf(value);
        }
    }
}
