from __future__ import annotations

import os
from dataclasses import dataclass


@dataclass(frozen=True)
class Settings:
    control_plane_base_url: str = os.getenv("AGENTOPS_CONTROL_PLANE_BASE_URL", "http://localhost:18084")
    internal_api_key: str = os.getenv("AGENTOPS_INTERNAL_KEY", "agentops-internal-key")
    gemini_api_key: str = os.getenv("GEMINI_API_KEY", "")
    gemini_model: str = os.getenv("AGENTOPS_GEMINI_MODEL", "gemini-2.5-flash")
    llm_mode: str = os.getenv("AGENTOPS_LLM_MODE", "live")
    max_graph_hops: int = int(os.getenv("AGENTOPS_MAX_GRAPH_HOPS", "6"))
    max_replans: int = int(os.getenv("AGENTOPS_MAX_REPLANS", "2"))
    max_tool_loops: int = int(os.getenv("AGENTOPS_MAX_TOOL_LOOPS", "3"))
    llm_max_retries: int = int(os.getenv("AGENTOPS_LLM_MAX_RETRIES", "2"))


settings = Settings()
