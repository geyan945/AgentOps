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
    private List<String> expectedKeywords;
}