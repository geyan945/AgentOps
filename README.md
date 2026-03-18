# AgentOps

AgentOps 是一个基于 Spring Boot 3.5.11 / Java 25 的企业级多工具智能体平台，当前已经完成可演示、可写简历、可讲 5 到 10 分钟的完整闭环版本。

## 当前已完成能力

- JWT 登录与鉴权
- 会话管理与消息记录
- Agent Run / Step Trace
- 本地工具 + MCP 远程工具第一版
- 3 类工具能力
  - `kb_search`
  - `doc_fetch`
  - `sql_query`
- Rule Planner / Gemini Planner 可切换
- Rule Answer / Gemini Answer 可切换
- RabbitMQ 异步 Eval
- Redis 限流
- 工具结果缓存
- 会话摘要缓存
- Eval Dashboard / 失败样本分析
- 复用 AISmartQA 的知识库数据与 ES 索引

## 目录

- `agentops-app/`：主后端工程
- `docs/01-Demo演示路径.md`
- `docs/02-高频问答清单.md`
- `docs/03-简历项目描述与讲稿.md`
- `docs/04-10分钟稳定讲稿与追问树.md`
- `docs/05-10分钟稳定讲稿与追问树（强化版）.md`
- `AgentOps.md`：完整实现路线文档

## 当前环境对齐

- Java 25
- Spring Boot 3.5.11
- MySQL: `localhost:3406`
- Redis: `localhost:16379`
- RabbitMQ: `localhost:5672`
- Elasticsearch: `localhost:19200`
- AgentOps app: `localhost:18084`

## 当前主链

```text
注册/登录
 -> 新建会话
 -> Agent Run
 -> Planner 规划
 -> 本地工具 / MCP 远程工具调用
 -> Final Answer
 -> Run/Step Trace 回放
 -> Eval 数据集/Run/结果统计
```

## 当前平台亮点

- **工具抽象**：统一 `ToolExecutor` 接口，主链不依赖具体工具实现方式
- **MCP 第一版**：支持 `initialize`、`tools/list`、`tools/call`
- **可观测性**：完整 `session -> run -> step` 执行链
- **评测闭环**：数据集、异步 Eval、结果、Dashboard、失败样本分析
- **工程化治理**：Redis 限流、工具缓存、会话摘要缓存

## 启动

在 `agentops-app/` 下执行：

```powershell
./mvnw.cmd spring-boot:run
```

## 推荐阅读顺序

1. `AgentOps.md`
2. `docs/01-Demo演示路径.md`
3. `docs/03-简历项目描述与讲稿.md`
4. `docs/02-高频问答清单.md`
5. `docs/04-10分钟稳定讲稿与追问树.md`
6. `docs/05-10分钟稳定讲稿与追问树（强化版）.md`