# 🤖 Minimum Java Agent Loop

> **最小的 Java Agent 循环实现** — 让大模型能调用你的 Java 代码。

```text
用户提问 → LLM思考 → 调用工具(Java方法) → 拿到结果 → LLM再思考 → ... → 最终回答
```

---

## 🎯 这是什么？

这是一个 **最小化的 Agent 循环** 的 Java 实现。核心思想很简单：

1. 你问一个问题
2. 程序把问题发给大模型（如 DeepSeek、GPT、Qwen）
3. 大模型决定是直接回答，还是调用某个工具
4. 如果需要调用工具，程序执行对应的 **Java 方法**，把结果返回给大模型
5. 大模型拿到结果后继续推理，再决定下一步
6. 循环直到大模型给出最终答案

就这么简单，没有复杂的框架，没有厚重的依赖，**纯 Java 21+ 实现**。

---

## ✨ 特性

- ✅ **纯 Java** — 无 Python、无 Node.js，Java 生态原生
- ✅ **最小依赖** — 只依赖 Jackson、Lombok、Hutool 与 Playwright
- ✅ **OpenAI 协议兼容** — 支持 DeepSeek、GPT、Qwen 等任何兼容 OpenAI API 的服务
- ✅ **注解驱动** — `@Tool` + `@Param` 注解，一行代码即可注册工具
- ✅ **自动 JSON Schema 生成** — 注解自动生成大模型需要的参数描述
- ✅ **支持思考链** — 原生支持 `reasoning_content`
- ✅ **流式 + 非流式** — 两套 API 都支持
- ✅ **摘要式短期记忆** — 上下文接近上限时，会保留最近几轮并压缩更早的对话
- ✅ **内置网页抓取** — 同时支持 HTTP 抓取和浏览器渲染抓取

---

## 🏗️ 项目结构速览

```text
src/main/java/com/greendam/
├── Main.java                    ← 入口 + Agent 循环（核心）
├── config/
│   └── ConfigLoader.java        ← 读 application.yml 配置
├── entity/                      ← 请求/响应数据模型
├── memory/
│   ├── ShortMemory.java         ← 短期记忆（摘要式上下文）
│   ├── ConversationSummarizer.java ← 对话摘要器
│   ├── ConversationLogger.java  ← 对话 Markdown 日志导出
│   ├── LongMemory.java          ← 长期记忆（预留）
│   └── StructureMemory.java     ← 结构化记忆（预留）
├── tools/
│   ├── ToolDefManager.java      ← 工具注册中心（注解扫描）
│   ├── ToolCallManager.java     ← 工具调用引擎（反射执行）
│   ├── annotation/              ← @Tool 和 @Param 注解
│   └── tools/                   ← 6 个工具类、16 个工具方法
└── util/
    ├── OpenAiClient.java        ← LLM HTTP 客户端
    └── TokenCounter.java        ← token 启发式估算
```

---

## 🚀 快速开始

### 前置条件

- JDK 21+
- Maven 3.8+
- 一个兼容 OpenAI API 的 LLM 服务（如 DeepSeek、OpenAI、Qwen 等）

### 1. 配置

基础配置位于 `src/main/resources/application.yml`：

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

推荐将敏感信息放到 `application-dev.yml` 或环境变量中：

```bash
export OPENAI_API_KEY=sk-xxx
export OPENAI_BASE_URL=https://api.deepseek.com
```

### 2. 运行

```bash
mvn compile exec:java -Dexec.mainClass="com.greendam.Main"
```

或者直接在 IDE 中运行 `Main.main()`。

### 3. 使用

程序启动后会显示当前模型与记忆配置，然后等待输入：

```text
==========Minimum Agent==========
当前模型：deepseek-v4-flash
当前BaseURL： https://api.deepseek.com
当前最大Tokens：393216
当前温度：0.7
已注册工具：
readFile writeFile replaceInFile listFiles deleteFile searchFiles grepFiles calculate randomNumber getCurrentTime executeShell countText textReplace base64Encode base64Decode httpGet httpPost webToText webToTextBrowser
记忆系统：maxTokens=200000 reserveTokens=8000 keepTurns=3
=================================
```

输入你的问题，多行输入以 `/send` 结束。比如：

```text
计算 2的10次方 + 3的5次方 等于多少？
帮我把结果写入 result.txt
/send
```

大模型会自动决定调用 `calculate` 工具计算结果，再调用 `writeFile` 工具写入文件。

---

## 🧠 短期记忆机制

当前项目已经不再是简单的滑动窗口，而是 **摘要式短期记忆**：

- 保留最近 `keep-turns` 轮完整对话与工具结果
- 当 token 接近 `max-context-tokens - reserve-tokens` 时
- 将更早的历史对话交给 `ConversationSummarizer` 调用 LLM 生成摘要
- 再用一条 `system` 摘要消息替换原始历史，减少信息丢失

这使得长对话能在有限上下文内尽量保留历史关键信息。

---

## 🧰 内置工具一览

| 工具类               | 方法                                                                                              | 说明                   |
|-------------------|-------------------------------------------------------------------------------------------------|----------------------|
| 📁 **FileTools**  | `readFile`, `writeFile`, `replaceInFile`, `listFiles`, `deleteFile`, `searchFiles`, `grepFiles` | 文件读写、替换、搜索           |
| 🔢 **MathTools**  | `calculate`, `randomNumber`                                                                     | 数学计算、随机数             |
| ⏰ **TimeTools**   | `getCurrentTime`                                                                                | 获取当前时间               |
| 💻 **ShellTools** | `executeShell`                                                                                  | 执行 Shell 命令（⚠️ 注意安全） |
| 📝 **TextTools**  | `countText`, `textReplace`, `base64Encode`, `base64Decode`                                      | 文本处理                 |
| 🌐 **WebTools**   | `httpGet`, `httpPost`, `webToText`, `webToTextBrowser`                                          | HTTP 请求、网页正文提取       |

> 每个工具的详细参数说明见 [TOOLS.md](./TOOLS.md)

---

## 🔁 Agent 循环是怎么工作的？

一句话概括：**让大模型反复“想 → 调用 → 想 → 调用”直到得出答案。**

```text
用户输入
   ↓
存入 ShortMemory
   ↓
ensureCapacity() 检查上下文窗口
   ↓
构造 OpenAiRequest（带历史消息 + tools）
   ↓
调用 OpenAiClient.chat(request)
   ↓
解析 finishReason
   ├─ stop           → 输出结果，结束
   ├─ tool_calls     → 执行工具，把工具结果追加进记忆，继续循环
   ├─ length         → 提示超长，结束
   ├─ content_filter → 提示过滤，结束
   └─ default        → 未知错误，结束
```

核心代码在 `Main.runLoop()`。

---

## 🎨 如何添加自己的工具？

### 第一步：写一个工具类

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

### 第二步：注册工具

在 `Main.registerTools()` 中加一行：

```java
public static void registerTools() {
    ToolDefManager.register(new FileTools());
    ToolDefManager.register(new MathTools());
    ToolDefManager.register(new MyTools());
}
```

### 第三步：运行

搞定。大模型会自动学习你的新工具，并在需要时调用它。

---

## 📐 架构设计理念

- **最小化** — 不引入 Spring，不依赖复杂 Agent 框架
- **可理解** — 核心循环结构直接，适合学习和二次开发
- **可扩展** — 工具靠注解注册，新增能力成本低
- **厂商无关** — 只依赖 OpenAI Chat Completions 协议
- **长对话友好** — 使用摘要式短期记忆替代简单丢弃

---

## 📦 依赖清单

| 依赖               | 版本      | 用途                   |
|------------------|---------|----------------------|
| Java             | 21+     | 编译运行环境               |
| Lombok           | 1.18.42 | 简化 getter/setter/构造器 |
| Jackson Databind | 2.18.3  | JSON 序列化/反序列化        |
| Jackson YAML     | 2.18.3  | 读取 YAML 配置           |
| Hutool           | 5.8.46  | 通用工具库                |
| Playwright       | 1.60.0  | 浏览器渲染抓取              |

---

## ⚠️ 注意事项

1. **`application-dev.yml` 已加入 `.gitignore`** — 不要提交你的 API Key
2. **ShellTools 可执行任意命令** — 谨慎使用，最好在沙箱环境运行
3. **摘要压缩仍会消耗 token 和时间** — 但比直接丢弃历史更稳妥
4. **当前仍是单线程设计** — 未做并发安全处理

---

## 📄 文档索引

| 文档                       | 读者            | 内容             |
|--------------------------|---------------|----------------|
| [README.md](./README.md) | 👤 人类开发者      | 项目介绍、快速开始      |
| [AGENT.md](./AGENT.md)   | 🤖 AI Agent   | 架构详解、代码指南、扩展说明 |
| [TOOLS.md](./TOOLS.md)   | 👤 人类 + 🤖 AI | 所有工具的详细参数说明    |

---

## 📜 开源协议

MIT License — 随便用，随便改，随便玩。
