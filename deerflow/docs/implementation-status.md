# DeerFlow 实现状态

本文用于回答三个问题：能力是否已经实现、是否在默认路径生效、以及还存在哪些工程边界。状态以当前源码、配置和测试为依据。

## 状态定义

| 状态 | 含义 |
| --- | --- |
| **Active** | 已实现，并在仓库默认运行路径生效 |
| **Optional** | 已实现，需要 profile、环境变量或配置显式启用 |
| **Experimental** | 有代码或测试，但不是当前产品入口的 active path |
| **Development default** | 默认启用，但仅适合可信本地开发，不代表生产安全默认 |
| **Not implemented** | 当前没有满足该能力的完整实现 |

## 能力矩阵

| 能力 | 状态 | 当前代码事实 | 主要证据 |
| --- | --- | --- | --- |
| WebFlux REST + SSE | **Active** | `/api/deerflow/runs/stream` 创建 Run 并流式返回 `AgentEvent` | [`RunController`](../src/main/java/org/wrj/haifa/ai/deerflow/web/RunController.java) |
| Graph First | **Active** | 默认 `graph.enabled=true`、`mode=GRAPH_FIRST` | [`application.yml`](../src/main/resources/application.yml)、[`SimpleAgentRuntime`](../src/main/java/org/wrj/haifa/ai/deerflow/agent/SimpleAgentRuntime.java) |
| 统一 Chat / Research Graph | **Active** | 当前 `CHAT` 和 `RESEARCH` active path 都进入 `GraphChatRuntime` | [`SimpleAgentRuntimeGraphFirstResearchTest`](../src/test/java/org/wrj/haifa/ai/deerflow/agent/SimpleAgentRuntimeGraphFirstResearchTest.java) |
| Legacy Agent Loop | **Optional** | Graph 关闭或 fallback 时使用 `AgentLoop` | [`AgentLoop`](../src/main/java/org/wrj/haifa/ai/deerflow/agent/loop/AgentLoop.java) |
| 独立 Research Graph | **Experimental** | `GraphResearchRuntime` / `ResearchAgentGraph` 存在且有测试，但当前入口明确不启用 | [`GraphResearchRuntime`](../src/main/java/org/wrj/haifa/ai/deerflow/graph/GraphResearchRuntime.java)、[`SimpleAgentRuntime`](../src/main/java/org/wrj/haifa/ai/deerflow/agent/SimpleAgentRuntime.java) |
| Graph checkpoint | **Active** | 节点状态和 next node 落入 SQLite checkpoint store | [`SQLiteCheckpointSaver`](../src/main/java/org/wrj/haifa/ai/deerflow/graph/checkpoint/SQLiteCheckpointSaver.java) |
| Clarification / Approval 恢复 | **Active** | Graph 可挂起；恢复请求校验同 Thread 的 `SUSPENDED` Run，并继续该 Run | [`SimpleAgentRuntime`](../src/main/java/org/wrj/haifa/ai/deerflow/agent/SimpleAgentRuntime.java)、[`GraphChatRuntime`](../src/main/java/org/wrj/haifa/ai/deerflow/graph/GraphChatRuntime.java) |
| 工具执行幂等 | **Active** | 持久化工具执行结果可避免恢复或重试时重复执行相同调用 | [`ToolExecutionIdempotencyService`](../src/main/java/org/wrj/haifa/ai/deerflow/tool/execution/ToolExecutionIdempotencyService.java) |
| Deep Research | **Active** | `RESEARCH` 模式自动激活 Skill，并维护 plan、source、evidence、claim、citation、quality、budget 与报告 | [`ResearchLoopObserver`](../src/main/java/org/wrj/haifa/ai/deerflow/research/ResearchLoopObserver.java) |
| Subagent | **Active** | Subagent 复用 Runtime，并受递归深度和并发限制 | [`SubagentRuntime`](../src/main/java/org/wrj/haifa/ai/deerflow/subagent/SubagentRuntime.java)、[`SubagentLimitMiddlewareTest`](../src/test/java/org/wrj/haifa/ai/deerflow/middleware/SubagentLimitMiddlewareTest.java) |
| MCP Client | **Active** | 默认启用；`utility` 是 required Streamable HTTP connection，动态工具进入 `ToolRegistry` | [`McpConnectionManager`](../src/main/java/org/wrj/haifa/ai/deerflow/mcp/McpConnectionManager.java)、[`ToolRegistryMcpExposureTest`](../src/test/java/org/wrj/haifa/ai/deerflow/tool/ToolRegistryMcpExposureTest.java) |
| 第三方 MCP 兼容连接 | **Optional** | 配置模板存在但默认关闭，工具 allowlist 默认为空 | [`application.yml`](../src/main/resources/application.yml) |
| Fetch MCP | **Experimental** | 高风险兼容连接默认关闭，内置 `web_fetch` 仍拥有能力 | [Fetch MCP 安全说明](fetch-mcp-security-and-parity.md) |
| Skills | **Active** | 加载 public / custom Skill；Skill 提供指令和资源，不等同于 Tool | [Skill / Tool Runtime](skill-tool-runtime.md) |
| Sandbox | **Development default** | 默认 `local-trusted`，允许宿主执行和网络；另有 `local-restricted` / `docker` | [Sandbox Runtime](sandbox-runtime.md) |
| HITL Approval | **Optional** | Graph 和 legacy loop 都有 approval gate，但 YAML 默认关闭 | [`ApprovalPolicyServiceTest`](../src/test/java/org/wrj/haifa/ai/deerflow/approval/ApprovalPolicyServiceTest.java) |
| Approval 跨重启恢复 | **Not implemented** | `ApprovalStore` 当前是进程内实现，pending approval 不持久化 | [`AgentApprovalStore`](../src/main/java/org/wrj/haifa/ai/deerflow/persistence/store/AgentApprovalStore.java) |
| Thread / Run / Event 审计 | **Active** | SQLite + JPA 保存运行主记录、消息、事件、模型步骤和工具执行 | [`persistence`](../src/main/java/org/wrj/haifa/ai/deerflow/persistence) |
| 长期记忆与 Persona | **Active** | 提供 fact / candidate、审批 API、persona 和 Run 后 reflection | [`MemoryController`](../src/main/java/org/wrj/haifa/ai/deerflow/web/MemoryController.java) |
| Artifact | **Active** | 产物可注册、查询、预览和下载；registry 为内存 + JSON 文件 | [`ArtifactService`](../src/main/java/org/wrj/haifa/ai/deerflow/artifact/ArtifactService.java) |
| 语音对话 | **Optional** | 默认 fake；阿里云百炼和火山引擎 ASR / TTS 需显式配置 | [语音对话](voice-conversation.md) |
| OpenAI-compatible 模型 | **Optional** | `-Popenai` 引入模型 starter，凭据和模型由环境变量注入 | [`pom.xml`](../pom.xml) |
| Google GenAI 模型 | **Optional** | `-Pgoogle-genai` 使用原生 adapter，保留 Gemini 工具调用元数据 | [`pom.xml`](../pom.xml) |
| 分布式多实例 Runtime | **Not implemented** | 默认 SQLite 单连接与本地文件状态只面向单实例 | [`application.yml`](../src/main/resources/application.yml) |

## 默认启动前提

仓库默认配置不是“零配置即可完整运行”：

1. MCP 默认启用，必须先启动 `utility-mcp-server`；若要关闭，需要同时把 `HAIFA_AI_DEERFLOW_MCP_ENABLED` 和 `HAIFA_DEERFLOW_MCP_ENABLED` 设为 `false`。
2. Web Search / Fetch 默认使用 Aliyun，必须设置 `ALIYUN_API_KEY` / `DASHSCOPE_API_KEY`，或切换到 `duckduckgo` / `jina`。
3. 真实模型需要启用 `openai` 或 `google-genai` Maven profile 并注入凭据；fallback client 只用于本地拓扑验证。

具体命令见 [`deerflow/README.md`](../README.md#本地启动)。

## 生产化缺口

- 将默认 `local-trusted` 改为经过审查的 Docker / 远端隔离执行环境，并重新开启审批策略。
- 把 pending approval 和 artifact metadata 纳入可恢复的持久化事实源。
- 用服务型数据库、对象存储和分布式协调替换单实例 SQLite / 本地文件假设。
- 增加认证、租户隔离、凭据系统、限流、审计导出和灾备流程。
- 用固定 Provider / 模型 / 日期补充真实模型 E2E 证据。
