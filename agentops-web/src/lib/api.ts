import type { AgentGraphResponse, AgentRunResponse, ApiResponse, AuthResponse, EvalDashboardResponse, HumanTaskResponse, MessageResponse, SessionResponse } from '../types'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:18084'

async function request<T>(path: string, options: RequestInit = {}, token?: string): Promise<T> {
  const headers = new Headers(options.headers)
  headers.set('Content-Type', 'application/json')
  if (token) {
    headers.set('Authorization', `Bearer ${token}`)
  }
  const response = await fetch(`${API_BASE_URL}${path}`, { ...options, headers })
  const payload = (await response.json()) as ApiResponse<T>
  if (!response.ok || payload.code !== 0) {
    throw new Error(payload.message || 'request failed')
  }
  return payload.data
}

export function login(username: string, password: string) {
  return request<AuthResponse>('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify({ username, password }),
  })
}

export function fetchSessions(token: string) {
  return request<SessionResponse[]>('/api/sessions', {}, token)
}

export function createSession(token: string, title: string) {
  return request<SessionResponse>('/api/sessions', {
    method: 'POST',
    body: JSON.stringify({ title }),
  }, token)
}

export function fetchSessionMessages(token: string, sessionId: number) {
  return request<MessageResponse[]>(`/api/sessions/${sessionId}/messages`, {}, token)
}

export function createAgentRun(
  token: string,
  sessionId: number,
  message: string,
  options?: { executionMode?: string; approvalPolicy?: string },
) {
  return request<AgentRunResponse>('/api/agent/runs', {
    method: 'POST',
    body: JSON.stringify({ sessionId, message, ...options }),
  }, token)
}

export function fetchRun(token: string, runId: number) {
  return request<AgentRunResponse>(`/api/agent/runs/${runId}`, {}, token)
}

export function fetchGraph(token: string, runId: number) {
  return request<AgentGraphResponse>(`/api/agent/runs/${runId}/graph`, {}, token)
}

export function fetchHumanTasks(token: string) {
  return request<HumanTaskResponse[]>('/api/human-tasks', {}, token)
}

export function approveHumanTask(token: string, taskId: number, decision: 'APPROVE' | 'REJECT') {
  return request<AgentRunResponse>(`/api/human-tasks/${taskId}/decision`, {
    method: 'POST',
    body: JSON.stringify({ decision, comment: `${decision.toLowerCase()} via AgentOps web` }),
  }, token)
}

export function fetchEvalDashboard(token: string) {
  return request<EvalDashboardResponse>('/api/evals/dashboard', {}, token)
}
