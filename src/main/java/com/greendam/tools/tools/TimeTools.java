package com.greendam.tools.tools;

import com.greendam.tools.annotation.Tool;

import java.time.LocalDateTime;

public class TimeTools {
    @Tool(name = "getCurrentTime", description = "获取当前时间")
    public String getCurrentTime() {
        return LocalDateTime.now().toString();
    }
}
