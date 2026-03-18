package com.jobproj.agentops.dto.session;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class SessionSummaryResponse {

    private Long sessionId;
    private int messageCount;
    private String summary;
    private boolean cacheHit;
}