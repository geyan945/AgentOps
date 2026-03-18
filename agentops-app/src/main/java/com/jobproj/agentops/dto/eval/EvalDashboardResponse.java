package com.jobproj.agentops.dto.eval;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
public class EvalDashboardResponse {

    private int datasetCount;
    private int runCount;
    private int runningRunCount;
    private int completedRunCount;
    private int failedResultCount;
    private double avgScore;
    private double passRate;
    private long avgLatencyMs;
    private List<EvalFailureSampleResponse> latestFailedSamples;
}