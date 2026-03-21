package com.jobproj.agentops.dto.human;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class HumanTaskDecisionRequest {

    @NotBlank(message = "decision 不能为空")
    private String decision;

    private String comment;
}
