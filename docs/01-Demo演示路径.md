# AgentOps 2.2 Demo 演示路径

## 目标

用 8 到 10 分钟展示下面这条完整链路：

```text
登录
 -> 新建会话
 -> 选择 SINGLE_GRAPH / TEAM_GRAPH
 -> 创建 Agent Run
 -> 看 LangGraph graph trace + Live Event Stream
 -> 触发 human approval
 -> 恢复执行
 -> 演示 checkpoint replay
 -> 看 Eval dashboard
```

## 演示前准备

- Java `agentops-app` 启动在 `18084`
- Python `agentops-runtime-py` 启动在 `18085`
- 前端 `agentops-web` 启动在 `5173`
- MySQL / Redis / RabbitMQ / ES 可用
- AISmartQA 文档与知识库数据可访问

## 演示步骤

### Step 1：登录工作台

- 页面：`agentops-web`
- 说明：
  - 项目不是脚本，而是完整 control plane + runtime + workspace
  - 登录后所有 session / run / approval / eval 都按用户隔离

### Step 2：创建会话

- 页面左侧 `Session` 区域创建一个新会话
- 说明：
  - Java control plane 负责会话和消息持久化
  - 后续 memory summary 与 run trace 都挂在 session 上

### Step 3：启动一个知识检索型 run

- 输入：`请总结 AgentOps 的工具调用链路`
- 模式：优先选择 `TEAM_GRAPH`
- 说明：
  - Java 只负责创建 run 和触发 runtime
  - Python runtime 会拉上下文，执行 `intake -> memory -> supervisor -> knowledge_researcher -> reviewer -> finalize`
  - `TEAM_GRAPH` 不是开放式 swarm，而是受控 multi-role / team-graph

### Step 4：展示 graph、step trace 和 live events

- 页面中部看 step trace
- 页面右侧看 graph
- 页面右侧继续看 `Live Event Stream`
- 说明：
  - 这是 `LangGraph` 风格的状态图，而不是单轮 `prompt -> answer`
  - 每个节点与工具调用都回写到 Java 的 `run/step`
  - 工作台现在优先通过 SSE 接收事件，轮询只做兜底

### Step 5：触发人工审批

- 输入：`统计所有运行状态并导出全量结果`
- 说明：
  - runtime 会识别为高风险 SQL 场景
  - graph 进入 `human_approval`
  - Java 生成 `agent_human_task`

### Step 6：在 Approval Inbox 里 approve / reject

- 页面：`Approvals`
- 说明：
  - 这是 `human-in-the-loop`
  - 审批通过后 run 从 pause 状态恢复
  - 审批拒绝后系统给出受限答案，不继续执行

### Step 7：演示 checkpoint replay

- 动作：对非审批态 run 执行 `Checkpoint Replay`
- 说明：
  - replay 走的是最新 checkpoint，而不是重新从用户输入全量跑一遍
  - 这是 `resume` 之外的第二种恢复能力，更像真实 runtime 平台

### Step 8：看 Eval Dashboard

- 页面：`Eval`
- 说明：
  - 评测不只看是否答出来
  - 现在展示 `route / grounding / citation / approval / latency / retry / skills`
  - 这是工程化 agent 的质量闭环

## 演示时的固定讲法

- “AgentOps 2.2 把 Java 保留为 control plane，把 Python LangGraph 做成 runtime，并补了 event stream、checkpoint replay 和 team-graph。”
- “真正的 agent 味不在于调模型，而在于 graph、tool orchestration、review/replan、human-in-the-loop、memory、replay 和 eval。”
- “我没有把 Java 推倒重来，而是把它升级成系统记录源和工具平面，这更像企业真实落地方式。”
