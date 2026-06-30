package com.greendam.tools.tools;

import com.greendam.skills.SkillsManager;
import com.greendam.tools.annotation.Param;
import com.greendam.tools.annotation.Tool;

public class SkillEnable {


    @Tool(description = "决定启用某个skill，当你认为需要启用某个skill时，请调用这个方法，该方法会将对应的skill自动注入系统提示词")
    public String enableSkill(@Param(name = "name", description = "skill名字") String name) {
        try {
            SkillsManager.injectSkill(name);
            return name + " skill注入成功！";
        } catch (RuntimeException e) {
            return name + " skill注入失败" + e.getMessage();
        }
    }
}
