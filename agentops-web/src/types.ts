export type ApiResponse<T> = {
  code: number
  message: string
  data: T
}

export type AuthResponse = {
  token: string
  expireSeconds: number
}

export type SessionResponse = {
  id: number
  title: string
  status: string
  createdAt: string
  updatedAt: string
}

export type MessageResponse = {
  id: number
  role: string
  content: string
  metadataJson?: string
  createdAt: string
}

export type AgentRunStepResponse = {
  id: number
  stepNo: number
  stepType: string
  nodeId?: string
  nodeLabel?: string
  toolName?: string
  eventSequence?: number
  skillName?: string
  skillType?: string
  riskLevel?: string
  approvalPolicy?: string
  approvalReason?: string
  retryReason?: string
  observationJson?: string
  inputJson?: string
  outputJson?: string
  costUsageJson?: string
  latencyMs?: number
  modelName?: string
  promptVersion?: string
  success?: boolean
  errorMessage?: string
  createdAt?: string
}

export type AgentRunResponse = {
  id: number
  runId: number
  sessionId: number
  userId: number
  status: string
  runtimeType: string
  executionMode: string
  approvalPolicy: string
  orchestrationMode: string
  graphName: string
  graphVersion: string
  currentNode: string
  requiresHuman: boolean
  resumeToken?: string
  checkpointVersion?: number
  lastEventSequence?: number
  finalAnswer?: string
  citationsJson?: string
  artifactsJson?: string
  costUsageJson?: string
  approvalReason?: string
  replayRecovered?: boolean
  totalSteps?: number
  totalLatencyMs?: number
  errorMessage?: string
  createdAt?: string
  lastCheckpointAt?: string
  finishedAt?: string
  steps: AgentRunStepResponse[]
}

export type AgentRunEventResponse = {
  id: number
  runId: number
  eventSequence: number
  eventType: string
  stepId?: number
  nodeId?: string
  status?: string
  payloadJson?: string
  createdAt: string
}

export type AgentGraphNode = {
  id: string
  label: string
  status: string
  current: boolean
}

export type AgentGraphEdge = {
  source: string
  target: string
  label: string
}

export type AgentGraphResponse = {
  runId: number
  graphName: string
  graphVersion: string
  orchestrationMode: string
  currentNode: string
  status: string
  nodes: AgentGraphNode[]
  edges: AgentGraphEdge[]
}

export type HumanTaskResponse = {
  id: number
  runId: number
  sessionId: number
  taskType: string
  title: string
  currentNode: string
  reason: string
  requestJson?: string
  responseJson?: string
  status: string
}

export type EvalFailureSampleResponse = {
  resultId: number
  question: string
  actualTool: string
  reason: string
  latencyMs: number
}

export type EvalDashboardResponse = {
  datasetCount: number
  runCount: number
  runningRunCount: number
  completedRunCount: number
  failedResultCount: number
  avgScore: number
  avgGroundingScore: number
  avgCitationScore: number
  passRate: number
  avgLatencyMs: number
  latestFailedSamples: EvalFailureSampleResponse[]
}
