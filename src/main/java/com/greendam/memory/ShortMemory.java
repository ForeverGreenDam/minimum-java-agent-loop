package com.greendam.memory;

import com.greendam.entity.Message;

import java.util.ArrayList;
import java.util.List;

public class ShortMemory {
    //短期记忆使用list保存，方便后续新增和删除
    private static final List<Message> SHORT_MEMORY = new ArrayList<>();

    public static void add(Message message) {
        SHORT_MEMORY.add(message);
    }

    public static void remove(Message message) {
        SHORT_MEMORY.remove(message);
    }

    public static List<Message> getAll() {
        return SHORT_MEMORY;
    }
}
