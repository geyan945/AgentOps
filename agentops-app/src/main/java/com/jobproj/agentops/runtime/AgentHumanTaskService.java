package com.jobproj.agentops.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobproj.agentops.common.BusinessException;
import com.jobproj.agentops.common.ErrorCode;
import com.jobproj.agentops.dto.human.HumanTaskResponse;
import com.jobproj.agentops.entity.AgentHumanTask;
import com.jobproj.agentops.repository.AgentHumanTaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AgentHumanTaskService {

    private final AgentHumanTaskRepository humanTaskRepository;
    private final ApprovalAuditLogService approvalAuditLogService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<HumanTaskResponse> listTasks(Long userId) {
        return humanTaskRepository.findByUserIdOrderByUpdatedAtDesc(userId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public AgentHumanTask getRequiredTask(Long userId, Long taskId) {
        return humanTaskRepository.findByIdAndUserId(taskId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.HUMAN_TASK_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public AgentHumanTask getPendingTaskByRunId(Long runId) {
        return humanTaskRepository.findFirstByRunIdAndStatusOrderByIdDesc(runId, "PENDING")
                .orElseThrow(() -> new BusinessException(ErrorCode.HUMAN_TASK_NOT_FOUND, "pending human task not found"));
    }

    @Transactional
    public AgentHumanTask createPendingTask(Long runId, Long sessionId, Long userId, Long tenantId, String currentNode, String taskType, String title, String reason, Object requestPayload) {
        AgentHumanTask task = humanTaskRepository.findFirstByRunIdAndStatusOrderByIdDesc(runId, "PENDING").orElseGet(AgentHumanTask::new);
        task.setRunId(runId);
        task.setSessionId(sessionId);
        task.setUserId(userId);
        task.setTenantId(tenantId);
        task.setCurrentNode(currentNode);
        task.setTaskType(taskType);
        task.setTitle(title);
        task.setReason(reason);
        task.setRequestJson(writeJson(requestPayload));
        task.setStatus("PENDING");
        task.setResponseJson(null);
        task.setDecidedBy(null);
        task.setDecidedAt(null);
        return humanTaskRepository.save(task);
    }

    @Transactional
    public AgentHumanTask decideTask(AgentHumanTask task, Long decidedBy, String decision, String comment) {
        task.setStatus("APPROVE".equalsIgnoreCase(decision) || "APPROVED".equalsIgnoreCase(decision) ? "APPROVED" : "REJECTED");
        task.setResponseJson(writeJson(comment));
        task.setDecidedBy(decidedBy);
        task.setDecidedAt(LocalDateTime.now());
        AgentHumanTask saved = humanTaskRepository.save(task);
        approvalAuditLogService.logDecision(saved, decidedBy, decision, comment);
        return saved;
    }

    public HumanTaskResponse toResponse(AgentHumanTask task) {
        return HumanTaskResponse.builder()
                .id(task.getId())
                .runId(task.getRunId())
                .sessionId(task.getSessionId())
                .taskType(task.getTaskType())
                .title(task.getTitle())
                .currentNode(task.getCurrentNode())
                .reason(task.getReason())
                .requestJson(task.getRequestJson())
                .responseJson(task.getResponseJson())
                .status(task.getStatus())
                .decidedAt(task.getDecidedAt())
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .build();
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
