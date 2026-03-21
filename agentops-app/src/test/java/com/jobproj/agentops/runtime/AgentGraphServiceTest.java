package com.jobproj.agentops.runtime;

import com.jobproj.agentops.dto.agent.AgentGraphResponse;
import com.jobproj.agentops.entity.AgentRun;
import com.jobproj.agentops.entity.AgentRunStep;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentGraphServiceTest {

    private final AgentGraphService agentGraphService = new AgentGraphService();

    @Test
    void shouldBuildGraphWithCurrentAndCompletedNodes() {
        AgentRun run = new AgentRun();
        run.setId(101L);
        run.setGraphName("enterprise-copilot");
        run.setGraphVersion("v2");
        run.setStatus("RUNNING");
        run.setCurrentNode("evidence_reviewer");

        AgentRunStep step = new AgentRunStep();
        step.setRunId(101L);
        step.setStepNo(1);
        step.setStepType("GRAPH_NODE");
        step.setNodeId("knowledge_researcher");
        step.setNodeLabel("Knowledge Researcher");

        AgentGraphResponse graph = agentGraphService.buildGraph(run, List.of(step));

        assertEquals(8, graph.getNodes().size());
        assertEquals("evidence_reviewer", graph.getCurrentNode());
        assertTrue(graph.getNodes().stream().anyMatch(node -> "knowledge_researcher".equals(node.getId()) && "COMPLETED".equals(node.getStatus())));
        assertTrue(graph.getNodes().stream().anyMatch(node -> "evidence_reviewer".equals(node.getId()) && node.isCurrent()));
    }
}
