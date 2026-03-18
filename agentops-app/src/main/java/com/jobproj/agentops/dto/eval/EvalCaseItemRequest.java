package com.jobproj.agentops.dto.eval;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class EvalCaseItemRequest {

    @NotBlank(message = "question 不能为空")
    private String question;

    private String expectedTool;

    private List<String> expectedKeywords;
}