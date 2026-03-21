# AgentOps

AgentOps 是一个面向大厂 `Agent 应用开发` 岗位重构后的企业级智能体项目，当前采用：

- `agentops-app`：Java / Spring Boot control plane
- `agentops-runtime-py`：Python / FastAPI / LangGraph / LangChain runtime
- `agentops-web`：React / Vite / React Query / React Flow / ECharts 工作台

## 2.0 主链

```text
登录
 -> 新建会话
 -> 创建 Agent Run
 -> Python LangGraph runtime 拉取上下文
 -> 走 intake / memory / supervisor / tool / review / approval / finalize
 -> Java 回写 run/step/graph/human-task/memory/eval
 -> Web 工作台轮询展示 graph trace / 审批 / dashboard
```

## 关键能力

- Java control plane：JWT、session/message、run/step、human task、memory fact、MCP server、Eval、以及 `agent_runtime_checkpoint` 持久化 checkpoint
- Python runtime：LangGraph graph、LangChain tools、Gemini-driven supervisor/reviewer/finalize、multi-step tool orchestration、review/replan、human-in-the-loop、memory extraction
- Web workspace：聊天区、run trace、graph 视图、审批面板、Eval dashboard
- 工具链：`kb_search`、`doc_fetch`、`sql_query`
- 评测闭环：真实 runtime 执行 + `route / grounding / citation / approval / latency / retry`

## 目录

- `agentops-app/`
- `agentops-runtime-py/`
- `agentops-web/`
- `docs/01-Demo演示路径.md`
- `docs/03-简历项目描述与讲稿.md`
- `AgentOps.md`

## 本地启动

### 1. Java control plane

```powershell
cd agentops-app
./mvnw.cmd spring-boot:run
```

默认端口：`18084`

### 2. Python runtime

```powershell
cd agentops-runtime-py
pip install -r requirements.txt
python -m uvicorn app.main:app --host 0.0.0.0 --port 18085 --reload
```

默认端口：`18085`

### 3. React workspace

```powershell
cd agentops-web
npm install
npm run dev
```

默认端口：`5173`

## 环境变量

- `GEMINI_API_KEY`
- `AGENTOPS_INTERNAL_KEY`
- `AGENTOPS_CONTROL_PLANE_BASE_URL`
- `VITE_API_BASE_URL`

## 当前说明

- 项目名称保留为 `AgentOps`
- Java 仍然是系统记录源
- Python runtime 通过 Java MCP、内部回调和 checkpoint API 接入，不直接读数据库
- Web 工作台以轮询方式刷新 run / graph / approvals / eval
- 当 `GEMINI_API_KEY` 可用时，`supervisor_plan / evidence_reviewer / finalize` 走真实 Gemini；否则自动退回 mock 模式保证本地 CI 稳定
