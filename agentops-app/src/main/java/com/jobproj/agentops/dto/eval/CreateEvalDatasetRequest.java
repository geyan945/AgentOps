package com.jobproj.agentops.dto.eval;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class CreateEvalDatasetRequest {

    @NotBlank(message = "name 不能为空")
    private String name;

    private String description;

    @Valid
    @NotEmpty(message = "cases 不能为空")
    private List<EvalCaseItemRequest> cases;
}