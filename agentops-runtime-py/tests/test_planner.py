from app.planner import build_sql_arguments, classify_adaptive_route, classify_route, is_greeting


def test_greeting_route_is_direct():
    assert is_greeting("你好，帮我看看") is True
    assert classify_route("hello agent") == "direct"


def test_classify_mixed_route():
    assert classify_route("统计知识库文档数量并总结Agent流程") == "mixed"


def test_adaptive_route_direct_is_conservative():
    decision = classify_adaptive_route("你好，帮我看看")
    assert decision["route"] == "direct"
    assert decision["queryComplexity"] == "DIRECT"
    assert decision["plannerBypass"] is True


def test_adaptive_route_single_hop_knowledge_bypasses_planner():
    decision = classify_adaptive_route("介绍一下AgentOps的MCP工具平面")
    assert decision["route"] == "knowledge"
    assert decision["queryComplexity"] == "SINGLE_HOP"
    assert decision["pendingTasks"] == ["knowledge_researcher"]
    assert decision["plannerBypass"] is True


def test_adaptive_route_single_hop_data_bypasses_planner():
    decision = classify_adaptive_route("统计知识库文档数量")
    assert decision["route"] == "data"
    assert decision["queryComplexity"] == "SINGLE_HOP"
    assert decision["pendingTasks"] == ["data_analyst"]
    assert decision["plannerBypass"] is True


def test_adaptive_route_mixed_is_multi_step():
    decision = classify_adaptive_route("统计知识库文档数量并总结Agent流程")
    assert decision["route"] == "mixed"
    assert decision["queryComplexity"] == "MULTI_STEP"
    assert decision["plannerBypass"] is False


def test_adaptive_route_review_feedback_forces_multi_step():
    decision = classify_adaptive_route("介绍AgentOps架构", review_feedback="insufficient evidence")
    assert decision["route"] == "knowledge"
    assert decision["queryComplexity"] == "MULTI_STEP"
    assert decision["plannerBypass"] is False


def test_build_sql_arguments():
    assert build_sql_arguments("please count my sessions")["queryType"] == "SESSION_COUNT_BY_USER"
    assert build_sql_arguments("统计知识库文档")["queryType"] == "KB_DOCUMENT_COUNT"
