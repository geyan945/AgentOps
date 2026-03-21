from app.planner import build_sql_arguments, classify_route, is_greeting


def test_greeting_route_is_direct():
    assert is_greeting("你好，帮我看看") is True
    assert classify_route("hello agent") == "direct"


def test_classify_mixed_route():
    assert classify_route("统计知识库文档数量并总结Agent流程") == "mixed"


def test_build_sql_arguments():
    assert build_sql_arguments("please count my sessions")["queryType"] == "SESSION_COUNT_BY_USER"
    assert build_sql_arguments("统计知识库文档")["queryType"] == "KB_DOCUMENT_COUNT"
