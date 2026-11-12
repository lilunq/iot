# AIOT - Spring AI Alibaba MCP 项目

基于 Spring AI Alibaba 构建的物联网 AI 智能体项目，通过 MCP（Model Context Protocol）协议实现 AI 与 IoT 数据的交互，以及构建智能体，完成数据分析和智能决策。

## 项目介绍

本项目是一个完整的 AI + IoT 解决方案，利用 MCP 协议让 AI 能够实时查询和分析物联网数据。项目采用微服务架构，基于 Spring Boot 3.5 + Spring AI Alibaba 1.1 构建，支持多种大模型接入（通义千问、OpenAI 兼容模型等）。

### 核心功能

- **MCP 服务端**：通过 Feign 调用 IoT 平台接口，为 AI 提供 MCP 工具服务
- **MCP 客户端**：智能体demo，集成 AI Agent 框架
- **AI 智能体**：支持多轮对话、工具调用、工作流编排

## 技术栈

| 技术 | 版本 | 说明 |
|------|------|------|
| Spring Boot | 3.5.7 | 基础框架 |
| Spring AI | 1.1.0 | AI 能力集成 |
| Spring AI Alibaba | 1.1.0.0 | 阿里云 AI 扩展 |
| Spring AI Alibaba Extensions | 1.1.2.0 | Agent 框架扩展 |
| Spring Cloud Alibaba | 2023.0.3.4 | 微服务组件 |
| Nacos | - | 服务注册与配置中心 |
| OpenFeign | - | 声明式 HTTP 客户端 |
| Java | 17 | 运行环境 |

## 项目架构

```
aiot/
├── mcp-server/                 # MCP 服务端模块
│   └── 使用 Feign 调用 IoT 接口，提供 MCP 工具服务
│
├── gateway-proxy/           # 结合openclaw
│   └── 使用websocket连接openclaw后端，并提供与前端的通信。扩展iot其他功能
│
├── mcp-client/                 # mcp 客户端
    └── mcp 使用测试

```

## 模块详解

### 1. mcp-server（MCP 服务端）

MCP 服务端是 AI 访问物联网数据的桥梁，通过 OpenFeign 调用 IoT 平台的各种接口，并将其封装为 MCP 工具供 AI 调用。

**核心组件**：
- **Feign 客户端**：
  - `AdminClient` - 系统管理接口
  - `ScadaClient` - SCADA 数据接口
  - `TdengineClient` - 时序数据库接口
  - `IfimClient` - 设备信息管理接口
  - `IemsClient` - 能源管理接口
  - `FepClient` - 前置机接口

- **MCP 工具**：后续持续扩展
  - `AdminTools` - 管理相关工具
  - `ScadaTools` - SCADA 数据查询工具
  - `OrderServiceTool` - 工单服务工具

**技术特点**：
- 使用 `spring-ai-starter-mcp-server-webmvc` 提供 MCP 服务
- 集成 Nacos 服务发现，动态调用微服务
- 支持 Hystrix 熔断降级

### 2. mcp-client（智能体demo）

智能体demo是 AI 智能体的核心运行环境，负责数据分析、思考和决策。

**核心能力**：
- **AI Agent 框架**：集成 `spring-ai-alibaba-agent-framework`
- **MCP 客户端**：集成‘spring-ai-starter-mcp-client’，连接 MCP 服务端获取 IoT 数据
- **多模型支持**：支持 OpenAI 协议的大模型（通义千问、VLLM 等）
- **sse 支持**：实时推送 AI 分析结果
- **数据持久化**：集成 MyBatis Plus + MySQL+redis

**应用场景**：
- 设备异常分析与预警
- 能源数据智能分析
- 自动化决策与工单生成

### 3. mcp-client-gaode（AI Demo）

一个简单的 MCP 客户端示例，演示如何使用 Spring AI Alibaba 构建 AI 应用。

**特点**：
- 使用 `spring-ai-alibaba-starter-dashscope` 接入通义千问
- 集成 MCP 客户端能力
- 简洁的代码结构，适合入门学习

### 4. examples（Spring AI Alibaba 示例）

官方提供的 Spring AI Alibaba 示例集合，涵盖从基础到高级的各种用法。

**示例分类**：
- **Chat**：聊天模型示例（DashScope、OpenAI、DeepSeek、Ollama 等）
- **Agent**：AI 智能体示例（A2A、React Agent、Skills Agent 等）
- **RAG**：检索增强生成示例
- **MCP**：MCP 客户端/服务端示例
- **Graph**：工作流图编排示例
- **Observability**：可观察性示例
- **Audio/Image**：多模态示例

> 详细文档：[examples/README.md](./examples/README.md)

## 架构图

```
┌─────────────────────────────────────────────────────────────┐
│                        用户/前端                              │
└─────────────────────────┬───────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────┐
│                    mcp-client (智慧底座)                      │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐          │
│  │  AI Agent   │  │  WebSocket  │  │  Chat API   │          │
│  │  Framework  │  │   Server    │  │ Controller  │          │
│  └──────┬──────┘  └─────────────┘  └─────────────┘          │
│         │ MCP Client                                           │
└─────────┼───────────────────────────────────────────────────┘
          │ MCP Protocol
          ▼
┌─────────────────────────────────────────────────────────────┐
│                     mcp-server (MCP 服务端)                   │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐          │
│  │ AdminTools  │  │ ScadaTools  │  │ OrderTools  │          │
│  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘          │
│         │                │                │                  │
└─────────┼────────────────┼────────────────┼──────────────────┘
          │ Feign          │ Feign          │ Feign
          ▼                ▼                ▼
┌─────────────────────────────────────────────────────────────┐
│                      IoT 平台微服务                           │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐     │
│  │  Admin   │  │  Scada   │  │ TDengine │  │   IEMS   │     │
│  │ Service  │  │ Service  │  │ Service  │  │ Service  │     │
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘     │
└─────────────────────────────────────────────────────────────┘
```

## 快速开始

### 环境要求

- JDK 17+
- Maven 3.6+
- Nacos Server（用于服务注册与配置）
- 通义千问 API Key（或其他兼容模型 API Key）

### 配置

1. 配置 Nacos 地址（`application.yml`）：
```yaml
spring:
  cloud:
    nacos:
      server-addr: localhost:8848
```

2. 配置 AI 模型 API Key：
```yaml
spring:
  ai:
    dashscope:
      api-key: ${DASHSCOPE_API_KEY}
```

### 构建

```bash
# 构建整个项目
mvn clean package -DskipTests

# 单独构建模块
cd mcp-server
mvn clean package -DskipTests
```

### 运行

```bash
# 启动 MCP 服务端
cd mcp-server
mvn spring-boot:run

# 启动 MCP 客户端（智慧底座）
cd mcp-client
mvn spring-boot:run
```

## 相关资源

- [Spring AI Alibaba 官网](https://java2ai.com)
- [Spring AI Alibaba GitHub](https://github.com/alibaba/spring-ai-alibaba)
- [MCP 协议规范](https://modelcontextprotocol.io/)

