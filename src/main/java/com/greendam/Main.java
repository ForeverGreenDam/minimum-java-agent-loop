package com.greendam;

import com.greendam.config.ConfigLoader;
import com.greendam.entity.*;
import com.greendam.memory.*;
import com.greendam.skills.SkillsManager;
import com.greendam.tools.ToolCallManager;
import com.greendam.tools.ToolDefManager;
import com.greendam.tools.tools.*;
import com.greendam.util.EmbeddingClient;
import com.greendam.util.OpenAiClient;

import java.util.List;
import java.util.Map;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        initDetails();
        while (true) {
            System.out.println("请输入你的问题（多行输入完成后，另起一行输入 /send 发送,如果仅输入 /send 则推出对话）：");
            Scanner scanner = new Scanner(System.in);
            StringBuilder inputBuilder = new StringBuilder();
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                if ("/send".equals(line.trim())) {
                    break;
                }
                if (inputBuilder.length() > 0) {
                    inputBuilder.append("\n");
                }
                inputBuilder.append(line);
            }
            String input = inputBuilder.toString();
            if (input.isEmpty()) {
                System.out.println("输入为空，程序退出。");
                break;
            }
            runLoop(input);
        }
        // 从本次对话中提取长期记忆
        if (LongMemoryStore.isLoaded()) {
            List<MemoryEntry> extracted = MemoryExtractor.extract(
                    ShortMemory.getAll(), LongMemoryStore.getAll());
            if (!extracted.isEmpty()) {
                LongMemoryStore.addBatch(extracted);
                System.out.println("🧠 长期记忆：本次提取 " + extracted.size() + " 条");
                for (MemoryEntry e : extracted) {
                    System.out.println("   [" + e.getCategory() + "/"
                            + e.getImportance() + "] " + e.getContent());
                }
            }
        }
        // 退出对话后保存对话记忆到 log 目录
        ConversationLogger.saveToFile(ShortMemory.getAll());
        // 持久化长期记忆
        LongMemoryStore.saveAll();
        // 清理 Playwright 浏览器资源
        WebTools.shutdownPlaywright();
    }

    public static void initDetails() {
        System.out.println("==========Minimum Agent==========");
        //设置 profile为DEV，这样就能使用application-dev.yml
        ConfigLoader.setProfile("dev");
        System.out.println("当前模型：" + ConfigLoader.get().getString("openai.model"));
        System.out.println("当前BaseURL： " + ConfigLoader.get().getString("openai.base-url"));
        System.out.println("当前最大Tokens：" + ConfigLoader.get().getInt("openai.max-tokens", 4096));
        System.out.println("当前温度：" + ConfigLoader.get().getDouble("openai.temperature", 0.7));
        registerTools();
        initMemory();
        initSkills();
        injectSystemPrompt();
        System.out.println("=================================");
    }

    public static void registerTools() {
        ToolDefManager.register(new FileTools());
        ToolDefManager.register(new MathTools());
        ToolDefManager.register(new TimeTools());
        ToolDefManager.register(new ShellTools());
        ToolDefManager.register(new TextTools());
        ToolDefManager.register(new WebTools());
        ToolDefManager.register(new MemoryTools());
        ToolDefManager.register(new SteamTools());
        ToolDefManager.register(new SkillEnable());
        System.out.println("已注册工具：");
        ToolDefManager.toolNames().forEach(name -> System.out.print(name + " "));
        System.out.println();
    }

    public static void runLoop(String input) {
        //首先将问题保存到短期记忆中
        ShortMemory.add(Message.builder().role("user").content(input).build());
        //检索相关长期记忆，注入上下文
        MemoryRetriever.retrieveAndInject(input);
        //进入循环
        while (true) {
            // 确保上下文窗口不超限
            ShortMemory.ensureCapacity();

            //非流式
            //构造请求体
            OpenAiRequest request = OpenAiRequest.builder()
                    .model(OpenAiClient.get().getModel())
                    .messages(ShortMemory.getAll())
                    .temperature(OpenAiClient.get().getTemperature())
                    .maxTokens(OpenAiClient.get().getMaxTokens())
                    .tools(ToolDefManager.getTools())
                    .build();
            //发起请求,获得返回结果
            OpenAiResponse response = OpenAiClient.get().chat(request);
            //解析输出结果
            //获取第一个结果
            Choice choice = response.getChoices().get(0);
            //获取结束原因
            String finishReason = choice.getFinishReason();
            //获取本轮对话的返回内容
            Message message = choice.getMessage();
            //首先将这轮的去除思考内容的message加到短期记忆中
            ShortMemory.add(message.toHistoryMessage());
            //打印出来
            System.out.println("=================================");
            System.out.println("Thinking         : " + message.getReasoningContent());
            System.out.println("ASSISTANT        : " + message.getContent());
            System.out.println("=================================");
            //然后判断本轮结束原因
            switch (finishReason) {
                case "stop":
                    break;
                case "length":
                    System.out.println("超出单轮最大长度限制，终止");
                    break;
                case "tool_calls":
                    ShortMemory.addAll(ToolCallManager.execute(response));
                    continue;
                case "content_filter":
                    System.out.println("包含过滤词，无法显示");
                    break;
                default:
                    System.out.println("未知错误，终止");
                    break;
            }
            break;
        }
    }

    /**
     * 从配置文件加载记忆系统参数.
     */
    private static void initMemory() {
        ConfigLoader cfg = ConfigLoader.get();
        int maxCtxTokens = cfg.getInt("memory.short.max-context-tokens", 128000);
        int reserveTk = cfg.getInt("memory.short.reserve-tokens", 8000);
        int keepTurns = cfg.getInt("memory.short.keep-turns", 3);
        ShortMemory.setMaxTokens(maxCtxTokens);
        ShortMemory.setReserveTokens(reserveTk);
        ShortMemory.setKeepTurns(keepTurns);
        System.out.println("短期记忆：maxTokens=" + maxCtxTokens
                + " reserveTokens=" + reserveTk + " keepTurns=" + keepTurns);

        // 长期记忆
        boolean longMemEnabled = cfg.getBool("memory.long.enabled", true);
        if (longMemEnabled) {
            String longDir = cfg.getString("memory.long.storage-dir", "memory/long");
            int longMax = cfg.getInt("memory.long.max-entries", 500);
            int minImp = cfg.getInt("memory.long.min-importance", 5);
            LongMemoryStore.configure(longDir, longMax, minImp);

            // Embedding 客户端
            boolean embEnabled = cfg.getBool("memory.long.embedding.enabled", false);
            if (embEnabled) {
                String embUrl = cfg.getString("memory.long.embedding.base-url",
                        "https://api.openai.com/v1");
                String embKey = System.getenv().getOrDefault("EMBEDDING_API_KEY",
                        cfg.getString("memory.long.embedding.api-key", ""));
                String embModel = cfg.getString("memory.long.embedding.model",
                        "text-embedding-3-small");
                EmbeddingClient.configure(true, embUrl, embKey, embModel);
            }

            // 混合检索参数
            double bm25W = cfg.getDouble("memory.long.retrieval.bm25-weight", 0.3);
            double cliffM = cfg.getDouble("memory.long.retrieval.cliff-multiplier", 2.0);
            int prefetchK = cfg.getInt("memory.long.retrieval.prefetch-k", 50);
            HybridRetriever.configure(bm25W, cliffM, prefetchK);

            // 检索注入参数
            int retTopK = cfg.getInt("memory.long.retrieval.top-k", 5);
            boolean autoInject = cfg.getBool("memory.long.retrieval.auto-inject", true);
            MemoryRetriever.configure(retTopK, autoInject);

            LongMemoryStore.loadAll();
            System.out.println("长期记忆：dir=" + longDir + " maxEntries=" + longMax
                    + " minImportance=" + minImp
                    + " retrieval=BM25" + (EmbeddingClient.isEnabled() ? "+Embedding" : "")
                    + " bm25Weight=" + bm25W);
        }
    }

    /**
     * 注入 system prompt — 定义 Agent 的角色、能力和行为准则.
     * <p>只在会话开始时调用一次，system 消息不会被上下文窗口截断移除.
     */
    private static void injectSystemPrompt() {
        Message systemMsg = Message.builder()
                .role("system")
                .content("""
                        你是 Minimum Java Agent，一个运行在 Java 21 环境中的最小的 AI 助手。
                        
                        ## 核心能力
                        - 你可以调用工具完成文件读写、Shell 命令执行、网络请求、数学计算、文本处理等任务
                        - 你拥有短期记忆（当前会话上下文）和长期记忆（跨会话持久化），能记住对话中的重要信息
                        - 你可以使用 remember 工具主动记住重要信息，使用 recall 工具搜索历史记忆，使用 forget 工具删除过时记忆
                        
                        ## Skill功能
                        - 当前你具有skill功能，具体的skill元数据如下
                        """
                        + SkillsManager.injectSkillMetadata() +
                        """
                        ## 记忆管理最佳实践
                        - 当用户明确说"记住xxx"、"别忘了xxx"时，立即调用 remember 工具
                        - 当用户分享重要偏好、做关键决策、透露项目配置信息时，主动调用 remember
                        - 执行重要操作前，如果觉得可能遗漏了之前的约定，先调用 recall 确认
                        - 当用户说"忘掉xxx"、"不用记xxx了"时，调用 forget 工具
                        
                        ## 行为准则
                        - 优先使用工具完成任务，而非凭空猜测
                        - 回答简洁准确，代码示例使用 Markdown 代码块标注语言
                        - 当上下文不足或信息不确定时，主动询问而非假设
                        - 涉及文件操作、Shell 命令等可能有副作用的操作时，先说明意图再执行
                        - 当涉及到时间敏感型问题时，优先调用getCurrentTime获取当前时间
                        - 如果你第一次调用webToText工具，网站没有返回想要的数据，那么有可能是这是个动态网页，需要你再调用webToTextBrowser获取动态网页内容
                        - 使用steam工具时，如果涉及到用户ID，你应当主动询问用户的steamID
                        """)
                .build();
        System.out.println("系统提示词：" + systemMsg.getContent());
        ShortMemory.add(systemMsg);
    }

    /**
     * 加载skill系统
     */
    private static void initSkills() {
        SkillsManager.skillLoader();
        Map<String, Map.Entry<SkillMetadata, String>> skills = SkillsManager.getSkills();
        System.out.println("已加载技能系统，当前技能列表：");
        skills.keySet().forEach(name -> System.out.print(name + " "));
        System.out.println();
    }

    public static void simpleChat() {
        String reply = OpenAiClient.get().chat("你好,你是什么模型？");
        System.out.println(reply);
    }

    public static void simpleStreamChat() {
        OpenAiRequest request = OpenAiRequest.builder()
                .model(OpenAiClient.get().getModel())
                .stream(true)
                .messages(List.of(Message.builder().role("user").content("你好,你是什么模型？").build()))
                .build();
        OpenAiClient.get().chatStream(request, System.out::print, System.out::print);
    }
}
