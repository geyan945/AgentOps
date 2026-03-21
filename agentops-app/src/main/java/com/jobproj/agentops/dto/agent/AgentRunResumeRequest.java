package com.jobproj.agentops.dto.agent;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AgentRunResumeRequest {

    @NotBlank(message = "decision 不能为空")
    private String decision;

    private String comment;

    private String resumeToken;

    private Integer checkpointVersion;
}
