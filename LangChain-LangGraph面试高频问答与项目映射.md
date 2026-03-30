# LangChain / LangGraph 面试高频问答与 AgentOps 项目映射

副标题：面向大厂 Agent 开发 / Agent 平台 / AI 应用工程面试  
版本：2026-03  
定位：把框架问答、系统抽象和 `AgentOps` 讲法统一成一套口径

## 使用说明

这份材料不是通用框架八股，也不是帮你堆名词，而是帮你把 `LangChain / LangGraph` 的问题，稳定落回 `AgentOps` 这套项目叙事里。

- 面试时不要把项目讲成“我用了 LangChain、LangGraph、Gemini”，而要讲成“我做了一个有 control plane、有 runtime、有 checkpoint、有审批恢复、有 Eval 的 Agent 系统”。
- 框架只回答两个问题：`它解决什么问题` 和 `它在 AgentOps 里具体落在哪一层`。
- 如果仓库里没有某项能力，就明确说“这是下一步增强”，不要硬说已上线。

## 一句话口径

最稳的框架口径是：

> `LangChain` 更偏高层抽象，负责模型接入、工具抽象和应用开发效率；`LangGraph` 更偏 Agent 运行时，负责状态化工作流、多步执行、人工介入和恢复能力。`AgentOps` 当前是直接把 LangGraph 用在 runtime 层，把 LangChain 用在工具与模型集成层，但项目价值不止是“用了框架”，而是把 control plane、runtime、checkpoint、approval 和 Eval 收成了一条完整主链。

## 30 秒版本

在 `AgentOps` 里，`LangGraph` 不是用来画图的，而是真正承接了多步状态图执行；`LangChain` 也不是高层 Agent 框架全家桶，而主要承担了 `StructuredTool` 和 Gemini 模型接入这层能力。真正让我项目更像大厂 Agent 系统的，不是“框架名词”本身，而是 `Java control plane + Python runtime + durable checkpoint + runtime Eval + human approval` 这整套工程骨架。

## 1 分钟版本

如果面试官问我 `LangChain / LangGraph` 在项目里怎么落地，我会分三层回答。第一层，`LangChain` 在 `AgentOps` 里主要承担模型与工具集成，比如 `ChatGoogleGenerativeAI` 和 `StructuredTool`，它帮我把 Gemini 节点和 `kb_search / doc_fetch / sql_query` 这类工具抽象接进 runtime。第二层，`LangGraph` 是 runtime 的核心，负责跑 `intake_guardrail -> load_memory -> supervisor_plan -> tool -> evidence_reviewer -> human_approval -> finalize` 这条状态图，并控制 `max_graph_hops / max_tool_loops / max_replans`。第三层，项目真正的工程价值在框架之外：Java control plane 统一持有 run、step、checkpoint、approval、Eval，Python runtime 不直接读数据库，只通过 Java 内部 API 和 MCP 工具平面工作。这样讲，框架和系统边界就统一了。

# 第一部分：为什么岗位总问 LangChain / LangGraph

## 面试题 1：为什么很多大厂 Agent 岗会写 LangChain / LangGraph？

**标准回答**

因为很多岗位想筛的不是“会不会调某个模型接口”，而是你有没有做过 `tool abstraction、stateful workflow、human-in-the-loop、trace、eval、recovery` 这些 Agent 系统能力。`LangChain / LangGraph` 已经成了描述这类能力的高频行业关键词，所以它们经常出现在 JD 里。

**AgentOps 映射**

`AgentOps` 当前已经覆盖了这些关键词背后的大部分能力：LangGraph runtime、LangChain tools、checkpoint、审批恢复、runtime Eval、工具平面和 step trace。

**大厂式继续追问**

如果岗位写了 LangChain / LangGraph，它筛的到底是 API 熟练度，还是系统抽象能力？

**回答禁区**

不要回答成“因为这两个框架最火”。招聘写它们，通常不是为了单纯筛名词。

## 面试题 2：为什么企业更在意 LangGraph 这类运行时，而不是只看模型 SDK？

**标准回答**

因为模型 SDK 解决的是“怎么调模型”，而企业场景真正难的是“怎么把模型接进一个可治理的系统”。这时候就会涉及工作流、状态、工具调用、审批、恢复、评测和可观测性。LangGraph 之所以被强调，是因为它更接近这层运行时问题。

**AgentOps 映射**

`AgentOps` 现在的价值也不在 Gemini SDK，而在 `run / step / checkpoint / approval / eval` 这些运行态对象已经形成系统主链。

**大厂式继续追问**

为什么“会调模型”在 Agent 岗面试里通常只算起点？

**回答禁区**

不要把 Agent 岗理解成“模型调用工程师”。

# 第二部分：LangChain / LangGraph 核心问答

## 面试题 3：什么是 LangChain？

**标准回答**

更稳的讲法不是“它是一个链式调用框架”，而是：LangChain 是一个偏高层的 LLM 应用开发框架，主要帮助你把模型、消息、结构化输出、工具和常见 Agent 抽象用统一方式组织起来。它的核心价值是提升 AI 应用开发效率，而不是替代系统设计。

**AgentOps 映射**

在 `AgentOps` 里，LangChain 主要落在模型和工具集成层，比如 `ChatGoogleGenerativeAI` 和 `StructuredTool`。我并没有用 LangChain 高层 Agent 框架去主导整个系统编排，主编排仍然在 LangGraph runtime。

**大厂式继续追问**

如果面试官问“那你项目里具体哪里用了 LangChain”，你怎么答？

**回答禁区**

不要答成“我用了 LangChain，所以项目就是 Agent 平台”。LangChain 只是其中一层。

## 面试题 4：什么是 LangGraph？

**标准回答**

LangGraph 更像一个 Agent 工作流运行时。它解决的重点不是“快速拼一个 prompt 链”，而是 `state、node、edge、multi-step execution、human-in-the-loop、durable execution、debugging` 这些问题。也就是说，它更接近 Agent 系统的运行层。

**AgentOps 映射**

`AgentOps` 的 Python runtime 用 LangGraph 承接真实图执行，节点包括 `supervisor_plan / evidence_reviewer / human_approval / finalize`，而不是只拿它画一张示意图。

**大厂式继续追问**

为什么 LangGraph 更适合复杂 Agent，而不是简单问答链？

**回答禁区**

不要把 LangGraph 讲成“多了几个节点和边而已”。重点是状态化运行时。

## 面试题 5：LangChain 和 LangGraph 的区别该怎么讲最稳？

**标准回答**

一句话讲：`LangChain` 偏高层抽象，帮你更快搭应用；`LangGraph` 偏底层运行时，帮你更细地控状态、流程和恢复。再补一句更完整：LangChain 的 Agent 能力是建立在 LangGraph 运行时之上的，所以它们不是对立关系，而是高低层关系。

**AgentOps 映射**

我在 `AgentOps` 里就是这么用的：LangChain 负责模型与工具接入，LangGraph 负责图执行和状态工作流，Java control plane 则负责记录源和治理。

**大厂式继续追问**

为什么一个复杂 Agent 系统通常不能只靠高层抽象讲清楚？

**回答禁区**

不要把它们说成“一个新版本替代另一个”。这会显得理解浅。

## 面试题 6：LangGraph 里的 `State / Node / Edge` 最该怎么讲？

**标准回答**

不要把它讲成图论定义。更稳的讲法是：State 是当前运行上下文和执行状态，Node 是某一步处理逻辑，Edge 是从一步走到下一步的条件和控制流。它们的价值在于让 Agent 的分支、回退、审批和停止条件都能被显式表达。

**AgentOps 映射**

`AgentOps` 的 state 里有 `runId / sessionId / pendingTasks / toolTrace / evidence / currentNode / checkpointVersion / approvalPolicy / executionMode / confidence`；Node 包括 `supervisor_plan`、`knowledge_researcher`、`data_analyst`、`evidence_reviewer`、`human_approval`；Edge 则表达 route、审批、replan 和 finalize 的流转。

**大厂式继续追问**

为什么有了人工审批和 replan 之后，图式建模会比单链路自然得多？

**回答禁区**

不要只回答“因为 LangGraph 就是图”。要讲清楚为什么图建模更适合 Agent。

## 面试题 7：什么叫 durable execution？在 AgentOps 里怎么讲才不虚？

**标准回答**

durable execution 的本质是“运行过程可持久化、失败或暂停后可以从已知状态恢复，而不是每次都从头来”。在 `AgentOps` 里，我更稳的讲法是：系统已经具备 durable runtime 的核心能力，因为 checkpoint 不再停留在 Python 内存里，而是回到了 Java / MySQL control plane；但我不会把它说成“已经接官方 durable checkpointer”。

**AgentOps 映射**

当前 Java 有 `agent_runtime_checkpoint` 和内部 checkpoint API，runtime 每个关键节点执行后都会落 checkpoint，进入 `human_approval` 前也一定会落 checkpoint。

**大厂式继续追问**

为什么这里要明确区分“durable runtime 能力已落地”和“官方 durable checkpointer 尚未接入”？

**回答禁区**

不要把现在的实现说成“已经完全等价于官方 durable checkpointer”。

## 面试题 8：什么叫 human-in-the-loop？为什么它不是“模型不够强”的表现？

**标准回答**

human-in-the-loop 不是模型退步，而是工程边界。企业系统里很多动作天然有风险，比如宽范围 SQL、敏感数据读取、知识证据和数据证据冲突。在这种场景下，允许人介入审批、补充信息或直接拒绝，是系统安全边界的一部分。

**AgentOps 映射**

`AgentOps` 里 `human_approval` 是 runtime 的正式节点，不是页面装饰；高风险 SQL、reviewer 低置信度和证据冲突都会进入这一分支。

**大厂式继续追问**

为什么审批被拒绝后，系统不能继续绕过风险工具？

**回答禁区**

不要把 human-in-the-loop 讲成“用户体验功能”。它首先是风控能力。

# 第三部分：AgentOps 里到底怎么用 LangChain / LangGraph

## 面试题 9：如果面试官问“你在 AgentOps 里具体哪里用了 LangChain”，怎么答？

**标准回答**

要答具体，不要泛化。我在 `AgentOps` 里主要用 LangChain 的两层能力：一层是模型接入，用 `langchain-google-genai` 对接 Gemini；另一层是工具抽象，用 `StructuredTool` 把 Java MCP 暴露的 `kb_search / doc_fetch / sql_query` 包成 runtime 可调用的工具。也就是说，我用 LangChain 的地方主要是“模型和工具接口层”，不是拿它的高层 Agent 抽象直接做整套编排。

**AgentOps 映射**

runtime 里真实存在 `ChatGoogleGenerativeAI`、`StructuredTool` 和工具调用封装；但主编排还是 LangGraph + 自己的 runtime 状态机。

**大厂式继续追问**

为什么这个回答比“我项目里用过 LangChain”更有说服力？

**回答禁区**

不要模糊地说“LangChain 到处都用了”。这会被追问到细节后露怯。

## 面试题 10：如果面试官问“你在 AgentOps 里具体哪里用了 LangGraph”，怎么答？

**标准回答**

LangGraph 在我的项目里落在 runtime 层，用来组织真实状态图执行，而不是简单示意图。我的 runtime 会按 `intake_guardrail -> load_memory -> supervisor_plan -> knowledge_researcher / data_analyst -> evidence_reviewer -> human_approval -> finalize` 这条图走，并且每个节点都和 state、checkpoint、trace、approval 这些运行态绑定。

**AgentOps 映射**

route、pendingTasks、reviewFeedback、toolTrace、humanDecision、checkpointVersion 这些状态都会随着图执行不断变化，而不是一次性问答后丢失。

**大厂式继续追问**

为什么这比“有一个 workflow 图”更像 Agent 运行时？

**回答禁区**

不要把 LangGraph 讲成可视化工具。它是 runtime，不是画图插件。

## 面试题 11：为什么 AgentOps 不直接用 LangChain 高层 Agent，而要自己保留 control plane？

**标准回答**

因为 LangChain 高层 Agent 解决的是开发效率问题，但系统记录源、鉴权、checkpoint、审批、Eval、trace 这些控制面能力，还是需要一个清晰的 control plane 来承接。我的目标不是快速做一个能跑的 agent demo，而是做一个可治理的 Agent 系统。

**AgentOps 映射**

Java control plane 统一持有 `session / run / step / checkpoint / human task / eval`，Python runtime 只负责运行时和决策，不直接侵入系统记录源。

**大厂式继续追问**

为什么这里不是“多写了一套壳”，而是“把系统记录源固定下来”？

**回答禁区**

不要回答成“因为我不想用高层框架”。关键是系统边界。

## 面试题 12：为什么 AgentOps 不是“LangGraph 一把梭”，还要 Java checkpoint / approval / Eval？

**标准回答**

因为 LangGraph 负责运行时，不等于自动提供完整的企业控制面。企业真正关心的是：状态落在哪、权限怎么管、审批怎么恢复、评测怎么和线上对齐、trace 怎么沉淀。Java control plane 在这里承接的是平台治理，而不是重复造图执行。

**AgentOps 映射**

checkpoint、resumeToken、checkpointVersion、human task、eval result、MCP、internal key 都在 Java 侧。

**大厂式继续追问**

为什么这类“框架之外的部分”恰恰是大厂最爱追的？

**回答禁区**

不要把 Java 层讲成“只是个 API 网关”。它是 control plane。

## 面试题 13：为什么 `AgentOps` 比 `AISmartQA` 更自然地映射到 LangGraph？

**标准回答**

因为 `AISmartQA` 更像工程化 RAG 系统，核心是检索链和异步治理；`AgentOps` 则天然有 `run / step / state / approval / eval / tool orchestration` 这些状态化执行模型，更贴近 LangGraph 语境里的 Agent 运行时问题。

**AgentOps 映射**

`AISmartQA` 更适合映射到 LangChain 语境里的工程化 RAG；`AgentOps` 则更适合映射到 LangGraph 语境里的状态化 Agent 系统。

**大厂式继续追问**

为什么这不是“谁更高级”，而是“问题类型不同”？

**回答禁区**

不要贬低 AISmartQA。它和 AgentOps 是不同层次的问题。

# 第四部分：高频追问与最稳接法

## 面试题 14：如果面试官问“为什么不把所有能力都写在 Python 里”，怎么答？

**标准回答**

因为我要一个稳定的系统记录源。run、step、checkpoint、approval、Eval 这些控制面事实放在 Java 更利于鉴权、审计和系统边界稳定；Python runtime 则专注图执行和模型决策。这种分层会多一跳网络，但换来了统一状态视图。

**AgentOps 映射**

runtime 不直接读数据库，而是通过 Java 内部 API 和 MCP 工具平面工作。

**大厂式继续追问**

这是不是过度设计？

**回答禁区**

不要只说“Java 更稳定”。面试官要听的是边界收益。

## 面试题 15：如果面试官问“那你是不是自己造轮子”，怎么接？

**标准回答**

更稳的回答不是否认，而是讲抽象层级：我没有重造 LangGraph 的图执行能力，而是用它承接 runtime；我自己补的是 control plane 这层，包括 checkpoint 持久化、审批恢复、运行轨迹和 Eval 闭环。也就是说，我不是重复造图执行，而是在做 LangGraph 之外的系统骨架。

**AgentOps 映射**

LangGraph 解决 stateful workflow，Java 解决系统记录源和治理，二者职责不同。

**大厂式继续追问**

为什么“自己做过控制面”在面试里反而是加分项？

**回答禁区**

不要回答成“我不用框架所以更厉害”。这种口气很危险。

## 面试题 16：如果面试官问“你现在这个项目离生产级 LangGraph 平台还差什么”，怎么答？

**标准回答**

我会把它分成“主链能力”和“平台增强”两类来讲。主链能力已经到位，包括 runtime 图执行、checkpoint、approval、Eval 和工具平面；平台增强还包括更严格的 schema-governed prompt 管理、官方 durable checkpointer 形态、事件流 UI、以及更完整的工具权限和 SQL 风控体系。

**AgentOps 映射**

README 和题库里都明确写了：当前前端仍是轮询，checkpoint 不是官方 durable checkpointer 形态，通用 crash replay 还是增强项。

**大厂式继续追问**

为什么把这些讲成“增强项”，而不是“项目没做完”，更稳？

**回答禁区**

不要把边界说成“还有很多没做”。要讲清主链已经成立。

# 第五部分：高压追问树

## 追问树 1：LangChain 和 LangGraph 的关系

**Q：LangChain 和 LangGraph 到底是什么关系？**  
答：LangChain 更偏高层抽象，LangGraph 更偏运行时；LangChain 的 Agent 能力是建立在 LangGraph 运行时之上的。

继续追问：那你项目里更重的是哪个？  
答：更重的是 LangGraph runtime，因为我的核心问题是状态化执行、审批、恢复和 Eval，而不是快速拼一个 Agent demo。

继续追问：那 LangChain 呢？  
答：LangChain 在我项目里主要承担模型和工具接口层，而不是主编排。

## 追问树 2：为什么不是全 Python

**Q：为什么你不把 run、checkpoint、Eval 也放 Python？**  
答：因为我要一个统一的系统记录源，控制面放 Java 更利于鉴权、审计和长期演进。

继续追问：那这样不是更复杂吗？  
答：是更复杂，但复杂换来了清晰边界和更强的治理能力。

继续追问：为什么这点是大厂会买账的？  
答：因为它说明我不是只会做 demo，而是在考虑控制面与运行时的分层。

## 追问树 3：durable execution

**Q：你说 durable execution，具体落在哪？**  
答：落在 checkpoint 不再依赖 Python 内存，而是进入 Java / MySQL control plane。

继续追问：是不是已经接官方 durable checkpointer？  
答：不是，我会明确说这是 control plane 持久化方案，能力上实现了 durable runtime，但不是官方 durable checkpointer 形态。

继续追问：为什么这么讲更稳？  
答：因为它既说明能力已落地，也不把边界说虚。

## 追问树 4：human-in-the-loop

**Q：human-in-the-loop 在你项目里是什么？**  
答：是 runtime 的正式分支，不是页面装饰。

继续追问：哪些情况触发？  
答：高风险 SQL、低置信 reviewer、知识与数据证据冲突。

继续追问：拒绝后为什么不能继续跑？  
答：因为审批拒绝就是风控边界本身，绕过它会破坏系统设计。

## 追问树 5：runtime Eval

**Q：为什么你现在的 Eval 更可信？**  
答：因为它执行的就是线上相同的 runtime 图，而不是旁路脚本。

继续追问：为什么一定要记录 nodePath？  
答：因为 Agent 系统不仅要看答案，还要看路径。

继续追问：如果答案对了但路径错了呢？  
答：仍然是问题，尤其在审批和风控分支里。

## 追问树 6：MCP 与 tool plane

**Q：MCP 在你项目里到底重不重要？**  
答：重要，但它是工具平面，不是系统全部核心。

继续追问：为什么没有 MCP 也能调工具？  
答：能调工具不等于有统一工具平面，MCP 的价值在于统一协议、发现和治理入口。

继续追问：为什么这点适合大厂面试？  
答：因为它说明我在做平台边界，而不是点对点调用。

## 追问树 7：failure modes

**Q：structured output 失败和证据不足，为什么不能一回事？**  
答：一个是模型协议失败，一个是业务语义失败，收口策略不同。

继续追问：runtime crash 怎么讲？  
答：最后一次已提交状态会留在 control plane，但当前最完整恢复路径仍是审批态 resume。

继续追问：为什么要诚实讲这个边界？  
答：因为大厂面试更看重你是否知道系统还差什么，而不是你能不能把项目吹满。

## 追问树 8：最大 tradeoff

**Q：你这个项目最大的 tradeoff 是什么？**  
答：我优先选择了“分层清晰、记录源统一、主链可审计”，而不是单语言、单模块的开发简单度。

继续追问：代价是什么？  
答：实现更复杂、服务交互更多、前端体验阶段性用了轮询。

继续追问：为什么仍然值得？  
答：因为这让项目更像真实 Agent 平台，而不是框架 demo。

# 第六部分：最稳的 20 秒结论

如果面试官只给我 20 秒解释 `LangChain / LangGraph` 和 `AgentOps` 的关系，我会这样说：

> `LangChain` 在我的项目里主要承担模型和工具集成，`LangGraph` 承担状态化 Agent 运行时；但 `AgentOps` 的真正价值不止是用了这两个框架，而是把 Java control plane、Python runtime、checkpoint、human approval 和 runtime Eval 统一成了一条可运行、可追踪、可恢复的 Agent 主链。

# 第七部分：诚实边界

- 我不会把当前 checkpoint 说成“官方 durable checkpointer 已接入”，更稳的说法是“control plane 持久化方案已落地”。
- 我不会把前端说成流式事件系统，当前仍是轮询。
- 我不会把工具权限和 SQL 风控说成完整生产级沙箱，当前是已有边界但未到终态。
- 我不会把 `LangChain` 说成项目主编排框架，更准确的说法是：它主要落在模型与工具集成层。
