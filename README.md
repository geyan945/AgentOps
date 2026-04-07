# AgentOps

AgentOps 是一个面向企业知识问答、数据分析和工具调用场景的 Agent 系统原型。  
它已经具备较强的工程化骨架，但**不能准确称为“完整企业级平台”**。

如果用一句话客观定性当前仓库：

> AgentOps 现在是“可运行、可恢复、可审批、可评测、可追踪”的强工程化原型，而不是完整 SaaS 级企业交付。

这份 README 的目标不是包装项目，而是把当前**已经做了什么、没做什么、还不能声称什么**写清楚。

## 1. 当前结论

### 可以成立的说法

- 这是一个三层架构的 Agent 系统：
  - `Java Control Plane`
  - `Python LangGraph Runtime`
  - `React Workspace`
- Agent 主链已经成立：
  - `run / step / checkpoint / replay / resume / approval / eval / event stream`
- 项目已经不是“Prompt + Tool Calling”的简单 Demo。
- 项目已经有最小控制面硬化：
  - 认证
  - internal key
  - public/internal MCP 分流
  - tool policy
  - tool audit
  - approval audit
  - tenant 骨架

### 不能成立的说法

- 不能说这是“完整企业级平台”。
- 不能说这是“完整多租户 SaaS Agent 平台”。
- 不能说权限体系已经完整闭环。
- 不能说已经具备完整生产运维能力。
- 不能说已经做成通用开放式 multi-agent platform。

### 更准确的项目定位

当前最稳妥的外部表述是：

> 面向企业知识与数据 Copilot 的可治理 Agent 系统骨架。Agent 主链、恢复链、审批链、评测链已经完成，但企业控制面和生产化治理仍未补全。

## 2. 仓库结构

```text
AgentOps/
├─ agentops-app/                # Java / Spring Boot control plane
├─ agentops-runtime-py/         # Python / FastAPI / LangGraph runtime
├─ agentops-web/                # React / Vite workspace
├─ docs/                        # Demo、讲稿、JD 映射等文档
├─ docker-compose.yml
└─ README.md
```

## 3. 系统骨架

### 3.1 总体分层

```text
Browser / React Workspace
  -> JWT Auth
  -> REST + SSE

React Workspace
  -> Java Control Plane
     -> Auth / Session / Run / Step / Checkpoint / Event / HumanTask / Eval
     -> Tool Governance / Tool Policy / Audit
     -> Public MCP API
     -> Internal Runtime API

Java Control Plane
  -> Python Runtime
     -> LangGraph orchestration
     -> Planner / Reviewer / Finalizer
     -> Adaptive-RAG router
     -> Replay / Resume / HITL

Python Runtime
  -> Internal MCP call back to Java
     -> kb_search
     -> doc_fetch
     -> sql_query
```

### 3.2 三层职责

#### Java Control Plane

Java 层是系统记录源，不是简单 API 包装层。

当前负责：

- 用户认证
  - `register / login / me`
  - JWT
- 运行态持久化
  - `session`
  - `run`
  - `step`
  - `checkpoint`
  - `event`
  - `human task`
  - `memory fact`
  - `eval`
- 控制面能力
  - public/internal MCP 分流
  - tool permission
  - tool policy
  - tool audit
  - approval audit
  - request id
- Runtime 接入
  - Python runtime 通过 internal API 回写
  - Python runtime 不直接读 Java 主数据库

#### Python Runtime

Python 层负责真实 Agent 运行。

当前负责：

- LangGraph 主链执行
- `Adaptive-RAG` 首轮复杂度分流
- `Chain-of-Draft` planner 约束
- `supervisor / reviewer / finalize`
- `resume / replay / approval continue`
- `SINGLE_GRAPH / TEAM_GRAPH`
- 调 internal MCP 工具

#### React Workspace

前端当前是运行时工作台，而不是运营后台。

当前负责：

- 登录
- Session / Chat
- Run 创建
- Step trace
- Graph 视图
- SSE 事件流
- Approval 面板
- Eval dashboard

## 4. 当前已完成能力

### 4.1 Agent 主链

当前已经完成：

- LangGraph 状态图执行
- 多步工具调用
- reviewer replan
- bounded failure
- memory summary + facts
- `Adaptive-RAG`
- `Chain-of-Draft`
- `SINGLE_GRAPH / TEAM_GRAPH`

### 4.2 恢复与人工介入

当前已经完成：

- Java-backed checkpoint
- `resumeToken + checkpointVersion`
- replay / continue
- human approval
- approval audit

### 4.3 评测

当前已经完成：

- Eval 走真实 runtime 主链
- route / grounding / citation / approval / latency / retry 等维度落表
- dashboard 可展示失败样本与结果聚合

### 4.4 事件与追踪

当前已经完成：

- `run / step / event` 持久化
- SSE event stream
- graph / trace / approval / eval 前端可视化

### 4.5 工具平面

当前核心工具：

- `kb_search`
- `doc_fetch`
- `sql_query`

当前工具元数据包括：

- `riskLevel`
- `approvalPolicy`
- `timeoutBudget`
- `retryPolicy`
- `auditRequired`

当前已开始做执行时治理：

- public MCP 需要登录
- internal MCP 需要 internal key
- 高风险工具不能通过 public MCP 直接绕过审批链
- tool 调用会进入审计日志

## 5. 当前已补的控制面

下面这些能力现在已经落地，但都还是“最小可用版本”，不是完整产品化版本。

### 5.1 身份与请求边界

已完成：

- JWT 登录态
- `X-AgentOps-Internal-Key` 保护 internal API
- `X-Request-Id` 注入
- `/api/mcp/**` 从匿名改为 authenticated
- `/internal/**` 不再裸放行

未完成：

- refresh token
- logout / token revoke
- 完整密码策略
- 更细粒度 RBAC

### 5.2 Tenant 骨架

已完成：

- `tenant`
- `tenant_membership`
- 核心实体增加 `tenantId`
  - `sys_user`
  - `agent_session`
  - `agent_run`
  - `agent_human_task`
  - `agent_memory_fact`
  - `eval_dataset`
  - `eval_run`

未完成：

- 完整 tenant-aware 查询隔离
- AISmartQA 知识库 ownership 全链路校验
- 多租户后台管理

### 5.3 工具治理

已完成：

- `ToolPermissionService`
- `ToolPolicy`
- `ToolAuditLog`
- `ApprovalAuditLog`
- public/internal MCP 分流

未完成：

- 完整策略中心
- 动态配额与额度治理
- 完整管理员策略控制台

## 6. 当前明确没做完的部分

如果你要从甲方或大厂面试官视角看，这些就是现在还差的地方。

### 6.1 不是完整企业控制面

虽然 tenant、audit、tool policy 都有骨架，但还不够说“控制面已经完整”。

差在：

- 权限模型还不够细
- tenant 隔离还没有打穿到知识数据层
- admin 能力只有最小 API，没有完整运营界面

### 6.2 不是完整生产运维体系

虽然已经补了 Docker / Compose / CI，但仍然不够称为完整生产化交付。

差在：

- 没有完整 metrics / tracing / alerting
- 没有完整部署流水线
- 没有完整容量压测与稳定性证据

### 6.3 不是完整 SaaS 平台

当前仓库没有这些：

- 完整多租户工作空间治理
- 完整管理员租户管理台
- 完整商业化权限模型
- 完整外部化配置中心

### 6.4 不是通用多 Agent 平台

当前有 `TEAM_GRAPH`，但这不等于开放式通用 multi-agent platform。

现在的 `TEAM_GRAPH` 更准确是：

- 受控多角色编排
- 不是开放式 swarm
- 不是通用 agent registry
- 不是开放式 agent-to-agent 协议平台

## 7. 数据模型骨架

当前关键实体包括：

### 身份与租户

- `sys_user`
- `tenant`
- `tenant_membership`

### 运行态

- `agent_session`
- `agent_message`
- `agent_run`
- `agent_run_step`
- `agent_runtime_checkpoint`
- `agent_run_event`
- `agent_human_task`
- `agent_memory_fact`

### 评测

- `eval_dataset`
- `eval_case`
- `eval_run`
- `eval_result`

### 控制面治理

- `tool_policy`
- `tool_audit_log`
- `approval_audit_log`

## 8. 核心 API 骨架

### 用户侧 API

- `/api/auth/register`
- `/api/auth/login`
- `/api/auth/me`
- `/api/sessions`
- `/api/agent/runs`
- `/api/agent/runs/{id}`
- `/api/agent/runs/{id}/graph`
- `/api/agent/runs/{id}/events`
- `/api/agent/runs/{id}/resume`
- `/api/agent/runs/{id}/replay`
- `/api/human-tasks`
- `/api/evals/**`
- `/api/mcp/**`

### internal API

- `/internal/runtime/sessions/{sessionId}/context`
- `/internal/runtime/runs/{runId}/steps`
- `/internal/runtime/runs/{runId}/status`
- `/internal/runtime/runs/{runId}/checkpoint`
- `/internal/mcp/tools/call`

### admin API

当前只补了最小入口：

- `/api/admin/tool-policies`
- `/api/admin/audit/tool-calls`
- `/api/admin/audit/approvals`

注意：

- 这里是最小管理 API，不是完整后台系统
- 默认注册用户不是 `ADMIN`

## 9. 本地启动

### 9.1 Docker Compose

这是当前最接近“一键拉起三层服务”的方式。

```powershell
cd E:\Desktop\JobProj\AgentOps
docker compose up --build
```

默认端口：

- Java control plane: `18084`
- Python runtime: `18085`
- Web: `18086`
- MySQL: `3406`
- Redis: `16379`
- RabbitMQ: `5672`
- RabbitMQ 管理台: `15672`

### 9.2 手动启动

#### Java control plane

```powershell
cd E:\Desktop\JobProj\AgentOps\agentops-app
./mvnw.cmd spring-boot:run
```

#### Python runtime

```powershell
cd E:\Desktop\JobProj\AgentOps\agentops-runtime-py
pip install -r requirements.txt
python -m uvicorn app.main:app --host 0.0.0.0 --port 18085 --reload
```

#### Web

```powershell
cd E:\Desktop\JobProj\AgentOps\agentops-web
npm install
npm run dev
```

开发端口：

- app: `18084`
- runtime: `18085`
- web dev: `5173`

## 10. 环境变量

### Java app

- `AGENTOPS_DB_URL`
- `AGENTOPS_DB_USERNAME`
- `AGENTOPS_DB_PASSWORD`
- `AGENTOPS_REDIS_HOST`
- `AGENTOPS_REDIS_PORT`
- `AGENTOPS_REDIS_PASSWORD`
- `AGENTOPS_RABBITMQ_HOST`
- `AGENTOPS_RABBITMQ_PORT`
- `AGENTOPS_RABBITMQ_USERNAME`
- `AGENTOPS_RABBITMQ_PASSWORD`
- `AGENTOPS_RUNTIME_BASE_URL`
- `AGENTOPS_INTERNAL_KEY`
- `AGENTOPS_JWT_SECRET`
- `AGENTOPS_ES_BASE_URL`
- `GEMINI_API_KEY`

### Python runtime

- `AGENTOPS_CONTROL_PLANE_BASE_URL`
- `AGENTOPS_INTERNAL_KEY`
- `GEMINI_API_KEY`
- `AGENTOPS_GEMINI_MODEL`
- `AGENTOPS_LLM_MODE`
- `AGENTOPS_MAX_GRAPH_HOPS`
- `AGENTOPS_MAX_REPLANS`
- `AGENTOPS_MAX_TOOL_LOOPS`
- `AGENTOPS_LLM_MAX_RETRIES`

### Web

- `VITE_API_BASE_URL`

## 11. 测试与交付证据

### Java

```powershell
cd agentops-app
./mvnw.cmd test
```

### Python

```powershell
cd agentops-runtime-py
python -m pytest -q tests
```

### Web

```powershell
cd agentops-web
npm run build
```

### CI

仓库已包含 GitHub Actions CI：

- Java: `mvn test`
- Python: `pytest`
- Web: `npm ci && npm run build`

位置：

- `.github/workflows/ci.yml`

## 12. 外部依赖与现实约束

### 依赖项

这个仓库不是完全自洽的单体示例，还依赖这些外部条件：

- MySQL
- Redis
- RabbitMQ
- 可选 Elasticsearch
- AISmartQA 知识数据表
- 可选 Gemini API Key

### 已知限制

- Compose 没内置 Elasticsearch
- `kb_search` 在缺少 ES 时会退回 DB fallback
- `doc_fetch` 和知识相关读取依赖本地已有 `ai_qa_system`
- 没有 Gemini key 时，runtime 会退回 mock

## 13. 对外描述建议

如果要对外一句话介绍这个项目，建议用下面这个版本，不要夸大：

> AgentOps 是一个面向企业知识与数据 Copilot 的可治理 Agent 系统原型，已经完成 Java control plane、Python LangGraph runtime、恢复与审批主链、runtime-based Eval 和最小控制面硬化，但仍未补齐完整多租户、完整权限中心和生产运维体系。

如果你要更严厉一点的版本，可以直接说：

> AgentOps 不是完整企业平台，但已经不是 Demo。它是一个主链完整、控制面刚开始成型的 Agent 系统骨架。
