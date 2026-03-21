from __future__ import annotations

from fastapi import BackgroundTasks, FastAPI, Header, HTTPException

from .config import settings
from .graph_runtime import AgentRuntime
from .models import ApiResponse, RuntimeCommandResponse, RuntimeResumeRunRequest, RuntimeStartRunRequest

app = FastAPI(title="AgentOps Runtime", version="2.0.0")
runtime = AgentRuntime()


def ensure_internal_access(internal_key: str | None) -> None:
    if internal_key != settings.internal_api_key:
        raise HTTPException(status_code=403, detail="invalid internal key")


@app.get("/runtime/health")
def health() -> ApiResponse:
    return ApiResponse(data=runtime.health())


@app.post("/runtime/graphs/enterprise-copilot/runs")
def start_run(
    request: RuntimeStartRunRequest,
    background_tasks: BackgroundTasks,
    x_agentops_internal_key: str | None = Header(default=None),
) -> ApiResponse:
    ensure_internal_access(x_agentops_internal_key)
    if request.waitForCompletion:
        return ApiResponse(data=runtime.start_run(request))
    background_tasks.add_task(runtime.start_run_background, request)
    return ApiResponse(data=RuntimeCommandResponse(accepted=True, status="RUNNING", currentNode="intake_guardrail"))


@app.post("/runtime/graphs/enterprise-copilot/runs/{run_id}/resume")
def resume_run(
    run_id: int,
    request: RuntimeResumeRunRequest,
    background_tasks: BackgroundTasks,
    x_agentops_internal_key: str | None = Header(default=None),
) -> ApiResponse:
    ensure_internal_access(x_agentops_internal_key)
    request.runId = run_id
    if request.waitForCompletion:
        return ApiResponse(data=runtime.resume_run(request))
    runtime.validate_resume_request(request)
    background_tasks.add_task(runtime.resume_run_background, request)
    return ApiResponse(data=RuntimeCommandResponse(accepted=True, status="RUNNING", currentNode="supervisor_plan", checkpointVersion=request.checkpointVersion))
