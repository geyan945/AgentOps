package com.jobproj.agentops.runtime;

import com.jobproj.agentops.dto.agent.AgentGraphEdgeResponse;
import com.jobproj.agentops.dto.agent.AgentGraphNodeResponse;
import com.jobproj.agentops.dto.agent.AgentGraphResponse;
import com.jobproj.agentops.entity.AgentRun;
import com.jobproj.agentops.entity.AgentRunStep;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AgentGraphService {

    private static final List<String> NODE_ORDER = List.of(
            "intake_guardrail",
            "load_memory",
            "supervisor_plan",
            "knowledge_researcher",
            "data_analyst",
            "evidence_reviewer",
            "human_approval",
            "finalize"
    );

    public AgentGraphResponse buildGraph(AgentRun run, List<AgentRunStep> steps) {
        Set<String> completedNodeIds = steps.stream()
                .map(AgentRunStep::getNodeId)
                .filter(nodeId -> nodeId != null && !nodeId.isBlank())
                .collect(Collectors.toSet());
        Map<String, String> labels = Map.of(
                "intake_guardrail", isTeamGraph(run) ? "Run Intake" : "Intake Guardrail",
                "load_memory", isTeamGraph(run) ? "Memory Loader" : "Load Memory",
                "supervisor_plan", isTeamGraph(run) ? "Planner Agent" : "Supervisor Plan",
                "knowledge_researcher", isTeamGraph(run) ? "Knowledge Skill" : "Knowledge Researcher",
                "data_analyst", isTeamGraph(run) ? "Data Skill" : "Data Analyst",
                "evidence_reviewer", isTeamGraph(run) ? "Review Skill" : "Evidence Reviewer",
                "human_approval", "Human Approval",
                "finalize", isTeamGraph(run) ? "Finalize Skill" : "Finalize"
        );
        List<AgentGraphNodeResponse> nodes = NODE_ORDER.stream()
                .map(nodeId -> AgentGraphNodeResponse.builder()
                        .id(nodeId)
                        .label(labels.getOrDefault(nodeId, nodeId))
                        .status(resolveNodeStatus(nodeId, run, completedNodeIds))
                        .current(nodeId.equals(run.getCurrentNode()))
                        .build())
                .toList();
        List<AgentGraphEdgeResponse> edges = List.of(
                edge("intake_guardrail", "load_memory", "ok"),
                edge("load_memory", "supervisor_plan", "context"),
                edge("supervisor_plan", "knowledge_researcher", "kb"),
                edge("supervisor_plan", "data_analyst", "sql"),
                edge("knowledge_researcher", "evidence_reviewer", "review"),
                edge("data_analyst", "evidence_reviewer", "review"),
                edge("evidence_reviewer", "supervisor_plan", "replan"),
                edge("evidence_reviewer", "human_approval", "need_human"),
                edge("human_approval", "supervisor_plan", "resume"),
                edge("evidence_reviewer", "finalize", "approve")
        );
        return AgentGraphResponse.builder()
                .runId(run.getId())
                .graphName(run.getGraphName())
                .graphVersion(run.getGraphVersion())
                .orchestrationMode(run.getOrchestrationMode())
                .currentNode(run.getCurrentNode())
                .status(run.getStatus())
                .nodes(nodes)
                .edges(edges)
                .build();
    }

    private AgentGraphEdgeResponse edge(String source, String target, String label) {
        return AgentGraphEdgeResponse.builder().source(source).target(target).label(label).build();
    }

    private String resolveNodeStatus(String nodeId, AgentRun run, Set<String> completedNodeIds) {
        if (nodeId.equals(run.getCurrentNode())) {
            return "RUNNING";
        }
        if (completedNodeIds.contains(nodeId)) {
            return "COMPLETED";
        }
        if ("FAILED".equalsIgnoreCase(run.getStatus())) {
            return "FAILED";
        }
        return "PENDING";
    }

    private boolean isTeamGraph(AgentRun run) {
        return "TEAM_GRAPH".equalsIgnoreCase(run.getOrchestrationMode());
    }
}
