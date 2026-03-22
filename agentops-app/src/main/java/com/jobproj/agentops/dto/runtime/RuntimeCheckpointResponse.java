package com.jobproj.agentops.dto.runtime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuntimeCheckpointResponse {

    private Long runId;
    private Long sessionId;
    private String status;
    private String currentNode;
    private String orchestrationMode;
    private Integer checkpointVersion;
    private String resumeToken;
    private Boolean requiresHuman;
    private Long humanTaskId;
    private String resumeAfterNode;
    private Integer eventSequence;
    private Integer loopCount;
    private Integer toolLoopCount;
    private Integer reviewCount;
    private Map<String, Object> state;
    private String lastError;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
