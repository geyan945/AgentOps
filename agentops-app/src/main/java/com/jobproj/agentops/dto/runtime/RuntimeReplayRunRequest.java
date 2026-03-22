package com.jobproj.agentops.dto.runtime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuntimeReplayRunRequest {

    private Long runId;
    private Integer checkpointVersion;
    private boolean waitForCompletion;
}
