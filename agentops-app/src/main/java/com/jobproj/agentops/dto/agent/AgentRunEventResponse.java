package com.jobproj.agentops.dto.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentRunEventResponse {

    private Long id;
    private Long runId;
    private Integer eventSequence;
    private String eventType;
    private Long stepId;
    private String nodeId;
    private String status;
    private String payloadJson;
    private LocalDateTime createdAt;
}
