# AGENT.md — Minimum Java Agent Loop

> **项目标识:** `com.greendam:MinimumJavaAgentLoop:0.0.1-SNAPSHOT`
> **描述:** 一个最小化的 Java Agent 循环实现，通过 `@Tool` / `@Param` 注解将 Java 方法暴露为大模型可调用的工具（Function
> Calling）。
> **语言:** Java 21+
> **构建工具:** Maven
> **LLM 协议:** OpenAI Chat Completions API（兼容 DeepSeek / Qwen / 任意兼容服务）

---

## 1. 系统架构概览

```
┌─────────────────────────────────────────────────────────────────────┐
│                         Main.java                                  │
│  (Agent Loop: 用户输入 → LLM 请求 → 解析响应 → 工具执行 → 循环)        │
└──────────┬──────────────────────────────────┬──────────────────────┘
           │                                  │
           ▼                                  ▼
┌─────────────────────┐          ┌──────────────────────────┐
│   OpenAiClient      │          │   ShortMemory             │
│   (LLM HTTP 客户端)  │◄────────►│   (短期记忆 — 消息列表)    │
└──────────┬──────────┘          └──────────────────────────┘
           │
           ▼
┌─────────────────────┐          ┌──────────────────────────┐
│   ToolDefManager    │─────────►│   ToolCallManager        │
│   (工具定义注册中心)  │          │   (工具调用执行引擎 — 反射) │
└──────────┬──────────┘          └──────────┬───────────────┘
           │                                 │
           ▼                                 ▼
┌─────────────────────┐          ┌──────────────────────────┐
│   @Tool / @Param    │          │   FileTools / MathTools  │
│   (注解定义)         │          │   TimeTools / ShellTools │
└─────────────────────┘          │   TextTools / WebTools   │
                                 └──────────────────────────┘
┌─────────────────────┐
│   ConfigLoader      │
│   (YAML 配置加载器)  │
└─────────────────────┘
```

### 1.1 核心循环（Agent Loop）

```
用户输入
    │
    ▼
存入 ShortMemory (role=user)
    │
    ▼
┌─────────────────────────────────────────────┐
│ while(true):                                │
│   构造 OpenAiRequest (含 tools)              │
│   → 调用 OpenAiClient.chat(request)          │
│   → 解析 Choice.finishReason:                │
│       ├─ "stop"        → 输出结果，break     │
│       ├─ "length"      → 截断提示，break     │
│       ├─ "tool_calls"  → ToolCallManager    │
│       │                   .executeAndAppend()│
│       │                   → continue (下一轮) │
│       ├─ "content_filter" → 过滤提示，break  │
│       └─ default        → 未知错误，break    │
└─────────────────────────────────────────────┘
```

---

## 2. 包结构

```
src/main/java/com/greendam/
├── Main.java                       # 主入口、Agent 循环、工具注册
├── config/
│   └── ConfigLoader.java           # YAML 配置加载器（模拟 Spring Boot application.yml）
├── entity/                         # 数据模型（全部 Lombok @Data + Jackson 序列化）
│   ├── Choice.java                 # 模型返回的选项
│   ├── FunctionCall.java           # 函数调用（name + arguments JSON）
│   ├── FunctionDef.java            # 函数定义（name + description + parameters schema）
│   ├── Message.java                # 消息体（role/content/tool_calls/reasoning_content）
│   ├── Model.java                  # OpenAI 连接配置 POJO
│   ├── OpenAiRequest.java          # OpenAI Chat Completions 请求体
│   ├── OpenAiResponse.java         # OpenAI Chat Completions 响应体
│   ├── ResponseFormat.java         # 响应格式控制
│   ├── Tool.java                   # 工具定义（type + function）
│   ├── ToolCall.java               # 工具调用（id + type + function）
│   └── Usage.java                  # Token 用量
├── memory/
│   ├── ShortMemory.java            # 短期记忆（会话上下文 List<Message>）
│   ├── LongMemory.java             # 长期记忆桩（预留，当前为空壳）
│   └── StructureMemory.java        # 结构化记忆桩（预留，当前为空壳）
├── tools/
│   ├── ToolDefManager.java         # 工具定义管理器：注解扫描 + 注册 + JSON Schema 生成
│   ├── ToolCallManager.java        # 工具调用管理器：解析 tool_calls → 反射执行
│   ├── annotation/
│   │   ├── Tool.java               # @Tool 方法注解
│   │   └── Param.java              # @Param 参数注解
│   └── tools/                      # 工具实现类
│       ├── FileTools.java          # 文件读写/列表/删除
│       ├── MathTools.java          # 数学表达式计算/随机数
│       ├── ShellTools.java         # Shell 命令执行
│       ├── TextTools.java          # 文本统计/替换/Base64编解码
│       ├── TimeTools.java          # 当前时间获取
│       └── WebTools.java           # HTTP GET/POST 请求
└── util/
    └── OpenAiClient.java           # OpenAI HTTP 客户端（同步 + 流式）
```

---

## 3. 核心模块详解

### 3.1 Agent 循环入口 — `Main.java`

```java
public class Main {
    public static void main(String[] args) {
        // 1. 显示配置信息（模型、BaseURL等）
        showDetails();
        // 2. 注册所有工具
        registerTools();
        // 3. 读取多行用户输入（以 /send 结束）
        String input = readUserInput();
        // 4. 进入 Agent 循环
        runLoop(input);
    }

    public static void runLoop(String input) {
        // 将用户问题加入短期记忆
        ShortMemory.add(Message.builder().role("user").content(input).build());

        while (true) {
            // 构造请求体（含工具定义）
            OpenAiRequest request = OpenAiRequest.builder()
                    .model(...)
                    .messages(ShortMemory.getAll())
                    .temperature(...)
                    .maxTokens(...)
                    .tools(ToolDefManager.getTools())
                    .build();

            // 调用 LLM API
            OpenAiResponse response = OpenAiClient.get().chat(request);

            // 解析响应
            Choice choice = response.getChoices().get(0);
            String finishReason = choice.getFinishReason();
            Message message = choice.getMessage();

            // 将 assistant 消息（去除 reasoning_content）存入短期记忆
            ShortMemory.add(message.toHistoryMessage());

            // 根据 finishReason 决定下一步
            switch (finishReason) {
                case "stop" -> {
                    break;
                }
                case "length" -> { /* 截断提示 */
                    break;
                }
                case "tool_calls" -> {
                    ToolCallManager.executeAndAppend(response, ShortMemory.getAll());
                    continue; // 继续循环
                }
                case "content_filter" -> { /* 过滤警告 */
                    break;
                }
                default -> { /* 未知错误 */
                    break;
                }
            }
            break;
        }
    }
}
```

**关键设计决策:**

- 使用 `message.toHistoryMessage()` 去除 `reasoning_content`（思考内容），避免浪费上下文窗口
- `ShortMemory` 是静态 `ArrayList<Message>`，每次循环直接取出全部历史发送

### 3.2 工具注册与定义 — `ToolDefManager.java`

`ToolDefManager` 是整个工具系统的注册中心，核心职责：

| 方法                          | 功能                                         |
|-----------------------------|--------------------------------------------|
| `register(Object instance)` | 扫描实例的 `@Tool` 注解方法，解析参数生成 `ToolMethod` 注册表 |
| `getTools()`                | 返回 OpenAI 格式的 `List<Tool>`（含 JSON Schema）  |
| `getMethod(name)`           | 按工具名获取反射调用信息                               |
| `toolNames()`               | 列出所有已注册工具名                                 |

**内部数据结构:**

```java
public static class ToolMethod {
    public final String name;          // 工具名（暴露给大模型）
    public final String description;   // 工具描述
    public final Method method;        // Java 反射方法
    public final Object instance;      // 方法所属实例
    public final List<ParamDef> params;// 参数定义列表
    public final Map<String, Object> schema; // JSON Schema
}

public static class ParamDef {
    public final String name;          // 参数名
    public final Class<?> type;        // Java 类型
    public final String description;   // 参数描述
    public final boolean required;     // 是否必填
    public final String[] enumValues;  // 枚举约束
}
```

**自动生成的 JSON Schema 示例:**

```json
{
  "type": "function",
  "function": {
    "name": "readFile",
    "description": "读取指定路径的文件内容",
    "parameters": {
      "type": "object",
      "properties": {
        "path": {
          "type": "string",
          "description": "文件路径"
        }
      },
      "required": [
        "path"
      ]
    }
  }
}
```

### 3.3 工具调用执行 — `ToolCallManager.java`

通过反射执行工具调用的完整流程：

```
ToolCallManager.executeAndAppend(response, history)
    │
    ├─ 提取 response 中的 tool_calls 列表
    │
    ├─ 对每个 ToolCall:
    │   ├─ 解析 JSON 参数 → Map<String, Object>
    │   ├─ 按 ParamDef 顺序组装参数数组（类型转换）
    │   ├─ method.invoke(instance, args) 反射调用
    │   └─ 构造 role="tool" 的 Message（含 tool_call_id）
    │
    └─ 将全部 tool 结果消息追加到 history → 继续下一轮循环
```

**类型转换规则（`convert()` 方法）:**
| 目标类型 | 转换方式 |
|----------|----------|
| `String` | `value.toString()` |
| `int/Integer` | `((Number)value).intValue()` 或 `Integer.parseInt(s)` |
| `long/Long` | 同上 |
| `double/Double` | 同上 |
| `boolean/Boolean` | `Boolean.parseBoolean(s)` |
| 复杂类型 | Jackson `convertValue()` |

### 3.4 注解系统

**`@Tool` — 方法注解:**

```java

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Tool {
    String name() default "";           // 工具名，默认取方法名

    String description() default "";    // 工具描述
}
```

**`@Param` — 参数注解:**

```java

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface Param {
    String name();                       // 参数名

    String description() default "";     // 参数描述

    boolean required() default true;     // 是否必填

    String[] enumValues() default {};    // 枚举值
}
```

### 3.5 LLM HTTP 客户端 — `OpenAiClient.java`

基于 Java 11+ 内置 `java.net.http.HttpClient`，无第三方 HTTP 依赖。

| 特性           | 实现                                                             |
|--------------|----------------------------------------------------------------|
| 同步请求         | `HttpClient.send()` + `BodyHandlers.ofString()`                |
| 流式请求         | `HttpClient.send()` + `BodyHandlers.ofInputStream()` + SSE 行解析 |
| 超时控制         | `Duration.ofSeconds(timeoutSeconds)`                           |
| 配置来源         | `application.yml`（可被环境变量覆盖）                                    |
| API Key 优先级  | 环境变量 `OPENAI_API_KEY` > YAML `openai.api-key`                  |
| Base URL 优先级 | 环境变量 `OPENAI_BASE_URL` > YAML `openai.base-url`                |

**流式响应处理:**

- 逐行读取 `data: {...}` SSE 事件
- 遇到 `[DONE]` 结束
- 分别回调 `onReasoning` 和 `onContent`（思考内容和实际回复）

### 3.6 记忆系统

| 记忆类型                | 实现                                          | 状态    |
|---------------------|---------------------------------------------|-------|
| **ShortMemory**     | `static List<Message>` — ArrayList 存储全部对话历史 | ✅ 已实现 |
| **LongMemory**      | 空壳类                                         | ⏳ 预留  |
| **StructureMemory** | 空壳类                                         | ⏳ 预留  |

**重要:** `Message.toHistoryMessage()` 会清除 `reasoningContent` 字段，因为：

1. 思考内容是模型的"草稿纸"，新轮次不需要看到
2. 思考内容通常数倍于实际回复，占用大量上下文窗口
3. DeepSeek 等厂商明确建议不要回传 `reasoning_content`

### 3.7 配置加载 — `ConfigLoader.java`

轻量级 YAML 配置加载器，模拟 Spring Boot 的 `application.yml` 行为。

**Profile 优先级（由高到低）:**

1. 代码内 `ConfigLoader.setProfile("dev")`
2. 环境变量 `CONFIG_PROFILE`
3. 系统属性 `config.profile`
4. 默认 `"default"`（仅加载 `application.yml`）

**占位符支持:** `${key}` 语法引用其他配置项或环境变量/系统属性。

---

## 4. 工具一览

| 工具类          | 方法数    | 方法列表                                                                           |
|--------------|--------|--------------------------------------------------------------------------------|
| `FileTools`  | 6      | `readFile`, `writeFile`, `listFiles`, `deleteFile`, `searchFiles`, `grepFiles` |
| `MathTools`  | 2      | `calculate`, `randomNumber`                                                    |
| `TimeTools`  | 1      | `getCurrentTime`                                                               |
| `ShellTools` | 1      | `executeShell`                                                                 |
| `TextTools`  | 4      | `countText`, `textReplace`, `base64Encode`, `base64Decode`                     |
| `WebTools`   | 2      | `httpGet`, `httpPost`                                                          |
| **合计**       | **16** |                                                                                |

> 详细的工具参数说明见 [TOOLS.md](./TOOLS.md)

---

## 5. 数据模型 — entity 包

所有 entity 使用 Lombok `@Data` + `@Builder` + Jackson 注解。

### 请求-响应映射关系

```
OpenAiRequest (请求)
  ├─ model: String
  ├─ messages: List<Message>
  ├─ temperature: Double
  ├─ maxTokens / maxCompletionTokens: Integer
  ├─ tools: List<Tool>
  │    └─ Tool { type="function", function: FunctionDef }
  │         └─ FunctionDef { name, description, parameters: JSON Schema }
  ├─ tool_choice: Object ("none" | "auto" | "required" | { type, function })
  ├─ stream: Boolean
  ├─ response_format: ResponseFormat
  ├─ extra: Map<String,Object> (@JsonAnyGetter — 厂商扩展参数)
  └─ ...

OpenAiResponse (响应)
  ├─ id: String
  ├─ choices: List<Choice>
  │    └─ Choice { index, message, delta, finish_reason, logprobs }
  │         └─ Message { role, content, tool_calls, tool_call_id, reasoning_content }
  │              └─ ToolCall { id, type="function", function: FunctionCall }
  │                   └─ FunctionCall { name, arguments: JSON String }
  └─ usage: Usage { prompt_tokens, completion_tokens, total_tokens }
```

### Message 的特殊处理

`Message` 的 `content` 字段类型为 `Object`，兼容两种形态：

- **纯文本:** `String`
- **多模态:** `List<ContentPart>`（预留，当前未实现具体类）

---

## 6. 配置文件

### `application.yml`（基础配置）

```yaml
openai:
  api-key: ${openai.api-key}      # 从环境变量或系统属性读取
  base-url: https://api.deepseek.com
  model: ${openai.model}
  temperature: 0.7
  max-tokens: 393216
  timeout-seconds: 60
```

### `application-dev.yml`（开发环境覆盖）

```yaml
openai:
  api-key: sk-xxx                # 实际密钥
  model: deepseek-v4-flash
```

---

## 7. 依赖

| 依赖               | 版本      | 作用域      | 用途        |
|------------------|---------|----------|-----------|
| Java             | 21+     | compile  | 编译运行环境    |
| Hutool           | 5.8.46  | runtime  | 通用工具库     |
| Lombok           | 1.18.42 | provided | 简化 POJO   |
| Jackson Databind | 2.18.3  | compile  | JSON 序列化  |
| Jackson YAML     | 2.18.3  | compile  | YAML 配置解析 |

---

## 8. 扩展指南

### 添加新工具

1. 创建工具类，方法上标注 `@Tool`，参数上标注 `@Param`
2. 在 `Main.registerTools()` 中调用 `ToolDefManager.register(new YourTools())`

```java
public class MyTools {
    @Tool(name = "hello", description = "向世界问好")
    public String hello(
            @Param(name = "name", description = "你的名字") String name
    ) {
        return "你好, " + name + "!";
    }
}
```

### 添加新 LLM 厂商

只需修改 `application.yml` 中的 `openai.base-url` 和 `openai.model`，兼容所有 OpenAI API 协议的服务。

### 扩展参数（厂商特有）

通过 `OpenAiRequest.builder().entry("key", value)` 添加非标准参数：

```java
OpenAiRequest.builder()
    .

model("deepseek-chat")
    .

entry("thinking",Map.of("type", "enabled"))
        .

build();
// → JSON: {"model":"deepseek-chat", ..., "thinking":{"type":"enabled"}}
```

---

## 9. 值得注意的实现细节

1. **`finishReason` 判断:** 使用 `switch` 而非 `if-else`，但注意 Java 14+ 的 `switch` 表达式不会穿透
2. **仅处理第一个 choice:** `response.getChoices().get(0)` — 未处理 `n > 1` 的情况
3. **流式不用于 Agent 循环:** `Main.runLoop()` 使用非流式请求，流式 API 仅用于简单对话示例
4. **无 Token 计数限制:** ShortMemory 不做长度检查，长对话可能超出上下文窗口
5. **无并发安全:** ShortMemory 使用 `ArrayList` 而非线程安全集合，仅适用于单线程场景
