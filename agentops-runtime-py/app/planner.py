from __future__ import annotations

from typing import Any, Dict, List, Literal, TypedDict


class AdaptiveRouteDecision(TypedDict):
    route: Literal["direct", "knowledge", "data", "mixed"]
    queryComplexity: Literal["DIRECT", "SINGLE_HOP", "MULTI_STEP"]
    pendingTasks: List[str]
    plannerBypass: bool
    routingReason: str


def is_greeting(message: str) -> bool:
    lowered = (message or "").lower()
    return any(token in lowered for token in ["hello", "hi", "你好", "您好"])


def classify_adaptive_route(message: str, review_feedback: str | None = None) -> AdaptiveRouteDecision:
    lowered = (message or "").lower()
    has_data = any(token in lowered for token in ["统计", "count", "多少", "数量", "sql", "报表", "趋势"])
    has_knowledge_entity = any(token in lowered for token in ["文档", "知识", "kb", "agent"])
    has_knowledge_action = any(token in lowered for token in ["设计", "流程", "what", "how", "explain", "总结", "总结下", "介绍", "说明"])
    has_knowledge = has_knowledge_entity or has_knowledge_action
    multi_step_hint = any(
        token in lowered
        for token in [
            "对比",
            "比较",
            "区别",
            "difference",
            "vs",
            "原因",
            "why",
            "根因",
            "先",
            "然后",
            "并分析",
            "分析并",
            "并总结",
            "总结并",
            "综合",
        ]
    )
    route = _route_from_signals(has_data, has_knowledge)
    if is_greeting(lowered):
        return {
            "route": "direct",
            "queryComplexity": "DIRECT",
            "pendingTasks": [],
            "plannerBypass": True,
            "routingReason": "greeting_or_low_risk_chat",
        }
    if review_feedback and "insufficient" in review_feedback.lower():
        return {
            "route": route,
            "queryComplexity": "MULTI_STEP",
            "pendingTasks": _pending_tasks_for_route(route),
            "plannerBypass": False,
            "routingReason": "review_feedback_requires_replan",
        }
    if has_data and has_knowledge_entity and not has_knowledge_action and not multi_step_hint:
        return {
            "route": "data",
            "queryComplexity": "SINGLE_HOP",
            "pendingTasks": ["data_analyst"],
            "plannerBypass": True,
            "routingReason": "data_query_with_knowledge_domain_entities",
        }
    if has_data and has_knowledge:
        return {
            "route": "mixed",
            "queryComplexity": "MULTI_STEP",
            "pendingTasks": _pending_tasks_for_route("mixed"),
            "plannerBypass": False,
            "routingReason": "knowledge_and_data_signals_detected",
        }
    if multi_step_hint:
        return {
            "route": route,
            "queryComplexity": "MULTI_STEP",
            "pendingTasks": _pending_tasks_for_route(route),
            "plannerBypass": False,
            "routingReason": "multi_step_reasoning_hint_detected",
        }
    if has_data:
        return {
            "route": "data",
            "queryComplexity": "SINGLE_HOP",
            "pendingTasks": ["data_analyst"],
            "plannerBypass": True,
            "routingReason": "single_intent_data_query",
        }
    return {
        "route": "knowledge",
        "queryComplexity": "SINGLE_HOP",
        "pendingTasks": ["knowledge_researcher"],
        "plannerBypass": True,
        "routingReason": "single_intent_knowledge_query",
    }


def classify_route(message: str, review_feedback: str | None = None) -> str:
    return classify_adaptive_route(message, review_feedback)["route"]


def build_sql_arguments(message: str) -> Dict[str, Any]:
    lowered = (message or "").lower()
    if "会话" in lowered or "session" in lowered:
        return {"queryType": "SESSION_COUNT_BY_USER"}
    if "文档" in lowered or "knowledge" in lowered or "知识库" in lowered:
        return {"queryType": "KB_DOCUMENT_COUNT", "knowledgeBaseId": 1}
    return {"queryType": "RUN_COUNT_BY_STATUS"}


def build_memory_facts(message: str, answer: str, citations: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    facts: List[Dict[str, Any]] = []
    trimmed_question = (message or "").strip()
    if trimmed_question:
        facts.append({"factType": "PREFERENCE", "factKey": "recent_user_goal", "factValue": trimmed_question[:160]})
    if answer:
        facts.append({"factType": "FACT", "factKey": "recent_agent_answer", "factValue": answer[:300]})
    if answer and "拒绝" in answer:
        facts.append({"factType": "REJECTED_ACTION", "factKey": "latest_rejected_action", "factValue": answer[:240]})
    if citations:
        facts.append({"factType": "FACT", "factKey": "last_citation_count", "factValue": str(len(citations))})
    if not answer:
        facts.append({"factType": "PENDING_TASK", "factKey": "follow_up_needed", "factValue": trimmed_question[:120] if trimmed_question else "follow_up_needed"})
    return facts


def build_answer(message: str, evidence: List[Dict[str, Any]], citations: List[Dict[str, Any]], human_decision: str | None = None) -> str:
    if human_decision and human_decision.lower() in {"reject", "rejected"}:
        return "人工审批拒绝了本次高风险执行请求，系统已停止后续动作。"
    if not evidence:
        return f"已收到问题：{message}。当前证据不足，我不会编造答案。"
    lines = ["基于当前工具结果，结论如下："]
    for item in evidence[:4]:
        summary = item.get("summary") or item.get("toolName") or "evidence"
        lines.append(f"- {summary}")
    if citations:
        cite_text = "、".join(str(item.get("documentId") or item.get("source") or item.get("chunkId")) for item in citations[:5])
        lines.append(f"引用来源：{cite_text}")
    return "\n".join(lines)


def _route_from_signals(has_data: bool, has_knowledge: bool) -> Literal["knowledge", "data", "mixed"]:
    if has_data and has_knowledge:
        return "mixed"
    if has_data:
        return "data"
    return "knowledge"


def _pending_tasks_for_route(route: str) -> List[str]:
    if route == "knowledge":
        return ["knowledge_researcher"]
    if route == "data":
        return ["data_analyst"]
    if route == "mixed":
        return ["knowledge_researcher", "data_analyst"]
    return []
