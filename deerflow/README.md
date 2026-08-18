# DeerFlow Runtime

`deerflow` 是 Haifa AI DeerFlow 的 Java 21 / Spring Boot WebFlux 后端。它负责 Thread / Run 生命周期、Graph 执行、模型与工具循环、SSE 事件、Deep Research、MCP、Skills、Sandbox、长期记忆和 SQLite 审计。

如果是第一次阅读项目，建议按以下顺序：

1. [实现状态](docs/implementation-status.md)：先区分当前生效、可选与实验性能力。
2. [架构说明](docs/architecture.md)：理解组件边界与主链路。
3. [Runtime 执行链路](docs/runtime-execution.md)：理解 Graph、checkpoint、恢复和幂等。
4. [Deep Research](docs/deep-research.md)：理解研究数据如何从来源走到报告。

## 核心能力

| 能力 | 当前行为 |
| --- | --- |
| Run API | `POST /api/deerflow/runs/stream` 创建 Run，并用 SSE 输出持久化的 `AgentEvent`。 |
| Graph Runtime | 默认 `enabled=true`、`mode=GRAPH_FIRST`；`CHAT` 与 `RESEARCH` 的 active path 都进入 `GraphChatRuntime`。 |
| Tool Calling | 接收 Provider 的结构化工具调用，经工具目录、策略、审批、预算和执行审计后返回模型。 |
| MCP | 默认启用并连接必需的 `utility` Server；远程工具以版本化快照加入 `ToolRegistry`。 |
| Skills | 从 `skills/public` 与 `skills/custom` 加载指令包；研究模式自动激活 `deep-research`。 |
| Deep Research | 持久化 plan、work item、source、evidence、claim、citation、quality、budget，并生成 Markdown 报告产物。 |
| 可恢复执行 | Graph checkpoint 落入 SQLite；clarification / approval 挂起后可继续同一 Run。 |
| 审计 | SQLite 保存 thread、run、message、event、model step、tool call / execution 和 graph checkpoint。 |
| 文件与产物 | uploads、workspace、outputs 与 artifact registry 位于 `${user.dir}/data/user-data`。 |

## 本地启动

命令都从仓库根目录执行。

### 默认拓扑

默认配置同时满足以下条件：

- `MCP` 已启用，`utility` 是 required connection，需要先启动 `utility-mcp-server`。
- Web Search / Fetch 默认使用 Aliyun，需要 `ALIYUN_API_KEY` 或 `DASHSCOPE_API_KEY`。
- 没有真实模型 starter / 凭据时会注入 fallback client，仅用于启动和接口拓扑验证。

```powershell
# Terminal 1
mvn -pl utility-mcp-server -am spring-boot:run

# Terminal 2
$env:ALIYUN_API_KEY = '<your-api-key>'
mvn -pl deerflow -am spring-boot:run
```

后端监听 `http://localhost:8095`，健康检查为：

```bash
curl http://localhost:8095/api/deerflow/health
```

### 无密钥启动检查

若只验证本地拓扑，可改用无需 API Key 的 DuckDuckGo / Jina，并按需关闭 MCP。该模式不证明真实 Agent 任务已通过。

```powershell
$env:HAIFA_AI_DEERFLOW_TOOLS_WEB_SEARCH_PROVIDER = 'duckduckgo'
$env:HAIFA_AI_DEERFLOW_TOOLS_WEB_FETCH_PROVIDER = 'jina'
$env:HAIFA_AI_DEERFLOW_MCP_ENABLED = 'false'
$env:HAIFA_DEERFLOW_MCP_ENABLED = 'false'
mvn -pl deerflow -am spring-boot:run
```

当前 `DeerFlowProperties.isMcpEnabled()` 兼容旧的 `mcp-enabled` 和新的 `mcp.enabled`，两者按 OR 合并，因此关闭 MCP 时两个开关都必须设为 `false`。

## 模型接入

### OpenAI-compatible

```powershell
$env:OPENAI_API_KEY = '<your-api-key>'
$env:OPENAI_BASE_URL = '<your-complete-chat-completions-endpoint>'
$env:HAIFA_DEERFLOW_MODEL = '<your-model-id>'
$env:ALIYUN_API_KEY = '<your-search-api-key>'
mvn -pl deerflow -am -Popenai spring-boot:run
```

`OPENAI_BASE_URL` 在本项目中是完整的 Chat Completions endpoint。可选代理变量为 `LLM_NETWORK_PROXY_URL`、`LLM_NETWORK_PROXY_USERNAME`、`LLM_NETWORK_PROXY_PASSWORD`。

### Google GenAI

Gemini 工具调用使用 Google 原生 profile，以保留 `thought_signature`：

```powershell
$env:GEMINI_API_KEY = '<your-gemini-api-key>'
$env:HAIFA_DEERFLOW_MODEL = '<your-gemini-model-id>'
$env:ALIYUN_API_KEY = '<your-search-api-key>'
mvn -pl deerflow -am -Pgoogle-genai spring-boot:run
```

不要同时启用 `openai` 和 `google-genai` profile。

## API 快速验证

### Chat

```bash
curl -N -X POST http://localhost:8095/api/deerflow/runs/stream \
  -H "Content-Type: application/json" \
  -d '{"message":"列出 workspace 文件并说明你看到了什么","mode":"CHAT"}'
```

### Deep Research

```bash
curl -N -X POST http://localhost:8095/api/deerflow/runs/stream \
  -H "Content-Type: application/json" \
  -d '{
    "message":"调研 Java Agent Sandbox 的主要设计路线，给出带引用的简明报告。",
    "mode":"RESEARCH",
    "researchOptions":{
      "depth":"STANDARD",
      "timeWindow":"LATEST",
      "maxSources":8,
      "requireCitations":true,
      "outputFormat":"REPORT"
    }
  }'
```

### 上传文件

```bash
curl -F "file=@notes.md" http://localhost:8095/api/deerflow/uploads
```

将返回的 `fileId` 放入 Run 请求的 `uploadedFileIds` 即可让模型读取该文件。

## 主要接口

| 接口 | 用途 |
| --- | --- |
| `POST /api/deerflow/runs/stream` | 创建 Run 并流式执行 |
| `POST /api/deerflow/runs/{runId}/cancel` | 取消 Run |
| `POST /api/deerflow/runs/{runId}/resume` | 回答 clarification / approval 后恢复挂起 Run |
| `GET /api/deerflow/runs/{runId}` | 查询 Run 状态 |
| `GET /api/deerflow/runs/{runId}/events` | 查询事件历史 |
| `GET /api/deerflow/runs/{runId}/observability` | 查询聚合观测视图 |
| `GET /api/deerflow/runs/{runId}/tool-calls` | 查询结构化工具调用 |
| `GET /api/deerflow/runs/{runId}/tool-executions` | 查询工具执行审计 |
| `GET /api/deerflow/runs/{runId}/sources` | 查询研究来源 |
| `GET /api/deerflow/runs/{runId}/evidence` | 查询研究证据 |
| `GET /api/deerflow/runs/{runId}/claims` | 查询研究主张 |
| `GET /api/deerflow/runs/{runId}/citations` | 查询引用映射 |
| `GET /api/deerflow/runs/{runId}/quality` | 查询质量评估 |
| `GET /api/deerflow/runs/{runId}/budget` | 查询预算账本 |
| `GET /api/deerflow/artifacts/{artifactId}/download` | 下载产物 |

控制器中的完整事实源见 [`web`](src/main/java/org/wrj/haifa/ai/deerflow/web) 包。

## 执行主链路

```text
RunController
  -> SimpleAgentRuntime
     -> MiddlewareChain
     -> GraphChatRuntime (default active path)
        -> model call
        -> approval / clarification gates
        -> built-in or MCP tools
        -> final-answer gate
        -> checkpoint + event persistence
```

关键源码：

- [`SimpleAgentRuntime`](src/main/java/org/wrj/haifa/ai/deerflow/agent/SimpleAgentRuntime.java)：Run 创建、路由、事件与收尾。
- [`GraphChatRuntime`](src/main/java/org/wrj/haifa/ai/deerflow/graph/GraphChatRuntime.java)：当前 Graph 节点和条件边。
- [`AgentLoop`](src/main/java/org/wrj/haifa/ai/deerflow/agent/loop/AgentLoop.java)：Graph 关闭时的 legacy fallback。
- [`ToolRegistry`](src/main/java/org/wrj/haifa/ai/deerflow/tool/ToolRegistry.java)：静态 Tool 与动态 MCP Tool 目录。
- [`ToolExecutionIdempotencyService`](src/main/java/org/wrj/haifa/ai/deerflow/tool/execution/ToolExecutionIdempotencyService.java)：持久化工具执行结果的幂等复用。
- [`SQLiteCheckpointSaver`](src/main/java/org/wrj/haifa/ai/deerflow/graph/checkpoint/SQLiteCheckpointSaver.java)：Graph checkpoint 持久化。

## 关键配置

以 [`application.yml`](src/main/resources/application.yml) 为当前仓库启动事实源：

| 配置 | 默认值 | 说明 |
| --- | --- | --- |
| `server.port` | `8095` | Runtime HTTP 端口 |
| `haifa.ai.deerflow.graph.enabled` | `true` | 启用 Graph |
| `haifa.ai.deerflow.graph.mode` | `GRAPH_FIRST` | Graph 为 active path |
| `haifa.ai.deerflow.graph.checkpoint.enabled` | `true` | SQLite checkpoint |
| `haifa.ai.deerflow.mcp.enabled` | `true` | 动态 MCP Tool 接入 |
| `haifa.ai.deerflow.mcp.servers.utility.required` | `true` | Utility 连接失败时阻止启动 |
| `haifa.ai.deerflow.sandbox.backend` | `local-trusted` | 可信本地开发模式 |
| `haifa.ai.deerflow.approval.enabled` | `false` | 默认关闭 HITL approval |
| `haifa.ai.deerflow.max-iterations` | `20` | Chat 迭代上限 |
| `haifa.ai.deerflow.max-tool-calls` | `80` | Chat 工具调用上限 |
| `haifa.ai.deerflow.max-research-steps` | `100` | Research 步数上限 |
| `haifa.ai.deerflow.max-fetches-per-run` | `200` | Research 工具预算上限 |

`DeerFlowProperties` 的 Java 默认值与 YAML 不完全相同；从本仓库启动时，以 YAML、profile 和环境变量合并后的结果为准。

## 开发与验证

```bash
# 编译
mvn -pl deerflow -am -DskipTests compile

# 测试
mvn -pl deerflow -am test

# 打包
mvn -pl deerflow -am package
```

Provider profile 的额外测试源码分别位于 `src/openai-test` 和 `src/google-genai-test`。真实外部 Provider 调用不属于默认测试路径。

## 运行边界

- 当前 active research 是统一 Agent Graph 上的研究模式，不是独立研究 Graph；`GraphResearchRuntime` / `ResearchAgentGraph` 保留为未启用的实验路径。
- SQLite WAL、`busy_timeout` 和单连接配置用于降低本地锁竞争，不支持多实例共享同一文件。
- `local-trusted`、宿主执行、网络访问和关闭 approval 的组合仅适合可信单用户开发。
- `ApprovalStore` 为内存实现；Graph checkpoint 可恢复执行状态，但 pending approval 本身不能跨进程恢复。
- Artifact registry 使用内存加 `${userDataRoot}/artifacts.json`；uploads、workspace、outputs 需要单独持久化和备份。

## 专题文档

- [文档索引](docs/README.md)
- [MCP 运维与治理](docs/mcp-operations.md)
- [Fetch MCP 安全与能力对齐](docs/fetch-mcp-security-and-parity.md)
- [Sandbox Runtime](docs/sandbox-runtime.md)
- [Skill / Tool Runtime](docs/skill-tool-runtime.md)
- [语音对话](docs/voice-conversation.md)
