package com.jobproj.agentops.dto.runtime;

import com.jobproj.agentops.dto.agent.ToolInfoResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuntimeContextResponse {

    private Long runId;
    private Long sessionId;
    private Long userId;
    private String userInput;
    private String status;
    private String conversationSummary;
    private List<RuntimeMessageResponse> messages;
    private List<RuntimeMemoryFactResponse> memoryFacts;
    private List<ToolInfoResponse> tools;
}
