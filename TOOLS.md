# Minimum Java Agent Loop — 工具文档

> 项目描述：一个最小化的 Java Agent 循环实现，通过 `@Tool` 注解机制将 Java 方法暴露为大模型可调用的工具。

---

## 📋 工具注册

工具通过 `ToolDefManager.register()` 注册。在 `Main.java` 中，以下工具类被注册：

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

---

## 🧰 工具一览

| # | 工具类            | 工具方法数  | 分类        |
|---|----------------|--------|-----------|
| 1 | **FileTools**  | 4      | 文件操作      |
| 2 | **MathTools**  | 2      | 数学计算      |
| 3 | **TimeTools**  | 1      | 时间获取      |
| 4 | **ShellTools** | 1      | Shell 命令  |
| 5 | **TextTools**  | 4      | 文本处理      |
| 6 | **WebTools**   | 2      | HTTP 网络请求 |
|   | **总计**         | **14** |           |

---

## 1. 📁 FileTools — 文件操作工具集

**类路径：** `com.greendam.tools.tools.FileTools`

提供文件读写、目录列表、删除等基础文件系统操作。所有路径均支持相对路径（相对于当前工作目录）和绝对路径。

### 1.1 readFile

读取指定路径的文件内容，以 UTF-8 编码返回文本字符串。

| 参数     | 类型       | 必填 | 描述                |
|--------|----------|:--:|-------------------|
| `path` | `string` | ✅  | 文件路径，可以是相对路径或绝对路径 |

### 1.2 writeFile

将文本内容写入指定路径的文件。默认覆盖已有文件，可设置追加模式。

| 参数        | 类型        | 必填 | 描述                        |
|-----------|-----------|:--:|---------------------------|
| `path`    | `string`  | ✅  | 文件路径，可以是相对路径或绝对路径         |
| `content` | `string`  | ✅  | 要写入文件的文本内容                |
| `append`  | `boolean` | ❌  | 是否追加到文件末尾，默认 `false` 表示覆盖 |

### 1.3 listFiles

列出指定目录下的所有文件和子目录名称。不递归子目录。

| 参数              | 类型       | 必填 | 描述             |
|-----------------|----------|:--:|----------------|
| `directoryPath` | `string` | ❌  | 目录路径，默认为当前工作目录 |

### 1.4 deleteFile

删除指定路径的文件。只能删除文件或空目录，不能删除非空目录以确保安全。

| 参数     | 类型       | 必填 | 描述       |
|--------|----------|:--:|----------|
| `path` | `string` | ✅  | 要删除的文件路径 |

---

## 2. 🔢 MathTools — 数学计算工具集

**类路径：** `com.greendam.tools.tools.MathTools`

提供数学表达式计算和随机数生成能力。表达式解析器支持加减乘除、取模、幂运算、括号以及常用数学函数。

### 2.1 calculate

计算数学表达式并返回结果。支持的操作包括：

- **二元运算符：** `+`（加）、`-`（减）、`*`（乘）、`/`（除）、`%`（取模）、`^`（幂运算）
- **括号：** `( )`
- **常量：** `pi` / `PI` / `π`、`e` / `E`
- **数学函数：**

| 函数                      | 参数数 | 说明          |
|-------------------------|:---:|-------------|
| `sqrt(x)`               |  1  | 平方根         |
| `abs(x)`                |  1  | 绝对值         |
| `sin(x)`                |  1  | 正弦          |
| `cos(x)`                |  1  | 余弦          |
| `tan(x)`                |  1  | 正切          |
| `asin(x)` / `arcsin(x)` |  1  | 反正弦         |
| `acos(x)` / `arccos(x)` |  1  | 反余弦         |
| `atan(x)` / `arctan(x)` |  1  | 反正切         |
| `log(x)` / `ln(x)`      |  1  | 自然对数        |
| `log10(x)`              |  1  | 常用对数（以10为底） |
| `exp(x)`                |  1  | e 的 x 次幂    |
| `floor(x)`              |  1  | 向下取整        |
| `ceil(x)`               |  1  | 向上取整        |
| `round(x)`              |  1  | 四舍五入        |
| `pow(x, y)`             |  2  | x 的 y 次幂    |
| `min(a, b, ...)`        | ≥2  | 最小值（可变参数）   |
| `max(a, b, ...)`        | ≥2  | 最大值（可变参数）   |
| `deg(x)`                |  1  | 弧度→角度       |
| `rad(x)`                |  1  | 角度→弧度       |

| 参数           | 类型       | 必填 | 描述                                                           |
|--------------|----------|:--:|--------------------------------------------------------------|
| `expression` | `string` | ✅  | 数学表达式字符串，例如 `'2+3*(4-1)'`、`'sqrt(144)'`、`'pow(2,10)+sin(0)'` |

### 2.2 randomNumber

生成一个指定范围内的随机浮点数。区间为 `[min, max)`，即包含最小值，不包含最大值。

| 参数    | 类型       | 必填 | 描述       |
|-------|----------|:--:|----------|
| `min` | `number` | ✅  | 最小值（包含）  |
| `max` | `number` | ✅  | 最大值（不包含） |

---

## 3. ⏰ TimeTools — 时间获取工具

**类路径：** `com.greendam.tools.tools.TimeTools`

### 3.1 getCurrentTime

获取当前时间，返回 `LocalDateTime` 的字符串表示（格式：`yyyy-MM-ddTHH:mm:ss.SSS`）。

| 参数    | 类型 | 必填 | 描述 |
|-------|----|:--:|----|
| *无参数* |    |    |    |

---

## 4. 💻 ShellTools — Shell 命令执行工具

**类路径：** `com.greendam.tools.tools.ShellTools`

> **安全警告：** 该工具可执行任意系统命令，调用前应仔细检查命令的合法性。命令执行默认有 60 秒超时限制。

### 4.1 executeShell

执行一条 Shell 命令并返回标准输出和标准错误。在 Windows 上使用 PowerShell，在 Linux/Mac 上使用 bash。

| 参数           | 类型       | 必填 | 描述                                                            |
|--------------|----------|:--:|---------------------------------------------------------------|
| `command`    | `string` | ✅  | 要执行的 Shell 命令。Windows 上使用 PowerShell 语法，Linux/Mac 上使用 bash 语法 |
| `workingDir` | `string` | ❌  | 命令执行的工作目录，默认为当前工作目录                                           |

**行为特性：**

- 自动根据 OS 选择执行器（Windows → PowerShell / Linux/Mac → sh）
- stdout 和 stderr 分别捕获
- 输出超过 8000 字符时自动截断
- 默认 60 秒超时，超时后强制终止进程

---

## 5. 📝 TextTools — 文本处理工具集

**类路径：** `com.greendam.tools.tools.TextTools`

所有方法均为纯函数，无副作用。

### 5.1 countText

统计文本的字符数（含/不含空格）、单词数、行数。

| 参数     | 类型       | 必填 | 描述       |
|--------|----------|:--:|----------|
| `text` | `string` | ✅  | 要统计的文本内容 |

**返回信息：**

- 字符数（总计）
- 字符数（不含空格）
- 空格数
- 单词数
- 行数

### 5.2 textReplace

在文本中查找并替换指定字符串。支持普通替换和正则表达式替换。

| 参数         | 类型        | 必填 | 描述                              |
|------------|-----------|:--:|---------------------------------|
| `text`     | `string`  | ✅  | 原始文本                            |
| `search`   | `string`  | ✅  | 要查找的字符串或正则表达式                   |
| `replace`  | `string`  | ✅  | 替换为的字符串                         |
| `useRegex` | `boolean` | ❌  | 是否将 search 作为正则表达式解析，默认 `false` |

### 5.3 base64Encode

将文本进行 Base64 编码，返回编码后的字符串（UTF-8 编码）。

| 参数     | 类型       | 必填 | 描述       |
|--------|----------|:--:|----------|
| `text` | `string` | ✅  | 要编码的原始文本 |

### 5.4 base64Decode

将 Base64 编码的字符串解码为原始文本（UTF-8）。

| 参数           | 类型       | 必填 | 描述            |
|--------------|----------|:--:|---------------|
| `base64Text` | `string` | ✅  | Base64 编码的字符串 |

---

## 6. 🌐 WebTools — 网络请求工具集

**类路径：** `com.greendam.tools.tools.WebTools`

使用 Java 11+ 内置的 `java.net.http.HttpClient`，无需额外依赖。连接超时 30 秒，自动跟随重定向。

### 6.1 httpGet

向指定 URL 发送 HTTP GET 请求，返回响应状态码和响应体文本。

| 参数        | 类型       | 必填 | 描述                                                     |
|-----------|----------|:--:|--------------------------------------------------------|
| `url`     | `string` | ✅  | 请求的 URL 地址，需包含协议（http/https）                           |
| `headers` | `string` | ❌  | 自定义请求头，JSON 格式的键值对，例如 `{"Authorization":"Bearer xxx"}` |

### 6.2 httpPost

向指定 URL 发送 HTTP POST 请求，可附带请求体。返回响应状态码和响应体文本。

| 参数            | 类型       | 必填 | 描述                                      |
|---------------|----------|:--:|-----------------------------------------|
| `url`         | `string` | ✅  | 请求的 URL 地址，需包含协议（http/https）            |
| `body`        | `string` | ❌  | POST 请求体内容（JSON 字符串或表单数据）               |
| `contentType` | `string` | ❌  | Content-Type 请求头，默认为 `application/json` |

**行为特性：**

- 响应体超过 10000 字符时自动截断
- 自动跟随 HTTP 重定向

---

## 🔧 注解机制

工具系统基于两个自定义注解实现：

### @Tool（方法注解）

```java

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Tool {
    String name() default "";        // 函数名（暴露给大模型），默认取方法名

    String description() default ""; // 函数描述（大模型据此判断何时调用）
}
```

### @Param（参数注解）

```java

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface Param {
    String name();                    // 参数名（暴露给大模型）

    String description() default "";  // 参数描述

    boolean required() default true;  // 是否必填

    String[] enumValues() default {}; // 枚举值列表
}
```

---

## 📦 项目依赖

| 依赖               |   版本    | 用途            |
|------------------|:-------:|---------------|
| Java             |   21    | 编译运行环境        |
| Hutool           | 5.8.46  | 工具库（运行时）      |
| Lombok           | 1.18.42 | 简化实体类编写       |
| Jackson Databind | 2.18.3  | JSON 序列化/反序列化 |
| Jackson YAML     | 2.18.3  | YAML 配置文件解析   |

---

## 🔁 Agent 循环流程

```
用户输入 → 存入 ShortMemory → 构造 OpenAiRequest（含 tools）
        → 调用 LLM API → 解析响应
        → 判断 finishReason:
            ├─ "stop"           → 输出结果，结束
            ├─ "length"         → 超出长度，结束
            ├─ "tool_calls"     → 执行工具，结果加入 ShortMemory，继续循环
            ├─ "content_filter" → 内容过滤，结束
            └─ 其他             → 未知错误，结束
```

---

## 📂 项目结构

```
src/main/java/com/greendam/
├── Main.java                       # 主入口，Agent 循环
├── config/
│   └── ConfigLoader.java           # 配置加载器
├── entity/                         # 数据模型（Lombok）
│   ├── Choice.java                 # 模型返回的选项
│   ├── FunctionCall.java           # 函数调用
│   ├── FunctionDef.java            # 函数定义
│   ├── Message.java                # 消息体
│   ├── Model.java                  # 模型配置
│   ├── OpenAiRequest.java          # OpenAI 请求体
│   ├── OpenAiResponse.java         # OpenAI 响应体
│   ├── ResponseFormat.java         # 响应格式
│   ├── Tool.java                   # 工具定义
│   ├── ToolCall.java               # 工具调用
│   └── Usage.java                  # Token 使用量
├── memory/
│   ├── LongMemory.java             # 长期记忆（桩）
│   ├── ShortMemory.java            # 短期记忆（对话上下文）
│   └── StructureMemory.java        # 结构化记忆（桩）
├── tools/
│   ├── ToolCallManager.java        # 工具调用管理器（反射执行）
│   ├── ToolDefManager.java         # 工具定义管理器（注册+注解扫描）
│   ├── annotation/
│   │   ├── Tool.java               # @Tool 注解
│   │   └── Param.java              # @Param 注解
│   └── tools/                      # 工具实现类
│       ├── FileTools.java
│       ├── MathTools.java
│       ├── ShellTools.java
│       ├── TextTools.java
│       ├── TimeTools.java
│       └── WebTools.java
└── util/
    └── OpenAiClient.java           # OpenAI API 客户端
```
