from __future__ import annotations

import json
import time
from typing import Any, Dict, List, Tuple

from pydantic import BaseModel, Field

from .config import settings
from .planner import build_answer, classify_route

try:
    from langchain_google_genai import ChatGoogleGenerativeAI
except Exception:  # pragma: no cover
    ChatGoogleGenerativeAI = None


class SupervisorPlanOutput(BaseModel):
    route: str
    pendingTasks: List[str] = Field(default_factory=list)
    needsHuman: bool = False
    reason: str
    confidence: float = 0.5
    directAnswerAllowed: bool = False


class ReviewerOutput(BaseModel):
    grounded: bool
    needsReplan: bool = False
    needsHuman: bool = False
    reviewFeedback: str
    confidence: float = 0.5


class FinalizeOutput(BaseModel):
    finalAnswer: str
    boundedFailure: bool = False
    confidence: float = 0.5


class GeminiLLMService:
    def __init__(self) -> None:
        self.mode = "live" if settings.gemini_api_key and settings.llm_mode.lower() != "mock" else "mock"
        self.model_name = settings.gemini_model
        self._llm = None
        if self.mode == "live" and ChatGoogleGenerativeAI is not None:
            self._llm = ChatGoogleGenerativeAI(
                model=settings.gemini_model,
                google_api_key=settings.gemini_api_key,
                temperature=0,
            )
        elif self.mode == "live":
            self.mode = "mock"

    @property
    def gemini_available(self) -> bool:
        return self._llm is not None

    def plan(self, state: Dict[str, Any]) -> Tuple[SupervisorPlanOutput, Dict[str, Any]]:
        if not self.gemini_available:
            return self._mock_plan(state), {"mode": self.mode, "retryCount": 0}
        prompt = (
            "You are the AgentOps supervisor planner.\n"
            "Return JSON only with keys route, pendingTasks, needsHuman, reason, confidence, directAnswerAllowed.\n"
            "Allowed route values: direct, knowledge, data, mixed.\n"
            "Allowed pendingTasks values: knowledge_researcher, data_analyst.\n"
            f"UserInput: {state.get('userInput', '')}\n"
            f"ConversationSummary: {state.get('conversationSummary', '')}\n"
            f"ReviewFeedback: {state.get('reviewFeedback', '')}\n"
            f"CompletedTools: {[item.get('toolName') for item in state.get('toolTrace', [])]}\n"
            f"CurrentLoop: {state.get('loopCount', 0)}"
        )
        return self._invoke_json(prompt, SupervisorPlanOutput, self._mock_plan, state)

    def review(self, state: Dict[str, Any]) -> Tuple[ReviewerOutput, Dict[str, Any]]:
        if not self.gemini_available:
            return self._mock_review(state), {"mode": self.mode, "retryCount": 0}
        prompt = (
            "You are the AgentOps evidence reviewer.\n"
            "Return JSON only with keys grounded, needsReplan, needsHuman, reviewFeedback, confidence.\n"
            "Judge whether current evidence is enough, whether a replan is needed, and whether human approval is needed.\n"
            f"UserInput: {state.get('userInput', '')}\n"
            f"Route: {state.get('route', 'knowledge')}\n"
            f"Evidence: {state.get('evidence', [])[:3]}\n"
            f"ToolTrace: {state.get('toolTrace', [])[-4:]}\n"
            f"HumanDecision: {state.get('humanDecision')}\n"
            f"LoopCount: {state.get('loopCount', 0)}\n"
            f"ReviewCount: {state.get('reviewCount', 0)}"
        )
        return self._invoke_json(prompt, ReviewerOutput, self._mock_review, state)

    def finalize(self, state: Dict[str, Any]) -> Tuple[FinalizeOutput, Dict[str, Any]]:
        if not self.gemini_available:
            return self._mock_finalize(state), {"mode": self.mode, "retryCount": 0}
        prompt = (
            "You are the AgentOps final answer generator.\n"
            "Return JSON only with keys finalAnswer, boundedFailure, confidence.\n"
            "Use the evidence and citations. If evidence is insufficient, return a bounded failure answer instead of guessing.\n"
            f"UserInput: {state.get('userInput', '')}\n"
            f"HumanDecision: {state.get('humanDecision')}\n"
            f"Evidence: {state.get('evidence', [])[:4]}\n"
            f"Citations: {state.get('citations', [])[:5]}"
        )
        return self._invoke_json(prompt, FinalizeOutput, self._mock_finalize, state)

    def _invoke_json(self, prompt: str, schema_cls, fallback, state: Dict[str, Any]) -> Tuple[Any, Dict[str, Any]]:
        retries = 0
        last_error = None
        for retries in range(settings.llm_max_retries):
            try:
                started = time.perf_counter()
                message = self._llm.invoke(prompt)
                raw_text = self._message_text(message)
                parsed = schema_cls.model_validate(json.loads(self._normalize_json(raw_text)))
                usage = getattr(message, "usage_metadata", None) or getattr(message, "response_metadata", None) or {}
                latency_ms = int((time.perf_counter() - started) * 1000)
                return parsed, {"mode": self.mode, "retryCount": retries, "usage": usage, "latencyMs": latency_ms}
            except Exception as exc:  # pragma: no cover
                last_error = str(exc)
        fallback_result = fallback(state)
        return fallback_result, {"mode": "mock-fallback", "retryCount": retries + 1, "lastError": last_error or "unknown", "latencyMs": 1}

    def _mock_plan(self, state: Dict[str, Any]) -> SupervisorPlanOutput:
        route = classify_route(state.get("userInput", ""), state.get("reviewFeedback"))
        completed_tools = {item.get("toolName") for item in state.get("toolTrace", [])}
        if route == "direct":
            pending = []
        elif route == "knowledge":
            pending = [] if "kb_search" in completed_tools else ["knowledge_researcher"]
        elif route == "data":
            pending = [] if "sql_query" in completed_tools else ["data_analyst"]
        else:
            pending = []
            if "kb_search" not in completed_tools:
                pending.append("knowledge_researcher")
            if "sql_query" not in completed_tools:
                pending.append("data_analyst")
        return SupervisorPlanOutput(
            route=route,
            pendingTasks=pending,
            needsHuman=False,
            reason="mock planner route selection",
            confidence=0.86 if route != "direct" else 0.98,
            directAnswerAllowed=route == "direct",
        )

    def _mock_review(self, state: Dict[str, Any]) -> ReviewerOutput:
        evidence = state.get("evidence", [])
        needs_human = bool(state.get("needsHuman")) and not state.get("humanDecision")
        if needs_human:
            return ReviewerOutput(
                grounded=False,
                needsReplan=False,
                needsHuman=True,
                reviewFeedback="human approval required",
                confidence=0.42,
            )
        if state.get("route") != "direct" and not evidence:
            return ReviewerOutput(
                grounded=False,
                needsReplan=True,
                needsHuman=False,
                reviewFeedback="insufficient evidence",
                confidence=0.38,
            )
        if state.get("route") == "mixed" and self._has_conflicting_evidence(evidence):
            return ReviewerOutput(
                grounded=False,
                needsReplan=False,
                needsHuman=True,
                reviewFeedback="kb and sql evidence conflict",
                confidence=0.31,
            )
        return ReviewerOutput(
            grounded=True,
            needsReplan=False,
            needsHuman=False,
            reviewFeedback="evidence sufficient",
            confidence=0.91 if evidence else 0.75,
        )

    def _mock_finalize(self, state: Dict[str, Any]) -> FinalizeOutput:
        final_answer = build_answer(
            state.get("userInput", ""),
            state.get("evidence", []),
            state.get("citations", []),
            state.get("humanDecision"),
        )
        bounded_failure = not state.get("evidence") and state.get("route") != "direct"
        return FinalizeOutput(finalAnswer=final_answer, boundedFailure=bounded_failure, confidence=0.9 if not bounded_failure else 0.55)

    def _has_conflicting_evidence(self, evidence: List[Dict[str, Any]]) -> bool:
        tool_names = {item.get("toolName") for item in evidence}
        return "kb_search" in tool_names and "sql_query" in tool_names and any("conflict" in str(item).lower() for item in evidence)

    def _message_text(self, message: Any) -> str:
        content = getattr(message, "content", "")
        if isinstance(content, str):
            return content
        if isinstance(content, list):
            return "\n".join(str(item) for item in content)
        return str(content)

    def _normalize_json(self, raw_text: str) -> str:
        text = (raw_text or "").strip()
        if text.startswith("```"):
            text = text.replace("```json", "").replace("```", "").strip()
        return text or "{}"
