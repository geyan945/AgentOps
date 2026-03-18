package com.jobproj.agentops.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlannerResult {

    private String decision;
    private String reason;
    @Builder.Default
    private List<ToolCallPlan> toolCalls = new ArrayList<>();
}