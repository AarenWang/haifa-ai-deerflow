# 演示与验证指南

本指南用于快速验证项目的三条核心链路：服务拓扑、受治理 Tool / MCP 调用，以及带结构化证据的 Deep Research。真实外部服务调用应记录 Provider、模型、日期和验证范围。

## 1. 演示前准备

### 必需环境

- JDK 21、Maven 3.x。
- Node.js 与 npm。
- 一个支持结构化 Tool Calling 的模型及凭据。
- 默认 Web Search / Fetch 所需的 `ALIYUN_API_KEY` / `DASHSCOPE_API_KEY`，或改用 `duckduckgo` / `jina`。

### 启动顺序

```powershell
# Terminal 1: Utility MCP Server
mvn -pl utility-mcp-server -am spring-boot:run

# Terminal 2: DeerFlow Runtime
$env:OPENAI_API_KEY = '<your-api-key>'
$env:OPENAI_BASE_URL = '<your-complete-chat-completions-endpoint>'
$env:HAIFA_DEERFLOW_MODEL = '<your-model-id>'
$env:ALIYUN_API_KEY = '<your-search-api-key>'
mvn -pl deerflow -am -Popenai spring-boot:run

# Terminal 3: Frontend
cd deerflow-frontend
npm install
npm run dev
```

预期地址：

| 服务 | 地址 |
| --- | --- |
| Frontend | `http://localhost:5173` |
| DeerFlow Runtime | `http://localhost:8095` |
| Runtime Health | `http://localhost:8095/api/deerflow/health` |
| Utility MCP | `http://127.0.0.1:8091/mcp` |

## 2. 场景 A：运行拓扑与事件流

目标：证明这不是一次 HTTP 请求返回整段文本，而是带 Run 状态、事件和审计的 Runtime。

1. 打开前端并创建一个 Chat 任务。
2. 输入：`列出当前 workspace 中的文件，并说明你使用了哪些工具。`
3. 观察 Activity Trace 中的 Run、模型、工具与完成事件。
4. 记录 `runId`，调用：

```bash
curl http://localhost:8095/api/deerflow/runs/<runId>
curl http://localhost:8095/api/deerflow/runs/<runId>/events
curl http://localhost:8095/api/deerflow/runs/<runId>/observability
curl http://localhost:8095/api/deerflow/runs/<runId>/tool-executions
```

应能说明：

- SSE 是实时输出通道，SQLite 中仍有可回查事件。
- Model Step、Tool Call 与 Tool Execution 是不同审计对象。
- Run 的完成、失败、取消和挂起状态由 Runtime 管理。

## 3. 场景 B：Utility MCP 动态工具

目标：证明 MCP Tool 来自远程发现，同时仍进入本地工具治理和审计。

建议任务：

```text
查询上海当前时间，并把 100 美元换算成人民币。说明你调用的工具和数据来源。
```

验证点：

1. Health 中 utility connection 为运行状态，并有非零工具数量。
2. Tool name 使用 `mcp__utility__<tool>` 形式。
3. Tool Call 与 Tool Execution 可从 Run 查询接口回查。
4. 停止 required utility server 后重启 Runtime，应按失败策略阻止启动，而不是静默丢失必需工具。
5. 同时设置 `HAIFA_AI_DEERFLOW_MCP_ENABLED=false` 与 `HAIFA_DEERFLOW_MCP_ENABLED=false` 后重启，MCP 工具从目录移除，内置工具仍可运行。

## 4. 场景 C：Deep Research

目标：证明最终报告背后有结构化 plan、source、evidence、claim、citation、quality 和 budget。

建议任务：

```text
调研 Java Agent Sandbox 的本地进程隔离、容器隔离与远端隔离三种路线。
比较安全边界、开发体验和运维成本，给出带来源引用的 Markdown 报告。
```

在前端选择 `RESEARCH` / `STANDARD` / `REPORT`，或调用 Runtime README 中的 curl 示例。

运行中观察：

- research Skill activation。
- plan / work item 的创建和状态变化。
- web search / fetch、MCP 或 Subagent 调用。
- source、evidence、claim 与 citation 数量变化。
- quality / budget 事件和最终 report artifact。

运行后使用同一个 `runId` 查询：

```bash
curl http://localhost:8095/api/deerflow/runs/<runId>/plan
curl http://localhost:8095/api/deerflow/runs/<runId>/work-items
curl http://localhost:8095/api/deerflow/runs/<runId>/sources
curl http://localhost:8095/api/deerflow/runs/<runId>/evidence
curl http://localhost:8095/api/deerflow/runs/<runId>/claims
curl http://localhost:8095/api/deerflow/runs/<runId>/citations
curl http://localhost:8095/api/deerflow/runs/<runId>/quality
curl http://localhost:8095/api/deerflow/runs/<runId>/budget
```

抽样核对至少一条 Claim 能映射到 Citation 和 Source，并下载报告产物。

## 5. 场景 D：挂起与恢复

目标：证明 active Graph 可以保存 checkpoint 并继续同一 Run。

### Clarification

1. 发起一个信息不足、需要 `clarification` Tool 的任务。
2. 确认出现 `CLARIFICATION_REQUIRED` 和 `RUN_SUSPENDED`。
3. 回答 clarification。
4. 调用 `POST /api/deerflow/runs/{runId}/resume`。
5. 确认收到 `RUN_RESUMED`，且返回的 Run ID 没有变化。

### Approval

Approval 默认关闭。仅在本地受控环境显式启用，并配置一个会触发审批的 Tool 风险策略。确认拒绝不会执行工具，批准才从 checkpoint 继续。

注意：pending approval 当前保存在内存中，不要通过重启服务演示 approval 恢复。

## 6. 自动化验证

```bash
# Backend
mvn -pl deerflow -am test

# Utility MCP
mvn -pl utility-mcp-server -am test

# Frontend
cd deerflow-frontend
npm run lint
npm run build
```

测试通过只证明确定性代码路径；真实模型、搜索、MCP 上游和语音 Provider 需要单独记录实际验证。

## 待人工补充的素材

以下占位符需要运行真实环境后补齐。添加文件前不在 README 中嵌入图片，以避免出现失效链接。

### `[HUMAN_TODO: PROJECT_OVERVIEW_SCREENSHOT]`

- 目标路径：`deerflow/docs/assets/project-overview.webp`
- 内容：前端任务输入、Activity Trace、最终答案和 Artifact 区域同屏。
- 建议尺寸：1600×900；隐藏 API Key、用户名、主机路径和无关窗口。

### `[HUMAN_TODO: DEEP_RESEARCH_DEMO]`

- 目标路径：`deerflow/docs/assets/deep-research-demo.webp`
- 内容：30–45 秒，从提交 Research 到查看 sources / evidence / citations / report artifact。
- 建议大小：小于 8 MB；裁掉等待时间并保留关键状态转换。

### `[HUMAN_TODO: ACTIVITY_TRACE_SCREENSHOT]`

- 目标路径：`deerflow/docs/assets/activity-trace.webp`
- 内容：同一个 Run 的 Model、Tool、MCP、checkpoint / resume 事件。

### `[HUMAN_TODO: VALIDATED_MODEL]`

在本节下方填写并保留验证日期：

```text
Provider: <provider>
Adapter/Profile: <openai | google-genai>
Model: <exact model id>
Verified at: <YYYY-MM-DD>
Scenarios: <chat/tool-calling/deep-research/clarification-resume>
Known limitations: <limitations observed>
```

### `[HUMAN_TODO: VERIFICATION_RESULT]`

在运行当前提交的验证命令后记录：

```text
Commit: <git sha>
Backend: <command and result>
Utility MCP: <command and result>
Frontend: <command and result>
Environment: <OS, JDK, Node.js>
Verified at: <YYYY-MM-DD>
```

补齐素材后，把根 README 顶部的“演示素材待补充”提示替换为项目概览图和 Deep Research 演示链接。
