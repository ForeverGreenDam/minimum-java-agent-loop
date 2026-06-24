package com.greendam.tools;

import com.greendam.entity.FunctionDef;
import com.greendam.entity.Tool;
import com.greendam.tools.annotation.Param;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工具定义管理器 — 注册带 {@link com.greendam.tools.annotation.Tool @Tool} 注解的方法，
 * 生成 OpenAI 格式的 Tool 列表.
 *
 * <pre>{@code
 *   ToolDefManager.register(new MyTools());
 *   List<Tool> tools = ToolDefManager.getTools();
 *   request.setTools(tools);
 * }</pre>
 */
public final class ToolDefManager {

    /**
     * 工具名 → ToolMethod（供调用）
     */
    private static final Map<String, ToolMethod> METHODS = new ConcurrentHashMap<>();

    private ToolDefManager() {
    }

    // ==================== 注册 ====================

    /**
     * 注册一个工具实例，扫描其所有带 {@link com.greendam.tools.annotation.Tool @Tool} 的方法.
     *
     * @param instance 工具实例
     * @return 注册成功的工具数量
     */
    public static int register(Object instance) {
        int count = 0;
        for (Method method : instance.getClass().getDeclaredMethods()) {
            com.greendam.tools.annotation.Tool ann = method.getAnnotation(com.greendam.tools.annotation.Tool.class);
            if (ann == null) {
                continue;
            }
            String name = ann.name().isEmpty() ? method.getName() : ann.name();
            String desc = ann.description();

            // 解析参数
            List<ParamDef> params = new ArrayList<>();
            for (Parameter p : method.getParameters()) {
                Param pAnn = p.getAnnotation(Param.class);
                if (pAnn != null) {
                    params.add(new ParamDef(
                            pAnn.name(),
                            p.getType(),
                            pAnn.description(),
                            pAnn.required(),
                            pAnn.enumValues()
                    ));
                }
            }

            // 生成 JSON Schema
            Map<String, Object> schema = buildJsonSchema(params);

            ToolMethod tm = new ToolMethod(name, desc, method, instance, params, schema);
            METHODS.put(name, tm);
            count++;
        }
        return count;
    }

    /**
     * 批量注册.
     */
    public static int register(Object... instances) {
        int count = 0;
        for (Object inst : instances) {
            count += register(inst);
        }
        return count;
    }

    // ==================== 查询 ====================

    /**
     * 获取所有已注册工具的 OpenAI 格式定义列表（可直接 set 到 OpenAiRequest.tools）
     */
    public static List<Tool> getTools() {
        List<Tool> tools = new ArrayList<>();
        for (ToolMethod tm : METHODS.values()) {
            tools.add(Tool.builder()
                    .type("function")
                    .function(FunctionDef.builder()
                            .name(tm.name)
                            .description(tm.description)
                            .parameters(tm.schema)
                            .build())
                    .build());
        }
        return tools;
    }

    /**
     * 根据工具名获取调用信息
     */
    public static ToolMethod getMethod(String name) {
        return METHODS.get(name);
    }

    /**
     * 所有已注册的工具名
     */
    public static Set<String> toolNames() {
        return Collections.unmodifiableSet(METHODS.keySet());
    }

    /**
     * 是否已注册
     */
    public static boolean contains(String name) {
        return METHODS.containsKey(name);
    }

    /**
     * 清空注册表
     */
    public static void clear() {
        METHODS.clear();
    }

    /**
     * 已注册数量
     */
    public static int size() {
        return METHODS.size();
    }

    // ==================== JSON Schema 生成 ====================

    @SuppressWarnings("unchecked")
    private static Map<String, Object> buildJsonSchema(List<ParamDef> params) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();

        for (ParamDef p : params) {
            Map<String, Object> prop = new LinkedHashMap<>();
            prop.put("type", toJsonType(p.type));
            if (!p.description.isEmpty()) {
                prop.put("description", p.description);
            }
            if (p.enumValues.length > 0) {
                prop.put("enum", Arrays.asList(p.enumValues));
            }
            properties.put(p.name, prop);

            if (p.required) {
                required.add(p.name);
            }
        }

        schema.put("properties", properties);
        if (!required.isEmpty()) {
            schema.put("required", required);
        }
        return schema;
    }

    /**
     * Java 类型 → JSON Schema type
     */
    private static String toJsonType(Class<?> clazz) {
        if (clazz == String.class) return "string";
        if (clazz == int.class || clazz == long.class || clazz == Integer.class || clazz == Long.class)
            return "integer";
        if (clazz == double.class || clazz == float.class || clazz == Double.class || clazz == Float.class)
            return "number";
        if (clazz == boolean.class || clazz == Boolean.class) return "boolean";
        if (clazz.isArray() || List.class.isAssignableFrom(clazz)) return "array";
        return "object";
    }

    // ==================== 内部类 ====================

    /**
     * 工具方法的反射信息 + 元数据
     */
    public static class ToolMethod {
        public final String name;
        public final String description;
        public final Method method;
        public final Object instance;
        public final List<ParamDef> params;
        public final Map<String, Object> schema;

        ToolMethod(String name, String description, Method method, Object instance,
                   List<ParamDef> params, Map<String, Object> schema) {
            this.name = name;
            this.description = description;
            this.method = method;
            this.instance = instance;
            this.params = params;
            this.schema = schema;
        }
    }

    /**
     * 单个参数的元数据
     */
    public static class ParamDef {
        public final String name;
        public final Class<?> type;
        public final String description;
        public final boolean required;
        public final String[] enumValues;

        ParamDef(String name, Class<?> type, String description, boolean required, String[] enumValues) {
            this.name = name;
            this.type = type;
            this.description = description;
            this.required = required;
            this.enumValues = enumValues;
        }
    }
}
