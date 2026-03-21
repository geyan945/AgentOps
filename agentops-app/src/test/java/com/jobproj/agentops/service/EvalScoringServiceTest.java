package com.jobproj.agentops.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobproj.agentops.agent.EvalCaseExecutionResult;
import com.jobproj.agentops.entity.EvalCase;
import com.jobproj.agentops.integration.GeminiClient;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EvalScoringServiceTest {

    @Test
    void shouldProduceCompositeScores() throws Exception {
        EvalScoringService scoringService = new EvalScoringService(new ObjectMapper(), new GeminiClient());
        ReflectionTestUtils.setField(scoringService, "passScore", 0.6D);

        EvalCase evalCase = new EvalCase();
        evalCase.setExpectedTool("kb_search");
        evalCase.setExpectedKeywordsJson(new ObjectMapper().writeValueAsString(List.of("AgentOps", "trace")));
        evalCase.setExpectedCitationMin(1);

        EvalCaseExecutionResult executionResult = EvalCaseExecutionResult.builder()
                .actualTool("kb_search")
                .answerText("AgentOps 提供完整 trace 与运行回放。")
                .citationCount(1)
                .route("knowledge")
                .approvalTriggered(false)
                .approvalDecision("NONE")
                .nodePath(List.of("intake_guardrail", "load_memory", "supervisor_plan", "knowledge_researcher", "evidence_reviewer", "finalize"))
                .artifactTypes(List.of())
                .success(true)
                .build();

        EvalScoringService.ScoreOutcome outcome = scoringService.score(evalCase, executionResult);

        assertTrue(outcome.getRouteScore() > 0.9D);
        assertTrue(outcome.getGroundingScore() > 0.9D);
        assertTrue(outcome.getCitationScore() > 0.9D);
        assertTrue(outcome.getFinalScore() > 0.9D);
        assertTrue(outcome.isSuccess());
    }
}
