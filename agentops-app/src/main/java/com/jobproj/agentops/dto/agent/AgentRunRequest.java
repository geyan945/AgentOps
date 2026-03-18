package com.jobproj.agentops.dto.agent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AgentRunRequest {

    @NotNull(message = "sessionId 不能为空")
    private Long sessionId;

    @NotBlank(message = "message 不能为空")
    private String message;
}