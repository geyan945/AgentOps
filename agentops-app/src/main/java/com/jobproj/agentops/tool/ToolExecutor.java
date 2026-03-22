package com.jobproj.agentops.tool;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public interface ToolExecutor {

    String getName();

    String getDescription();

    List<String> getArgumentNames();

    default String getRiskLevel() {
        return "LOW";
    }

    default String getApprovalPolicy() {
        return "NONE";
    }

    default boolean isIdempotent() {
        return true;
    }

    default int getTimeoutBudgetMs() {
        return 3_000;
    }

    default String getRetryPolicy() {
        return "SAFE_RETRY";
    }

    default boolean isAuditRequired() {
        return false;
    }

    ToolResult execute(JsonNode arguments, ToolContext context);
}
