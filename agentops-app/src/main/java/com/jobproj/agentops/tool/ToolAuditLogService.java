package com.jobproj.agentops.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobproj.agentops.entity.ToolAuditLog;
import com.jobproj.agentops.repository.ToolAuditLogRepository;
import com.jobproj.agentops.web.RequestIdHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ToolAuditLogService {

    private final ToolAuditLogRepository toolAuditLogRepository;
    private final ObjectMapper objectMapper;

    public void log(ToolContext context, String toolName, JsonNode arguments, String decision, boolean success, String responseSummary) {
        ToolAuditLog log = new ToolAuditLog();
        log.setTenantId(context.getTenantId());
        log.setUserId(context.getUserId());
        log.setSessionId(context.getSessionId());
        log.setRunId(context.getRunId());
        log.setToolName(toolName);
        log.setRequestId(context.getRequestId() == null ? RequestIdHolder.currentOrGenerate() : context.getRequestId());
        log.setSource(context.getSource() == null ? "UNKNOWN" : context.getSource());
        log.setArgumentsSummary(summarize(arguments));
        log.setDecision(decision);
        log.setSuccess(success);
        log.setResponseSummary(trim(responseSummary));
        toolAuditLogRepository.save(log);
    }

    private String summarize(JsonNode arguments) {
        if (arguments == null || arguments.isNull()) {
            return "{}";
        }
        try {
            return trim(objectMapper.writeValueAsString(arguments));
        } catch (Exception ex) {
            return trim(String.valueOf(arguments));
        }
    }

    private String trim(String value) {
        if (value == null) {
            return null;
        }
        return value.length() > 1000 ? value.substring(0, 1000) + "..." : value;
    }
}
