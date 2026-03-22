from __future__ import annotations

from typing import Any, Dict, List


def is_greeting(message: str) -> bool:
    lowered = (message or "").lower()
    return any(token in lowered for token in ["hello", "hi", "你好", "您好"])


def classify_route(message: str, review_feedback: str | None = None) -> str:
    lowered = (message or "").lower()
    if is_greeting(lowered):
        return "direct"
    has_data = any(token in lowered for token in ["统计", "count", "多少", "数量", "sql", "报表", "趋势"])
    has_knowledge = any(token in lowered for token in ["文档", "知识", "kb", "agent", "设计", "流程", "what", "how", "explain", "总结", "总结下"])
    if review_feedback and "insufficient" in review_feedback.lower():
        if has_data and has_knowledge:
            return "mixed"
        if has_data:
            return "data"
        return "knowledge"
    if has_data and has_knowledge:
        return "mixed"
    if has_data:
        return "data"
    return "knowledge"


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
