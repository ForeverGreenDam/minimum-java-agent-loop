package com.greendam.tools.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注工具方法的参数，描述参数名、类型、是否必填.
 * <p>该信息会被用于自动生成 JSON Schema，大模型据此传参.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface Param {

    /**
     * 参数名（暴露给大模型），例如 "city"
     */
    String name();

    /**
     * 参数描述，大模型据此理解参数含义
     */
    String description() default "";

    /**
     * 是否必填，默认 true
     */
    boolean required() default true;

    /**
     * 枚举值列表，约束大模型只能从中选择
     */
    String[] enumValues() default {};
}
