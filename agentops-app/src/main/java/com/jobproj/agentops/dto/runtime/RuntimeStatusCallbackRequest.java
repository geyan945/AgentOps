package com.jobproj.agentops.dto.runtime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuntimeStatusCallbackRequest {

    private String status;
    private String currentNode;
    private String graphName;
    private String graphVersion;
    private Boolean requiresHuman;
    private String resumeToken;
    private Integer checkpointVersion;
    private String finalAnswer;
    private List<Map<String, Object>> citations;
    private List<Map<String, Object>> artifacts;
    private List<Map<String, Object>> memoryFacts;
    private String errorMessage;
}
