package com.greendam;

import com.greendam.config.ConfigLoader;
import com.greendam.util.OpenAiClient;

public class Main {
    public static void main(String[] args) {
        showDetails();
        simpleChat();
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

    public static void simpleChat() {
        String reply = OpenAiClient.get().chat("你好,你是什么模型？");
        System.out.println(reply);
    }
}
