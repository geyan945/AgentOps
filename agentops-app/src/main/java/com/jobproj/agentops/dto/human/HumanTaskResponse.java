package com.jobproj.agentops.dto.human;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HumanTaskResponse {

    private Long id;
    private Long runId;
    private Long sessionId;
    private String taskType;
    private String title;
    private String currentNode;
    private String reason;
    private String requestJson;
    private String responseJson;
    private String status;
    private LocalDateTime decidedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
