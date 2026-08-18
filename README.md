# Haifa AI DeerFlow

Haifa AI DeerFlow 是从 [AarenWang/haifa](https://github.com/AarenWang/haifa) 独立迁出的 DeerFlow 风格 Agent Runtime 仓库。迁移保留了三个模块的原始提交作者、提交消息和提交日期；原仓库中的对应目录作为只读历史快照继续保留。

## 仓库结构

```text
.
├── deerflow/              # Java / Spring Boot Agent Runtime（主目录）
├── deerflow-frontend/     # React / Vite 前端
└── utility-mcp-server/    # 独立 Utility MCP Server
```

## 环境要求

- Java 21
- Maven 3.x
- Node.js 与 npm（仅前端需要）

## 后端

```bash
mvn -pl deerflow -am test
mvn -pl deerflow -am spring-boot:run
```

启用 OpenAI-compatible provider：

```bash
mvn -pl deerflow -am -Popenai spring-boot:run
```

## 前端

```bash
cd deerflow-frontend
npm install
npm run dev
```

## Utility MCP Server

```bash
mvn -pl utility-mcp-server -am spring-boot:run
```

更详细的配置和架构说明见各模块 README：

- [DeerFlow Runtime](deerflow/README.md)
- [DeerFlow Frontend](deerflow-frontend/README.md)
- [Utility MCP Server](utility-mcp-server/README.md)

## License

MIT，见 [LICENSE](LICENSE)。
