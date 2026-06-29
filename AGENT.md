# AGENT.md — Minimum Java Agent Loop

> **项目标识:** `com.greendam:MinimumJavaAgentLoop:0.0.1-SNAPSHOT`
> **描述:** 一个最小化的 Java Agent 循环实现，通过 `@Tool` / `@Param` 注解将 Java 方法暴露为大模型可调用的工具（Function
> Calling）。
> **语言:** Java 21+
> **构建工具:** Maven
> **LLM 协议:** OpenAI Chat Completions API（兼容 DeepSeek / Qwen / 任意兼容服务）

---

## 1. 系统架构概览

```text
┌─────────────────────────────────────────────────────────────────────┐
│                         Main.java                                  │
│  (Agent Loop: 用户输入 → LLM 请求 → 解析响应 → 工具执行 → 循环)        │
└──────────┬────────────────────────────┬──────────────────────────────┘
           │                            │
           ▼                            ▼
┌─────────────────────┐      ┌──────────────────────────────────────┐
│   OpenAiClient      │      │   ShortMemory                        │
│   (LLM HTTP 客户端)  │◄────►│   (摘要式短期记忆 / 会话上下文)        │
└──────────┬──────────┘      └──────────────┬───────────────────────┘
           │                                 │
           ▼                                 ▼
┌─────────────────────┐      ┌──────────────────────────────────────┐
│   ToolDefManager    │─────►│   ConversationSummarizer            │
│   (工具定义注册中心)  │      │   (历史对话摘要压缩)                  │
└──────────┬──────────┘      └──────────────────────────────────────┘
           │
           ▼
┌─────────────────────┐      ┌──────────────────────────────────────┐
│   ToolCallManager   │─────►│ FileTools / MathTools / TimeTools    │
│   (工具调用执行引擎)  │      │ ShellTools / TextTools / WebTools    │
└─────────────────────┘      └──────────────────────────────────────┘
```

---

## 2. 当前核心能力

### 2.1 Agent 循环

`Main.runLoop(String input)` 是项目的主循环：

1. 将用户输入写入 `ShortMemory`
2. 调用 `ShortMemory.ensureCapacity()` 管理上下文窗口
3. 构造 `OpenAiRequest`（带消息历史 + tools）
4. 调用 `OpenAiClient.get().chat(request)`
5. 解析 `finishReason`
6. 如果是 `tool_calls`，执行工具并将结果继续写回记忆
7. 否则结束本轮

### 2.2 摘要式短期记忆

当前 `ShortMemory` 已不是简单滑动窗口，而是**摘要压缩策略**：

- 保留最近 `keep-turns` 轮完整对话
- 当 token 接近 `max-context-tokens - reserve-tokens` 时
- 将更早的轮次交给 `ConversationSummarizer` 调用 LLM 生成摘要
- 用一条 `system` 摘要消息替换原始早期消息

这样做的好处：

- 避免直接丢失早期上下文
- 比单纯截断更适合长任务、长文生成、排障过程
- 与当前最小架构兼容，不需要引入数据库或向量库

### 2.3 工具系统

工具通过 `@Tool` + `@Param` 暴露给模型，`ToolDefManager` 负责注册与 JSON Schema 生成，`ToolCallManager` 负责执行。

当前工具总数：**16 个**

| 工具类          | 方法                                                                                              |
|--------------|-------------------------------------------------------------------------------------------------|
| `FileTools`  | `readFile`, `writeFile`, `replaceInFile`, `listFiles`, `deleteFile`, `searchFiles`, `grepFiles` |
| `MathTools`  | `calculate`, `randomNumber`                                                                     |
| `TimeTools`  | `getCurrentTime`                                                                                |
| `ShellTools` | `executeShell`                                                                                  |
| `TextTools`  | `countText`, `textReplace`, `base64Encode`, `base64Decode`                                      |
| `WebTools`   | `httpGet`, `httpPost`, `webToText`, `webToTextBrowser`                                          |

---

## 3. 包结构

```text
src/main/java/com/greendam/
├── Main.java                       # 主入口、Agent 循环、初始化
├── config/
│   └── ConfigLoader.java           # YAML 配置加载器
├── entity/
│   ├── Choice.java                 # 模型返回的选项
│   ├── FunctionCall.java           # 函数调用（name + arguments）
│   ├── FunctionDef.java            # 函数定义（name + description + schema）
│   ├── Message.java                # 消息体（role/content/tool_calls/reasoning_content）
│   ├── Model.java                  # OpenAI 连接配置 POJO
│   ├── OpenAiRequest.java          # Chat Completions 请求体
│   ├── OpenAiResponse.java         # Chat Completions 响应体
│   ├── ResponseFormat.java         # 响应格式控制
│   ├── Tool.java                   # 工具定义（type + function）
│   ├── ToolCall.java               # 工具调用（id + type + function）
│   └── Usage.java                  # Token 用量
├── memory/
│   ├── ShortMemory.java            # 摘要式短期记忆
│   ├── ConversationSummarizer.java # 对话摘要器
│   ├── ConversationLogger.java     # 对话日志导出
│   ├── LongMemory.java             # 长期记忆预留
│   └── StructureMemory.java        # 结构化记忆预留
├── tools/
│   ├── ToolDefManager.java         # 工具定义注册中心
│   ├── ToolCallManager.java        # 工具调用执行器
│   ├── annotation/
│   │   ├── Tool.java               # @Tool 方法注解
│   │   └── Param.java              # @Param 参数注解
│   └── tools/
│       ├── FileTools.java
│       ├── MathTools.java
│       ├── ShellTools.java
│       ├── TextTools.java
│       ├── TimeTools.java
│       └── WebTools.java
└── util/
    ├── OpenAiClient.java           # OpenAI 兼容 HTTP 客户端
    └── TokenCounter.java           # Token 启发式估算器
```

---

## 4. 关键模块说明

### 4.1 `Main.java`

职责：

- 初始化配置与工具注册
- 初始化记忆系统参数
- 注入 system prompt
- 读取多行用户输入（以 `/send` 结束）
- 驱动 Agent 主循环
- 退出时写对话日志并关闭 Playwright

当前初始化流程：

```java
public static void main(String[] args) {
    initDetails();
    while (true) {
        // 读取输入
        runLoop(input);
    }
    ConversationLogger.saveToFile(ShortMemory.getAll());
    WebTools.shutdownPlaywright();
}
```

### 4.2 `ShortMemory.java`

当前职责：

- 存储 `system/user/assistant/tool` 消息
- 提供不可变快照读取接口 `getAll()`
- 通过 `TokenCounter` 估算 token 占用
- 在 `ensureCapacity()` 中执行摘要压缩

关键配置：

- `maxTokens`
- `reserveTokens`
- `keepTurns`

### 4.3 `ConversationSummarizer.java`

负责把旧消息压缩为摘要。支持两种模式：

1. **首次摘要** — 对一批历史消息直接总结
2. **增量合并** — 把已有摘要与新增旧消息再合并为新摘要

摘要调用方式：

- 使用 `OpenAiClient.get().chat()`
- `temperature=0.3`
- `maxTokens=2000`
- 不启用 tools

### 4.4 `OpenAiClient.java`

基于 JDK 内置 `java.net.http.HttpClient`。

支持：

- 单轮文本 chat
- 多轮消息 chat
- 完整请求体 chat
- SSE 流式 chat

配置来源：

- `application.yml`
- `application-dev.yml`
- 环境变量（优先级更高）

---

## 5. Agent 循环详解

### 5.1 主流程

```text
用户输入
  ↓
ShortMemory.add(user)
  ↓
while(true)
  ↓
ShortMemory.ensureCapacity()
  ↓
OpenAiClient.chat(OpenAiRequest)
  ↓
解析 choice / finishReason / message
  ↓
ShortMemory.add(message.toHistoryMessage())
  ↓
switch(finishReason)
  ├─ stop           → break
  ├─ length         → 打印提示后 break
  ├─ tool_calls     → ToolCallManager.execute(response)
  │                   → ShortMemory.addAll(results)
  │                   → continue
  ├─ content_filter → 打印提示后 break
  └─ default        → 打印未知错误后 break
```

### 5.2 `reasoning_content` 的处理

`Message.toHistoryMessage()` 会故意移除 `reasoningContent`，原因：

1. 思考内容通常远大于最终回答
2. 重新回传会浪费上下文窗口
3. 多数模型厂商不建议将 reasoning 原样回灌到历史中

---

## 6. 配置文件

### 6.1 `application.yml`

```yaml
openai:
  api-key: ${openai.api-key}
  base-url: https://api.deepseek.com
  model: ${openai.model}
  temperature: 0.7
  max-tokens: 393216
  timeout-seconds: 60

memory:
  short:
    max-context-tokens: 200000
    reserve-tokens: 8000
    keep-turns: 3
```

### 6.2 记忆配置说明

| 配置项                               | 说明                   |
|-----------------------------------|----------------------|
| `memory.short.max-context-tokens` | 短期记忆允许占用的最大上下文 token |
| `memory.short.reserve-tokens`     | 给模型输出预留的 token 数     |
| `memory.short.keep-turns`         | 永远保留的最近完整轮次数         |

---

## 7. Tool 调用执行机制

### 7.1 工具注册

在 `Main.registerTools()` 中注册工具实例：

```java
ToolDefManager.register(new FileTools());
        ToolDefManager.

register(new MathTools());
        ToolDefManager.

register(new TimeTools());
        ToolDefManager.

register(new ShellTools());
        ToolDefManager.

register(new TextTools());
        ToolDefManager.

register(new WebTools());
```

### 7.2 工具执行

`ToolCallManager.execute(response)` 的流程：

1. 取出 assistant message 里的 `tool_calls`
2. 根据工具名在 `ToolDefManager` 中找反射元数据
3. 将 JSON 参数转为 Java 参数类型
4. 反射调用 Java 方法
5. 包装成 `role="tool"` 的消息列表返回

`Main.runLoop()` 再把这些 tool 结果追加回 `ShortMemory`。

---

## 8. 当前局限与后续方向

### 已完成

- 短期记忆 token 估算
- 摘要式上下文压缩
- system prompt 注入
- 浏览器抓取能力
- 对话日志导出

### 仍是预留

- `LongMemory.java`
- `StructureMemory.java`

### 后续可扩展方向

1. 长期记忆持久化
2. 结构化记忆（三元组 / 实体关系）
3. 检索式记忆召回
4. 更精确的 tokenizer
5. 更丰富的工具权限与安全边界

---

## 9. 给后续 Agent 的建议

如果你要继续开发这个项目，优先注意这些事实：

- 现在 `ShortMemory` 已经是**摘要压缩模式**，不要再按旧文档理解为“无限增长 ArrayList”
- `Main` 中已经有 `initMemory()` 与 `injectSystemPrompt()`，启动流程不是最初版本了
- `WebTools` 不再只有 `httpGet/httpPost`，还包含 `webToText/webToTextBrowser`
- `FileTools` 不再只有基础读写，还包含 `replaceInFile/searchFiles/grepFiles`
- `ConversationLogger` 会在程序结束时导出 Markdown 对话日志

---

## 10. 文档关系

- `README.md`：给人类开发者看的快速介绍
- `AGENT.md`：给 AI Agent 看的系统结构与当前实现事实
- `TOOLS.md`：所有内置工具的详细说明
