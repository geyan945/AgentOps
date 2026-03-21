package com.jobproj.agentops.dto.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentGraphNodeResponse {

    private String id;
    private String label;
    private String status;
    private boolean current;
}
