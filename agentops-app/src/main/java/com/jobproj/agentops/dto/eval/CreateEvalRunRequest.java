package com.jobproj.agentops.dto.eval;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class CreateEvalRunRequest {

    @NotNull(message = "datasetId 不能为空")
    private Long datasetId;
}