from __future__ import annotations

from typing import Any, Dict, List, Literal, Optional, TypedDict

from pydantic import BaseModel, Field


class ApiResponse(BaseModel):
    code: int = 0
    message: str = "success"
    data: Any = None


class RuntimeStartRunRequest(BaseModel):
    runId: int
    sessionId: int
    userId: int
    userInput: str
    executionMode: str = "USER"
    approvalPolicy: str = "MANUAL"
    orchestrationMode: str = "SINGLE_GRAPH"
    waitForCompletion: bool = False


class RuntimeResumeRunRequest(BaseModel):
    runId: int
    decision: str
    comment: Optional[str] = None
    resumeToken: Optional[str] = None
    checkpointVersion: Optional[int] = None
    waitForCompletion: bool = False


class RuntimeReplayRunRequest(BaseModel):
    runId: int
    checkpointVersion: Optional[int] = None
    waitForCompletion: bool = False


class RuntimeCommandResponse(BaseModel):
    accepted: bool = True
    status: str
    currentNode: str
    checkpointVersion: Optional[int] = None
    resumeToken: Optional[str] = None
    orchestrationMode: Optional[str] = None


class ToolResult(TypedDict):
    toolName: str
    success: bool
    summary: str
    data: Dict[str, Any]


class AgentState(TypedDict, total=False):
    runId: int
    sessionId: int
    userId: int
    userInput: str
    conversationSummary: str
    memoryFacts: List[Dict[str, Any]]
    taskPlan: List[Dict[str, Any]]
    pendingTasks: List[str]
    toolTrace: List[Dict[str, Any]]
    evidence: List[Dict[str, Any]]
    draftAnswer: str
    finalAnswer: str
    citations: List[Dict[str, Any]]
    artifacts: List[Dict[str, Any]]
    reviewFeedback: str
    loopCount: int
    reviewCount: int
    toolLoopCount: int
    currentNode: str
    currentStatus: str
    checkpointVersion: int
    humanTaskId: Optional[int]
    humanDecision: Optional[str]
    humanComment: Optional[str]
    resumeAfterNode: Optional[str]
    resumeToken: Optional[str]
    route: Literal["direct", "knowledge", "data", "mixed"]
    needsHuman: bool
    approvalPolicy: str
    executionMode: str
    orchestrationMode: str
    confidence: float
    messages: List[Dict[str, Any]]
    nodePath: List[str]
    errorMessage: Optional[str]
    eventSequence: int
    skillsUsed: List[str]
    skillTrace: List[Dict[str, Any]]
    costUsage: Dict[str, Any]
    approvalReason: Optional[str]
    replayRecovered: bool


class StepPayload(BaseModel):
    stepNo: Optional[int] = None
    stepType: str
    nodeId: Optional[str] = None
    nodeLabel: Optional[str] = None
    toolName: Optional[str] = None
    eventSequence: Optional[int] = None
    attemptNo: int = 1
    parentStepId: Optional[int] = None
    skillName: Optional[str] = None
    skillType: Optional[str] = None
    riskLevel: Optional[str] = None
    approvalPolicy: Optional[str] = None
    approvalReason: Optional[str] = None
    retryReason: Optional[str] = None
    input: Dict[str, Any] = Field(default_factory=dict)
    output: Dict[str, Any] = Field(default_factory=dict)
    stateBefore: Dict[str, Any] = Field(default_factory=dict)
    stateAfter: Dict[str, Any] = Field(default_factory=dict)
    observation: Dict[str, Any] = Field(default_factory=dict)
    costUsage: Dict[str, Any] = Field(default_factory=dict)
    latencyMs: Optional[int] = None
    modelName: Optional[str] = None
    promptVersion: Optional[str] = None
    success: bool = True
    errorMessage: Optional[str] = None


class StatusPayload(BaseModel):
    status: str
    currentNode: str
    graphName: str = "enterprise-copilot"
    graphVersion: str = "v2"
    orchestrationMode: str = "SINGLE_GRAPH"
    requiresHuman: bool = False
    resumeToken: Optional[str] = None
    checkpointVersion: Optional[int] = None
    eventSequence: Optional[int] = None
    finalAnswer: Optional[str] = None
    citations: List[Dict[str, Any]] = Field(default_factory=list)
    artifacts: List[Dict[str, Any]] = Field(default_factory=list)
    memoryFacts: List[Dict[str, Any]] = Field(default_factory=list)
    costUsage: Dict[str, Any] = Field(default_factory=dict)
    approvalReason: Optional[str] = None
    replayRecovered: bool = False
    errorMessage: Optional[str] = None


class CheckpointPayload(BaseModel):
    sessionId: int
    status: str
    currentNode: str
    orchestrationMode: str = "SINGLE_GRAPH"
    resumeToken: Optional[str] = None
    requiresHuman: bool = False
    humanTaskId: Optional[int] = None
    resumeAfterNode: Optional[str] = None
    eventSequence: int = 0
    loopCount: int = 0
    toolLoopCount: int = 0
    reviewCount: int = 0
    state: Dict[str, Any] = Field(default_factory=dict)
    lastError: Optional[str] = None
