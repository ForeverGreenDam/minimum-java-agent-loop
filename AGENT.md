# 🤖 AGENT.md — AI Agent 代码指南

> 本文档面向 AI Agent，提供项目的架构全景、代码定位和扩展指南。
> 阅读本文后你应能快速定位任意功能的实现位置并进行修改。

---

## 一、全局架构

```text
┌─────────────────────────────────────────────────────────┐
│                      Main.java                          │
│  main() → initDetails() → while(input) → runLoop()     │
│                                                         │
│  runLoop(input):                                        │
│    ShortMemory.add(user)                                │
│    MemoryRetriever.retrieveAndInject(input)             │
│    while(true):                                         │
│      ShortMemory.ensureCapacity()                       │
│      request = build(ShortMemory.getAll(), tools)       │
│      response = OpenAiClient.chat(request)              │
│      if stop → break                                    │
│      if tool_calls → execute → ShortMemory.addAll       │
└──────────┬──────────────────────┬───────────────────────┘
           │                      │
    ┌──────▼──────┐       ┌───────▼───────┐
    │   memory/   │       │    tools/     │
    │  短期+长期   │       │  注解驱动工具  │
    └─────────────┘       └───────────────┘
```

### 核心调用链

```text
Main.main()
  ├── initDetails()
  │     ├── ConfigLoader.setProfile("dev")
  │     ├── registerTools()          → ToolDefManager.register(...)
  │     ├── initMemory()             → ShortMemory / LongMemoryStore / HybridRetriever / MemoryRetriever
  │     └── injectSystemPrompt()     → ShortMemory.add(system)
  │
  ├── while(true)
  │     ├── Scanner.nextLine()       → 用户输入
  │     └── runLoop(input)
  │           ├── ShortMemory.add(user)
  │           ├── MemoryRetriever.retrieveAndInject(input)
  │           └── while(true)
  │                 ├── ShortMemory.ensureCapacity()
  │                 ├── OpenAiClient.chat(request)
  │                 ├── if "stop"    → break
  │                 └── if "tool_calls" → ToolCallManager.execute() → ShortMemory.addAll()
  │
  └── 退出后
        ├── MemoryExtractor.extract()  → LongMemoryStore.addBatch()
        ├── ConversationLogger.saveToFile()
        └── LongMemoryStore.saveAll()
```

---

## 二、包结构与职责

### `com.greendam` — 根包

| 文件          | 职责                                   |
|-------------|--------------------------------------|
| `Main.java` | 程序入口、Agent 循环、初始化编排、system prompt 注入 |

### `com.greendam.config` — 配置

| 文件                  | 职责                                          |
|---------------------|---------------------------------------------|
| `ConfigLoader.java` | YAML 配置加载器（单例），支持 profile 覆盖、`${key}` 占位符解析 |

**使用方式：**
```java
ConfigLoader.get().

getString("openai.model");
ConfigLoader.

get().

getInt("memory.long.min-importance",5);
ConfigLoader.

get().

getBool("memory.long.enabled",true);
```

### `com.greendam.entity` — 数据模型

| 文件                                    | 职责                                                      |
|---------------------------------------|---------------------------------------------------------|
| `Message.java`                        | 消息体（role/content/toolCalls/reasoningContent），支持 builder |
| `MemoryEntry.java`                    | 长期记忆条目（content/category/importance/keywords/embedding）  |
| `OpenAiRequest.java`                  | OpenAI 请求体（model/messages/tools/temperature）            |
| `OpenAiResponse.java`                 | OpenAI 响应体（choices/usage）                               |
| `Choice.java`                         | 单个 choice（message/finishReason）                         |
| `ToolCall.java` / `FunctionCall.java` | 工具调用描述                                                  |
| `Tool.java` / `FunctionDef.java`      | 工具定义（type/function/parameters）                          |
| `Usage.java`                          | token 用量统计                                              |

### `com.greendam.memory` — 记忆系统

| 文件                            | 职责                                            |
|-------------------------------|-----------------------------------------------|
| `ShortMemory.java`            | 短期记忆（静态 ArrayList），管理上下文窗口和摘要压缩               |
| `ConversationSummarizer.java` | 调用 LLM 将历史对话压缩为增量摘要                           |
| `ConversationLogger.java`     | 将对话导出为 Markdown 日志文件                          |
| `LongMemoryStore.java`        | 长期记忆存储引擎（JSON 文件），提供 CRUD + 淘汰 + embedding 管理 |
| `MemoryExtractor.java`        | 调用 LLM 从对话中提取值得长期记住的信息                        |
| `MemoryRetriever.java`        | 每轮对话前检索相关长期记忆并注入 ShortMemory                  |
| `HybridRetriever.java`        | 混合检索算法（BM25 + Embedding + 断崖截断）               |
| `BM25Scorer.java`             | BM25 关键词评分器（TF-IDF）                           |
| `SessionContext.java`         | 当前会话上下文（会话 ID）                                |
| `StructureMemory.java`        | 结构化记忆（预留，未使用）                                 |

### `com.greendam.tools` — 工具系统

| 文件                      | 职责                                             |
|-------------------------|------------------------------------------------|
| `ToolDefManager.java`   | 工具注册中心：扫描 `@Tool` 注解，生成 OpenAI 格式的 JSON Schema |
| `ToolCallManager.java`  | 工具调用引擎：解析 LLM 返回的 tool_calls，反射执行 Java 方法      |
| `annotation/Tool.java`  | `@Tool` 注解 — 标记方法为可调用工具                        |
| `annotation/Param.java` | `@Param` 注解 — 描述参数名/描述/是否必填/枚举值                |

### `com.greendam.tools.tools` — 工具实现

| 文件                 | 工具数 | 说明                             |
|--------------------|-----|--------------------------------|
| `FileTools.java`   | 7   | 文件读写、替换、搜索、grep                |
| `MathTools.java`   | 2   | 数学表达式计算、随机数                    |
| `TimeTools.java`   | 1   | 获取当前时间                         |
| `ShellTools.java`  | 1   | 执行 Shell 命令                    |
| `TextTools.java`   | 4   | 统计、替换、Base64 编解码               |
| `WebTools.java`    | 4   | HTTP GET/POST、网页正文提取（静态+浏览器渲染） |
| `MemoryTools.java` | 3   | remember / recall / forget     |
| `SteamTools.java`  | 6   | Steam 玩家资料、游戏库、成就、游戏详情         |

### `com.greendam.util` — 工具类

| 文件                     | 职责                                    |
|------------------------|---------------------------------------|
| `OpenAiClient.java`    | LLM HTTP 客户端（单例），支持 chat / chatStream |
| `TokenCounter.java`    | token 数启发式估算（中英混合）                    |
| `EmbeddingClient.java` | Embedding 向量客户端（可选），支持单条/批量 embedding |

---

## 三、记忆系统详解

### 3.1 短期记忆 — `ShortMemory`

**核心方法：**

- `add(Message)` / `addAll(List<Message>)` — 追加消息
- `getAll()` — 返回不可变快照
- `ensureCapacity()` — 上下文窗口管理入口

**摘要压缩流程：**
```text
while (estimateTokens() > maxTokens - reserveTokens):
  1. 找到所有 user 消息位置（轮次边界）
  2. 如果只剩 keepTurns 轮 → 停止
  3. 收集 batchStart~batchEnd 之间的消息
  4. 提取已有摘要（如有）
  5. 调用 ConversationSummarizer.summarize(消息, 已有摘要)
  6. 删除旧消息，插入新摘要 system 消息
```

### 3.2 长期记忆 — `LongMemoryStore`

**存储格式：** `memory/long/index.json`（JSON 数组）

**MemoryEntry 字段：**

```java
id           // UUID 前 8 位
        content      // 一句话描述
category     // fact / preference / decision / context
        importance   // 1-10，越高越不易被淘汰
keywords     // 关键词列表（用于 BM25 检索）
        createdAt    // 创建时间戳
lastAccessedAt // 最后访问时间
        accessCount  // 累计访问次数
source       // 来源会话 ID
        embedding    // 语义向量（float[]，Base64 序列化存储）
```

**淘汰评分公式（越高越不易被淘汰）：**
```java
score =importance *10.0
        +accessCount *2.0
        -ageDays *0.1
```

**重要度阈值：** `min-importance`（配置项），低于此值的记忆在 `add()` / `addBatch()` 时被静默丢弃。

### 3.3 混合检索 — `HybridRetriever`

```text
query → BM25 粗筛 Top-50 候选
      → BM25 分数 min-max 归一化
      → 计算混合分 = α × BM25_norm + (1-α) × cosine_sim
      → 按混合分降序排序
      → 断崖检测截断（阈值 = 平均正向跌幅 × cliffMultiplier）
      → 返回 Top-K
```

当 Embedding 未启用时，退化为纯 BM25 + 断崖截断。

### 3.4 记忆提取 — `MemoryExtractor`

会话结束时调用，流程：

1. 将对话消息格式化为文本
2. 附上已有记忆列表（用于去重）
3. 构建 prompt 要求 LLM 返回 JSON 数组
4. 解析响应，过滤无效条目和低重要度条目（`< min-importance`）
5. 通过 `LongMemoryStore.addBatch()` 批量写入

**提取标准（prompt 中定义）：**

- fact: 客观事实（技术栈、配置、路径等）
- preference: 用户偏好（代码风格、工具偏好等）
- decision: 做出的决定（方案选择、架构决策等）
- context: 项目背景（当前目标、进行中的任务等）

**重要度评分参考：**

- 10: 必须记住（密码、关键配置、用户明确要求"记住"的）
- 7-9: 很重要（技术选型、偏好设定、项目结构信息）
- 4-6: 有用（一般性上下文、讨论过的方案）
- 1-3: 可记可不记（临时性信息）

### 3.5 记忆注入 — `MemoryRetriever`

每轮对话前自动执行：

1. 用用户输入作为 query 调用 `LongMemoryStore.search()`
2. 清除 ShortMemory 中上一轮的记忆上下文消息（通过 `📚 相关历史记忆` 标记识别）
3. 构建 system 消息，注入到所有 system 消息之后、对话消息之前

---

## 四、工具系统详解

### 4.1 注册流程

```text
Main.registerTools()
  → ToolDefManager.register(instance)
      → 遍历 instance.getClass().getDeclaredMethods()
      → 找到带 @Tool 注解的方法
      → 解析 @Param 注解生成参数定义
      → buildJsonSchema() 生成 OpenAI 格式的 parameters
      → 存入 METHODS Map<String, ToolMethod>
```

### 4.2 调用流程

```text
LLM 返回 tool_calls
  → ToolCallManager.execute(response)
      → 遍历 toolCalls
      → invoke(toolCall)
          → ToolDefManager.getMethod(name) 获取 ToolMethod
          → JSON 解析 arguments 字符串
          → convert() 类型转换（String/int/double/boolean/复杂类型）
          → method.invoke(instance, args) 反射执行
      → 构建 role="tool" 的 Message 返回
```

### 4.3 如何添加新工具

1. 在 `tools/tools/` 下创建类（或使用已有的）
2. 在方法上加 `@Tool(name = "xxx", description = "xxx")`
3. 参数加 `@Param(name = "xxx", description = "xxx")`
4. 在 `Main.registerTools()` 中注册：`ToolDefManager.register(new XxxTools())`
5. 返回值会自动转为字符串返回给 LLM

**注意事项：**

- 工具方法必须是 `public` 的
- 返回类型通常是 `String`
- `required = false` 的参数 LLM 可以不传（Java 侧收到 null）
- `enumValues = {"a", "b"}` 约束 LLM 只能从中选择

---

## 五、配置系统

`ConfigLoader` 是一个轻量级 YAML 配置加载器，模拟 Spring Boot 的 `application.yml` 行为。

**Profile 优先级（由高到低）：**

1. `ConfigLoader.setProfile("dev")` — 代码内指定
2. 环境变量 `CONFIG_PROFILE`
3. 系统属性 `config.profile`
4. 默认 `"default"`（不加载额外文件）

**支持的配置项：**

| Key                                      | 类型     | 默认值                    | 说明                |
|------------------------------------------|--------|------------------------|-------------------|
| `openai.api-key`                         | String | —                      | LLM API Key       |
| `openai.base-url`                        | String | —                      | LLM API 地址        |
| `openai.model`                           | String | —                      | 模型名               |
| `openai.temperature`                     | double | 0.7                    | 温度                |
| `openai.max-tokens`                      | int    | 4096                   | 最大输出 token        |
| `openai.timeout-seconds`                 | int    | 60                     | HTTP 超时           |
| `memory.short.max-context-tokens`        | int    | 128000                 | 上下文窗口上限           |
| `memory.short.reserve-tokens`            | int    | 8000                   | 预留给响应的 token      |
| `memory.short.keep-turns`                | int    | 3                      | 保留最近 N 轮不压缩       |
| `memory.long.enabled`                    | bool   | true                   | 是否启用长期记忆          |
| `memory.long.storage-dir`                | String | memory/long            | 存储目录              |
| `memory.long.max-entries`                | int    | 500                    | 最大条目数             |
| `memory.long.min-importance`             | int    | 5                      | 最低重要度阈值（1-10）     |
| `memory.long.embedding.enabled`          | bool   | false                  | 是否启用 Embedding    |
| `memory.long.embedding.base-url`         | String | —                      | Embedding API 地址  |
| `memory.long.embedding.api-key`          | String | —                      | Embedding API Key |
| `memory.long.embedding.model`            | String | text-embedding-3-small | Embedding 模型      |
| `memory.long.retrieval.bm25-weight`      | double | 0.3                    | BM25 权重           |
| `memory.long.retrieval.cliff-multiplier` | double | 2.0                    | 断崖检测乘数            |
| `memory.long.retrieval.prefetch-k`       | int    | 50                     | BM25 粗筛候选数        |
| `memory.long.retrieval.top-k`            | int    | 5                      | 每轮注入记忆数           |
| `memory.long.retrieval.auto-inject`      | bool   | true                   | 是否自动注入            |
| `steam.api-key`                          | String | —                      | Steam Web API Key |

---

## 六、关键设计决策

1. **不用 Spring** — 用 `ConfigLoader` + 手动 `new` 替代 DI，保持最小依赖
2. **注解驱动工具** — `@Tool` + `@Param` 替代手写 JSON Schema，降低新增工具的成本
3. **静态类为主** — `ShortMemory`、`LongMemoryStore`、`ToolDefManager` 都是静态方法，适合单线程 Agent 场景
4. **双层记忆** — 短期用摘要压缩，长期用 JSON 文件 + 混合检索，兼顾上下文长度和跨会话记忆
5. **断崖截断** — 检索结果按分数排序后，在质量显著下降处自动截断，避免注入低质量记忆
6. **重要度阈值** — 低分记忆不写入长期存储，从源头控制噪声

---

## 七、常见修改场景

### 修改 system prompt

→ `Main.injectSystemPrompt()`

### 修改 Agent 循环逻辑

→ `Main.runLoop(String input)`

### 添加新工具

1. 创建 `src/main/java/com/greendam/tools/tools/XxxTools.java`
2. 写 `@Tool` 方法
3. `Main.registerTools()` 中加 `ToolDefManager.register(new XxxTools())`

### 修改记忆提取逻辑

→ `MemoryExtractor.buildPrompt()` — 修改提取 prompt
→ `MemoryExtractor.parseResponse()` — 修改解析/过滤逻辑

### 修改检索算法

→ `HybridRetriever.retrieve()` — 混合检索主流程
→ `BM25Scorer` — BM25 评分实现

### 修改摘要压缩策略

→ `ShortMemory.ensureCapacity()` — 压缩触发逻辑
→ `ConversationSummarizer.summarize()` — 摘要生成 prompt

### 修改淘汰策略

→ `LongMemoryStore.evictionScore()` — 淘汰评分公式

---

## 八、数据文件

| 路径                                       | 格式       | 说明              |
|------------------------------------------|----------|-----------------|
| `memory/long/index.json`                 | JSON 数组  | 长期记忆持久化存储       |
| `log/conversation_*.md`                  | Markdown | 每次会话的对话日志       |
| `src/main/resources/application.yml`     | YAML     | 主配置文件           |
| `src/main/resources/application-dev.yml` | YAML     | 开发配置（gitignore） |

---

## 九、文档关系

- `README.md`：给人类开发者看的快速介绍
- `AGENT.md`：给 AI Agent 看的系统结构与当前实现事实
- `TOOLS.md`：所有内置工具的详细说明
