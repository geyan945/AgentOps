from app.graph_runtime import AgentRuntime
from app.models import RuntimeStartRunRequest


class DummyClient:
    def __init__(self) -> None:
        self.steps = []

    def save_step(self, run_id, payload):
        self.steps.append(payload.model_dump())
        return {}

    def save_checkpoint(self, run_id, payload):
        return {"checkpointVersion": payload.eventSequence + 1}

    def update_status(self, run_id, payload):
        return None

    def delete_checkpoint(self, run_id):
        return None

    def call_tool(self, tool_name, arguments):
        raise AssertionError("tools should not be called in supervisor fast-path tests")


class RejectPlannerLLM:
    gemini_available = False
    model_name = "test-llm"
    mode = "mock"

    def plan(self, state):
        raise AssertionError("planner LLM should not be called for adaptive fast-path")


class CountingPlannerLLM:
    gemini_available = False
    model_name = "test-llm"
    mode = "mock"

    def __init__(self) -> None:
        self.plan_calls = 0

    def plan(self, state):
        self.plan_calls += 1
        return (
            type(
                "PlanResult",
                (),
                {
                    "route": "knowledge",
                    "pendingTasks": ["knowledge_researcher"],
                    "needsHuman": False,
                    "reason": "llm planner fallback",
                    "confidence": 0.73,
                    "model_dump": lambda self=None: {
                        "route": "knowledge",
                        "pendingTasks": ["knowledge_researcher"],
                        "needsHuman": False,
                        "reason": "llm planner fallback",
                        "confidence": 0.73,
                        "directAnswerAllowed": False,
                    },
                },
            )(),
            {"mode": "mock", "retryCount": 0, "latencyMs": 1},
        )


def _build_runtime():
    runtime = AgentRuntime()
    runtime.client = DummyClient()
    return runtime


def _build_state(runtime: AgentRuntime, user_input: str):
    request = RuntimeStartRunRequest(runId=1, sessionId=2, userId=3, userInput=user_input)
    return runtime._base_state(request, {"conversationSummary": "", "memoryFacts": [], "messages": []})


def test_supervisor_plan_uses_adaptive_fast_path_for_single_hop_knowledge():
    runtime = _build_runtime()
    runtime.llm = RejectPlannerLLM()
    state = _build_state(runtime, "介绍一下AgentOps的MCP工具平面")

    state = runtime._intake_guardrail(state)
    state = runtime._load_memory(state)
    state = runtime._supervisor_plan(state)

    assert state["route"] == "knowledge"
    assert state["queryComplexity"] == "SINGLE_HOP"
    assert state["planningMode"] == "ADAPTIVE_FAST_PATH"
    assert state["plannerBypass"] is True
    assert state["pendingTasks"] == ["knowledge_researcher"]
    assert state["currentNode"] == "knowledge_researcher"


def test_supervisor_plan_switches_to_llm_when_review_feedback_exists():
    runtime = _build_runtime()
    counting_llm = CountingPlannerLLM()
    runtime.llm = counting_llm
    state = _build_state(runtime, "介绍一下AgentOps的MCP工具平面")

    state = runtime._intake_guardrail(state)
    state = runtime._load_memory(state)
    state["reviewFeedback"] = "insufficient evidence"
    state["queryComplexity"] = "MULTI_STEP"
    state["plannerBypass"] = False
    state = runtime._supervisor_plan(state)

    assert counting_llm.plan_calls == 1
    assert state["planningMode"] == "LLM_PLANNER"
    assert state["pendingTasks"] == ["knowledge_researcher"]
