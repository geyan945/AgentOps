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

    private String expectedRoute;

    private List<String> expectedKeywords;

    private List<String> expectedNodePath;

    private String expectedApprovalPolicy;

    private Integer expectedCitationMin;

    private List<String> expectedArtifactTypes;

    private String expectedOrchestrationMode;

    private List<String> expectedSkills;
}
