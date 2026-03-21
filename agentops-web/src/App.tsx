import { useEffect, useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import ReactECharts from 'echarts-for-react'
import ReactFlow, { Background, Controls, MiniMap, type Edge, type Node } from 'reactflow'
import { Link, Route, Routes, useLocation, useNavigate } from 'react-router-dom'
import 'reactflow/dist/style.css'
import './App.css'
import {
  approveHumanTask,
  createAgentRun,
  createSession,
  fetchEvalDashboard,
  fetchGraph,
  fetchHumanTasks,
  fetchRun,
  fetchSessionMessages,
  fetchSessions,
  login,
} from './lib/api'
import type { AgentGraphResponse, AgentRunResponse, EvalDashboardResponse, HumanTaskResponse, MessageResponse, SessionResponse } from './types'

function App() {
  const [token, setToken] = useState<string>(() => localStorage.getItem('agentops_token') ?? '')
  const [username, setUsername] = useState('demo')
  const [password, setPassword] = useState('123456')
  const [selectedSessionId, setSelectedSessionId] = useState<number | null>(null)
  const [currentRunId, setCurrentRunId] = useState<number | null>(null)
  const [runMessage, setRunMessage] = useState('请总结 AgentOps 的工具调用链路，并统计当前会话数量')
  const [newSessionTitle, setNewSessionTitle] = useState('AgentOps 工作台')
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const location = useLocation()

  const loginMutation = useMutation({
    mutationFn: () => login(username, password),
    onSuccess: (response) => {
      localStorage.setItem('agentops_token', response.token)
      setToken(response.token)
    },
  })

  const sessionsQuery = useQuery({
    queryKey: ['sessions', token],
    queryFn: () => fetchSessions(token),
    enabled: Boolean(token),
  })

  useEffect(() => {
    if (!selectedSessionId && sessionsQuery.data?.length) {
      setSelectedSessionId(sessionsQuery.data[0].id)
    }
  }, [selectedSessionId, sessionsQuery.data])

  const messagesQuery = useQuery({
    queryKey: ['messages', token, selectedSessionId],
    queryFn: () => fetchSessionMessages(token, selectedSessionId!),
    enabled: Boolean(token && selectedSessionId),
  })

  const createSessionMutation = useMutation({
    mutationFn: () => createSession(token, newSessionTitle),
    onSuccess: (session) => {
      queryClient.invalidateQueries({ queryKey: ['sessions', token] })
      setSelectedSessionId(session.id)
      navigate('/')
    },
  })

  const runMutation = useMutation({
    mutationFn: () => createAgentRun(token, selectedSessionId!, runMessage),
    onSuccess: (run) => {
      setCurrentRunId(run.runId)
      navigate('/')
    },
  })

  const runQuery = useQuery({
    queryKey: ['run', token, currentRunId],
    queryFn: () => fetchRun(token, currentRunId!),
    enabled: Boolean(token && currentRunId),
    refetchInterval: (query) => {
      const data = query.state.data as AgentRunResponse | undefined
      return data && ['RUNNING', 'QUEUED', 'WAITING_HUMAN'].includes(data.status) ? 2000 : false
    },
  })

  const graphQuery = useQuery({
    queryKey: ['graph', token, currentRunId],
    queryFn: () => fetchGraph(token, currentRunId!),
    enabled: Boolean(token && currentRunId),
    refetchInterval: () => (runQuery.data && ['RUNNING', 'QUEUED', 'WAITING_HUMAN'].includes(runQuery.data.status) ? 2000 : false),
  })

  const approvalsQuery = useQuery({
    queryKey: ['humanTasks', token],
    queryFn: () => fetchHumanTasks(token),
    enabled: Boolean(token),
    refetchInterval: 4000,
  })

  const dashboardQuery = useQuery({
    queryKey: ['evalDashboard', token],
    queryFn: () => fetchEvalDashboard(token),
    enabled: Boolean(token),
    refetchInterval: 5000,
  })

  const approvalMutation = useMutation({
    mutationFn: ({ taskId, decision }: { taskId: number; decision: 'APPROVE' | 'REJECT' }) =>
      approveHumanTask(token, taskId, decision),
    onSuccess: (run) => {
      setCurrentRunId(run.runId)
      queryClient.invalidateQueries({ queryKey: ['humanTasks', token] })
      queryClient.invalidateQueries({ queryKey: ['run', token, run.runId] })
      queryClient.invalidateQueries({ queryKey: ['graph', token, run.runId] })
      navigate('/')
    },
  })

  if (!token) {
    return (
      <div className="min-h-screen bg-[radial-gradient(circle_at_top_left,_rgba(249,115,22,0.18),_transparent_45%),linear-gradient(160deg,#f7f1e8,#eef6ff)] px-6 py-10 text-slate-900">
        <div className="mx-auto max-w-6xl rounded-[2rem] border border-white/70 bg-white/80 p-8 shadow-[0_30px_80px_rgba(15,23,42,0.14)] backdrop-blur">
          <p className="text-sm uppercase tracking-[0.35em] text-orange-600">AgentOps</p>
          <div className="mt-6 grid gap-8 lg:grid-cols-[1.15fr_0.85fr]">
            <div>
              <h1 className="max-w-3xl font-serif text-5xl leading-tight text-slate-950">
                AgentOps 控制面与 LangGraph Runtime 的企业级智能体工作台
              </h1>
              <p className="mt-4 max-w-2xl text-lg text-slate-600">
                这个工作台对应新的 AgentOps 2.0 主链：Java control plane、Python runtime、graph trace、人工审批和评测闭环。
              </p>
            </div>
            <form
              className="rounded-[1.5rem] border border-slate-200 bg-slate-950 p-6 text-white shadow-2xl"
              onSubmit={(event) => {
                event.preventDefault()
                loginMutation.mutate()
              }}
            >
              <h2 className="text-xl font-semibold">登录 AgentOps</h2>
              <div className="mt-6 space-y-4">
                <label className="block">
                  <span className="mb-1 block text-sm text-slate-300">用户名</span>
                  <input value={username} onChange={(event) => setUsername(event.target.value)} className="w-full rounded-xl border border-slate-700 bg-slate-900 px-4 py-3 outline-none" />
                </label>
                <label className="block">
                  <span className="mb-1 block text-sm text-slate-300">密码</span>
                  <input type="password" value={password} onChange={(event) => setPassword(event.target.value)} className="w-full rounded-xl border border-slate-700 bg-slate-900 px-4 py-3 outline-none" />
                </label>
              </div>
              <button className="mt-6 w-full rounded-xl bg-orange-500 px-4 py-3 font-medium text-white transition hover:bg-orange-600" type="submit">
                {loginMutation.isPending ? '登录中...' : '进入工作台'}
              </button>
              {loginMutation.error ? <p className="mt-4 text-sm text-rose-300">登录失败，请先用后端注册一个用户。</p> : null}
            </form>
          </div>
        </div>
      </div>
    )
  }

  return (
    <div className="min-h-screen bg-[linear-gradient(145deg,#f6f0e5,#ecf5ff)] text-slate-900">
      <header className="sticky top-0 z-20 border-b border-white/60 bg-white/85 px-6 py-4 backdrop-blur">
        <div className="mx-auto flex max-w-7xl items-center justify-between gap-4">
          <div>
            <p className="text-xs uppercase tracking-[0.35em] text-orange-600">AgentOps</p>
            <h1 className="font-serif text-2xl text-slate-950">Enterprise Copilot Workspace</h1>
          </div>
          <nav className="flex items-center gap-3 rounded-full border border-slate-200 bg-white/70 px-2 py-2 text-sm">
            <NavItem to="/" label="Workspace" active={location.pathname === '/'} />
            <NavItem to="/approvals" label="Approvals" active={location.pathname === '/approvals'} />
            <NavItem to="/evals" label="Eval" active={location.pathname === '/evals'} />
          </nav>
          <button
            className="rounded-full border border-slate-300 px-4 py-2 text-sm text-slate-600 transition hover:border-slate-950 hover:text-slate-950"
            onClick={() => {
              localStorage.removeItem('agentops_token')
              setToken('')
            }}
          >
            退出
          </button>
        </div>
      </header>

      <main className="mx-auto max-w-7xl px-6 py-6">
        <Routes>
          <Route
            path="/"
            element={
              <WorkspaceView
                sessions={sessionsQuery.data ?? []}
                messages={messagesQuery.data ?? []}
                selectedSessionId={selectedSessionId}
                setSelectedSessionId={setSelectedSessionId}
                currentRun={runQuery.data ?? null}
                currentGraph={graphQuery.data ?? null}
                runMessage={runMessage}
                setRunMessage={setRunMessage}
                runPending={runMutation.isPending}
                onRun={() => selectedSessionId && runMutation.mutate()}
                newSessionTitle={newSessionTitle}
                setNewSessionTitle={setNewSessionTitle}
                onCreateSession={() => createSessionMutation.mutate()}
                approvals={approvalsQuery.data ?? []}
              />
            }
          />
          <Route
            path="/approvals"
            element={<ApprovalsView tasks={approvalsQuery.data ?? []} onDecision={(taskId, decision) => approvalMutation.mutate({ taskId, decision })} />}
          />
          <Route path="/evals" element={<EvalView dashboard={dashboardQuery.data ?? null} />} />
        </Routes>
      </main>
    </div>
  )
}

function WorkspaceView(props: {
  sessions: SessionResponse[]
  messages: MessageResponse[]
  selectedSessionId: number | null
  setSelectedSessionId: (id: number) => void
  currentRun: AgentRunResponse | null
  currentGraph: AgentGraphResponse | null
  runMessage: string
  setRunMessage: (value: string) => void
  runPending: boolean
  onRun: () => void
  newSessionTitle: string
  setNewSessionTitle: (value: string) => void
  onCreateSession: () => void
  approvals: HumanTaskResponse[]
}) {
  const flow = useMemo(() => buildFlow(props.currentGraph), [props.currentGraph])
  return (
    <div className="grid gap-6 xl:grid-cols-[280px_minmax(0,1.2fr)_420px]">
      <section className="rounded-[1.5rem] border border-white/70 bg-white/80 p-5 shadow-lg backdrop-blur">
        <div className="flex items-center justify-between">
          <h2 className="text-lg font-semibold">会话</h2>
          <span className="rounded-full bg-orange-100 px-3 py-1 text-xs font-medium text-orange-700">{props.sessions.length}</span>
        </div>
        <div className="mt-4 space-y-3">
          <input value={props.newSessionTitle} onChange={(event) => props.setNewSessionTitle(event.target.value)} className="w-full rounded-xl border border-slate-200 px-4 py-3 text-sm" placeholder="新会话标题" />
          <button className="w-full rounded-xl bg-slate-950 px-4 py-3 text-sm font-medium text-white" onClick={props.onCreateSession}>创建会话</button>
        </div>
        <div className="mt-6 space-y-3">
          {props.sessions.map((session) => (
            <button
              key={session.id}
              className={`w-full rounded-2xl border px-4 py-4 text-left transition ${props.selectedSessionId === session.id ? 'border-slate-950 bg-slate-950 text-white' : 'border-slate-200 bg-slate-50 text-slate-700 hover:border-orange-300'}`}
              onClick={() => props.setSelectedSessionId(session.id)}
            >
              <p className="font-medium">{session.title}</p>
              <p className="mt-1 text-xs opacity-70">{session.status}</p>
            </button>
          ))}
        </div>
      </section>

      <section className="rounded-[1.75rem] border border-white/70 bg-white/80 p-5 shadow-lg backdrop-blur">
        <div className="flex items-center justify-between">
          <div>
            <p className="text-sm uppercase tracking-[0.2em] text-orange-600">Control Plane</p>
            <h2 className="text-xl font-semibold">Chat + Run Playback</h2>
          </div>
          {props.currentRun ? <RunStatusBadge status={props.currentRun.status} /> : null}
        </div>
        <div className="mt-5 rounded-[1.25rem] border border-slate-200 bg-slate-50 p-4">
          <textarea value={props.runMessage} onChange={(event) => props.setRunMessage(event.target.value)} className="min-h-28 w-full resize-none bg-transparent text-sm outline-none" />
          <div className="mt-4 flex items-center justify-between">
            <div className="text-sm text-slate-500">工作台每 2 秒轮询 run 与 graph 状态</div>
            <button className="rounded-full bg-orange-500 px-5 py-2 text-sm font-medium text-white hover:bg-orange-600" onClick={props.onRun}>
              {props.runPending ? '启动中...' : '启动 Run'}
            </button>
          </div>
        </div>
        <div className="mt-6 grid gap-4 lg:grid-cols-[1fr_1fr]">
          <div className="rounded-[1.25rem] border border-slate-200 bg-white p-4">
            <h3 className="font-semibold">消息流</h3>
            <div className="mt-4 max-h-[28rem] space-y-3 overflow-auto pr-1">
              {props.messages.map((message) => (
                <article key={message.id} className={`rounded-2xl px-4 py-3 text-sm ${message.role === 'assistant' ? 'bg-slate-950 text-white' : message.role === 'tool' ? 'bg-orange-50 text-slate-800' : 'bg-slate-100 text-slate-800'}`}>
                  <p className="mb-1 text-[11px] uppercase tracking-[0.2em] opacity-70">{message.role}</p>
                  <p className="whitespace-pre-wrap">{message.content}</p>
                </article>
              ))}
            </div>
          </div>
          <div className="rounded-[1.25rem] border border-slate-200 bg-white p-4">
            <h3 className="font-semibold">Step Trace</h3>
            <div className="mt-4 max-h-[28rem] space-y-3 overflow-auto pr-1">
              {props.currentRun?.steps?.map((step) => (
                <article key={step.id} className="rounded-2xl border border-slate-200 bg-slate-50 p-4">
                  <div className="flex items-center justify-between">
                    <span className="text-sm font-semibold">{step.stepType}</span>
                    <span className="text-xs text-slate-500">{step.nodeLabel ?? step.nodeId ?? '-'}</span>
                  </div>
                  <p className="mt-2 text-xs text-slate-500">{step.toolName ? `tool: ${step.toolName}` : 'graph event'}</p>
                  <pre className="mt-3 overflow-auto rounded-xl bg-slate-950 p-3 text-xs text-slate-100">{step.observationJson || step.outputJson || step.inputJson || '{}'}</pre>
                </article>
              ))}
            </div>
          </div>
        </div>
      </section>

      <section className="space-y-6">
        <div className="rounded-[1.75rem] border border-white/70 bg-white/80 p-5 shadow-lg backdrop-blur">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm uppercase tracking-[0.2em] text-orange-600">LangGraph Runtime</p>
              <h2 className="text-xl font-semibold">Execution Graph</h2>
            </div>
            {props.currentGraph ? <p className="text-xs text-slate-500">{props.currentGraph.graphName} / {props.currentGraph.graphVersion}</p> : null}
          </div>
          <div className="mt-4 h-[22rem] overflow-hidden rounded-[1.25rem] border border-slate-200">
            <ReactFlow nodes={flow.nodes} edges={flow.edges} fitView nodesDraggable={false} elementsSelectable={false}>
              <Background />
              <MiniMap zoomable pannable />
              <Controls showInteractive={false} />
            </ReactFlow>
          </div>
        </div>
        <div className="rounded-[1.75rem] border border-white/70 bg-white/80 p-5 shadow-lg backdrop-blur">
          <div className="flex items-center justify-between">
            <h2 className="text-xl font-semibold">Pending Approvals</h2>
            <span className="rounded-full bg-slate-100 px-3 py-1 text-xs text-slate-600">{props.approvals.filter((item) => item.status === 'PENDING').length} waiting</span>
          </div>
          <div className="mt-4 space-y-3">
            {props.approvals.slice(0, 4).map((task) => (
              <article key={task.id} className="rounded-2xl border border-slate-200 bg-slate-50 p-4">
                <p className="text-sm font-semibold">{task.title}</p>
                <p className="mt-2 text-sm text-slate-600">{task.reason}</p>
                <p className="mt-3 text-xs uppercase tracking-[0.2em] text-slate-400">{task.status}</p>
              </article>
            ))}
          </div>
        </div>
      </section>
    </div>
  )
}

function ApprovalsView(props: { tasks: HumanTaskResponse[]; onDecision: (taskId: number, decision: 'APPROVE' | 'REJECT') => void }) {
  return (
    <section className="rounded-[1.75rem] border border-white/70 bg-white/80 p-6 shadow-lg backdrop-blur">
      <div className="flex items-center justify-between">
        <div>
          <p className="text-sm uppercase tracking-[0.2em] text-orange-600">Human-in-the-loop</p>
          <h2 className="text-2xl font-semibold">Approval Inbox</h2>
        </div>
        <span className="rounded-full bg-orange-100 px-3 py-1 text-xs font-medium text-orange-700">{props.tasks.length} tasks</span>
      </div>
      <div className="mt-6 grid gap-4 lg:grid-cols-2">
        {props.tasks.map((task) => (
          <article key={task.id} className="rounded-[1.5rem] border border-slate-200 bg-slate-50 p-5">
            <div className="flex items-center justify-between">
              <h3 className="font-semibold">{task.title}</h3>
              <RunStatusBadge status={task.status} />
            </div>
            <p className="mt-3 text-sm text-slate-600">{task.reason}</p>
            <pre className="mt-4 overflow-auto rounded-xl bg-slate-950 p-3 text-xs text-slate-100">{task.requestJson ?? '{}'}</pre>
            {task.status === 'PENDING' ? (
              <div className="mt-4 flex gap-3">
                <button className="rounded-full bg-slate-950 px-4 py-2 text-sm text-white" onClick={() => props.onDecision(task.id, 'APPROVE')}>Approve</button>
                <button className="rounded-full border border-slate-300 px-4 py-2 text-sm text-slate-700" onClick={() => props.onDecision(task.id, 'REJECT')}>Reject</button>
              </div>
            ) : null}
          </article>
        ))}
      </div>
    </section>
  )
}

function EvalView(props: { dashboard: EvalDashboardResponse | null }) {
  const option = useMemo(
    () => ({
      tooltip: {},
      radar: {
        indicator: [
          { name: 'Pass Rate', max: 1 },
          { name: 'Avg Score', max: 1 },
          { name: 'Grounding', max: 1 },
          { name: 'Citation', max: 1 },
          { name: 'Latency', max: Math.max(1, props.dashboard?.avgLatencyMs ?? 1) },
        ],
      },
      series: [
        {
          type: 'radar',
          data: [
            {
              value: [
                props.dashboard?.passRate ?? 0,
                props.dashboard?.avgScore ?? 0,
                props.dashboard?.avgGroundingScore ?? 0,
                props.dashboard?.avgCitationScore ?? 0,
                props.dashboard?.avgLatencyMs ?? 0,
              ],
              areaStyle: { color: 'rgba(249,115,22,0.24)' },
              lineStyle: { color: '#f97316' },
            },
          ],
        },
      ],
    }),
    [props.dashboard],
  )

  return (
    <section className="grid gap-6 xl:grid-cols-[1fr_420px]">
      <div className="rounded-[1.75rem] border border-white/70 bg-white/80 p-6 shadow-lg backdrop-blur">
        <p className="text-sm uppercase tracking-[0.2em] text-orange-600">Trace & Eval</p>
        <h2 className="mt-2 text-2xl font-semibold">Quality Dashboard</h2>
        <div className="mt-6 grid gap-4 md:grid-cols-2 xl:grid-cols-4">
          <MetricCard label="Datasets" value={props.dashboard?.datasetCount ?? 0} />
          <MetricCard label="Runs" value={props.dashboard?.runCount ?? 0} />
          <MetricCard label="Pass Rate" value={`${Math.round((props.dashboard?.passRate ?? 0) * 100)}%`} />
          <MetricCard label="Avg Score" value={(props.dashboard?.avgScore ?? 0).toFixed(2)} />
        </div>
        <div className="mt-6 rounded-[1.5rem] border border-slate-200 bg-slate-50 p-4">
          <ReactECharts option={option} style={{ height: 360 }} />
        </div>
      </div>
      <div className="rounded-[1.75rem] border border-white/70 bg-white/80 p-6 shadow-lg backdrop-blur">
        <h3 className="text-xl font-semibold">Latest Failures</h3>
        <div className="mt-5 space-y-3">
          {props.dashboard?.latestFailedSamples?.map((item) => (
            <article key={item.resultId} className="rounded-2xl border border-slate-200 bg-slate-50 p-4">
              <p className="text-sm font-semibold">{item.question}</p>
              <p className="mt-2 text-sm text-slate-600">{item.reason}</p>
              <div className="mt-3 flex items-center justify-between text-xs text-slate-400">
                <span>{item.actualTool}</span>
                <span>{item.latencyMs} ms</span>
              </div>
            </article>
          ))}
        </div>
      </div>
    </section>
  )
}

function MetricCard(props: { label: string; value: number | string }) {
  return (
    <div className="rounded-[1.25rem] border border-slate-200 bg-slate-50 p-4">
      <p className="text-sm text-slate-500">{props.label}</p>
      <p className="mt-2 text-3xl font-semibold text-slate-950">{props.value}</p>
    </div>
  )
}

function RunStatusBadge(props: { status: string }) {
  const tone =
    props.status === 'SUCCEEDED'
      ? 'bg-emerald-100 text-emerald-700'
      : props.status === 'FAILED' || props.status === 'REJECTED'
        ? 'bg-rose-100 text-rose-700'
        : props.status === 'WAITING_HUMAN' || props.status === 'PENDING'
          ? 'bg-amber-100 text-amber-700'
          : 'bg-slate-100 text-slate-700'
  return <span className={`rounded-full px-3 py-1 text-xs font-medium ${tone}`}>{props.status}</span>
}

function NavItem(props: { to: string; label: string; active: boolean }) {
  return (
    <Link className={`rounded-full px-4 py-2 transition ${props.active ? 'bg-slate-950 text-white' : 'text-slate-600 hover:bg-slate-100'}`} to={props.to}>
      {props.label}
    </Link>
  )
}

function buildFlow(graph: AgentGraphResponse | null): { nodes: Node[]; edges: Edge[] } {
  if (!graph) {
    return { nodes: [], edges: [] }
  }
  const positions: Record<string, { x: number; y: number }> = {
    intake_guardrail: { x: 0, y: 40 },
    load_memory: { x: 240, y: 40 },
    supervisor_plan: { x: 480, y: 40 },
    knowledge_researcher: { x: 240, y: 180 },
    data_analyst: { x: 480, y: 180 },
    evidence_reviewer: { x: 720, y: 110 },
    human_approval: { x: 960, y: 40 },
    finalize: { x: 960, y: 190 },
  }
  return {
    nodes: graph.nodes.map((node) => ({
      id: node.id,
      position: positions[node.id] ?? { x: 0, y: 0 },
      data: { label: `${node.label}\n${node.status}` },
      style: {
        borderRadius: 18,
        border: node.current ? '2px solid #f97316' : '1px solid #cbd5e1',
        padding: 12,
        width: 180,
        background: node.status === 'COMPLETED' ? '#ecfdf5' : node.current ? '#fff7ed' : '#f8fafc',
      },
    })),
    edges: graph.edges.map((edge) => ({
      id: `${edge.source}-${edge.target}`,
      source: edge.source,
      target: edge.target,
      label: edge.label,
      animated: edge.target === graph.currentNode,
    })),
  }
}

export default App
