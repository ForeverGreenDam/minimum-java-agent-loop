package com.greendam;

import com.greendam.config.ConfigLoader;
import com.greendam.entity.Choice;
import com.greendam.entity.Message;
import com.greendam.entity.OpenAiRequest;
import com.greendam.entity.OpenAiResponse;
import com.greendam.memory.ShortMemory;
import com.greendam.tools.ToolCallManager;
import com.greendam.tools.ToolDefManager;
import com.greendam.tools.tools.*;
import com.greendam.util.OpenAiClient;

import java.util.List;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        showDetails();
        registerTools();
        System.out.println("初始化完成,请输入你的问题：");
        Scanner scanner = new Scanner(System.in);
        String input = scanner.nextLine();
        runLoop(input);
    }

    public static void showDetails() {
        System.out.println("==========Minimum Agent==========");
        //设置 profile为DEV，这样就能使用application-dev.yml
        ConfigLoader.setProfile("dev");
        System.out.println("当前模型：" + ConfigLoader.get().getString("openai.model"));
        System.out.println("当前BaseURL： " + ConfigLoader.get().getString("openai.base-url"));
        System.out.println("当前最大Tokens：" + ConfigLoader.get().getInt("openai.max-tokens", 4096));
        System.out.println("当前温度：" + ConfigLoader.get().getDouble("openai.temperature", 0.7));
    }

    public static void registerTools() {
        ToolDefManager.register(new FileTools());
        ToolDefManager.register(new MathTools());
        ToolDefManager.register(new TimeTools());
        ToolDefManager.register(new ShellTools());
        ToolDefManager.register(new TextTools());
        ToolDefManager.register(new WebTools());
        System.out.println("已注册工具：");
        ToolDefManager.toolNames().forEach(System.out::println);
        System.out.println("=================================");
    }

    public static void runLoop(String input) {
        //首先将问题保存到短期记忆中
        ShortMemory.add(Message.builder().role("user").content(input).build());
        //进入循环
        while (true) {
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
                    ToolCallManager.executeAndAppend(response, ShortMemory.getAll());
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
