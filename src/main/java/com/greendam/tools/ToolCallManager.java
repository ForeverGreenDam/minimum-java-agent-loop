package com.greendam.tools;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.greendam.entity.FunctionCall;
import com.greendam.entity.Message;
import com.greendam.entity.OpenAiResponse;
import com.greendam.entity.ToolCall;
import com.greendam.tools.ToolDefManager.ParamDef;
import com.greendam.tools.ToolDefManager.ToolMethod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;


/**
 * 工具调用管理器 — 解析大模型返回的 tool_calls，反射执行对应的 Java 方法.
 *
 * <h3>典型用法</h3>
 * <pre>{@code
 *   List<Message> history = new ArrayList<>();
 *   history.add(new Message("user", "北京今天天气怎么样？"));
 *
 *   while (true) {
 *       OpenAiResponse resp = client.chat(request.messages(history));
 *       Choice choice = resp.getChoices().get(0);
 *
 *       if ("tool_calls".equals(choice.getFinishReason())) {
 *           // 大模型想调用工具 → 执行并追加结果
 *           ToolCallManager.executeAndAppend(resp, history);
 *           continue;  // 继续下一轮
 *       }
 *
 *       // 正常结束
 *       System.out.println(choice.getMessage().getContent());
 *       break;
 *   }
 * }</pre>
 */
public final class ToolCallManager {

    private static final ObjectMapper JSON = new ObjectMapper();

    private ToolCallManager() {
    }

    // ==================== 执行 + 追加 ====================

    /**
     * 执行响应中所有的 tool_calls，并将结果以 tool 消息追加到消息历史.
     *
     * @param response 大模型返回的响应（finish_reason 必须为 "tool_calls"）
     * @param messages 消息历史列表（会原地追加 assistant 消息 + tool 结果消息）
     * @return 实际执行的工具数量
     */
    public static int executeAndAppend(OpenAiResponse response, List<Message> messages) {
        List<Message> results = execute(response);
        if (results.isEmpty()) {
            return 0;
        }
        // 追加 tool 结果消息
        messages.addAll(results);
        return results.size();
    }

    /**
     * 执行响应中所有的 tool_calls，返回 tool 结果消息列表.
     * <p>调用方自行决定如何管理消息历史.
     *
     * @param response 大模型返回的响应
     * @return tool 结果消息列表（每个 tool_call 对应一条 role="tool" 的消息）
     */
    public static List<Message> execute(OpenAiResponse response) {
        if (response.getChoices() == null || response.getChoices().isEmpty()) {
            return Collections.emptyList();
        }
        Message msg = response.getChoices().get(0).getMessage();
        if (msg == null || msg.getToolCalls() == null || msg.getToolCalls().isEmpty()) {
            return Collections.emptyList();
        }

        List<Message> results = new ArrayList<>();
        for (ToolCall tc : msg.getToolCalls()) {
            String result = invoke(tc);
            results.add(Message.builder()
                    .role("tool")
                    .toolCallId(tc.getId())
                    .content(result)
                    .build());
        }
        return results;
    }

    // ==================== 单个调用 ====================

    /**
     * 执行单个 ToolCall，返回执行结果的字符串.
     *
     * @param toolCall 大模型返回的 tool_call
     * @return 工具执行的返回值（字符串形式）
     */
    public static String invoke(ToolCall toolCall) {
        System.out.println("工具调用》》》》" + toolCall.getFunction().getName() + "参数： " + toolCall.getFunction().getArguments());
        FunctionCall fc = toolCall.getFunction();
        if (fc == null) {
            return error("function is null");
        }
        String result = invoke(fc.getName(), fc.getArguments());
        System.out.println("工具结果》》》》" + result);
        return result;
    }

    /**
     * 按工具名 + JSON 参数字符串直接调用.
     *
     * @param toolName  工具名
     * @param arguments JSON 参数字符串，例如 {@code {"city":"Beijing"}}
     * @return 工具执行的返回值（字符串形式）
     */
    public static String invoke(String toolName, String arguments) {
        ToolMethod tm = ToolDefManager.getMethod(toolName);
        if (tm == null) {
            return error("未知工具: " + toolName);
        }

        // 1) 解析 JSON 参数
        Map<String, Object> argsMap;
        try {
            argsMap = JSON.readValue(arguments, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            return error("参数 JSON 解析失败: " + e.getMessage());
        }

        // 2) 组装反射调用参数
        Object[] invokeArgs = new Object[tm.params.size()];
        for (int i = 0; i < tm.params.size(); i++) {
            ParamDef pd = tm.params.get(i);
            Object raw = argsMap.get(pd.name);
            try {
                invokeArgs[i] = convert(raw, pd.type);
            } catch (Exception e) {
                return error("参数 '" + pd.name + "' 转换失败: " + e.getMessage());
            }
        }

        // 3) 反射调用
        try {
            Object result = tm.method.invoke(tm.instance, invokeArgs);
            return result == null ? "" : result.toString();
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            return error("工具执行异常: " + cause.getMessage());
        }
    }

    // ==================== 类型转换 ====================

    @SuppressWarnings("unchecked")
    private static Object convert(Object value, Class<?> targetType) {
        if (value == null) {
            return null;
        }
        // 已经是目标类型
        if (targetType.isInstance(value)) {
            return value;
        }
        String s = value.toString();
        if (targetType == String.class) {
            return s;
        }
        if (targetType == int.class || targetType == Integer.class) {
            return (value instanceof Number) ? ((Number) value).intValue() : Integer.parseInt(s);
        }
        if (targetType == long.class || targetType == Long.class) {
            return (value instanceof Number) ? ((Number) value).longValue() : Long.parseLong(s);
        }
        if (targetType == double.class || targetType == Double.class) {
            return (value instanceof Number) ? ((Number) value).doubleValue() : Double.parseDouble(s);
        }
        if (targetType == float.class || targetType == Float.class) {
            return (value instanceof Number) ? ((Number) value).floatValue() : Float.parseFloat(s);
        }
        if (targetType == boolean.class || targetType == Boolean.class) {
            return Boolean.parseBoolean(s);
        }
        // 复杂类型走 Jackson 反序列化
        if (value instanceof Map) {
            return JSON.convertValue(value, targetType);
        }
        return s;
    }

    // ==================== 工具 ====================

    private static String error(String msg) {
        return "[ERROR] " + msg;
    }
}
