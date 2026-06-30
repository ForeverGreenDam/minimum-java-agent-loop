package com.greendam.skills;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.greendam.config.ConfigLoader;
import com.greendam.entity.Message;
import com.greendam.entity.SkillMetadata;
import com.greendam.memory.ShortMemory;
import lombok.NoArgsConstructor;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@NoArgsConstructor
public class SkillsManager {
    // 技能目录
    private static final String SKILL_DIR = ConfigLoader.get().getString("skill.dir", "skills");
    //预加载时缓存技能列表
    private static final Map<String, Map.Entry<SkillMetadata, String>> SKILLS = new HashMap<>();
    //YAML解析器
    private static final YAMLMapper YAML_MAPPER = new YAMLMapper();

    /**
     * 加载技能列表
     */
    public static void skillLoader() {
        //查找skill目录下的文件夹
        File skillDir = new File(SKILL_DIR);
        if (!skillDir.exists()) {
            skillDir.mkdirs();
        }
        for (File file : Objects.requireNonNull(skillDir.listFiles())) {
            if (!file.isDirectory()) {
                continue;
            }
            String fileName = file.getName();
            String path = file.getPath() + File.separator + "SKILL.md";
            //解析当前目录下的技能数据
            Map.Entry<SkillMetadata, String> skill = parseSkillMetadata(path);
            //放入到缓存中
            SKILLS.put(fileName, skill);
        }
    }

    /**
     * 解析技能元数据
     *
     * @param path 技能元数据路径
     * @return 技能元数据和技能内容的键值对
     */
    private static Map.Entry<SkillMetadata, String> parseSkillMetadata(String path) {
        try (BufferedReader bufferedReader = new BufferedReader(new FileReader(path))) {
            String line;
            StringBuilder sb = new StringBuilder();
            while ((line = bufferedReader.readLine()) != null) {
                sb.append(line).append(System.lineSeparator());
            }
            String content = sb.toString().trim();

            // 解析 YAML front matter（---分隔的元数据）
            String yamlContent = "";
            String skillContent = content;

            if (content.startsWith("---")) {
                int endIndex = content.indexOf("---", 3);
                if (endIndex != -1) {
                    yamlContent = content.substring(3, endIndex).trim();
                    skillContent = content.substring(endIndex + 3).trim();
                }
            }

            // 使用Jackson解析YAML元数据
            SkillMetadata metadata = YAML_MAPPER.readValue(yamlContent, SkillMetadata.class);
            return Map.entry(metadata, skillContent);

        } catch (IOException e) {
            System.out.println(e.getMessage());
            throw new RuntimeException(e);
        }
    }

    /**
     * 获取不可变的技能列表
     *
     * @return 技能列表
     */
    public static Map<String, Map.Entry<SkillMetadata, String>> getSkills() {
        return Map.copyOf(SKILLS);
    }

    /**
     * 注入技能元数据到提示词
     *
     * @return 技能元数据字符串
     */
    public static String injectSkillMetadata() {
        StringBuilder stringBuilder = new StringBuilder();
        SKILLS.forEach((name, entry) -> {
            String line = "name: " + name + "  description:" + entry.getKey().getDescription();
            stringBuilder.append(line).append("\n");
        });
        return stringBuilder.toString();
    }

    /**
     * 注入技能内容到系统提示词
     *
     * @param name 技能名称
     */
    public static void injectSkill(String name) {
        if (SKILLS.containsKey(name)) {
            //获取skill内容
            String content = SKILLS.get(name).getValue();
            //将skill内容注入到系统提示词
            //1. 获取到当前对话的系统提示词
            Message system = ShortMemory.getAll().getFirst();
            //2.往系统提示词语追加技能内容
            system.setContent(system.getContent() + "\n" + "当前已启用skill：" + name + "\n" + content);
            ShortMemory.setSystemMsg(system);
        } else {
            throw new RuntimeException(name + "技能不存在,请检查skill名称是否正确");
        }
    }
}
