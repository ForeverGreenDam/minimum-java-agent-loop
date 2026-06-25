package com.greendam.tools.tools;

import com.greendam.tools.annotation.Param;
import com.greendam.tools.annotation.Tool;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.stream.Collectors;

/**
 * 文件操作工具集 — 提供文件读写、目录列表、文件删除等基础文件系统操作.
 *
 * <p>所有路径均支持相对路径（相对于当前工作目录）和绝对路径.
 */
public class FileTools {

    /**
     * 读取文件内容并以字符串返回.
     */
    @Tool(name = "readFile", description = "读取指定路径的文件内容，以UTF-8编码返回文本字符串。支持相对路径和绝对路径。")
    public String readFile(
            @Param(name = "path", description = "文件路径，可以是相对路径或绝对路径") String path
    ) {
        try {
            Path filePath = Paths.get(path);
            if (!Files.exists(filePath)) {
                return "[ERROR] 文件不存在: " + filePath.toAbsolutePath();
            }
            if (!Files.isRegularFile(filePath)) {
                return "[ERROR] 路径不是文件: " + filePath.toAbsolutePath();
            }
            return Files.readString(filePath, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "[ERROR] 读取文件失败: " + e.getMessage();
        }
    }

    /**
     * 将字符串内容写入文件（覆盖或追加）.
     */
    @Tool(name = "writeFile", description = "将文本内容写入指定路径的文件。默认覆盖已有文件，设置append为true则在末尾追加。")
    public String writeFile(
            @Param(name = "path", description = "文件路径，可以是相对路径或绝对路径") String path,
            @Param(name = "content", description = "要写入文件的文本内容") String content,
            @Param(name = "append", description = "是否追加到文件末尾，默认false表示覆盖", required = false) Boolean append
    ) {
        try {
            Path filePath = Paths.get(path);
            // 确保父目录存在
            Path parent = filePath.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }
            boolean shouldAppend = append != null && append;
            if (shouldAppend) {
                Files.writeString(filePath, content, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } else {
                Files.writeString(filePath, content, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            }
            return "文件写入成功: " + filePath.toAbsolutePath() + " (" + content.length() + " 字符)";
        } catch (IOException e) {
            return "[ERROR] 写入文件失败: " + e.getMessage();
        }
    }

    /**
     * 列出目录下的所有文件和子目录.
     */
    @Tool(name = "listFiles", description = "列出指定目录下的所有文件和子目录名称。不递归子目录。")
    public String listFiles(
            @Param(name = "directoryPath", description = "目录路径，默认为当前工作目录", required = false) String directoryPath
    ) {
        try {
            Path dirPath = directoryPath != null && !directoryPath.isEmpty()
                    ? Paths.get(directoryPath)
                    : Paths.get("").toAbsolutePath();

            if (!Files.exists(dirPath)) {
                return "[ERROR] 目录不存在: " + dirPath.toAbsolutePath();
            }
            if (!Files.isDirectory(dirPath)) {
                return "[ERROR] 路径不是目录: " + dirPath.toAbsolutePath();
            }

            StringBuilder sb = new StringBuilder();
            sb.append("目录: ").append(dirPath.toAbsolutePath()).append("\n");
            sb.append("----------------------------------------\n");

            var entries = Files.list(dirPath).collect(Collectors.toList());
            if (entries.isEmpty()) {
                sb.append("(空目录)");
            } else {
                for (Path entry : entries) {
                    String type = Files.isDirectory(entry) ? "[DIR] " : "[FILE]";
                    String size = "";
                    if (Files.isRegularFile(entry)) {
                        try {
                            long bytes = Files.size(entry);
                            size = String.format("  (%d bytes)", bytes);
                        } catch (IOException ignored) {
                        }
                    }
                    sb.append(type).append(entry.getFileName().toString()).append(size).append("\n");
                }
            }
            sb.append("----------------------------------------\n");
            sb.append("共 ").append(entries.size()).append(" 项");
            return sb.toString();
        } catch (IOException e) {
            return "[ERROR] 列出目录失败: " + e.getMessage();
        }
    }

    /**
     * 删除指定路径的文件或空目录.
     */
    @Tool(name = "deleteFile", description = "删除指定路径的文件。只能删除文件或空目录，不能删除非空目录以确保安全。")
    public String deleteFile(
            @Param(name = "path", description = "要删除的文件路径") String path
    ) {
        try {
            Path filePath = Paths.get(path);
            if (!Files.exists(filePath)) {
                return "[ERROR] 文件不存在: " + filePath.toAbsolutePath();
            }
            if (Files.isDirectory(filePath)) {
                // 安全检查: 只删除空目录
                if (Files.list(filePath).findAny().isPresent()) {
                    return "[ERROR] 目录非空，出于安全考虑拒绝删除: " + filePath.toAbsolutePath();
                }
            }
            Files.delete(filePath);
            return "删除成功: " + filePath.toAbsolutePath();
        } catch (IOException e) {
            return "[ERROR] 删除失败: " + e.getMessage();
        }
    }
}
