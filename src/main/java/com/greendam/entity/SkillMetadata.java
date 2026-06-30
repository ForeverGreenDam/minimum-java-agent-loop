package com.greendam.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public class SkillMetadata {
    //===============必填字段===============
    /**
     * 技能名称
     *
     */
    private String name;
    /**
     * 技能描述
     *
     */
    private String description;

    //==============可选字段===============
    /**
     * 许可协议
     */
    private String license;
    /**
     * 技能元数据
     */
    private Map<String, String> metadata;


}
