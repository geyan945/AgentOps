import type { AgentGraphResponse, AgentRunEventResponse, AgentRunResponse, ApiResponse, AuthResponse, EvalDashboardResponse, HumanTaskResponse, MessageResponse, SessionResponse } from '../types'

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
  options?: { executionMode?: string; approvalPolicy?: string; orchestrationMode?: string },
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

export function replayRun(token: string, runId: number, checkpointVersion?: number) {
  return request<AgentRunResponse>(`/api/agent/runs/${runId}/replay`, {
    method: 'POST',
    body: JSON.stringify({ checkpointVersion }),
  }, token)
}

export function fetchRunEventHistory(token: string, runId: number) {
  return request<AgentRunEventResponse[]>(`/api/agent/runs/${runId}/events/history`, {}, token)
}

export function subscribeRunEvents(
  token: string,
  runId: number,
  handlers: {
    onEvent: (eventType: string, event: AgentRunEventResponse | Record<string, unknown>) => void
    onError?: (error: Error) => void
  },
) {
  const controller = new AbortController()
  ;(async () => {
    const response = await fetch(`${API_BASE_URL}/api/agent/runs/${runId}/events`, {
      method: 'GET',
      headers: {
        Authorization: `Bearer ${token}`,
        Accept: 'text/event-stream',
      },
      signal: controller.signal,
    })
    if (!response.ok || !response.body) {
      throw new Error('event stream unavailable')
    }
    const reader = response.body.getReader()
    const decoder = new TextDecoder('utf-8')
    let buffer = ''
    while (true) {
      const { done, value } = await reader.read()
      if (done) {
        break
      }
      buffer += decoder.decode(value, { stream: true })
      const chunks = buffer.split('\n\n')
      buffer = chunks.pop() ?? ''
      for (const chunk of chunks) {
        const lines = chunk.split(/\r?\n/)
        let eventType = 'message'
        const dataLines: string[] = []
        for (const line of lines) {
          if (line.startsWith('event:')) {
            eventType = line.slice(6).trim()
          }
          if (line.startsWith('data:')) {
            dataLines.push(line.slice(5).trim())
          }
        }
        if (!dataLines.length) {
          continue
        }
        const raw = dataLines.join('\n')
        try {
          handlers.onEvent(eventType, JSON.parse(raw))
        } catch {
          handlers.onEvent(eventType, { raw })
        }
      }
    }
  })().catch((error) => {
    if (!controller.signal.aborted) {
      handlers.onError?.(error instanceof Error ? error : new Error('event stream failed'))
    }
  })
  return () => controller.abort()
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
