package com.greendam.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 轻量级 YAML 配置加载器，模拟 Spring Boot 的 application.yml 行为.
 *
 * <h3>使用方式</h3>
 * <pre>{@code
 *   // 任意位置获取配置
 *   String model = ConfigLoader.get().getString("openai.model");
 *   int port    = ConfigLoader.get().getInt("server.port", 8080);
 * }</pre>
 *
 * <h3>Profile 优先级（由高到低）</h3>
 * <ol>
 *   <li>代码内 {@link #setProfile(String)}</li>
 *   <li>环境变量 CONFIG_PROFILE</li>
 *   <li>系统属性 config.profile</li>
 *   <li>默认 "default"（不加载额外文件）</li>
 * </ol>
 *
 * <h3>占位符</h3>
 * 支持 {@code ${key}} 语法引用其他配置项，例如：
 * <pre>
 *   app:
 *     name: my-app
 *     desc: "This is ${app.name}"
 * </pre>
 */
public final class ConfigLoader {

    // ==================== 静态单例 ====================

    private static final Object LOCK = new Object();
    private static volatile ConfigLoader INSTANCE;
    /**
     * 代码内指定的 profile；置空则回退到 env/property
     */
    private static volatile String PROGRAMMATIC_PROFILE;
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    private final Map<String, String> config = new ConcurrentHashMap<>();
    /**
     * 当前激活的 profile
     */
    private String activeProfile;

    // ==================== 实例字段 ====================

    /**
     * 在代码中直接指定 profile，最优先.
     *
     * <p>必须在首次调用 {@link #get()} 之前设置才能生效.
     *
     * <pre>{@code
     *   public static void main(String[] args) {
     *       ConfigLoader.setProfile("dev");
     *       // ... 后续所有 ConfigLoader.get() 都会加载 application-dev.yml
     *   }
     * }</pre>
     */
    public static void setProfile(String profile) {
        PROGRAMMATIC_PROFILE = profile;
        // 如果已经初始化过，立刻 reload
        if (INSTANCE != null) {
            reload();
        }
    }

    /**
     * 获取全局单例，首次调用时自动初始化.
     */
    public static ConfigLoader get() {
        if (INSTANCE == null) {
            synchronized (LOCK) {
                if (INSTANCE == null) {
                    INSTANCE = new ConfigLoader();
                    INSTANCE.init();
                }
            }
        }
        return INSTANCE;
    }

    /**
     * 强制重新加载（可用于热更新），保留 {@link #setProfile(String)} 指定的值.
     */
    public static void reload() {
        synchronized (LOCK) {
            INSTANCE = null;
        }
        get();
    }

    // ==================== 初始化 ====================

    private void init() {
        // 解析 profile：代码 > 环境变量 > 系统属性 > "default"
        String p = PROGRAMMATIC_PROFILE;
        if (p != null && !p.isBlank()) {
            activeProfile = p;
        } else {
            activeProfile = System.getenv("CONFIG_PROFILE");
        }
        if (activeProfile == null || activeProfile.isBlank()) {
            activeProfile = System.getProperty("config.profile", "default");
        }

        // 1) 加载基础配置
        Map<String, Object> merged = loadYaml("application.yml");

        // 2) 覆盖 profile 配置
        if (!"default".equals(activeProfile)) {
            String profileFile = "application-" + activeProfile + ".yml";
            Map<String, Object> profileMap = loadYaml(profileFile);
            if (profileMap != null) {
                merged = deepMerge(merged, profileMap);
            }
        }

        // 3) 扁平化为 dot.key=value 并解析占位符
        if (merged != null) {
            Map<String, String> flat = new LinkedHashMap<>();
            flatten("", merged, flat);
            // 解析 ${...} 占位符
            resolvePlaceholders(flat);
            this.config.putAll(flat);
        }
    }

    // ==================== 公开 API ====================

    /**
     * 当前激活的 profile 名称
     */
    public String activeProfile() {
        return activeProfile;
    }

    /**
     * 获取原始值（String 形态）
     */
    public String getString(String key) {
        return config.get(key);
    }

    /**
     * 获取 String，不存在时返回默认值
     */
    public String getString(String key, String defaultValue) {
        return config.getOrDefault(key, defaultValue);
    }

    /**
     * 获取 int
     */
    public int getInt(String key, int defaultValue) {
        String v = config.get(key);
        return v != null ? Integer.parseInt(v) : defaultValue;
    }

    /**
     * 获取 long
     */
    public long getLong(String key, long defaultValue) {
        String v = config.get(key);
        return v != null ? Long.parseLong(v) : defaultValue;
    }

    /**
     * 获取 double
     */
    public double getDouble(String key, double defaultValue) {
        String v = config.get(key);
        return v != null ? Double.parseDouble(v) : defaultValue;
    }

    /**
     * 获取 boolean
     */
    public boolean getBool(String key, boolean defaultValue) {
        String v = config.get(key);
        return v != null ? Boolean.parseBoolean(v) : defaultValue;
    }

    /**
     * 是否存在某个 key
     */
    public boolean containsKey(String key) {
        return config.containsKey(key);
    }

    /**
     * 返回所有配置项的不可变副本
     */
    public Map<String, String> asMap() {
        return Collections.unmodifiableMap(config);
    }

    /**
     * 配置项总数
     */
    public int size() {
        return config.size();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("ConfigLoader{profile=").append(activeProfile)
                .append(", keys=").append(config.size()).append("}\n");
        config.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> sb.append("  ").append(e.getKey()).append(" = ").append(e.getValue()).append("\n"));
        return sb.toString();
    }

    // ==================== 内部方法 ====================

    @SuppressWarnings("unchecked")
    private Map<String, Object> loadYaml(String resourcePath) {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) {
                return null;
            }
            return yamlMapper.readValue(in, Map.class);
        } catch (Exception e) {
            // 文件不存在时静默跳过
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> deepMerge(Map<String, Object> base, Map<String, Object> override) {
        Map<String, Object> result = new LinkedHashMap<>(base);
        for (Map.Entry<String, Object> entry : override.entrySet()) {
            String key = entry.getKey();
            Object ov = entry.getValue();
            Object bv = result.get(key);
            if (bv instanceof Map && ov instanceof Map) {
                result.put(key, deepMerge((Map<String, Object>) bv, (Map<String, Object>) ov));
            } else {
                result.put(key, ov);
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private void flatten(String prefix, Map<String, Object> source, Map<String, String> target) {
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            Object value = entry.getValue();
            if (value instanceof Map) {
                flatten(key, (Map<String, Object>) value, target);
            } else if (value instanceof List) {
                List<?> list = (List<?>) value;
                for (int i = 0; i < list.size(); i++) {
                    Object item = list.get(i);
                    if (item instanceof Map) {
                        flatten(key + "[" + i + "]", (Map<String, Object>) item, target);
                    } else {
                        target.put(key + "[" + i + "]", item == null ? "" : item.toString());
                    }
                }
            } else {
                target.put(key, value == null ? "" : value.toString());
            }
        }
    }

    /**
     * 解析 ${key} 占位符，引用其他配置项或环境变量/系统属性
     */
    private void resolvePlaceholders(Map<String, String> flat) {
        for (Map.Entry<String, String> entry : flat.entrySet()) {
            String value = entry.getValue();
            if (value != null && value.contains("${")) {
                entry.setValue(resolveValue(value, flat, new HashSet<>()));
            }
        }
    }

    private String resolveValue(String value, Map<String, String> source, Set<String> visited) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < value.length()) {
            int start = value.indexOf("${", i);
            if (start == -1) {
                sb.append(value.substring(i));
                break;
            }
            sb.append(value, i, start);
            int end = value.indexOf('}', start + 2);
            if (end == -1) {
                // 没闭合，原样保留
                sb.append(value.substring(start));
                break;
            }
            String refKey = value.substring(start + 2, end).trim();
            if (!visited.add(refKey)) {
                // 循环引用 — 原样保留占位符，不抛异常
                sb.append("${").append(refKey).append("}");
                visited.remove(refKey);
                i = end + 1;
                continue;
            }
            // 1) 先从配置 map 中查找
            String refValue = source.get(refKey);
            // 2) 找不到再查系统属性
            if (refValue == null) {
                refValue = System.getProperty(refKey);
            }
            // 3) 最后查环境变量
            if (refValue == null) {
                refValue = System.getenv(refKey);
            }
            if (refValue != null && refValue.contains("${")) {
                refValue = resolveValue(refValue, source, visited);
            }
            sb.append(refValue != null ? refValue : "");
            visited.remove(refKey);
            i = end + 1;
        }
        return sb.toString();
    }
}
