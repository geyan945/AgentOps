package com.jobproj.agentops.dto.runtime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuntimeResumeRunRequest {

    private Long runId;
    private Long tenantId;
    private String decision;
    private String comment;
    private String resumeToken;
    private Integer checkpointVersion;
    private boolean waitForCompletion;
}
