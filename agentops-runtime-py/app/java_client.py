from __future__ import annotations

from typing import Any, Dict

import httpx

from .config import settings
from .models import CheckpointPayload, StatusPayload, StepPayload


class JavaControlPlaneClient:
    def __init__(self) -> None:
        self.base_url = settings.control_plane_base_url.rstrip("/")
        self.internal_key = settings.internal_api_key

    def _headers(self, request_id: str | None = None) -> Dict[str, str]:
        headers = {"X-AgentOps-Internal-Key": self.internal_key}
        if request_id:
            headers["X-Request-Id"] = request_id
        return headers

    def fetch_context(self, session_id: int, request_id: str | None = None) -> Dict[str, Any]:
        with httpx.Client(timeout=20.0) as client:
            response = client.get(
                f"{self.base_url}/internal/runtime/sessions/{session_id}/context",
                headers=self._headers(request_id),
            )
            response.raise_for_status()
            return response.json()["data"]

    def save_step(self, run_id: int, payload: StepPayload, request_id: str | None = None) -> Dict[str, Any]:
        with httpx.Client(timeout=20.0) as client:
            response = client.post(
                f"{self.base_url}/internal/runtime/runs/{run_id}/steps",
                headers=self._headers(request_id),
                json=payload.model_dump(),
            )
            response.raise_for_status()
            return response.json()["data"]

    def update_status(self, run_id: int, payload: StatusPayload, request_id: str | None = None) -> None:
        with httpx.Client(timeout=20.0) as client:
            response = client.post(
                f"{self.base_url}/internal/runtime/runs/{run_id}/status",
                headers=self._headers(request_id),
                json=payload.model_dump(),
            )
            response.raise_for_status()

    def fetch_checkpoint(self, run_id: int, request_id: str | None = None) -> Dict[str, Any]:
        with httpx.Client(timeout=20.0) as client:
            response = client.get(
                f"{self.base_url}/internal/runtime/runs/{run_id}/checkpoint",
                headers=self._headers(request_id),
            )
            response.raise_for_status()
            return response.json()["data"]

    def save_checkpoint(self, run_id: int, payload: CheckpointPayload, request_id: str | None = None) -> Dict[str, Any]:
        with httpx.Client(timeout=20.0) as client:
            response = client.put(
                f"{self.base_url}/internal/runtime/runs/{run_id}/checkpoint",
                headers=self._headers(request_id),
                json=payload.model_dump(),
            )
            response.raise_for_status()
            return response.json()["data"]

    def delete_checkpoint(self, run_id: int, request_id: str | None = None) -> None:
        with httpx.Client(timeout=20.0) as client:
            response = client.delete(
                f"{self.base_url}/internal/runtime/runs/{run_id}/checkpoint",
                headers=self._headers(request_id),
            )
            response.raise_for_status()

    def call_tool(
        self,
        tool_name: str,
        arguments: Dict[str, Any],
        *,
        user_id: int,
        tenant_id: int | None,
        session_id: int,
        run_id: int,
        request_id: str | None = None,
    ) -> Dict[str, Any]:
        with httpx.Client(timeout=20.0) as client:
            response = client.post(
                f"{self.base_url}/internal/mcp/tools/call",
                headers=self._headers(request_id),
                json={
                    "toolName": tool_name,
                    "arguments": arguments,
                    "userId": user_id,
                    "tenantId": tenant_id,
                    "sessionId": session_id,
                    "runId": run_id,
                },
            )
            response.raise_for_status()
            return response.json()["data"]
