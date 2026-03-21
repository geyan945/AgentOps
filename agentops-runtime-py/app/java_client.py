from __future__ import annotations

from typing import Any, Dict

import httpx

from .config import settings
from .models import CheckpointPayload, StatusPayload, StepPayload


class JavaControlPlaneClient:
    def __init__(self) -> None:
        self.base_url = settings.control_plane_base_url.rstrip("/")
        self.internal_key = settings.internal_api_key

    def _headers(self) -> Dict[str, str]:
        return {"X-AgentOps-Internal-Key": self.internal_key}

    def fetch_context(self, session_id: int) -> Dict[str, Any]:
        with httpx.Client(timeout=20.0) as client:
            response = client.get(
                f"{self.base_url}/internal/runtime/sessions/{session_id}/context",
                headers=self._headers(),
            )
            response.raise_for_status()
            return response.json()["data"]

    def save_step(self, run_id: int, payload: StepPayload) -> Dict[str, Any]:
        with httpx.Client(timeout=20.0) as client:
            response = client.post(
                f"{self.base_url}/internal/runtime/runs/{run_id}/steps",
                headers=self._headers(),
                json=payload.model_dump(),
            )
            response.raise_for_status()
            return response.json()["data"]

    def update_status(self, run_id: int, payload: StatusPayload) -> None:
        with httpx.Client(timeout=20.0) as client:
            response = client.post(
                f"{self.base_url}/internal/runtime/runs/{run_id}/status",
                headers=self._headers(),
                json=payload.model_dump(),
            )
            response.raise_for_status()

    def fetch_checkpoint(self, run_id: int) -> Dict[str, Any]:
        with httpx.Client(timeout=20.0) as client:
            response = client.get(
                f"{self.base_url}/internal/runtime/runs/{run_id}/checkpoint",
                headers=self._headers(),
            )
            response.raise_for_status()
            return response.json()["data"]

    def save_checkpoint(self, run_id: int, payload: CheckpointPayload) -> Dict[str, Any]:
        with httpx.Client(timeout=20.0) as client:
            response = client.put(
                f"{self.base_url}/internal/runtime/runs/{run_id}/checkpoint",
                headers=self._headers(),
                json=payload.model_dump(),
            )
            response.raise_for_status()
            return response.json()["data"]

    def delete_checkpoint(self, run_id: int) -> None:
        with httpx.Client(timeout=20.0) as client:
            response = client.delete(
                f"{self.base_url}/internal/runtime/runs/{run_id}/checkpoint",
                headers=self._headers(),
            )
            response.raise_for_status()

    def call_tool(self, tool_name: str, arguments: Dict[str, Any]) -> Dict[str, Any]:
        with httpx.Client(timeout=20.0) as client:
            response = client.post(
                f"{self.base_url}/api/mcp/tools/call",
                json={"toolName": tool_name, "arguments": arguments},
            )
            response.raise_for_status()
            return response.json()["data"]
