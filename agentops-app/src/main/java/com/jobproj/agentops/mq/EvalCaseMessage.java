package com.jobproj.agentops.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EvalCaseMessage {

    private Long evalRunId;
    private Long datasetId;
    private Long caseId;
    private Long userId;
}