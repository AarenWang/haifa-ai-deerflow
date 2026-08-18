# DeerFlow 文档索引

本目录记录 `deerflow` Runtime 的当前实现、运行方式和安全边界。项目事实优先以当前代码、`pom.xml`、`application.yml` 和测试为准；设计设想不得写成已经生效的行为。

## 建议阅读路径

### 首次阅读 / 架构评审

1. [实现状态](implementation-status.md)：快速判断能力成熟度与默认状态。
2. [架构说明](architecture.md)：了解模块、边界和主数据流。
3. [Runtime 执行链路](runtime-execution.md)：查看 Run、Graph、恢复、幂等和审计设计。
4. [Deep Research](deep-research.md)：查看结构化研究和报告生成链路。
5. [演示与验证](demo-and-verification.md)：用最短路径验证项目，而不是只阅读功能清单。

### 使用与运维

- [MCP 运维与治理](mcp-operations.md)：连接拓扑、快照、失败策略、观测和回滚。
- [Fetch MCP 安全与能力对齐](fetch-mcp-security-and-parity.md)：为什么 Fetch MCP 默认不开放。
- [Sandbox Runtime](sandbox-runtime.md)：执行后端、文件系统映射、环境变量和安全配置。
- [Skill / Tool Runtime](skill-tool-runtime.md)：Skill、Tool 和媒体生成的职责边界。
- [语音对话](voice-conversation.md)：浏览器音频、ASR / TTS Provider 和运行配置。

## 文档维护规则

- 新增或改变用户可见行为时，同步更新 `../README.md` 和 [实现状态](implementation-status.md)。
- 改变执行节点、挂起恢复或 checkpoint 语义时，同步更新 [Runtime 执行链路](runtime-execution.md)。
- 改变研究领域对象、质量门禁或报告产物时，同步更新 [Deep Research](deep-research.md)。
- 默认值只从 `src/main/resources/application.yml` 抄录，并明确区分 Java 属性默认值。
- 外部模型、搜索、语音等真实服务的验证结果必须注明 Provider、模型或服务、日期和验证范围。
