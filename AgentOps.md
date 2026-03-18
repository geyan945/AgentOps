# AgentOps：基于 MCP 的企业级多工具智能体平台（详细实现路线）

> 目标读者：基础一般、希望做出“能写进大厂 AI 应用 / 大模型应用 / 应用型算法简历”的项目的人。  
> 技术环境：严格贴合你当前环境：Java 25、Spring Boot 3.5.11、Spring Web、Spring Data JPA、Spring Security、Jakarta Validation、Lombok、JJWT、MySQL、Redis、RabbitMQ、Elasticsearch、Milvus、Gemini API。  
> 设计原则：先做“能跑、能讲、能扩展”的 MVP，再逐步升级到 MCP 化、评测化、可观测化。

---

## 1. 项目最终要做成什么

一句话版本：

**做一个面向企业知识库与数据分析场景的多工具智能体平台，支持会话管理、知识库检索、SQL 查询、工具调用编排、调用追踪、自动评测，并逐步升级为支持远程 MCP Server 的 Agent 平台。**

你最终简历里可以把它写成：

**AgentOps：基于 MCP 的企业级多工具智能体平台**  
支持知识库检索、结构化数据查询、工具调用编排、调用追踪与自动评测，具备 Agent 工程化落地能力。

---

## 2. 为什么这个项目适合你

你已经有 AISmartQA，所以你不应该再做一个普通知识库问答系统；你要做的是：

- 保留 AISmartQA 的 **RAG / 文档解析 / ES + Milvus 检索** 能力；
- 在它上面再加一层 **Agent 编排 + Tool Use + MCP + Evals**；
- 让你的简历从“会做知识库问答”升级成“会做企业级 AI 应用系统”。

这个项目最适合你的原因：

1. 你已有 Java 后端能力，主后端可以继续用 Spring Boot。  
2. 你已有 AISmartQA，可直接复用知识库与检索能力。  
3. 你要投 AI 应用 / 大模型应用岗，最需要补的是：
   - 多工具调用
   - Agent 编排
   - MCP
   - 自动评测
   - 调用链追踪

---

## 3. 先定项目边界：这次只做“强可实现版本”

为了保证你能真正做出来，这个项目不从第一天就做成完整通用 Agent 平台，而是分成 **3 个版本**：

### V1：本地工具版（一定先做）
先不急着上远程 MCP Server，先做：

- 用户登录鉴权
- 会话管理
- Agent Run / Step 记录
- 3 个工具：
  - `kb_search`：知识库混合检索
  - `sql_query`：只读 SQL 查询
  - `doc_fetch`：根据文档或 chunk 读取内容
- Gemini 规划 + 执行 + 最终回答
- Trace 记录
- 简单评测

### V2：MCP 化（第二阶段升级）
在 V1 工具抽象稳定后，把 `kb_search` 和 `sql_query` 拆成 **远程 MCP Server**：

- 主系统充当 MCP Client
- 工具服务充当 MCP Server
- 支持 `initialize`、`tools/list`、`tools/call`
- 可选支持 `resources/list`、`resources/read`

### V3：工程增强版（最后加分）
- RabbitMQ 异步评测
- Redis 限流 / 会话缓存
- 工具权限控制
- Prompt 版本管理
- Evals Dashboard
- 失败样本分析

**结论：先做 V1，再升级 V2，最后做 V3。不要一上来就从协议细节开打。**

---

## 4. 最终系统架构（推荐）

```text
┌─────────────────────────────────────────────────────────────┐
│                    AgentOps Main Backend                   │
│                  Spring Boot 3.5.11 / Java 25             │
│                                                             │
│  Auth / Session / AgentRun / Trace / Eval / ToolRegistry   │
│  GeminiGateway / AgentPlanner / AgentExecutor / McpClient  │
└───────────────┬───────────────────────────┬─────────────────┘
                │                           │
                │                           │
                ▼                           ▼
      ┌───────────────────┐       ┌─────────────────────┐
      │ Knowledge Service │       │   Data Tool Server  │
      │  (can reuse AISmartQA)    │  (Spring Boot)      │
      │ ES + Milvus + MySQL       │  Read-only SQL Tool │
      └───────────────────┘       └─────────────────────┘
                │                           │
                └──────────────┬────────────┘
                               │
                               ▼
                     ┌──────────────────┐
                     │ Gemini API       │
                     │ 2.5 Flash        │
                     │ Embedding 001    │
                     └──────────────────┘

Infra:
MySQL / Redis / RabbitMQ / Elasticsearch / Milvus
```

---

## 5. 功能清单（你真正需要做出来的功能）

### 5.1 用户与安全
- 用户注册 / 登录
- JWT 鉴权
- 接口权限拦截
- 工具权限（哪些用户能调用哪些工具）

### 5.2 Agent 会话与运行
- 新建会话
- 发送消息
- 生成一次 Agent Run
- Run 下面有多个 Step
- Step 可能是：规划、工具调用、总结回答

### 5.3 工具系统
至少做 3 个工具：

#### 工具 1：`kb_search`
输入：query, topK  
输出：chunk 列表、来源文档、score、摘要

#### 工具 2：`doc_fetch`
输入：documentId 或 chunkId  
输出：具体内容

#### 工具 3：`sql_query`
输入：自然语言描述或受控 SQL 模板参数  
输出：表格数据 / JSON 结果

> 注意：`sql_query` 一开始不要开放“任意 SQL 执行”，一定只做 **只读 + 白名单表 + 语法校验**。

### 5.4 Agent 编排
- 判断是否需要调用工具
- 选择工具
- 生成工具参数
- 调用工具
- 将工具结果回填到上下文
- 生成最终答案
- 返回引用与中间步骤

### 5.5 追踪与评测
- 记录每一步耗时
- 记录每步输入/输出
- 记录工具是否成功
- 批量跑评测集
- 输出成功率、平均耗时、工具命中率

---

## 6. 这套项目和 AISmartQA 的关系

你**不要重复造轮子**，一定要复用 AISmartQA 的能力。

### 可以直接复用的部分
1. 文档上传与解析流程  
2. 文档切 chunk 逻辑  
3. Embedding 接口调用  
4. Milvus 向量写入 / 搜索  
5. Elasticsearch 索引与全文检索  
6. 混合检索逻辑  
7. 文档、chunk、问答记录表结构思路

### 在 AgentOps 里新增的部分
1. Agent 会话与执行链路  
2. 多工具路由  
3. 工具抽象层  
4. MCP Client / MCP Server  
5. Trace 与 Eval

**你可以把 AgentOps 理解成“基于 AISmartQA 检索能力的上层智能体平台”。**

---

## 7. 目录结构（推荐）

```text
agentops/
├─ agentops-app/                    # 主后端
│  ├─ controller/
│  ├─ service/
│  ├─ domain/
│  │  ├─ entity/
│  │  ├─ repository/
│  │  └─ dto/
│  ├─ security/
│  ├─ agent/
│  │  ├─ planner/
│  │  ├─ executor/
│  │  ├─ trace/
│  │  └─ tool/
│  ├─ integration/
│  │  ├─ gemini/
│  │  ├─ milvus/
│  │  ├─ elastic/
│  │  ├─ redis/
│  │  ├─ rabbitmq/
│  │  └─ mcp/
│  └─ config/
├─ agentops-mcp-kb-server/          # 可选：知识库 MCP Server
├─ agentops-mcp-data-server/        # 可选：数据查询 MCP Server
├─ docker/
├─ docs/
└─ sql/
```

如果你暂时不想多模块，也可以先只做一个 Spring Boot 项目，等 V2 再拆分。

---

## 8. 核心数据表设计

下面这些表足够支撑项目。

### 8.1 用户与权限

#### `sys_user`
- id
- username
- password_hash
- role
- status
- created_at
- updated_at

### 8.2 会话与消息

#### `agent_session`
- id
- user_id
- title
- status
- created_at
- updated_at

#### `agent_message`
- id
- session_id
- role (`user` / `assistant` / `tool`)
- content
- metadata_json
- created_at

### 8.3 一次运行与步骤

#### `agent_run`
- id
- session_id
- user_input
- final_answer
- status (`PENDING/RUNNING/SUCCEEDED/FAILED`)
- total_steps
- total_latency_ms
- error_message
- created_at
- finished_at

#### `agent_run_step`
- id
- run_id
- step_no
- step_type (`PLAN/TOOL_CALL/FINAL_ANSWER`)
- tool_name
- input_json
- output_json
- latency_ms
- success
- error_message
- created_at

### 8.4 工具定义与 MCP 配置

#### `tool_definition`
- id
- tool_name
- display_name
- tool_type (`LOCAL/MCP_REMOTE`)
- input_schema_json
- enabled
- permission_code
- created_at

#### `mcp_server`
- id
- server_name
- base_url
- auth_type
- auth_config_json
- enabled
- created_at

### 8.5 评测体系

#### `eval_dataset`
- id
- name
- description
- created_by
- created_at

#### `eval_case`
- id
- dataset_id
- question
- expected_tool
- expected_keywords_json
- expected_reference_json
- created_at

#### `eval_run`
- id
- dataset_id
- status
- total_cases
- passed_cases
- avg_latency_ms
- created_at
- finished_at

#### `eval_result`
- id
- eval_run_id
- case_id
- actual_tool
- answer_text
- success
- score
- reason
- latency_ms
- created_at

---

## 9. 关键 Java 类设计

### 9.1 鉴权相关
- `SecurityConfig`
- `JwtTokenProvider`
- `JwtAuthenticationFilter`
- `CustomUserDetailsService`

### 9.2 Agent 相关
- `AgentController`
- `AgentRunService`
- `AgentPlannerService`
- `AgentExecutorService`
- `TraceService`
- `EvalService`

### 9.3 工具系统
- `ToolExecutor`（核心接口）
- `LocalToolExecutor`
- `McpToolExecutor`
- `ToolRegistry`
- `ToolPermissionService`

### 9.4 Gemini 集成
- `GeminiClient`
- `GeminiPlannerClient`
- `GeminiAnswerClient`
- `GeminiEmbeddingClient`

### 9.5 检索相关
- `KnowledgeSearchService`
- `ElasticSearchService`
- `MilvusVectorService`
- `HybridRetrieveService`
- `RerankService`（先做简单版本）

### 9.6 异步相关
- `EvalRunProducer`
- `EvalRunConsumer`
- `TraceEventProducer`

---

## 10. 最关键的设计：工具先抽象，再决定本地还是 MCP

这一点非常重要。

你不要把 Agent 直接写死成“if toolName == kb_search 就调用某个 service”。  
你要先定义统一工具接口：

```java
public interface ToolExecutor {
    String getName();
    JsonNode getInputSchema();
    ToolResult execute(JsonNode arguments, AgentContext context);
}
```

再做两类实现：

### 10.1 本地工具实现 `LocalToolExecutor`
直接调用你自己的 Spring Service：
- `KbSearchToolExecutor`
- `DocFetchToolExecutor`
- `SqlQueryToolExecutor`

### 10.2 远程工具实现 `McpToolExecutor`
封装对 MCP Server 的 HTTP / JSON-RPC 调用：
- 先 `initialize`
- 再 `tools/list`
- 需要调用时 `tools/call`

这样做的好处：
- V1 只做本地工具，简单
- V2 再把工具平滑替换成远程 MCP Server
- 主业务代码几乎不改

---

## 11. 为什么推荐你先用“结构化输出”做规划器

很多人一做 Agent 就想直接上 function calling。  
但对于基础一般的人，我建议：

### 第一阶段：先用结构化输出做 Planner
让 Gemini 输出固定 JSON：

```json
{
  "decision": "CALL_TOOLS",
  "toolCalls": [
    {
      "toolName": "kb_search",
      "arguments": {
        "query": "解释 AgentOps 的工具调用流程",
        "topK": 5
      }
    }
  ],
  "reason": "问题需要先检索知识库"
}
```

这样有 4 个好处：
1. 你更容易 debug  
2. Java 更容易反序列化  
3. 你能清晰记录 step trace  
4. 你更容易做评测

### 第二阶段：再升级为 Gemini function calling
当 V1 跑通后，再把 Planner 升级成模型原生工具调用。

**结论：先结构化输出，后 function calling。**

---

## 12. Agent 主流程（一定照这个顺序做）

### 12.1 用户发起请求
前端或 Postman：

`POST /api/agent/runs`

请求：
```json
{
  "sessionId": 1001,
  "message": "请帮我总结 AgentOps 项目的工具调用链路，并列出涉及的表结构"
}
```

### 12.2 后端创建 Run
- 新增 `agent_run`
- 状态设为 `RUNNING`
- 记录用户消息到 `agent_message`

### 12.3 Planner 阶段
调用 Gemini：
- 输入：用户问题 + 可用工具列表 + 每个工具 schema + 最近几轮消息摘要
- 输出：JSON 规划结果

可能输出：
- `ANSWER_DIRECTLY`
- `CALL_TOOLS`

### 12.4 执行工具
如果需要调工具：
- 遍历 `toolCalls`
- 权限校验
- 参数校验
- 调用 `ToolRegistry.get(toolName).execute(...)`
- 把结果写入 `agent_run_step`

### 12.5 最终回答
把：
- 用户问题
- 工具结果
- 历史上下文
- 引用信息

再发给 Gemini，生成最终答案。

### 12.6 收尾
- 更新 `agent_run.final_answer`
- 状态改为 `SUCCEEDED`
- 记录 assistant 消息
- 返回 API 响应

---

## 13. 详细实现路线（最重要部分）

下面按照真正开发顺序写。

---

# 第一阶段：先把基础工程搭起来

## Step 1：创建 Spring Boot 项目

依赖建议：
- Spring Web
- Spring Data JPA
- Spring Security
- Validation
- Lombok
- MySQL Driver
- Redis Starter
- AMQP Starter
- Elasticsearch Java Client 或 Spring Data Elasticsearch
- Jackson
- JJWT

Milvus 用 Java SDK：
- `io.milvus:milvus-sdk-java`

### 你要做的事
1. 新建 Maven 工程  
2. 确定 Java 25  
3. 建立多环境配置：
   - `application.yml`
   - `application-dev.yml`
   - `application-prod.yml`
4. 引入统一异常处理
5. 引入统一响应体 `ApiResponse<T>`

### 验收标准
- 项目能启动
- 能连 MySQL / Redis / RabbitMQ / ES / Milvus
- `/actuator/health` 能正常返回

---

## Step 2：先把基础配置写好

### 2.1 `application-dev.yml` 建议结构

```yaml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/agentops?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: root
    password: root
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: true
    properties:
      hibernate:
        format_sql: true
  data:
    redis:
      host: localhost
      port: 6379
  rabbitmq:
    host: localhost
    port: 5672
    username: guest
    password: guest

app:
  jwt:
    secret: your-secret-key-change-me
    expiration-seconds: 86400
  gemini:
    api-key: ${GEMINI_API_KEY}
    base-url: https://generativelanguage.googleapis.com
    chat-model: gemini-2.5-flash
    embedding-model: models/gemini-embedding-001
  elasticsearch:
    host: http://localhost:9200
  milvus:
    host: localhost
    port: 19530
    username: root
    password: Milvus
```

### 2.2 推荐先写这些配置类
- `JwtProperties`
- `GeminiProperties`
- `MilvusProperties`
- `ElasticProperties`

---

## Step 3：把用户登录鉴权先做完

这是所有后续接口的基础。

### 你要实现的接口
- `POST /api/auth/register`
- `POST /api/auth/login`
- `GET /api/auth/me`

### 你要写的类
- `User` 实体
- `UserRepository`
- `AuthService`
- `AuthController`
- `JwtTokenProvider`
- `JwtAuthenticationFilter`
- `SecurityConfig`

### 关键点
- 密码必须加密（BCrypt）
- JWT 放在 `Authorization: Bearer xxx`
- 所有 `/api/**` 默认鉴权，只有 `/api/auth/**` 放行

### 验收标准
- 登录能返回 token
- 携带 token 能访问 `/api/auth/me`

---

# 第二阶段：先把 Agent 的“壳”搭起来

## Step 4：做会话与消息模块

### 表
- `agent_session`
- `agent_message`

### 接口
- `POST /api/sessions`
- `GET /api/sessions`
- `GET /api/sessions/{id}`
- `GET /api/sessions/{id}/messages`

### 你要做的事
1. 能新建会话  
2. 能保存用户消息  
3. 能保存 assistant 消息  
4. 暂时不做复杂标题生成，先用首条问题截断做标题

### 验收标准
- 一个用户可以新建多会话
- 同一会话可以看到历史消息

---

## Step 5：做 Agent Run 与 Step 模块

### 表
- `agent_run`
- `agent_run_step`

### 设计思路
一次用户提问 = 一次 `run`。  
run 下面有多个 `step`：
- step1：PLAN
- step2：TOOL_CALL(kb_search)
- step3：FINAL_ANSWER

### 接口
- `POST /api/agent/runs`
- `GET /api/agent/runs/{id}`
- `GET /api/agent/runs/{id}/steps`

### 验收标准
- 调一次提问，数据库里能看到对应 run 和 step
- 即使失败，也能看到失败在哪一步

---

# 第三阶段：先把本地工具版跑通

## Step 6：实现工具抽象层

### 核心接口

```java
public interface ToolExecutor {
    String getName();
    ToolSchema getSchema();
    ToolResult execute(JsonNode arguments, AgentContext context);
}
```

### 配套类
- `ToolSchema`
- `ToolCallRequest`
- `ToolResult`
- `ToolRegistry`
- `ToolPermissionService`

### `ToolRegistry` 做什么
- 启动时收集所有 `ToolExecutor`
- 通过 `toolName` 找具体执行器
- 返回可用工具列表给 Planner

### 验收标准
- 能打印当前系统所有工具定义
- 可以根据名字找到工具并执行

---

## Step 7：先做最简单工具：`doc_fetch`

这个工具最容易，是第一颗定心丸。

### 输入
```json
{
  "documentId": 1
}
```

### 输出
```json
{
  "documentId": 1,
  "title": "AgentOps 设计文档",
  "content": "..."
}
```

### 实现方式
- 如果你已经有文档表，就按文档 id 读库
- 如果没有，就先创建一个最简单文档表示例数据

### 验收标准
- 不走模型，只调用工具接口，也能拿到文档内容

---

## Step 8：实现 `kb_search` 工具（核心工具）

这个工具是整个项目价值最高的部分之一。

### 方案
复用 AISmartQA 的文档、chunk、Embedding、Milvus、ES。

### 输入
```json
{
  "query": "总结 AgentOps 的工具调用链路",
  "topK": 5
}
```

### 输出
```json
{
  "query": "总结 AgentOps 的工具调用链路",
  "hits": [
    {
      "documentId": 1,
      "chunkId": 101,
      "content": "...",
      "score": 0.91,
      "source": "设计文档A"
    }
  ]
}
```

### 推荐实现步骤

#### 8.1 先做最小版：只做 ES 检索
为什么？因为最容易先跑通。

- chunk 入 ES
- query 走 `match` / `multi_match`
- 先返回 topK

#### 8.2 再做向量版：Milvus 检索
- `query` 用 `gemini-embedding-001` 生成 query embedding
- 在 Milvus 里搜 topK

#### 8.3 再做混合版：ES + Milvus 合并
- ES topK = 10
- Milvus topK = 10
- 按 `chunkId` 去重
- 简单加权融合

#### 8.4 再做简易 rerank
一开始不要搞复杂模型，先用这两种方式之一：

方案 A（推荐）：再调用一次 Gemini，让模型根据 query + chunk 摘要打分  
方案 B：手工规则分（标题命中 + 内容命中 + 向量分数）

### 关键注意
- `gemini-embedding-001` 的文档和查询要使用合适的 task type：
  - 文档：`RETRIEVAL_DOCUMENT`
  - 查询：`RETRIEVAL_QUERY`
- 先固定向量维度，例如 768，避免后续集合维度不一致

### 验收标准
- 输入 query，能返回 5 条可读 chunk
- chunk 能带来源文档信息
- 混合检索结果明显优于单纯关键字

---

## Step 9：实现 `sql_query` 工具（第二个高价值工具）

### 这是最容易踩坑的地方
你**绝对不要**做成“自然语言直接变任意 SQL 执行”。  
正确路线是：

### 9.1 先做“受控查询模板版”
比如你先准备 3 类查询：
- 用户数统计
- 文档数统计
- 某时间段运行次数统计

模型只负责输出：
- 查询类型
- 参数

例如：
```json
{
  "queryType": "RUN_COUNT_BY_DATE_RANGE",
  "startDate": "2026-03-01",
  "endDate": "2026-03-10"
}
```

然后 Java 后端根据模板拼装 SQL。

### 9.2 再做“白名单 SQL 版”
- 只允许 `SELECT`
- 禁止 `UPDATE/DELETE/INSERT`
- 禁止多语句
- 禁止系统表
- 限制表白名单

### 实现建议
- 用 `NamedParameterJdbcTemplate`
- 专门写 `SqlGuardService`
- 做 `QueryTemplateRegistry`

### 验收标准
- 模型可以查统计信息
- 不会执行危险 SQL

---

# 第四阶段：把 Gemini 接进来

## Step 10：实现 GeminiClient

### 为什么推荐你用 REST + Spring `RestClient`
因为你当前主后端是 Java Spring Boot，直接用 REST 最可控，也最容易记录请求和响应。

### 你至少要写 3 个方法
- `generatePlannerJson(...)`
- `generateFinalAnswer(...)`
- `embedText(...)`

### 10.1 `generatePlannerJson(...)`
输入：
- 用户问题
- 工具列表
- 最近消息摘要
- 约束提示词

输出：固定 JSON 对象

### 10.2 `generateFinalAnswer(...)`
输入：
- 用户问题
- 工具返回结果
- 引用来源

输出：自然语言答案

### 10.3 `embedText(...)`
输入：一段 query 或 chunk  
输出：向量数组

---

## Step 11：给 Planner 设计稳定 Prompt

### 目标
让模型不要自由发挥，而是稳定地产生你需要的 JSON。

### Planner 系统提示词建议结构
1. 你是 Agent Planner  
2. 你的任务是决定是否调用工具  
3. 你只能从给定工具列表中选择  
4. 若工具不需要，则返回 `ANSWER_DIRECTLY`  
5. 若需要工具，返回 `CALL_TOOLS` 和参数  
6. 输出必须符合 JSON Schema

### 规划输出 Schema（建议）

```json
{
  "type": "object",
  "properties": {
    "decision": {
      "type": "string",
      "enum": ["ANSWER_DIRECTLY", "CALL_TOOLS"]
    },
    "reason": { "type": "string" },
    "toolCalls": {
      "type": "array",
      "items": {
        "type": "object",
        "properties": {
          "toolName": { "type": "string" },
          "arguments": { "type": "object" }
        },
        "required": ["toolName", "arguments"]
      }
    }
  },
  "required": ["decision", "reason", "toolCalls"]
}
```

### 关键建议
- 第一版只允许 **最多 2 个工具调用**
- 第一版只允许 **串行调用**
- 第一版 `maxSteps = 4`

这样容易控制复杂度。

---

## Step 12：实现 AgentExecutor（核心中的核心）

这是整个项目最重要的类。

### 伪代码

```java
public AgentRunResult execute(Long sessionId, String userMessage) {
    AgentRun run = createRun(sessionId, userMessage);

    PlannerResult plan = planner.plan(...);
    savePlanStep(run, plan);

    List<ToolResult> toolResults = new ArrayList<>();
    if (plan.needTools()) {
        for (ToolCall call : plan.getToolCalls()) {
            ToolResult result = toolRegistry.get(call.getToolName())
                .execute(call.getArguments(), buildContext(run));
            saveToolStep(run, call, result);
            toolResults.add(result);
        }
    }

    String answer = answerGenerator.generate(userMessage, toolResults, ...);
    saveFinalAnswer(run, answer);
    return buildResponse(run, answer, toolResults);
}
```

### 这一层一定要做的事
- 统一异常处理
- 每一步都记录 trace
- 控制最大步数
- 工具失败时允许兜底回答
- 超时控制

### 验收标准
- 一个问题可以完整走完：PLAN -> TOOL_CALL -> FINAL_ANSWER
- 即使工具失败，也不会整个系统直接崩

---

# 第五阶段：把 Trace 做出来

## Step 13：Trace 不是可选项，是项目亮点

你这个项目能不能像“大厂项目”，Trace 很关键。

### 每个 step 至少记录
- `runId`
- `stepNo`
- `stepType`
- `toolName`
- `inputJson`
- `outputJson`
- `latencyMs`
- `success`
- `errorMessage`

### 你还可以额外记录
- `promptVersion`
- `modelName`
- `retrievalDocIds`
- `tokenUsage`（如果你后面补）

### 对外接口
- `GET /api/agent/runs/{id}`
- `GET /api/agent/runs/{id}/steps`

### 验收标准
- 任意一次 Agent 运行，都能回放每一步
- 面试时你可以现场给面试官展示这个 trace

---

# 第六阶段：把 Eval 做出来

## Step 14：先做最小评测系统

不要一开始追求复杂评分模型，先做最小闭环：

### 14.1 评测集数据结构
每条 case 包括：
- question
- expectedTool
- expectedKeywords
- expectedReference

### 14.2 评测执行流程
1. 用户创建 `eval_run`
2. 把所有 case 丢到 RabbitMQ
3. Consumer 逐条跑 Agent
4. 存 `eval_result`
5. 汇总成功率与耗时

### 14.3 第一版评分方式
- 是否命中预期工具
- 答案是否包含关键字
- 是否返回引用
- 是否超时

### 评测接口
- `POST /api/evals/datasets`
- `POST /api/evals/runs`
- `GET /api/evals/runs/{id}`
- `GET /api/evals/runs/{id}/results`

### 验收标准
- 可以上传或录入 20~50 条 case
- 可以一键跑批
- 能得到成功率和失败样本

---

# 第七阶段：把 RabbitMQ 用起来

## Step 15：RabbitMQ 主要用于 2 个地方

### 用途 1：异步跑评测
这是最推荐的，因为不会影响主聊天链路。

队列建议：
- `agent.eval.run.queue`
- `agent.eval.run.dlx`

### 用途 2：异步做知识库索引更新
如果你想把 AgentOps 跟 AISmartQA 进一步打通：
- 上传文档后异步切 chunk
- 异步 embedding
- 异步写 ES / Milvus

### 你一开始不用做的地方
- 不要把主对话流程直接上 MQ
- 先同步走通，评测再异步

### 验收标准
- 发起评测后请求能立即返回
- Consumer 后台慢慢跑完所有 case

---

# 第八阶段：加 Redis

## Step 16：Redis 先做这 4 件事

### 16.1 JWT 黑名单（可选）
如果你要做退出登录或强制失效。

### 16.2 会话摘要缓存
将最近 N 轮会话摘要缓存起来，减少每次都查全量消息。

### 16.3 工具结果短期缓存
例如相同 `kb_search(query=xxx, topK=5)` 结果缓存 5 分钟。

### 16.4 限流
最简单：按用户维度限制每分钟调用次数。

### 推荐 Key 设计
- `agent:session:summary:{sessionId}`
- `agent:tool:kb_search:{hash}`
- `agent:rate_limit:user:{userId}`

### 验收标准
- 相同查询重复请求能看到明显缓存效果
- 高频刷接口会被限流挡住

---

# 第九阶段：把 MCP 真正接进来

## Step 17：什么时候开始做 MCP

只有当你下面这些都已经跑通时，才开始：
- 本地工具版 Agent 能工作
- Trace 能回放
- Eval 能跑
- 你能稳定完成一次完整对话

如果这些还没跑通，不要碰 MCP。

---

## Step 18：MCP 最小实现方案

### 你在项目里要实现的最小 MCP 能力

#### 主系统（AgentOps Main Backend）
充当 **MCP Client**，至少支持：
- `initialize`
- `tools/list`
- `tools/call`

#### 子系统（例如 `agentops-mcp-kb-server`）
充当 **MCP Server**，至少支持：
- `initialize`
- `tools/list`
- `tools/call`

### 为什么先不做 `prompts` / `resources`
不是不能做，而是：
- `tools` 足够支撑 MVP
- `resources` 可以作为加分项后补
- `prompts` 最后补也不迟

---

## Step 19：知识库 MCP Server 怎么做

建议你第一个拆出去的就是 `kb_search`。

### 独立服务职责
- 提供 `kb_search`
- 提供 `doc_fetch`
- 可选提供 `resources/read`

### 建议接口语义（在服务内部）
- `tools/list` 返回：
  - `kb_search`
  - `doc_fetch`
- `tools/call` 根据 name 分发

### 这个服务内部仍然可以调用：
- Elasticsearch
- Milvus
- MySQL
- Gemini Embedding

这样主系统和工具系统边界就清楚了。

---

## Step 20：MCP Client 怎么做

### 推荐做法
封装一个 `McpHttpClient`：
- `initialize(serverConfig)`
- `listTools(serverConfig)`
- `callTool(serverConfig, toolName, arguments)`

### 你先不需要追求的东西
- 多 server 并发
- 自动重连
- 双向复杂流式控制

### 你第一版需要做到的事
- 能连通一个 MCP Server
- 能拿到工具列表
- 能成功发起一次 `tools/call`
- 调用失败能记录错误

---

## Step 21：何时补 `resources`

当你 `kb_search` 跑通后，可以再做 `resources/read`：

例如：
- `kb://document/{id}`
- `kb://chunk/{id}`

这样主系统可以让模型先调用 `kb_search` 再读取具体资源。

**这一步是加分项，不是必做项。**

---

# 第十阶段：前端与展示

## Step 22：基础展示页面（哪怕简单也要有）

你如果没时间做复杂前端，至少做 Swagger + 简单 HTML 页面；但最好有一个最简单的页面展示：

- 登录
- 会话列表
- 聊天界面
- Trace 面板
- Eval 面板

如果你前端弱，可以：
- 用 Thymeleaf 做极简页面，或者
- 用一个简单 Vue 页面，只做展示

### 最低要求
面试时你至少能演示：
1. 输入一个问题  
2. 系统规划调用工具  
3. 展示工具返回  
4. 展示最终回答  
5. 展示 trace

---

# 第十一阶段：开发顺序总结（强执行版）

## 最优顺序（严格照这个来）

### 第 1 周
1. Spring Boot 工程初始化  
2. MySQL / Redis / RabbitMQ / ES / Milvus 本地环境启动  
3. JWT 登录鉴权  
4. 会话、消息、run、step 表与接口  
5. 先做 `doc_fetch`

### 第 2 周
6. Gemini REST 调用封装  
7. Planner 结构化输出  
8. `kb_search`（先 ES，再 Milvus，再混合）  
9. AgentExecutor 串起来  
10. 最终回答生成

### 第 3 周
11. `sql_query` 模板版  
12. Trace 记录与回放接口  
13. Redis 缓存与限流  
14. Eval 数据集与跑批  
15. RabbitMQ 异步评测

### 第 4 周（加分阶段）
16. 拆出 `agentops-mcp-kb-server`  
17. 主系统实现 MCP Client  
18. 接入 `tools/list` + `tools/call`  
19. 可选 `resources/read`  
20. 完善简历与项目文档

---

## 23. 最低 MVP 交付标准

如果你时间有限，下面这些做出来就已经够写简历：

### 必须有
- JWT 登录
- 会话管理
- Agent Run / Step
- `kb_search`
- `doc_fetch`
- Gemini Planner + Final Answer
- Trace 回放

### 强烈建议有
- `sql_query`
- Eval 跑批
- RabbitMQ 异步评测

### 加分项
- MCP 远程工具化
- `resources/read`
- Prompt 版本管理

---

## 24. 你一定会踩的坑（提前告诉你）

### 坑 1：一开始就想做“通用 Agent 平台”
解决：只做 3 个工具、串行调用、4 步以内。

### 坑 2：一开始就做“自然语言任意 SQL”
解决：先模板化，只做白名单查询。

### 坑 3：检索没做干净就开始做 Agent
解决：先单独把 `kb_search` 调到满意，再接模型。

### 坑 4：一上来就远程 MCP Server
解决：先本地工具，后 MCP。

### 坑 5：没有 Trace，调试全靠猜
解决：每一步都记录输入、输出、耗时、异常。

### 坑 6：功能太多，结果没有一个做扎实
解决：先把以下 1 条链路跑通：
`用户提问 -> 规划 -> kb_search -> 最终回答 -> trace`

---

## 25. 你可以直接复用的 Prompt 设计思路

### Planner Prompt 要点
- 你是工具规划器
- 你只能从给定工具中选
- 若问题可以直接回答，则不用工具
- 若工具不足，优先选 `kb_search`
- 输出必须为 JSON

### Answer Prompt 要点
- 你是企业级 Agent 的回答生成器
- 必须以工具结果为依据
- 如有引用，按列表返回来源
- 如果工具结果不足，明确说明信息不足，不得编造

---

## 26. 简历写法（做完后）

项目名：**AgentOps：基于 MCP 的企业级多工具智能体平台**

你最终可以写成：

- 设计并实现面向企业知识库与数据分析场景的多工具智能体平台，支持 **会话管理、工具调用编排、调用链追踪与自动评测**。  
- 复用知识库问答系统的 **Elasticsearch + Milvus 混合检索** 能力，封装 `kb_search` / `doc_fetch` 工具，支持基于检索结果的引用式回答生成。  
- 基于 **Gemini 2.5 Flash** 构建 Agent 规划与回答链路，使用结构化输出驱动工具参数生成，并通过 `Run/Step` 机制记录完整执行轨迹。  
- 构建 `sql_query` 只读白名单工具、RabbitMQ 异步评测流程与 Redis 缓存/限流能力，提升系统稳定性与评测效率。  
- 将本地工具进一步抽象为 **MCP Client / MCP Server** 架构，支持远程工具发现与调用，增强系统扩展性。

---

## 27. 面试时怎么讲这项目

你可以用这条主线：

1. 我先做了一个多工具智能体平台，而不是再做一个普通 RAG。  
2. 我复用了 AISmartQA 的检索能力，把它升级成 Agent 的一个工具。  
3. 主系统负责会话、运行链路、trace、评测；工具系统负责能力暴露。  
4. 为了降低复杂度，我先做本地工具，再升级成 MCP。  
5. 项目亮点不只是“调模型”，而是：
   - 工具调用抽象
   - 结构化规划
   - trace 回放
   - 评测闭环
   - MCP 扩展性

---

## 28. 最终建议：你现在就按这个顺序开工

### 立刻开始做
1. Spring Boot 工程 + 鉴权  
2. Session / Run / Step  
3. `doc_fetch`  
4. Gemini Planner 结构化输出  
5. `kb_search`  
6. AgentExecutor  
7. Trace

### 稳了之后再做
8. `sql_query`  
9. Eval + RabbitMQ  
10. Redis 限流 / 缓存

### 最后再补
11. MCP Server  
12. Resources  
13. 更复杂的多工具编排

---

## 29. 你最应该记住的一句话

**这个项目最关键的不是“工具多”，而是“链路完整”：**

> 用户提问 -> Planner 规划 -> Tool 调用 -> Final Answer -> Trace -> Eval

只要这条链路完整，而且你能展示其中的工程细节，这个项目就足够有竞争力。

---

## 30. 你现在下一步该做什么

按优先级：

1. 搭 Spring Boot 主工程  
2. 先做 JWT 登录  
3. 建表：session / message / run / step  
4. 用假数据先跑通 `doc_fetch`  
5. 接 Gemini，跑通 Planner JSON 输出  
6. 接 `kb_search`  
7. 做完整一次 run

做完这 7 步，你就已经从“想法”进入“项目真正在长出来”的阶段了。


---

## 配套材料索引

- `README.md`
- `docs/01-Demo演示路径.md`
- `docs/02-高频问答清单.md`
- `docs/03-简历项目描述与讲稿.md`
- `docs/04-10分钟稳定讲稿与追问树.md`