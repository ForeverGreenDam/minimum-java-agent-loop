# 🤖 Minimum Java Agent Loop

> **最小的 Java Agent 循环实现** — 让大模型能调用你的 Java 代码。

```
用户提问 → LLM思考 → 调用工具(Java方法) → 拿到结果 → LLM再思考 → ... → 最终回答
```

---

## 🎯 这是什么？

这是一个 **最小化的 Agent 循环** 的 Java 实现。核心思想很简单：

1. 你问一个问题
2. 程序把问题发给大模型（如 DeepSeek、GPT）
3. 大模型决定是直接回答，还是调用某个工具
4. 如果需要调用工具，程序执行对应的 **Java 方法**，把结果返回给大模型
5. 大模型拿到结果后继续推理，再决定下一步
6. 循环直到大模型给出最终答案

就这么简单，没有复杂的框架，没有厚重的依赖，**纯 Java 21+ 实现**。

---

## ✨ 特性

- ✅ **纯 Java** — 无 Python、无 Node.js，Java 生态原生
- ✅ **最小依赖** — 只依赖 Jackson（JSON/YAML）和 Lombok，Hutool 仅运行时
- ✅ **OpenAI 协议兼容** — 支持 DeepSeek、GPT、Qwen 等任何兼容 OpenAI API 的服务
- ✅ **注解驱动** — `@Tool` + `@Param` 注解，一行代码即可注册工具
- ✅ **自动 JSON Schema 生成** — 注解自动生成大模型需要的参数描述
- ✅ **支持思考链** — 原生支持 `reasoning_content`（如 DeepSeek 的思维链）
- ✅ **流式 + 非流式** — 两套 API 都支持
- ✅ **自研表达式计算器** — 不依赖第三方表达式引擎

---

## 🏗️ 项目结构速览

```
src/main/java/com/greendam/
├── Main.java                 ← 入口 + Agent 循环（核心！）
├── config/
│   └── ConfigLoader.java     ← 读 application.yml 配置
├── entity/                   ← 11 个 POJO（请求/响应模型）
├── memory/
│   ├── ShortMemory.java      ← 短期记忆（对话上下文）
│   ├── LongMemory.java       ← 长期记忆（预留）
│   └── StructureMemory.java  ← 结构化记忆（预留）
├── tools/
│   ├── ToolDefManager.java   ← 工具注册中心（注解扫描）
│   ├── ToolCallManager.java  ← 工具调用引擎（反射执行）
│   ├── annotation/           ← @Tool 和 @Param 注解
│   └── tools/                ← 6 个工具类、14 个工具方法
└── util/
    └── OpenAiClient.java     ← LLM HTTP 客户端
```

---

## 🚀 快速开始

### 前置条件

- JDK 21+
- Maven 3.8+
- 一个兼容 OpenAI API 的 LLM 服务（如 DeepSeek、OpenAI、Qwen 等）

### 1. 配置

编辑 `src/main/resources/application.yml`：

```yaml
openai:
  api-key: sk-your-api-key-here    # 你的 API Key
  base-url: https://api.deepseek.com  # API 地址
  model: deepseek-chat              # 模型名
  temperature: 0.7
  max-tokens: 4096
```

也可以用环境变量（优先级更高）：

```bash
export OPENAI_API_KEY=sk-xxx
export OPENAI_BASE_URL=https://api.deepseek.com
```

### 2. 运行

```bash
mvn compile exec:java -Dexec.mainClass="com.greendam.Main"
```

或者直接在 IDE 里运行 `Main.main()`。

### 3. 使用

程序启动后会显示当前模型配置，然后等待输入：

```
==========Minimum Agent==========
当前模型：deepseek-v4-flash
当前BaseURL： https://api.deepseek.com
...
初始化完成，请输入你的问题（多行输入完成后，另起一行输入 /send 发送）：
```

输入你的问题，多行输入以 `/send` 结束。比如：

```
计算 2的10次方 + 3的5次方 等于多少？
帮我把结果写入 result.txt
/send
```

大模型会自动决定调用 `calculate` 工具计算结果，再调用 `writeFile` 工具写入文件。

---

## 🧰 内置工具一览

| 工具                | 方法                                                         | 说明                   |
|-------------------|------------------------------------------------------------|----------------------|
| 📁 **FileTools**  | `readFile`, `writeFile`, `listFiles`, `deleteFile`         | 读写文件、列目录、删文件         |
| 🔢 **MathTools**  | `calculate`, `randomNumber`                                | 数学计算、随机数（自研解析器！）     |
| ⏰ **TimeTools**   | `getCurrentTime`                                           | 获取当前时间               |
| 💻 **ShellTools** | `executeShell`                                             | 执行 Shell 命令（⚠️ 注意安全） |
| 📝 **TextTools**  | `countText`, `textReplace`, `base64Encode`, `base64Decode` | 文本处理                 |
| 🌐 **WebTools**   | `httpGet`, `httpPost`                                      | HTTP 网络请求            |

> 每个工具的详细参数说明见 [TOOLS.md](./TOOLS.md)

---

## 🔁 Agent 循环是怎么工作的？

用一句话概括：**让大模型反复"想-调用-想-调用"直到得出答案**。

```
         ┌──────────────────────────────────────┐
         │         用户输入问题                   │
         │     "帮我算 2^10 + 3^5"               │
         └──────────────┬───────────────────────┘
                        ▼
         ┌──────────────────────────────────────┐
         │     1. 存入短期记忆 (ShortMemory)      │
         │     2. 构造请求，发给 LLM              │
         │        (请求里带着所有工具定义)         │
         └──────────────┬───────────────────────┘
                        ▼
              LLM 决定调用工具
         ┌──────────────────────────────────────┐
         │   finishReason = "tool_calls"         │
         │   → 调用 calculate(2^10)              │
         │   → 调用 calculate(3^5)               │
         │   → 结果存回短期记忆                    │
         └──────────────┬───────────────────────┘
                        ▼
              再次请求 LLM（带着工具结果）
         ┌──────────────────────────────────────┐
         │   LLM 现在有了中间结果                  │
         │   算出最终答案: 1024 + 243 = 1267      │
         └──────────────┬───────────────────────┘
                        ▼
         ┌──────────────────────────────────────┐
         │   finishReason = "stop"               │
         │   输出答案 ✓                           │
         └──────────────────────────────────────┘
```

**代码核心就是一个 `while(true)` 循环**（见 `Main.runLoop()`），每次循环：

1. 把整个对话历史 + 工具定义发给大模型
2. 看大模型返回的 `finishReason`：
    - `"stop"` → 回答完毕，结束
    - `"tool_calls"` → 执行工具，继续循环
    - 其他 → 异常处理，结束

---

## 🎨 如何添加自己的工具？

三步搞定：

### 第一步：写一个工具类

```java
public class MyTools {

    @Tool(name = "hello", description = "向世界问好")
    public String hello(
            @Param(name = "name", description = "你的名字") String name
    ) {
        return "你好, " + name + "!";
    }

    @Tool(name = "add", description = "计算两个数的和")
    public String add(
            @Param(name = "a", description = "第一个数") int a,
            @Param(name = "b", description = "第二个数") int b
    ) {
        return String.valueOf(a + b);
    }
}
```

### 第二步：注册工具

在 `Main.registerTools()` 方法里加一行：

```java
public static void registerTools() {
    ToolDefManager.register(new FileTools());
    ToolDefManager.register(new MathTools());
    // ... 已有工具 ...
    ToolDefManager.register(new MyTools());  // ← 加这一行
}
```

### 第三步：运行

搞定！大模型会自动学习你的新工具，在需要时调用它。

---

## 📐 架构设计理念

### 为什么这么设计？

- **最小化** — 不引入 Spring、不引入复杂的 Agent 框架，能跑就行
- **可理解** — 整个核心循环不到 40 行代码，看完就能改
- **可扩展** — 通过注解和反射，添加工具不用改框架代码
- **厂商无关** — 只依赖 OpenAI 协议，换模型只需改配置

### 关键设计决策

| 决策                     | 理由                       |
|------------------------|--------------------------|
| 不清理历史消息                | 简单场景够用，复杂场景需自行加 Token 计数 |
| 仅处理第一个 choice          | `n=1` 是最常见场景             |
| 自行实现表达式解析              | 不依赖第三方，减少依赖              |
| reasoning_content 不存历史 | 节省上下文窗口，厂商建议如此           |

---

## 📦 依赖清单

| 依赖               | 版本      | 用途                    |
|------------------|---------|-----------------------|
| Java 21+         | —       | 运行时（用了很多新特性）          |
| Lombok           | 1.18.42 | 省去写 getter/setter/构造器 |
| Jackson Databind | 2.18.3  | JSON 序列化/反序列化         |
| Jackson YAML     | 2.18.3  | 读 application.yml     |
| Hutool           | 5.8.46  | 运行时工具（可选）             |

**零外部 HTTP 依赖** — 直接用 Java 11+ 内置的 `java.net.http.HttpClient`。

---

## ⚠️ 注意事项

1. **`application-dev.yml` 已加入 `.gitignore`** — 不要提交你的 API Key
2. **ShellTools 可执行任意命令** — 谨慎使用，最好在沙箱环境运行
3. **无上下文长度控制** — 长对话可能超出模型上下文限制，后续建议加 Token 裁剪
4. **单线程** — 没有考虑并发安全，仅适用于单用户场景

---

## 📄 文档索引

| 文档                       | 读者            | 内容             |
|--------------------------|---------------|----------------|
| [README.md](./README.md) | 👤 人类开发者      | 项目介绍、快速开始（本文）  |
| [AGENT.md](./AGENT.md)   | 🤖 AI Agent   | 架构详解、代码指南、扩展说明 |
| [TOOLS.md](./TOOLS.md)   | 👤 人类 + 🤖 AI | 所有工具的详细参数说明    |

---

## 📜 开源协议

MIT License — 随便用，随便改，随便玩。
