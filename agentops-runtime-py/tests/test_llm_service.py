from app.llm_service import GeminiLLMService


def test_plan_prompt_contains_cod_and_adaptive_rag_hints():
    service = GeminiLLMService()
    prompt = service._build_plan_prompt(
        {
            "route": "knowledge",
            "queryComplexity": "SINGLE_HOP",
            "routingReason": "single_intent_knowledge_query",
            "userInput": "介绍一下AgentOps的MCP工具平面",
            "conversationSummary": "recent agent runtime discussion",
            "reviewFeedback": "",
            "toolTrace": [{"toolName": "kb_search"}],
            "loopCount": 0,
        }
    )

    assert "Chain-of-Draft" in prompt
    assert "telegraphic keywords only" in prompt
    assert "Do not reveal reasoning, scratchpad, or draft content." in prompt
    assert "Return JSON only" in prompt
    assert "AdaptiveRouteHint: knowledge" in prompt
    assert "AdaptiveComplexity: SINGLE_HOP" in prompt
    assert "AdaptiveRoutingReason: single_intent_knowledge_query" in prompt
