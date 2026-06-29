# TOOLS.md — Minimum Java Agent Loop 工具文档

> 项目内置工具的功能说明、参数定义与典型用途。

---

## 📋 工具注册

当前在 `Main.registerTools()` 中注册了以下工具类：

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

## 🧰 工具总览

| # | 工具类            |  工具方法数 | 分类          |
|---|----------------|-------:|-------------|
| 1 | **FileTools**  |      7 | 文件操作        |
| 2 | **MathTools**  |      2 | 数学计算        |
| 3 | **TimeTools**  |      1 | 时间获取        |
| 4 | **ShellTools** |      1 | Shell 命令    |
| 5 | **TextTools**  |      4 | 文本处理        |
| 6 | **WebTools**   |      4 | HTTP / 网页抓取 |
|   | **总计**         | **19** |             |

---

## 1. 📁 FileTools — 文件操作工具集

**类路径：** `com.greendam.tools.tools.FileTools`

提供文件读写、目录列表、删除、文件搜索、文件内容搜索、原地替换等文件系统操作。所有路径均支持相对路径和绝对路径。

### 1.1 readFile

读取指定路径的文件内容，以 UTF-8 编码返回文本字符串。

| 参数     | 类型       | 必填 | 描述                |
|--------|----------|:--:|-------------------|
| `path` | `string` | ✅  | 文件路径，可以是相对路径或绝对路径 |

### 1.2 writeFile

将文本内容写入指定路径的文件。默认覆盖已有文件，也可设置追加模式。

| 参数        | 类型        | 必填 | 描述                   |
|-----------|-----------|:--:|----------------------|
| `path`    | `string`  | ✅  | 文件路径，可以是相对路径或绝对路径    |
| `content` | `string`  | ✅  | 要写入文件的文本内容           |
| `append`  | `boolean` | ❌  | 是否追加到文件末尾，默认 `false` |

### 1.3 replaceInFile

在文件中查找并替换文本，直接写回原文件。相当于 `readFile + textReplace + writeFile` 的组合版。

| 参数         | 类型        | 必填 | 描述                                |
|------------|-----------|:--:|-----------------------------------|
| `path`     | `string`  | ✅  | 要修改的文件路径                          |
| `search`   | `string`  | ✅  | 要查找的字符串或正则表达式                     |
| `replace`  | `string`  | ✅  | 替换为的字符串                           |
| `useRegex` | `boolean` | ❌  | 是否将 `search` 作为正则表达式解析，默认 `false` |

### 1.4 listFiles

列出指定目录下的所有文件和子目录名称，不递归子目录。

| 参数              | 类型       | 必填 | 描述            |
|-----------------|----------|:--:|---------------|
| `directoryPath` | `string` | ❌  | 目录路径，默认当前工作目录 |

### 1.5 deleteFile

删除指定路径的文件或空目录。不会删除非空目录。

| 参数     | 类型       | 必填 | 描述       |
|--------|----------|:--:|----------|
| `path` | `string` | ✅  | 要删除的文件路径 |

### 1.6 searchFiles

按文件名模式搜索文件，支持 glob 通配符与递归搜索。

| 参数              | 类型        | 必填 | 描述                           |
|-----------------|-----------|:--:|------------------------------|
| `pattern`       | `string`  | ✅  | 文件名搜索模式，如 `*.java`、`README*` |
| `directoryPath` | `string`  | ❌  | 搜索根目录，默认当前工作目录               |
| `recursive`     | `boolean` | ❌  | 是否递归搜索，默认 `true`             |
| `maxResults`    | `number`  | ❌  | 最大返回结果数，默认 100               |

### 1.7 grepFiles

在文件内容中搜索指定关键词或正则表达式，类似 `grep -r`。

| 参数              | 类型        | 必填 | 描述                                 |
|-----------------|-----------|:--:|------------------------------------|
| `pattern`       | `string`  | ✅  | 要搜索的文本或正则表达式                       |
| `directoryPath` | `string`  | ❌  | 搜索根目录，默认当前工作目录                     |
| `filePattern`   | `string`  | ❌  | 文件名 glob 过滤模式，如 `*.java`           |
| `useRegex`      | `boolean` | ❌  | 是否将 `pattern` 作为正则表达式解析，默认 `false` |
| `maxResults`    | `number`  | ❌  | 最大返回匹配行数，默认 50                     |
| `contextLines`  | `number`  | ❌  | 匹配行前后各显示多少行上下文，默认 0                |

---

## 2. 🔢 MathTools — 数学计算工具集

**类路径：** `com.greendam.tools.tools.MathTools`

提供数学表达式计算和随机数生成能力。

### 2.1 calculate

计算数学表达式并返回结果。支持：

- 二元运算符：`+ - * / % ^`
- 括号：`( )`
- 常量：`pi` / `PI` / `π`、`e` / `E`
- 函数：`sqrt`, `abs`, `sin`, `cos`, `tan`, `asin`, `acos`, `atan`, `log`, `ln`, `log10`, `exp`, `floor`, `ceil`,
  `round`, `pow`, `min`, `max`, `deg`, `rad`

| 参数           | 类型       | 必填 | 描述       |
|--------------|----------|:--:|----------|
| `expression` | `string` | ✅  | 数学表达式字符串 |

### 2.2 randomNumber

生成一个指定范围内的随机浮点数，区间为 `[min, max)`。

| 参数    | 类型       | 必填 | 描述       |
|-------|----------|:--:|----------|
| `min` | `number` | ✅  | 最小值（包含）  |
| `max` | `number` | ✅  | 最大值（不包含） |

---

## 3. ⏰ TimeTools — 时间获取工具

**类路径：** `com.greendam.tools.tools.TimeTools`

### 3.1 getCurrentTime

获取当前时间，返回 `LocalDateTime` 的字符串表示。

| 参数    | 类型 | 必填 | 描述     |
|-------|----|:--:|--------|
| *无参数* | —  | —  | 返回当前时间 |

---

## 4. 💻 ShellTools — Shell 命令执行工具

**类路径：** `com.greendam.tools.tools.ShellTools`

> **安全警告：** 该工具可执行任意系统命令，调用前应仔细检查命令合法性。

### 4.1 executeShell

执行一条 Shell 命令并返回标准输出和标准错误。Windows 使用 PowerShell，Linux/Mac 使用 shell。

| 参数           | 类型       | 必填 | 描述            |
|--------------|----------|:--:|---------------|
| `command`    | `string` | ✅  | 要执行的命令        |
| `workingDir` | `string` | ❌  | 工作目录，默认当前工作目录 |

---

## 5. 📝 TextTools — 文本处理工具集

**类路径：** `com.greendam.tools.tools.TextTools`

所有方法均为纯函数，无副作用。

### 5.1 countText

统计文本的字符数、单词数、行数、空格数。

| 参数     | 类型       | 必填 | 描述       |
|--------|----------|:--:|----------|
| `text` | `string` | ✅  | 要统计的文本内容 |

### 5.2 textReplace

在文本中查找并替换指定字符串，支持普通替换和正则替换。

> 注意：本工具只返回替换后的文本，不会直接修改文件。

| 参数         | 类型        | 必填 | 描述                                |
|------------|-----------|:--:|-----------------------------------|
| `text`     | `string`  | ✅  | 原始文本                              |
| `search`   | `string`  | ✅  | 要查找的字符串或正则表达式                     |
| `replace`  | `string`  | ✅  | 替换为的字符串                           |
| `useRegex` | `boolean` | ❌  | 是否将 `search` 作为正则表达式解析，默认 `false` |

### 5.3 base64Encode

将文本做 Base64 编码。

| 参数     | 类型       | 必填 | 描述       |
|--------|----------|:--:|----------|
| `text` | `string` | ✅  | 要编码的原始文本 |

### 5.4 base64Decode

将 Base64 编码字符串解码为原始文本。

| 参数           | 类型       | 必填 | 描述            |
|--------------|----------|:--:|---------------|
| `base64Text` | `string` | ✅  | Base64 编码的字符串 |

---

## 6. 🌐 WebTools — 网络请求与网页抓取工具集

**类路径：** `com.greendam.tools.tools.WebTools`

除了基础 HTTP GET/POST，还支持网页正文提取与浏览器渲染抓取。

### 6.1 httpGet

发送 HTTP GET 请求，返回状态码与响应体。

| 参数        | 类型       | 必填 | 描述                |
|-----------|----------|:--:|-------------------|
| `url`     | `string` | ✅  | 请求 URL，需带协议       |
| `headers` | `string` | ❌  | 自定义请求头，JSON 字符串格式 |

### 6.2 httpPost

发送 HTTP POST 请求，返回状态码与响应体。

| 参数            | 类型       | 必填 | 描述                                 |
|---------------|----------|:--:|------------------------------------|
| `url`         | `string` | ✅  | 请求 URL，需带协议                        |
| `body`        | `string` | ❌  | POST 请求体                           |
| `contentType` | `string` | ❌  | Content-Type，默认 `application/json` |

### 6.3 webToText

直接抓取网页 HTML，并提取纯文本正文。适合静态页面、文章、博客、文档站点。

| 参数         | 类型       | 必填 | 描述              |
|------------|----------|:--:|-----------------|
| `url`      | `string` | ✅  | 要抓取的网页 URL      |
| `maxChars` | `number` | ❌  | 最大返回字符数，默认 8000 |

### 6.4 webToTextBrowser

使用 Playwright + Chromium 渲染页面后提取可见文本。适合 React/Vue/Angular 等 SPA 页面。

| 参数         | 类型       | 必填 | 描述              |
|------------|----------|:--:|-----------------|
| `url`      | `string` | ✅  | 要抓取的网页 URL      |
| `maxChars` | `number` | ❌  | 最大返回字符数，默认 8000 |

---

## 🔧 注解机制

工具系统基于两个注解实现：

### `@Tool`

用于标记一个方法可暴露给大模型调用。

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Tool {
    String name() default "";

    String description() default "";
}
```

### `@Param`

用于声明工具参数的名称、描述、是否必填与枚举值。

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface Param {
    String name();

    String description() default "";

    boolean required() default true;

    String[] enumValues() default {};
}
```

---

## 🔁 工具如何接入 Agent 循环

```text
LLM 返回 tool_calls
   ↓
ToolCallManager.execute(response)
   ↓
按工具名查 ToolDefManager 注册表
   ↓
反射执行对应 Java 方法
   ↓
封装为 role="tool" 消息
   ↓
ShortMemory.addAll(results)
   ↓
进入下一轮 LLM 请求
```

---

## ⚠️ 使用建议

1. **优先用高层工具**：比如改文件优先 `replaceInFile`，不要总是手动 read/replace/write 三步
2. **网页抓取分场景**：静态站点优先 `webToText`，动态站点再用 `webToTextBrowser`
3. **Shell 慎用**：它能力最强，但副作用也最大
4. **大文本注意长度**：某些工具会自动截断返回内容，避免上下文过大

---

## 📌 当前事实速记

- 当前总工具数：**19**
- `FileTools` 已包含 `replaceInFile`
- `WebTools` 已包含 `webToText` 与 `webToTextBrowser`
- 这些工具都会被 `ToolDefManager` 自动转成 JSON Schema 暴露给模型
