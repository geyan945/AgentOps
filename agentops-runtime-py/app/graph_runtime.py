from __future__ import annotations

import secrets
import threading
import time
from copy import deepcopy
from typing import Any, Dict, List, Optional, Tuple

from .config import settings
from .java_client import JavaControlPlaneClient
from .llm_service import GeminiLLMService
from .models import (
    AgentState,
    CheckpointPayload,
    RuntimeCommandResponse,
    RuntimeReplayRunRequest,
    RuntimeResumeRunRequest,
    RuntimeStartRunRequest,
    StatusPayload,
    StepPayload,
)
from .planner import build_memory_facts, build_sql_arguments, classify_adaptive_route, classify_route

try:
    from langchain_core.tools import StructuredTool
except Exception:  # pragma: no cover
    StructuredTool = None

try:
    from langgraph.graph import END, START, StateGraph
    LANGGRAPH_AVAILABLE = True
except Exception:  # pragma: no cover
    StateGraph = None
    START = "START"
    END = "END"
    LANGGRAPH_AVAILABLE = False


SKILL_REGISTRY: Dict[str, Dict[str, Any]] = {
    "knowledge_researcher": {
        "skillName": "knowledge_skill",
        "skillType": "retrieval",
        "inputSchema": {"query": "string", "knowledgeBaseId": "number"},
        "outputSchema": {"hits": "array", "citations": "array"},
        "budget": {"maxCalls": 2, "maxLatencyMs": 2500},
        "appliesTo": "企业知识检索和引用式回答",
    },
    "data_analyst": {
        "skillName": "data_skill",
        "skillType": "analytics",
        "inputSchema": {"queryType": "string", "knowledgeBaseId": "number"},
        "outputSchema": {"table": "object", "artifacts": "array"},
        "budget": {"maxCalls": 1, "maxLatencyMs": 4000},
        "appliesTo": "统计、报表和数据分析",
    },
    "evidence_reviewer": {
        "skillName": "review_skill",
        "skillType": "review",
        "inputSchema": {"evidence": "array", "citations": "array"},
        "outputSchema": {"grounded": "boolean", "needsReplan": "boolean"},
        "budget": {"maxCalls": settings.max_replans + 1, "maxLatencyMs": 2000},
        "appliesTo": "证据校验、冲突识别和 bounded failure",
    },
    "finalize": {
        "skillName": "finalize_skill",
        "skillType": "response",
        "inputSchema": {"evidence": "array", "citations": "array"},
        "outputSchema": {"finalAnswer": "string", "boundedFailure": "boolean"},
        "budget": {"maxCalls": 1, "maxLatencyMs": 2500},
        "appliesTo": "最终回答整合和收口",
    },
}

TOOL_GOVERNANCE: Dict[str, Dict[str, Any]] = {
    "kb_search": {
        "riskLevel": "LOW",
        "approvalPolicy": "NONE",
        "idempotent": True,
        "timeoutBudgetMs": 2500,
        "retryPolicy": "LINEAR_BACKOFF",
        "auditRequired": False,
    },
    "doc_fetch": {
        "riskLevel": "LOW",
        "approvalPolicy": "NONE",
        "idempotent": True,
        "timeoutBudgetMs": 2000,
        "retryPolicy": "SAFE_RETRY",
        "auditRequired": False,
    },
    "sql_query": {
        "riskLevel": "HIGH",
        "approvalPolicy": "HUMAN_REVIEW",
        "idempotent": True,
        "timeoutBudgetMs": 4000,
        "retryPolicy": "NO_RETRY",
        "auditRequired": True,
    },
}


class FallbackTool:
    def __init__(self, name: str, callback):
        self.name = name
        self._callback = callback

    def invoke(self, arguments: Dict[str, Any]) -> Dict[str, Any]:
        return self._callback(arguments)


class AgentRuntime:
    def __init__(self) -> None:
        self.client = JavaControlPlaneClient()
        self.llm = GeminiLLMService()
        self._graph = self._build_langgraph()
        self._tools = self._build_tools()

    def health(self) -> Dict[str, Any]:
        return {
            "graph": "enterprise-copilot",
            "langgraphAvailable": LANGGRAPH_AVAILABLE,
            "geminiAvailable": self.llm.gemini_available,
            "checkpointBackend": "java-control-plane",
            "llmMode": self.llm.mode,
            "eventStream": "sse",
            "skills": list({item["skillName"] for item in SKILL_REGISTRY.values()}),
        }

    def start_run(self, request: RuntimeStartRunRequest) -> RuntimeCommandResponse:
        context = self.client.fetch_context(request.sessionId)
        state = self._base_state(request, context)
        self._update_status(state, status="RUNNING", current_node="intake_guardrail")
        self._save_checkpoint(state, status="RUNNING")
        self._run_state_machine(state)
        return RuntimeCommandResponse(
            accepted=True,
            status=state.get("currentStatus", "RUNNING"),
            currentNode=state.get("currentNode", "intake_guardrail"),
            checkpointVersion=state.get("checkpointVersion"),
            resumeToken=state.get("resumeToken"),
            orchestrationMode=state.get("orchestrationMode"),
        )

    def start_run_background(self, request: RuntimeStartRunRequest) -> None:
        threading.Thread(target=self.start_run, args=(request,), daemon=True).start()

    def validate_resume_request(self, request: RuntimeResumeRunRequest) -> Dict[str, Any]:
        checkpoint = self.client.fetch_checkpoint(request.runId)
        if not checkpoint.get("resumeToken") or request.resumeToken != checkpoint.get("resumeToken"):
            raise ValueError("invalid resume token")
        if request.checkpointVersion != checkpoint.get("checkpointVersion"):
            raise ValueError("stale checkpoint version")
        return checkpoint

    def validate_replay_request(self, request: RuntimeReplayRunRequest) -> Dict[str, Any]:
        checkpoint = self.client.fetch_checkpoint(request.runId)
        if checkpoint.get("requiresHuman"):
            raise ValueError("run is waiting for human approval")
        if request.checkpointVersion is not None and request.checkpointVersion != checkpoint.get("checkpointVersion"):
            raise ValueError("stale checkpoint version")
        if (checkpoint.get("status") or "").upper() in {"SUCCEEDED", "FAILED", "CANCELLED"}:
            raise ValueError("terminal checkpoint cannot be replayed")
        return checkpoint

    def resume_run(self, request: RuntimeResumeRunRequest) -> RuntimeCommandResponse:
        checkpoint = self.validate_resume_request(request)
        state = deepcopy(checkpoint.get("state") or {})
        if not state:
            raise KeyError(f"run {request.runId} checkpoint has empty state")
        state["checkpointVersion"] = checkpoint.get("checkpointVersion", state.get("checkpointVersion", 0))
        state["resumeToken"] = None
        state["humanTaskId"] = None
        state["needsHuman"] = False
        state["humanDecision"] = (request.decision or "").upper()
        state["humanComment"] = request.comment
        state["currentStatus"] = "RUNNING"
        state["approvalReason"] = None
        state["currentNode"] = "finalize" if state["humanDecision"] == "REJECT" else (state.get("resumeAfterNode") or "supervisor_plan")
        if state["humanDecision"] == "REJECT":
            state["finalAnswer"] = "人工审批拒绝了本次高风险执行请求，系统已停止后续动作。"
        self._save_checkpoint(state, status="RUNNING")
        self._update_status(state, status="RUNNING", current_node=state["currentNode"], requires_human=False, resume_token=None)
        self._run_state_machine(state)
        return RuntimeCommandResponse(
            accepted=True,
            status=state.get("currentStatus", "RUNNING"),
            currentNode=state.get("currentNode", "supervisor_plan"),
            checkpointVersion=state.get("checkpointVersion"),
            orchestrationMode=state.get("orchestrationMode"),
        )

    def resume_run_background(self, request: RuntimeResumeRunRequest) -> None:
        threading.Thread(target=self.resume_run, args=(request,), daemon=True).start()

    def replay_run(self, request: RuntimeReplayRunRequest) -> RuntimeCommandResponse:
        checkpoint = self.validate_replay_request(request)
        state = deepcopy(checkpoint.get("state") or {})
        if not state:
            raise KeyError(f"run {request.runId} checkpoint has empty state")
        state["checkpointVersion"] = checkpoint.get("checkpointVersion", state.get("checkpointVersion", 0))
        state["currentStatus"] = "RUNNING"
        state["replayRecovered"] = True
        state["needsHuman"] = False
        state["resumeToken"] = None
        state["currentNode"] = checkpoint.get("currentNode") or state.get("currentNode") or "supervisor_plan"
        self._save_checkpoint(state, status="RUNNING")
        self._update_status(
            state,
            status="RUNNING",
            current_node=state["currentNode"],
            requires_human=False,
            resume_token=None,
            replay_recovered=True,
            approval_reason="checkpoint replay / continue",
        )
        self._run_state_machine(state)
        return RuntimeCommandResponse(
            accepted=True,
            status=state.get("currentStatus", "RUNNING"),
            currentNode=state.get("currentNode", "checkpoint_replay"),
            checkpointVersion=state.get("checkpointVersion"),
            orchestrationMode=state.get("orchestrationMode"),
        )

    def replay_run_background(self, request: RuntimeReplayRunRequest) -> None:
        threading.Thread(target=self.replay_run, args=(request,), daemon=True).start()

    def _build_tools(self) -> Dict[str, Any]:
        def tool_factory(tool_name: str):
            def execute(arguments: Dict[str, Any]) -> Dict[str, Any]:
                return self.client.call_tool(tool_name, arguments)
            if StructuredTool is not None:
                return StructuredTool.from_function(func=execute, name=tool_name, description=f"AgentOps Java MCP tool: {tool_name}")
            return FallbackTool(tool_name, execute)
        return {"kb_search": tool_factory("kb_search"), "doc_fetch": tool_factory("doc_fetch"), "sql_query": tool_factory("sql_query")}

    def _build_langgraph(self) -> Optional[Any]:
        if not LANGGRAPH_AVAILABLE:
            return None
        graph = StateGraph(AgentState)
        for node in ["intake_guardrail", "load_memory", "supervisor_plan", "knowledge_researcher", "data_analyst", "evidence_reviewer", "human_approval", "finalize"]:
            graph.add_node(node, lambda state: state)
        graph.add_edge(START, "intake_guardrail")
        graph.add_edge("intake_guardrail", "load_memory")
        graph.add_edge("load_memory", "supervisor_plan")
        graph.add_edge("knowledge_researcher", "evidence_reviewer")
        graph.add_edge("data_analyst", "evidence_reviewer")
        graph.add_edge("human_approval", "supervisor_plan")
        graph.add_edge("finalize", END)
        return graph.compile()

    def _base_state(self, request: RuntimeStartRunRequest, context: Dict[str, Any]) -> AgentState:
        return {
            "runId": request.runId,
            "sessionId": request.sessionId,
            "userId": request.userId,
            "userInput": request.userInput,
            "conversationSummary": context.get("conversationSummary") or "",
            "memoryFacts": context.get("memoryFacts") or [],
            "messages": context.get("messages") or [],
            "taskPlan": [],
            "pendingTasks": [],
            "toolTrace": [],
            "evidence": [],
            "draftAnswer": "",
            "finalAnswer": "",
            "citations": [],
            "artifacts": [],
            "reviewFeedback": "",
            "loopCount": 0,
            "reviewCount": 0,
            "toolLoopCount": 0,
            "currentNode": "intake_guardrail",
            "currentStatus": "RUNNING",
            "checkpointVersion": 0,
            "humanTaskId": None,
            "humanDecision": None,
            "humanComment": None,
            "resumeAfterNode": None,
            "resumeToken": None,
            "route": "knowledge",
            "queryComplexity": "MULTI_STEP",
            "routingReason": "",
            "planningMode": "LLM_PLANNER",
            "plannerBypass": False,
            "needsHuman": False,
            "approvalPolicy": (request.approvalPolicy or "MANUAL").upper(),
            "executionMode": (request.executionMode or "USER").upper(),
            "orchestrationMode": (request.orchestrationMode or "SINGLE_GRAPH").upper(),
            "confidence": 0.0,
            "nodePath": [],
            "errorMessage": None,
            "eventSequence": 0,
            "skillsUsed": [],
            "skillTrace": [],
            "costUsage": {
                "modelCalls": 0,
                "toolCalls": 0,
                "promptTokens": 0,
                "candidateTokens": 0,
                "totalLatencyMs": 0,
                "toolLatencyMs": 0,
                "modelLatencyMs": 0,
            },
            "approvalReason": None,
            "replayRecovered": False,
        }

    def _run_state_machine(self, state: AgentState) -> None:
        hops = 0
        try:
            while hops < settings.max_graph_hops:
                hops += 1
                state["loopCount"] = max(state.get("loopCount", 0), hops - 1)
                current = state.get("currentNode") or "intake_guardrail"
                if current == "intake_guardrail":
                    state = self._intake_guardrail(state)
                elif current == "load_memory":
                    state = self._load_memory(state)
                elif current == "supervisor_plan":
                    state = self._supervisor_plan(state)
                elif current == "knowledge_researcher":
                    state = self._knowledge_researcher(state)
                elif current == "data_analyst":
                    state = self._data_analyst(state)
                elif current == "evidence_reviewer":
                    state = self._evidence_reviewer(state)
                elif current == "human_approval":
                    state = self._human_approval(state)
                    if state is None:
                        return
                elif current == "finalize":
                    self._finalize(state)
                    return
                else:
                    raise ValueError(f"unknown node {current}")
            state["errorMessage"] = "graph hops exceeded"
            self._fail(state)
        except Exception as exc:  # pragma: no cover
            state["errorMessage"] = str(exc)
            self._fail(state)

    def _intake_guardrail(self, state: AgentState) -> AgentState:
        before = deepcopy(state)
        adaptive_route = classify_adaptive_route(state["userInput"], state.get("reviewFeedback"))
        state["route"] = adaptive_route["route"]
        state["queryComplexity"] = adaptive_route["queryComplexity"]
        state["routingReason"] = adaptive_route["routingReason"]
        state["plannerBypass"] = adaptive_route["plannerBypass"]
        state["planningMode"] = "ADAPTIVE_FAST_PATH" if adaptive_route["plannerBypass"] else "LLM_PLANNER"
        state["currentNode"] = "load_memory"
        self._save_graph_step(
            state,
            before,
            {
                "route": state["route"],
                "queryComplexity": state.get("queryComplexity"),
                "routingReason": state.get("routingReason"),
                "plannerBypass": state.get("plannerBypass"),
                "planningMode": state.get("planningMode"),
            },
            "intake_guardrail",
            "Intake Guardrail",
        )
        self._save_checkpoint(state, status="RUNNING")
        return state

    def _load_memory(self, state: AgentState) -> AgentState:
        before = deepcopy(state)
        state["currentNode"] = "supervisor_plan"
        memory_preview = [{"factType": item.get("factType"), "factKey": item.get("factKey")} for item in state.get("memoryFacts", [])[:5]]
        self._save_graph_step(
            state,
            before,
            {"memoryCount": len(state.get("memoryFacts", [])), "messageCount": len(state.get("messages", [])), "memoryPreview": memory_preview},
            "load_memory",
            "Load Memory",
        )
        self._save_checkpoint(state, status="RUNNING")
        return state

    def _supervisor_plan(self, state: AgentState) -> AgentState:
        before = deepcopy(state)
        if self._should_bypass_planner(state):
            route = state.get("route") or classify_route(state.get("userInput", ""), state.get("reviewFeedback"))
            pending = self._normalize_pending_tasks(route, self._adaptive_pending_tasks(route), state)
            state["pendingTasks"] = pending
            state["planningMode"] = "ADAPTIVE_FAST_PATH"
            state["taskPlan"] = [{
                "route": route,
                "pendingTasks": pending,
                "reason": state.get("routingReason"),
                "confidence": self._adaptive_confidence(state),
                "orchestrationMode": state.get("orchestrationMode"),
                "queryComplexity": state.get("queryComplexity"),
                "plannerBypass": True,
                "planningMode": state.get("planningMode"),
            }]
            state["confidence"] = self._adaptive_confidence(state)
            state["needsHuman"] = False
            state["currentNode"] = "evidence_reviewer" if route == "direct" or not pending else pending[0]
        else:
            state["planningMode"] = "LLM_PLANNER"
            state["plannerBypass"] = False
            plan_result, llm_meta = self.llm.plan(state)
            route = self._normalize_route(plan_result.route, state)
            pending = self._normalize_pending_tasks(route, plan_result.pendingTasks, state)
            state["route"] = route
            state["pendingTasks"] = pending
            state["taskPlan"] = [{
                "route": route,
                "pendingTasks": pending,
                "reason": plan_result.reason,
                "confidence": plan_result.confidence,
                "orchestrationMode": state.get("orchestrationMode"),
                "queryComplexity": state.get("queryComplexity"),
                "plannerBypass": False,
                "planningMode": state.get("planningMode"),
            }]
            state["confidence"] = max(0.0, min(1.0, plan_result.confidence))
            state["needsHuman"] = bool(plan_result.needsHuman)
            state["currentNode"] = "evidence_reviewer" if route == "direct" or not pending else pending[0]
            self._save_model_step(
                state,
                before,
                "supervisor_plan",
                "Supervisor Plan Model",
                {
                    "userInput": state["userInput"],
                    "reviewFeedback": state.get("reviewFeedback", ""),
                    "memoryFacts": state.get("memoryFacts", [])[:4],
                    "queryComplexity": state.get("queryComplexity"),
                    "routingReason": state.get("routingReason"),
                },
                plan_result.model_dump(),
                llm_meta,
                "supervisor-v2.3-adaptive-cod",
            )
        self._save_graph_step(
            state,
            before,
            {
                "route": route,
                "pendingTasks": pending,
                "confidence": state["confidence"],
                "teamGraph": self._is_team_graph(state),
                "queryComplexity": state.get("queryComplexity"),
                "routingReason": state.get("routingReason"),
                "plannerBypass": state.get("plannerBypass"),
                "planningMode": state.get("planningMode"),
            },
            "supervisor_plan",
            "Supervisor Plan",
        )
        self._save_checkpoint(state, status="RUNNING")
        return state

    def _knowledge_researcher(self, state: AgentState) -> AgentState:
        before = deepcopy(state)
        self._assert_tool_budget(state)
        kb_result = self._invoke_tool(state, "kb_search", {"query": state["userInput"], "topK": 5, "knowledgeBaseId": 1}, parent_node="knowledge_researcher")
        state["toolTrace"].append({"toolName": "kb_search", "summary": kb_result.get("summary"), "data": kb_result.get("data")})
        state["evidence"].append({"toolName": "kb_search", "summary": kb_result.get("summary"), "data": kb_result.get("data")})
        hits = kb_result.get("data", {}).get("hits", [])
        for hit in hits[:3]:
            state["citations"].append({"documentId": hit.get("documentId"), "chunkId": hit.get("chunkId"), "source": hit.get("highlight") or hit.get("content", "")[:80]})
        if hits and hits[0].get("documentId"):
            self._assert_tool_budget(state)
            doc_result = self._invoke_tool(state, "doc_fetch", {"documentId": hits[0].get("documentId")}, parent_node="knowledge_researcher")
            state["toolTrace"].append({"toolName": "doc_fetch", "summary": doc_result.get("summary"), "data": doc_result.get("data")})
            state["evidence"].append({"toolName": "doc_fetch", "summary": doc_result.get("summary"), "data": doc_result.get("data")})
        state["currentNode"] = "data_analyst" if state["route"] == "mixed" and not self._has_sql_trace(state) else "evidence_reviewer"
        self._record_skill_use(state, "knowledge_researcher")
        self._save_graph_step(state, before, {"evidenceCount": len(state["evidence"]), "citationCount": len(state["citations"])}, "knowledge_researcher", "Knowledge Researcher")
        self._save_checkpoint(state, status="RUNNING")
        return state

    def _data_analyst(self, state: AgentState) -> AgentState:
        before = deepcopy(state)
        if self._requires_human_for_sql(state["userInput"]) and state.get("humanDecision") is None:
            state["needsHuman"] = True
            state["resumeAfterNode"] = "data_analyst"
            state["approvalReason"] = "high risk sql aggregate or wide export"
            state["currentNode"] = "human_approval"
            self._save_graph_step(state, before, {"needsHuman": True, "reason": state["approvalReason"]}, "data_analyst", "Data Analyst")
            self._save_checkpoint(state, status="RUNNING")
            return state
        self._assert_tool_budget(state)
        sql_args = build_sql_arguments(state["userInput"])
        sql_result = self._invoke_tool(state, "sql_query", sql_args, parent_node="data_analyst")
        state["toolTrace"].append({"toolName": "sql_query", "summary": sql_result.get("summary"), "data": sql_result.get("data")})
        state["evidence"].append({"toolName": "sql_query", "summary": sql_result.get("summary"), "data": sql_result.get("data")})
        state["artifacts"].append({"type": "table", "title": "SQL Result", "payload": sql_result.get("data")})
        state["artifacts"].append({"type": "chart", "title": "SQL Snapshot", "payload": {"queryType": sql_args.get("queryType"), "summary": sql_result.get("summary")}})
        state["currentNode"] = "evidence_reviewer"
        self._record_skill_use(state, "data_analyst")
        self._save_graph_step(state, before, {"sqlArgs": sql_args}, "data_analyst", "Data Analyst")
        self._save_checkpoint(state, status="RUNNING")
        return state

    def _evidence_reviewer(self, state: AgentState) -> AgentState:
        before = deepcopy(state)
        review_result, llm_meta = self.llm.review(state)
        state["confidence"] = max(0.0, min(1.0, review_result.confidence))
        state["reviewFeedback"] = review_result.reviewFeedback
        self._save_model_step(
            state,
            before,
            "evidence_reviewer",
            "Evidence Reviewer Model",
            {"route": state.get("route"), "evidenceCount": len(state.get("evidence", [])), "skillsUsed": state.get("skillsUsed", [])},
            review_result.model_dump(),
            llm_meta,
            "reviewer-v2.2",
        )
        self._record_skill_use(state, "evidence_reviewer")
        if review_result.needsHuman and state.get("humanDecision") is None:
            state["needsHuman"] = True
            state["resumeAfterNode"] = "supervisor_plan"
            state["approvalReason"] = review_result.reviewFeedback or "reviewer low confidence"
            state["currentNode"] = "human_approval"
            self._save_graph_step(state, before, {"decision": "need_human", "reviewFeedback": review_result.reviewFeedback}, "evidence_reviewer", "Evidence Reviewer", step_type="REVIEW")
            self._save_checkpoint(state, status="RUNNING")
            return state
        if not review_result.grounded and state["route"] != "direct":
            if state.get("reviewCount", 0) < settings.max_replans and review_result.needsReplan:
                state["reviewCount"] = state.get("reviewCount", 0) + 1
                state["queryComplexity"] = "MULTI_STEP"
                state["plannerBypass"] = False
                state["planningMode"] = "LLM_PLANNER"
                state["routingReason"] = "review_feedback_requires_replan"
                state["currentNode"] = "supervisor_plan"
                self._save_graph_step(state, before, {"decision": "replan", "reviewCount": state["reviewCount"], "reviewFeedback": review_result.reviewFeedback}, "evidence_reviewer", "Evidence Reviewer", step_type="REVIEW")
                self._save_checkpoint(state, status="RUNNING")
                return state
            state["finalAnswer"] = self._bounded_failure_message()
            state["currentNode"] = "finalize"
            self._save_graph_step(state, before, {"decision": "bounded_failure", "reviewFeedback": review_result.reviewFeedback}, "evidence_reviewer", "Evidence Reviewer", step_type="REVIEW")
            self._save_checkpoint(state, status="RUNNING")
            return state
        state["currentNode"] = "finalize"
        self._save_graph_step(state, before, {"decision": "finalize", "reviewFeedback": review_result.reviewFeedback}, "evidence_reviewer", "Evidence Reviewer", step_type="REVIEW")
        self._save_checkpoint(state, status="RUNNING")
        return state

    def _human_approval(self, state: AgentState) -> Optional[AgentState]:
        before = deepcopy(state)
        policy = (state.get("approvalPolicy") or "MANUAL").upper()
        if policy == "AUTO_APPROVE":
            state["humanDecision"] = "APPROVE"
            state["humanComment"] = "auto-approved by policy"
            state["needsHuman"] = False
            state["currentNode"] = state.get("resumeAfterNode") or "supervisor_plan"
            self._save_graph_step(state, before, {"autoDecision": "APPROVE", "policy": policy, "approvalReason": state.get("approvalReason")}, "human_approval", "Human Approval", step_type="HUMAN_TASK")
            self._save_checkpoint(state, status="RUNNING")
            return state
        if policy == "AUTO_REJECT":
            state["humanDecision"] = "REJECT"
            state["humanComment"] = "auto-rejected by policy"
            state["needsHuman"] = False
            state["finalAnswer"] = "人工审批拒绝了本次高风险执行请求，系统已停止后续动作。"
            state["currentNode"] = "finalize"
            self._save_graph_step(state, before, {"autoDecision": "REJECT", "policy": policy, "approvalReason": state.get("approvalReason")}, "human_approval", "Human Approval", step_type="HUMAN_TASK")
            self._save_checkpoint(state, status="RUNNING")
            return state
        state["resumeToken"] = secrets.token_urlsafe(24)
        state["currentStatus"] = "WAITING_HUMAN"
        state["needsHuman"] = True
        self._save_graph_step(state, before, {"reason": state.get("approvalReason") or state.get("reviewFeedback") or "high risk sql or reviewer low confidence"}, "human_approval", "Human Approval", step_type="HUMAN_TASK", success=False, error_message="等待人工审批")
        self._save_checkpoint(state, status="WAITING_HUMAN")
        self._update_status(
            state,
            status="WAITING_HUMAN",
            current_node="human_approval",
            requires_human=True,
            resume_token=state["resumeToken"],
            approval_reason=state.get("approvalReason") or state.get("reviewFeedback"),
        )
        return None

    def _finalize(self, state: AgentState) -> None:
        before = deepcopy(state)
        llm_meta: Dict[str, Any] = {}
        if not state.get("finalAnswer"):
            final_result, llm_meta = self.llm.finalize(state)
            state["finalAnswer"] = final_result.finalAnswer
            state["confidence"] = max(0.0, min(1.0, final_result.confidence))
            self._save_model_step(
                state,
                before,
                "finalize",
                "Finalize Model",
                {"userInput": state["userInput"], "evidenceCount": len(state.get("evidence", [])), "skillsUsed": state.get("skillsUsed", [])},
                final_result.model_dump(),
                llm_meta,
                "finalize-v2.2",
            )
        self._record_skill_use(state, "finalize")
        memory_facts = build_memory_facts(state["userInput"], state["finalAnswer"], state.get("citations", []))
        state["currentNode"] = "finalize"
        state["currentStatus"] = "SUCCEEDED"
        self._save_graph_step(state, before, {"finalAnswer": state["finalAnswer"][:200], "llmMode": llm_meta.get("mode"), "skillsUsed": state.get("skillsUsed", [])}, "finalize", "Finalize", step_type="FINAL_OUTPUT")
        self._save_checkpoint(state, status="SUCCEEDED")
        self._update_status(
            state,
            status="SUCCEEDED",
            current_node="finalize",
            final_answer=state["finalAnswer"],
            citations=state.get("citations", []),
            artifacts=state.get("artifacts", []),
            memory_facts=memory_facts,
            requires_human=False,
            resume_token=None,
        )
        self.client.delete_checkpoint(state["runId"])

    def _fail(self, state: AgentState) -> None:
        state["currentStatus"] = "FAILED"
        self._save_checkpoint(state, status="FAILED", error=state.get("errorMessage") or "runtime failure")
        self._update_status(
            state,
            status="FAILED",
            current_node=state.get("currentNode", "unknown"),
            error_message=state.get("errorMessage") or "runtime failure",
            requires_human=False,
            resume_token=None,
        )
        self.client.delete_checkpoint(state["runId"])

    def _invoke_tool(self, state: AgentState, tool_name: str, arguments: Dict[str, Any], *, parent_node: str) -> Dict[str, Any]:
        before = deepcopy(state)
        started = time.perf_counter()
        result = self._tools[tool_name].invoke(arguments)
        state["toolLoopCount"] = state.get("toolLoopCount", 0) + 1
        latency_ms = int((time.perf_counter() - started) * 1000)
        governance = TOOL_GOVERNANCE.get(tool_name, {})
        state["costUsage"] = self._merge_cost_usage(state.get("costUsage", {}), latency_ms=latency_ms, prompt_tokens=0, candidate_tokens=0, category="tool")
        skill_meta = self._skill_meta(parent_node)
        self.client.save_step(
            state["runId"],
            StepPayload(
                stepType="TOOL_CALL",
                nodeId=tool_name,
                nodeLabel=tool_name,
                toolName=tool_name,
                eventSequence=self._next_event_sequence(state),
                input=arguments,
                output=result,
                stateBefore=self._minify_state(before),
                stateAfter=self._minify_state(state),
                observation={"summary": result.get("summary"), "toolLoopCount": state["toolLoopCount"], "governance": governance},
                costUsage={"latencyMs": latency_ms, "category": "tool"},
                latencyMs=latency_ms,
                modelName="tool-executor",
                promptVersion="tool-v3",
                success=bool(result.get("success", True)),
                skillName=skill_meta.get("skillName"),
                skillType=skill_meta.get("skillType"),
                riskLevel=governance.get("riskLevel"),
                approvalPolicy=governance.get("approvalPolicy"),
                approvalReason=state.get("approvalReason"),
            ),
        )
        return result

    def _save_model_step(self, state: AgentState, before: AgentState, node_id: str, node_label: str, input_payload: Dict[str, Any], output_payload: Dict[str, Any], llm_meta: Dict[str, Any], prompt_version: str) -> None:
        prompt_tokens, candidate_tokens = self._extract_usage(llm_meta.get("usage"))
        latency_ms = int(llm_meta.get("latencyMs", 1))
        state["costUsage"] = self._merge_cost_usage(
            state.get("costUsage", {}),
            latency_ms=latency_ms,
            prompt_tokens=prompt_tokens,
            candidate_tokens=candidate_tokens,
            category="model",
        )
        skill_meta = self._skill_meta(node_id)
        self.client.save_step(
            state["runId"],
            StepPayload(
                stepType="MODEL_CALL",
                nodeId=node_id,
                nodeLabel=self._display_label(node_id, node_label, state),
                eventSequence=self._next_event_sequence(state),
                input=input_payload,
                output=output_payload,
                stateBefore=self._minify_state(before),
                stateAfter=self._minify_state(state),
                observation=llm_meta,
                costUsage={"promptTokens": prompt_tokens, "candidateTokens": candidate_tokens, "category": "model"},
                latencyMs=latency_ms,
                modelName=self.llm.model_name if self.llm.gemini_available else "mock-llm",
                promptVersion=prompt_version,
                success=True,
                skillName=skill_meta.get("skillName"),
                skillType=skill_meta.get("skillType"),
                riskLevel="LOW",
                approvalPolicy="NONE",
                approvalReason=state.get("approvalReason"),
            ),
        )

    def _save_graph_step(self, state: AgentState, before: AgentState, observation: Dict[str, Any], node_id: str, node_label: str, *, step_type: str = "GRAPH_NODE", success: bool = True, error_message: str | None = None) -> None:
        self._append_node_path(state, node_id)
        skill_meta = self._skill_meta(node_id)
        self.client.save_step(
            state["runId"],
            StepPayload(
                stepType=step_type,
                nodeId=node_id,
                nodeLabel=self._display_label(node_id, node_label, state),
                eventSequence=self._next_event_sequence(state),
                input={"userInput": state["userInput"]},
                output={"route": state.get("route"), "currentNode": state.get("currentNode"), "orchestrationMode": state.get("orchestrationMode")},
                stateBefore=self._minify_state(before),
                stateAfter=self._minify_state(state),
                observation=observation,
                costUsage={"category": "graph", "skillsUsed": state.get("skillsUsed", [])},
                latencyMs=1,
                modelName="langgraph-runtime",
                promptVersion="graph-v2.2",
                success=success,
                errorMessage=error_message,
                skillName=skill_meta.get("skillName"),
                skillType=skill_meta.get("skillType"),
                riskLevel="NONE",
                approvalPolicy=state.get("approvalPolicy"),
                approvalReason=state.get("approvalReason"),
                retryReason=observation.get("reviewFeedback") if observation.get("decision") == "replan" else None,
            ),
        )

    def _save_checkpoint(self, state: AgentState, *, status: str, error: str | None = None) -> None:
        state["currentStatus"] = status
        response = self.client.save_checkpoint(
            state["runId"],
            CheckpointPayload(
                sessionId=state["sessionId"],
                status=status,
                currentNode=state.get("currentNode", "unknown"),
                orchestrationMode=state.get("orchestrationMode", "SINGLE_GRAPH"),
                resumeToken=state.get("resumeToken"),
                requiresHuman=bool(state.get("needsHuman")),
                humanTaskId=state.get("humanTaskId"),
                resumeAfterNode=state.get("resumeAfterNode"),
                eventSequence=state.get("eventSequence", 0),
                loopCount=state.get("loopCount", 0),
                toolLoopCount=state.get("toolLoopCount", 0),
                reviewCount=state.get("reviewCount", 0),
                state=dict(state),
                lastError=error or state.get("errorMessage"),
            ),
        )
        state["checkpointVersion"] = response.get("checkpointVersion", state.get("checkpointVersion", 0))

    def _append_node_path(self, state: AgentState, node_id: str) -> None:
        path = state.setdefault("nodePath", [])
        if not path or path[-1] != node_id:
            path.append(node_id)

    def _record_skill_use(self, state: AgentState, node_id: str) -> None:
        skill_meta = self._skill_meta(node_id)
        skill_name = skill_meta.get("skillName")
        if not skill_name:
            return
        if skill_name not in state.setdefault("skillsUsed", []):
            state["skillsUsed"].append(skill_name)
        state.setdefault("skillTrace", []).append(
            {
                "skillName": skill_name,
                "skillType": skill_meta.get("skillType"),
                "inputSchema": skill_meta.get("inputSchema"),
                "outputSchema": skill_meta.get("outputSchema"),
                "budget": skill_meta.get("budget"),
                "status": state.get("currentStatus"),
                "parentRunId": state.get("runId"),
                "parentStepId": None,
            }
        )

    def _next_event_sequence(self, state: AgentState) -> int:
        state["eventSequence"] = int(state.get("eventSequence", 0)) + 1
        return state["eventSequence"]

    def _update_status(
        self,
        state: AgentState,
        *,
        status: str,
        current_node: str,
        final_answer: str | None = None,
        citations: List[Dict[str, Any]] | None = None,
        artifacts: List[Dict[str, Any]] | None = None,
        memory_facts: List[Dict[str, Any]] | None = None,
        requires_human: bool = False,
        resume_token: str | None = None,
        approval_reason: str | None = None,
        replay_recovered: bool | None = None,
        error_message: str | None = None,
    ) -> None:
        graph_name, graph_version = self._graph_metadata(state)
        self.client.update_status(
            state["runId"],
            StatusPayload(
                status=status,
                currentNode=current_node,
                graphName=graph_name,
                graphVersion=graph_version,
                orchestrationMode=state.get("orchestrationMode", "SINGLE_GRAPH"),
                requiresHuman=requires_human,
                resumeToken=resume_token,
                checkpointVersion=state.get("checkpointVersion"),
                eventSequence=self._next_event_sequence(state),
                finalAnswer=final_answer,
                citations=citations or [],
                artifacts=artifacts or [],
                memoryFacts=memory_facts or [],
                costUsage=state.get("costUsage", {}),
                approvalReason=approval_reason,
                replayRecovered=state.get("replayRecovered", False) if replay_recovered is None else replay_recovered,
                errorMessage=error_message,
            ),
        )

    def _minify_state(self, state: AgentState) -> Dict[str, Any]:
        return {
            "currentNode": state.get("currentNode"),
            "route": state.get("route"),
            "queryComplexity": state.get("queryComplexity"),
            "routingReason": state.get("routingReason"),
            "planningMode": state.get("planningMode"),
            "plannerBypass": state.get("plannerBypass"),
            "loopCount": state.get("loopCount"),
            "reviewCount": state.get("reviewCount"),
            "toolLoopCount": state.get("toolLoopCount"),
            "evidenceCount": len(state.get("evidence", [])),
            "toolTraceCount": len(state.get("toolTrace", [])),
            "needsHuman": state.get("needsHuman"),
            "humanDecision": state.get("humanDecision"),
            "confidence": state.get("confidence"),
            "checkpointVersion": state.get("checkpointVersion"),
            "approvalPolicy": state.get("approvalPolicy"),
            "executionMode": state.get("executionMode"),
            "orchestrationMode": state.get("orchestrationMode"),
            "eventSequence": state.get("eventSequence"),
            "skillsUsed": state.get("skillsUsed", []),
            "replayRecovered": state.get("replayRecovered", False),
        }

    def _assert_tool_budget(self, state: AgentState) -> None:
        if state.get("toolLoopCount", 0) >= settings.max_tool_loops:
            raise ValueError("tool loop limit exceeded")

    def _normalize_route(self, route: str, state: AgentState) -> str:
        normalized = (route or "").lower().strip()
        return normalized if normalized in {"direct", "knowledge", "data", "mixed"} else classify_route(state.get("userInput", ""), state.get("reviewFeedback"))

    def _normalize_pending_tasks(self, route: str, pending_tasks: List[str], state: AgentState) -> List[str]:
        normalized = [item for item in pending_tasks if item in {"knowledge_researcher", "data_analyst"}]
        if route == "direct":
            return []
        if route == "knowledge":
            return [] if self._has_kb_trace(state) else (normalized or ["knowledge_researcher"])
        if route == "data":
            return [] if self._has_sql_trace(state) else (normalized or ["data_analyst"])
        result: List[str] = []
        if not self._has_kb_trace(state):
            result.append("knowledge_researcher")
        if not self._has_sql_trace(state):
            result.append("data_analyst")
        return result or normalized

    def _should_bypass_planner(self, state: AgentState) -> bool:
        return bool(
            state.get("plannerBypass")
            and not state.get("toolTrace")
            and not state.get("reviewFeedback")
            and state.get("humanDecision") is None
        )

    def _adaptive_pending_tasks(self, route: str) -> List[str]:
        if route == "knowledge":
            return ["knowledge_researcher"]
        if route == "data":
            return ["data_analyst"]
        if route == "mixed":
            return ["knowledge_researcher", "data_analyst"]
        return []

    def _adaptive_confidence(self, state: AgentState) -> float:
        complexity = (state.get("queryComplexity") or "").upper()
        if complexity == "DIRECT":
            return 0.99
        if complexity == "SINGLE_HOP":
            return 0.9
        return 0.76

    def _requires_human_for_sql(self, message: str) -> bool:
        lowered = (message or "").lower()
        return any(token in lowered for token in ["全部", "所有", "全量", "all", "导出", "export", "全公司", "所有用户", "所有会话"])

    def _has_sql_trace(self, state: AgentState) -> bool:
        return any(item.get("toolName") == "sql_query" for item in state.get("toolTrace", []))

    def _has_kb_trace(self, state: AgentState) -> bool:
        return any(item.get("toolName") == "kb_search" for item in state.get("toolTrace", []))

    def _graph_metadata(self, state: AgentState) -> Tuple[str, str]:
        if self._is_team_graph(state):
            return "enterprise-team-copilot", "v2.2"
        return "enterprise-copilot", "v2.2"

    def _display_label(self, node_id: str, default_label: str, state: AgentState) -> str:
        if not self._is_team_graph(state):
            return default_label
        labels = {
            "intake_guardrail": "Run Intake",
            "load_memory": "Memory Loader",
            "supervisor_plan": "Planner Agent",
            "knowledge_researcher": "Knowledge Skill",
            "data_analyst": "Data Skill",
            "evidence_reviewer": "Review Skill",
            "human_approval": "Human Approval",
            "finalize": "Finalize Skill",
        }
        return labels.get(node_id, default_label)

    def _skill_meta(self, node_id: str) -> Dict[str, Any]:
        return SKILL_REGISTRY.get(node_id, {})

    def _is_team_graph(self, state: AgentState) -> bool:
        return (state.get("orchestrationMode") or "SINGLE_GRAPH").upper() == "TEAM_GRAPH"

    def _merge_cost_usage(
        self,
        current: Dict[str, Any],
        *,
        latency_ms: int,
        prompt_tokens: int,
        candidate_tokens: int,
        category: str,
    ) -> Dict[str, Any]:
        merged = dict(current or {})
        merged["totalLatencyMs"] = int(merged.get("totalLatencyMs", 0)) + int(latency_ms)
        merged["promptTokens"] = int(merged.get("promptTokens", 0)) + int(prompt_tokens)
        merged["candidateTokens"] = int(merged.get("candidateTokens", 0)) + int(candidate_tokens)
        if category == "tool":
            merged["toolCalls"] = int(merged.get("toolCalls", 0)) + 1
            merged["toolLatencyMs"] = int(merged.get("toolLatencyMs", 0)) + int(latency_ms)
        elif category == "model":
            merged["modelCalls"] = int(merged.get("modelCalls", 0)) + 1
            merged["modelLatencyMs"] = int(merged.get("modelLatencyMs", 0)) + int(latency_ms)
        return merged

    def _extract_usage(self, usage: Any) -> Tuple[int, int]:
        if not isinstance(usage, dict):
            return 0, 0
        prompt_tokens = usage.get("promptTokenCount") or usage.get("input_tokens") or usage.get("inputTokenCount") or 0
        candidate_tokens = usage.get("candidatesTokenCount") or usage.get("output_tokens") or usage.get("outputTokenCount") or 0
        try:
            return int(prompt_tokens), int(candidate_tokens)
        except Exception:
            return 0, 0

    def _bounded_failure_message(self) -> str:
        return "系统尝试了多轮检索与分析，但证据仍不足，因此不输出可能失真的结论。"
