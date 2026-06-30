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
        ToolDefManager.

register(new MemoryTools());
        ToolDefManager.

register(new SteamTools());
```

---

## 🧰 工具总览

| # | 工具类             |  工具方法数 | 分类          |
|---|-----------------|-------:|-------------|
| 1 | **FileTools**   |      7 | 文件操作        |
| 2 | **MathTools**   |      2 | 数学计算        |
| 3 | **TimeTools**   |      1 | 时间获取        |
| 4 | **ShellTools**  |      1 | Shell 命令    |
| 5 | **TextTools**   |      4 | 文本处理        |
| 6 | **WebTools**    |      4 | HTTP / 网页抓取 |
| 7 | **MemoryTools** |      3 | 长期记忆管理      |
| 8 | **SteamTools**  |      6 | Steam 信息查询  |
|   | **总计**          | **28** |             |

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

| 参数              | 类型        | 必填 | 描述                                          |
|-----------------|-----------|:--:|---------------------------------------------|
| `pattern`       | `string`  | ✅  | 文件名搜索模式，如 `*.java`、`README*`、`*.{java,xml}` |
| `directoryPath` | `string`  | ❌  | 搜索根目录，默认当前工作目录                              |
| `recursive`     | `boolean` | ❌  | 是否递归搜索，默认 `true`                            |
| `maxResults`    | `number`  | ❌  | 最大返回结果数，默认 100                              |

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

| 参数           | 类型       | 必填 | 描述                                                   |
|--------------|----------|:--:|------------------------------------------------------|
| `expression` | `string` | ✅  | 数学表达式字符串，如 `'2+3*(4-1)'`、`'sqrt(144)'`、`'pow(2,10)'` |

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

执行一条 Shell 命令并返回标准输出和标准错误。默认 60 秒超时。Windows 使用 PowerShell，Linux/Mac 使用 bash。

| 参数               | 类型       | 必填 | 描述                                        |
|------------------|----------|:--:|-------------------------------------------|
| `command`        | `string` | ✅  | 要执行的 Shell 命令                             |
| `workingDir`     | `string` | ❌  | 工作目录，默认当前工作目录                             |
| `timeoutSeconds` | `number` | ❌  | 超时秒数，默认 60。启动 Web 服务器等后台进程时设为 5~10 秒可立即返回 |

---

## 5. 📝 TextTools — 文本处理工具集

**类路径：** `com.greendam.tools.tools.TextTools`

所有方法均为纯函数，无副作用。

### 5.1 countText

统计文本的字符数（含/不含空格）、单词数、行数，返回统计摘要。

| 参数     | 类型       | 必填 | 描述       |
|--------|----------|:--:|----------|
| `text` | `string` | ✅  | 要统计的文本内容 |

### 5.2 textReplace

在文本中查找并替换指定字符串，支持普通替换和正则替换。

> **注意：** 本工具只返回替换后的文本，不会直接修改文件。如需写入文件，请将返回值传给 `writeFile`。

| 参数         | 类型        | 必填 | 描述                                |
|------------|-----------|:--:|-----------------------------------|
| `text`     | `string`  | ✅  | 原始文本                              |
| `search`   | `string`  | ✅  | 要查找的字符串或正则表达式                     |
| `replace`  | `string`  | ✅  | 替换为的字符串                           |
| `useRegex` | `boolean` | ❌  | 是否将 `search` 作为正则表达式解析，默认 `false` |

### 5.3 base64Encode

将文本进行 Base64 编码，返回编码后的字符串。

| 参数     | 类型       | 必填 | 描述       |
|--------|----------|:--:|----------|
| `text` | `string` | ✅  | 要编码的原始文本 |

### 5.4 base64Decode

将 Base64 编码的字符串解码为原始文本（UTF-8）。

| 参数           | 类型       | 必填 | 描述            |
|--------------|----------|:--:|---------------|
| `base64Text` | `string` | ✅  | Base64 编码的字符串 |

---

## 6. 🌐 WebTools — 网络请求与网页抓取工具集

**类路径：** `com.greendam.tools.tools.WebTools`

除了基础 HTTP GET/POST，还支持网页正文提取与浏览器渲染抓取。

### 6.1 httpGet

发送 HTTP GET 请求，返回状态码与响应体。

| 参数        | 类型       | 必填 | 描述                                                |
|-----------|----------|:--:|---------------------------------------------------|
| `url`     | `string` | ✅  | 请求 URL，需带协议 (http/https)                          |
| `headers` | `string` | ❌  | 自定义请求头，JSON 格式，如 `{"Authorization":"Bearer xxx"}` |

### 6.2 httpPost

发送 HTTP POST 请求，返回状态码与响应体。

| 参数            | 类型       | 必填 | 描述                                 |
|---------------|----------|:--:|------------------------------------|
| `url`         | `string` | ✅  | 请求 URL，需带协议                        |
| `body`        | `string` | ❌  | POST 请求体（JSON 字符串或表单数据）            |
| `contentType` | `string` | ❌  | Content-Type，默认 `application/json` |

### 6.3 webToText

使用 GET 方法获取指定 URL 的网页内容并提取纯文本。自动去除 HTML 标签、CSS、JS、注释，保留正文。适合新闻、博客、文档等静态页面。

> 不适合调用 JSON API（请用 `httpGet`）。如内容为空或提示动态渲染，请改用 `webToTextBrowser`。

| 参数         | 类型       | 必填 | 描述              |
|------------|----------|:--:|-----------------|
| `url`      | `string` | ✅  | 要抓取的网页 URL      |
| `maxChars` | `number` | ❌  | 最大返回字符数，默认 8000 |

### 6.4 webToTextBrowser

使用无头浏览器 (Chromium) 渲染网页后提取纯文本。会等待 JavaScript 执行和网络请求完成，适合 React/Vue/Angular 等 SPA 页面。

> 速度较慢，优先用 `webToText`。仅当 `webToText` 抓不到内容时再用本工具。

| 参数         | 类型       | 必填 | 描述              |
|------------|----------|:--:|-----------------|
| `url`      | `string` | ✅  | 要抓取的网页 URL      |
| `maxChars` | `number` | ❌  | 最大返回字符数，默认 8000 |

---

## 7. 🧠 MemoryTools — 长期记忆管理工具集

**类路径：** `com.greendam.tools.tools.MemoryTools`

让 LLM 能自主管理跨会话持久化的长期记忆。

### 7.1 remember

主动将一条信息存入长期记忆。当用户明确要求记住某信息时调用，或当 LLM 认为某信息值得跨会话保留时主动调用。

> 存入的记忆必须满足 `min-importance` 阈值（默认 5），否则会被静默丢弃。

| 参数           | 类型       | 必填 | 描述                                                          |
|--------------|----------|:--:|-------------------------------------------------------------|
| `content`    | `string` | ✅  | 要记住的内容，一句话描述清楚                                              |
| `category`   | `string` | ❌  | 分类：`fact` / `preference` / `decision` / `context`，默认 `fact` |
| `importance` | `number` | ❌  | 重要度 1-10，默认 5                                               |
| `keywords`   | `string` | ❌  | 关键词，逗号分隔，用于后续检索                                             |

**重要度参考：**

- 10: 必须记住（密码、关键配置、用户明确要求"记住"的）
- 7-9: 很重要（技术选型、偏好设定、项目结构信息）
- 4-6: 有用（一般性上下文、讨论过的方案）
- 1-3: 可记可不记（临时性信息，**不会写入**）

### 7.2 recall

搜索长期记忆。在执行任务前如果觉得可能遗漏了之前的约定或背景信息，应先调用此工具检查。

| 参数      | 类型       | 必填 | 描述             |
|---------|----------|:--:|----------------|
| `query` | `string` | ✅  | 搜索查询，描述你要找什么信息 |
| `topK`  | `number` | ❌  | 返回最多几条结果，默认 5  |

### 7.3 forget

删除长期记忆中的某条信息。需要提供足够具体的查询来定位要删除的记忆。

| 参数      | 类型       | 必填 | 描述            |
|---------|----------|:--:|---------------|
| `query` | `string` | ✅  | 用于定位要删除记忆的搜索词 |

---

## 8. 🎮 SteamTools — Steam 信息查询工具集

**类路径：** `com.greendam.tools.tools.SteamTools`

查询 Steam 玩家资料、游戏库、成就等信息。需要在 `application.yml` 中配置 `steam.api-key`。

> 所有涉及用户信息的工具都需要 Steam 64 位 ID（如 `76561198012345678`）。LLM 应在缺少 ID 时主动询问用户。

### 8.1 steamGetPlayerProfile

获取 Steam 玩家的基本资料信息。

| 参数        | 类型       | 必填 | 描述                |
|-----------|----------|:--:|-------------------|
| `steamId` | `string` | ✅  | 用户的 64 位 Steam ID |

**返回信息：** 昵称、头像、个人资料状态、Steam 等级等。

### 8.2 steamGetRecentGames

获取 Steam 用户最近两周的游戏使用情况。

| 参数        | 类型       | 必填 | 描述                |
|-----------|----------|:--:|-------------------|
| `steamId` | `string` | ✅  | 用户的 64 位 Steam ID |
| `count`   | `number` | ❌  | 返回的游戏数量，默认返回全部    |

**返回信息：** 游戏名称、最近两周游玩时长、总游玩时长。

### 8.3 steamGetOwnedGames

获取 Steam 用户拥有的所有游戏列表。

| 参数               | 类型        | 必填 | 描述                    |
|------------------|-----------|:--:|-----------------------|
| `steamId`        | `string`  | ✅  | 用户的 64 位 Steam ID     |
| `count`          | `number`  | ❌  | 返回的游戏数量，默认 20         |
| `sortByPlaytime` | `boolean` | ❌  | 是否按游玩时长降序排列，默认 `true` |

### 8.4 steamGetAchievements

获取 Steam 用户在指定游戏中的成就完成情况。

| 参数        | 类型       | 必填 | 描述                              |
|-----------|----------|:--:|---------------------------------|
| `steamId` | `string` | ✅  | 用户的 64 位 Steam ID               |
| `appId`   | `string` | ✅  | 游戏的 Steam App ID（如 CS2 是 `730`） |

**返回信息：** 已解锁和未解锁的成就列表。

### 8.5 steamGetGameSchema

获取游戏的成就 Schema 定义。不需要用户 Steam ID，可用于查看游戏有哪些成就。

| 参数      | 类型       | 必填 | 描述               |
|---------|----------|:--:|------------------|
| `appId` | `string` | ✅  | 游戏的 Steam App ID |

### 8.6 steamGetGameDetails

获取 Steam 商店中游戏的详细信息。

| 参数         | 类型       | 必填 | 描述                     |
|------------|----------|:--:|------------------------|
| `appId`    | `string` | ✅  | 游戏的 Steam App ID       |
| `language` | `string` | ❌  | 语言，默认 `schinese`（简体中文） |

**返回信息：** 价格、评价数量、发行日期、开发商、游戏简介、详细描述等。

---

## 🔧 注解机制

工具系统基于两个注解实现：

### `@Tool`

用于标记一个方法可暴露给大模型调用。

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Tool {
    String name() default "";       // 函数名，默认取方法名
    String description() default ""; // 函数描述（大模型据此判断何时调用）
}
```

### `@Param`

用于声明工具参数的名称、描述、是否必填与枚举值。

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface Param {
    String name();                      // 参数名
    String description() default "";    // 参数描述
    boolean required() default true;    // 是否必填
    String[] enumValues() default {};   // 枚举值约束
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
JSON 参数 → Java 类型转换
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

1. **优先用高层工具**：改文件优先 `replaceInFile`，不要手动 read/replace/write 三步
2. **网页抓取分场景**：静态站点优先 `webToText`，动态站点再用 `webToTextBrowser`
3. **Shell 慎用**：能力最强，但副作用也最大
4. **记忆管理要主动**：重要信息及时 `remember`，执行任务前先 `recall`
5. **大文本注意长度**：某些工具会自动截断返回内容，避免上下文过大

---

## 📌 当前事实速记

- 当前总工具数：**28**
- `FileTools` 包含 `replaceInFile`（原地替换）
- `WebTools` 包含 `webToText`（静态）和 `webToTextBrowser`（动态渲染）
- `MemoryTools` 提供 `remember` / `recall` / `forget` 三个记忆管理工具
- `SteamTools` 提供 6 个 Steam 相关查询工具
- 所有工具都会被 `ToolDefManager` 自动转成 JSON Schema 暴露给模型
- 低于 `min-importance`（默认 5）的记忆不会写入长期存储
