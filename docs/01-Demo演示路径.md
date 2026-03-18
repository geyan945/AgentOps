# AgentOps Demo 演示路径

## 1. 目标

用 5 到 10 分钟展示：

```text
登录 -> 新建会话 -> MCP 初始化/工具列表 -> 发起 Agent Run -> 回看 Trace
-> 发起 Eval -> 看 Dashboard / 失败样本
```

## 2. 前置条件

- AISmartQA 已经有知识库与文档数据
- AgentOps 服务启动在 `18084`
- MySQL / Redis / RabbitMQ / ES 已可用

## 3. 固定演示顺序

### Step 1：注册登录
- `POST /api/auth/register`
- `POST /api/auth/login`
- `GET /api/auth/me`

讲法：
- Agent 平台不是脚本，先把用户和安全边界立住
- 后续 session、run、trace、eval 都按用户隔离

### Step 2：新建会话
- `POST /api/sessions`
- `GET /api/sessions`
- `GET /api/sessions/{id}/summary`

讲法：
- 会话是平台最外层业务边界
- 摘要缓存用于支撑后续上下文压缩和更长会话

### Step 3：展示 MCP 第一版
- `POST /api/mcp/initialize`
- `GET /api/mcp/tools`
- `POST /api/mcp/tools/call`

讲法：
- 当前不是空喊 MCP，而是已经有最小可用协议链
- 主系统既能本地调工具，也能走远程工具模式

### Step 4：跑一个统计类问题
- `POST /api/agent/runs`
- message: `please count my sessions`

讲法：
- Planner 会选 `sql_query_remote`
- 说明 Agent 不只是问答，也能走结构化数据工具

### Step 5：跑一个文档读取问题
- `POST /api/agent/runs`
- message: `show document 23`

讲法：
- Planner 会选 `doc_fetch`
- 适合说明工具抽象和明确参数生成

### Step 6：跑一个知识库检索问题
- `POST /api/agent/runs`
- message: `semantic vector retrieval`

讲法：
- Planner 会选 `kb_search_remote`
- 这个工具复用了 AISmartQA 的知识库和 ES 索引数据

### Step 7：回看 Trace
- `GET /api/agent/runs/{id}`
- `GET /api/agent/runs/{id}/steps`

讲法：
- 这里能看到 PLAN / TOOL_CALL / FINAL_ANSWER 三步
- 这就是 Agent 可观测性的核心

### Step 8：跑 Eval
- `POST /api/evals/datasets`
- `POST /api/evals/runs`
- `GET /api/evals/dashboard`
- `GET /api/evals/failures`

讲法：
- AgentOps 不只会执行，还能批量评测
- 评测走 RabbitMQ 异步链，不阻塞主聊天链路

## 4. 演示重点

- 这不是普通 RAG，而是多工具智能体平台
- 工具调用可追踪、可解释、可扩展
- 已经具备 MCP 第一版、异步评测和平台运维视角