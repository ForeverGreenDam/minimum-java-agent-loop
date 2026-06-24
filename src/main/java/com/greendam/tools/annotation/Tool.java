package com.greendam.tools.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记一个方法为大模型可调用的工具.
 *
 * <pre>{@code
 * @Tool(name = "get_weather", description = "获取指定城市的当前天气")
 * public String getWeather(
 *     @Param(name = "city", description = "城市名称", required = true) String city,
 *     @Param(name = "unit", description = "温度单位", enumValues = {"celsius", "fahrenheit"}) String unit
 * ) {
 *     return "北京当前温度 25°C";
 * }
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Tool {

    /**
     * 函数名（暴露给大模型），默认取方法名
     */
    String name() default "";

    /**
     * 函数描述（大模型据此判断何时调用）
     */
    String description() default "";
}
