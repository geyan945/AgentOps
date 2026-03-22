package com.jobproj.agentops.dto.eval;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class EvalCaseResponse {

    private Long id;
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
