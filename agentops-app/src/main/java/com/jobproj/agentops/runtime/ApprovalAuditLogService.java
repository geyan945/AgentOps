package com.jobproj.agentops.runtime;

import com.jobproj.agentops.entity.AgentHumanTask;
import com.jobproj.agentops.entity.ApprovalAuditLog;
import com.jobproj.agentops.repository.ApprovalAuditLogRepository;
import com.jobproj.agentops.web.RequestIdHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ApprovalAuditLogService {

    private final ApprovalAuditLogRepository approvalAuditLogRepository;

    public void logDecision(AgentHumanTask task, Long decidedBy, String decision, String comment) {
        ApprovalAuditLog log = new ApprovalAuditLog();
        log.setTenantId(task.getTenantId());
        log.setUserId(decidedBy);
        log.setRunId(task.getRunId());
        log.setHumanTaskId(task.getId());
        log.setRequestId(RequestIdHolder.currentOrGenerate());
        log.setDecision(decision == null ? "UNKNOWN" : decision.toUpperCase());
        log.setComment(comment);
        approvalAuditLogRepository.save(log);
    }
}
