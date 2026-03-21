package com.jobproj.agentops.dto.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentGraphResponse {

    private Long runId;
    private String graphName;
    private String graphVersion;
    private String currentNode;
    private String status;
    private List<AgentGraphNodeResponse> nodes;
    private List<AgentGraphEdgeResponse> edges;
}
