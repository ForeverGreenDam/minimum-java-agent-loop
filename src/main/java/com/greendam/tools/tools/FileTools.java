package com.greendam.tools.tools;

import com.greendam.tools.annotation.Param;
import com.greendam.tools.annotation.Tool;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 文件操作工具集 — 提供文件读写、目录列表、文件删除、文件搜索、内容搜索等文件系统操作.
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
     * 在文件中查找并替换文本，直接保存到原文件.
     *
     * <p>读取指定文件 → 查找替换文本 → 写回原文件，一步完成。
     * 支持普通文本替换和正则表达式替换。
     */
    @Tool(name = "replaceInFile", description = "在文件中查找并替换指定字符串，直接保存到原文件。支持普通替换和正则表达式替换。相当于 readFile + textReplace + writeFile 的一步整合，避免手动三步调用的麻烦。")
    public String replaceInFile(
            @Param(name = "path", description = "要修改的文件路径") String path,
            @Param(name = "search", description = "要查找的字符串或正则表达式") String search,
            @Param(name = "replace", description = "替换为的字符串") String replace,
            @Param(name = "useRegex", description = "是否将search作为正则表达式解析，默认false为普通文本替换", required = false) Boolean useRegex
    ) {
        if (search == null || search.isEmpty()) {
            return "[ERROR] search 不能为空";
        }
        if (replace == null) {
            replace = "";
        }
        try {
            Path filePath = Paths.get(path);
            if (!Files.exists(filePath)) {
                return "[ERROR] 文件不存在: " + filePath.toAbsolutePath();
            }
            if (!Files.isRegularFile(filePath)) {
                return "[ERROR] 路径不是文件: " + filePath.toAbsolutePath();
            }

            // 读取原文件
            String original = Files.readString(filePath, StandardCharsets.UTF_8);

            // 执行替换
            boolean regex = useRegex != null && useRegex;
            String replaced;
            if (regex) {
                replaced = original.replaceAll(search, replace);
            } else {
                replaced = original.replace(search, replace);
            }

            // 如果没有变化，直接返回
            if (replaced.equals(original)) {
                return "文件未发生变化（未找到匹配内容）: " + filePath.toAbsolutePath();
            }

            // 写回原文件
            Files.writeString(filePath, replaced, StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING);

            int diffChars = replaced.length() - original.length();
            String diffSign = diffChars >= 0 ? "+" : "";
            return "文件替换成功: " + filePath.toAbsolutePath() + "\n"
                    + "替换前: " + original.length() + " 字符, 替换后: " + replaced.length() + " 字符 (" + diffSign + diffChars + ")\n"
                    + "注: 如果需要查看修改后的内容，请使用 readFile 工具读取该文件。";
        } catch (IOException e) {
            return "[ERROR] 文件替换失败: " + e.getMessage();
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

    /**
     * 按文件名模式搜索文件（支持通配符匹配）。
     *
     * <p>搜索指定目录下匹配文件名模式的文件，支持递归搜索。
     * 文件名模式支持 glob 语法，例如 {@code "*.java"}、{@code "*.{java,xml}"}、{@code "README*"} 等。
     */
    @Tool(name = "searchFiles", description = "按文件名模式搜索文件，支持通配符。例如 pattern=\"*.java\" 搜索所有Java文件，pattern=\"*test*\" 搜索文件名包含test的文件。支持glob语法如 \"*.{java,xml,txt}\"")
    public String searchFiles(
            @Param(name = "pattern", description = "文件名搜索模式，支持glob通配符。例如：\"*.java\"、\"*test*\"、\"README*\"、\"*.{java,xml}\"") String pattern,
            @Param(name = "directoryPath", description = "搜索的根目录路径，默认为当前工作目录", required = false) String directoryPath,
            @Param(name = "recursive", description = "是否递归搜索子目录，默认true", required = false) Boolean recursive,
            @Param(name = "maxResults", description = "最大返回结果数，默认100", required = false) Integer maxResults
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

            boolean isRecursive = recursive == null || recursive;
            int max = maxResults != null && maxResults > 0 ? maxResults : 100;

            // 将glob模式转换为PathMatcher
            String globPattern = "glob:" + pattern;
            PathMatcher matcher = FileSystems.getDefault().getPathMatcher(globPattern);

            List<Path> results = new ArrayList<>();

            if (isRecursive) {
                // 递归搜索：使用Files.walk遍历目录树
                try (Stream<Path> stream = Files.walk(dirPath)) {
                    List<Path> matched = stream
                            .filter(Files::isRegularFile)
                            .filter(path -> {
                                // 对相对路径应用匹配（相对于搜索根目录）
                                Path relativePath = dirPath.relativize(path);
                                return matcher.matches(relativePath) || matcher.matches(path.getFileName());
                            })
                            .limit(max + 1L)
                            .collect(Collectors.toList());
                    results.addAll(matched);
                }
            } else {
                // 非递归搜索：只搜索当前目录
                try (Stream<Path> stream = Files.list(dirPath)) {
                    List<Path> matched = stream
                            .filter(Files::isRegularFile)
                            .filter(path -> matcher.matches(path.getFileName()))
                            .limit(max + 1L)
                            .collect(Collectors.toList());
                    results.addAll(matched);
                }
            }

            if (results.isEmpty()) {
                return "未找到匹配 '" + pattern + "' 的文件（搜索目录: " + dirPath.toAbsolutePath() + "）";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("搜索模式: ").append(pattern).append("\n");
            sb.append("搜索目录: ").append(dirPath.toAbsolutePath()).append("\n");
            sb.append(isRecursive ? "(递归搜索)" : "(非递归搜索)").append("\n");
            sb.append("----------------------------------------\n");

            boolean truncated = results.size() > max;
            List<Path> displayResults = truncated ? results.subList(0, max) : results;

            for (Path path : displayResults) {
                try {
                    long bytes = Files.size(path);
                    String sizeStr = formatFileSize(bytes);
                    sb.append(path.toAbsolutePath()).append("  (").append(sizeStr).append(")\n");
                } catch (IOException e) {
                    sb.append(path.toAbsolutePath()).append("\n");
                }
            }

            sb.append("----------------------------------------\n");
            sb.append("共找到 ").append(displayResults.size()).append(" 个文件");
            if (truncated) {
                sb.append("（已截断，仅显示前 ").append(max).append(" 个结果）");
            }
            return sb.toString();

        } catch (IOException e) {
            return "[ERROR] 搜索文件失败: " + e.getMessage();
        }
    }

    /**
     * 在文件中搜索指定关键词/文本（类似 grep -r 功能）。
     *
     * <p>在指定目录的所有文件中搜索包含指定文本的行，返回匹配的文件路径、行号和内容片段。
     * 支持普通文本搜索和正则表达式搜索。
     */
    @Tool(name = "grepFiles", description = "在文件内容中搜索指定关键词或正则表达式，返回匹配的文件路径、行号和内容片段（类似 grep -r 功能）。支持普通文本搜索和正则表达式搜索。")
    public String grepFiles(
            @Param(name = "pattern", description = "要搜索的文本或正则表达式模式") String pattern,
            @Param(name = "directoryPath", description = "搜索的根目录路径，默认为当前工作目录", required = false) String directoryPath,
            @Param(name = "filePattern", description = "文件名的glob过滤模式，仅搜索匹配此模式的文件，例如\"*.java\"只搜索Java文件，默认搜索所有文件(*)", required = false) String filePattern,
            @Param(name = "useRegex", description = "是否将pattern作为正则表达式解析，默认false表示普通文本搜索", required = false) Boolean useRegex,
            @Param(name = "maxResults", description = "最大返回匹配行数（不含上下文显示行），默认50", required = false) Integer maxResults,
            @Param(name = "contextLines", description = "匹配行前后各显示多少行上下文，默认0", required = false) Integer contextLines
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

            boolean isRegex = useRegex != null && useRegex;
            int max = maxResults != null && maxResults > 0 ? maxResults : 50;
            int context = contextLines != null && contextLines > 0 ? contextLines : 0;

            // 文件过滤PathMatcher
            String fileGlob = (filePattern != null && !filePattern.isEmpty()) ? "glob:" + filePattern : "glob:*";
            PathMatcher fileMatcher = FileSystems.getDefault().getPathMatcher(fileGlob);

            // 预编译正则（如果是正则模式）
            java.util.regex.Pattern regexPattern = null;
            if (isRegex) {
                try {
                    regexPattern = java.util.regex.Pattern.compile(pattern);
                } catch (java.util.regex.PatternSyntaxException e) {
                    return "[ERROR] 正则表达式语法错误: " + e.getMessage();
                }
            }

            List<String> matchResults = new ArrayList<>();
            int totalFilesSearched = 0;
            int totalFilesMatched = 0;
            int totalMatchedLines = 0;
            int displayedMatchedLines = 0;
            boolean truncated = false;

            // 递归遍历目录
            try (Stream<Path> stream = Files.walk(dirPath)) {
                List<Path> files = stream
                        .filter(Files::isRegularFile)
                        .filter(path -> {
                            Path relativePath = dirPath.relativize(path);
                            return fileMatcher.matches(relativePath) || fileMatcher.matches(path.getFileName());
                        })
                        .collect(Collectors.toList());

                for (Path filePath : files) {
                    totalFilesSearched++;
                    if (truncated) break;

                    try {
                        List<String> allLines = Files.readAllLines(filePath, StandardCharsets.UTF_8);
                        List<Integer> matchedLines = new ArrayList<>();

                        for (int i = 0; i < allLines.size(); i++) {
                            String line = allLines.get(i);
                            boolean found;
                            if (isRegex) {
                                found = regexPattern.matcher(line).find();
                            } else {
                                found = line.contains(pattern);
                            }
                            if (found) {
                                matchedLines.add(i);
                            }
                        }

                        if (!matchedLines.isEmpty()) {
                            totalFilesMatched++;
                            totalMatchedLines += matchedLines.size();
                            // 将匹配行（含上下文）加入结果
                            boolean firstInFile = true;
                            for (int matchedLineIdx : matchedLines) {
                                if (displayedMatchedLines >= max) {
                                    truncated = true;
                                    break;
                                }

                                if (firstInFile) {
                                    matchResults.add("");
                                    matchResults.add("文件: " + filePath.toAbsolutePath());
                                    firstInFile = false;
                                }

                                int startLine = Math.max(0, matchedLineIdx - context);
                                int endLine = Math.min(allLines.size() - 1, matchedLineIdx + context);

                                for (int lineNum = startLine; lineNum <= endLine; lineNum++) {
                                    String lineContent = allLines.get(lineNum);
                                    // 截断过长行
                                    if (lineContent.length() > 500) {
                                        lineContent = lineContent.substring(0, 500) + "... [截断]";
                                    }
                                    String lineNumber = String.format("  %5d", lineNum + 1);
                                    String marker = (lineNum == matchedLineIdx) ? " >" : "  ";
                                    String resultLine = lineNumber + marker + " " + lineContent;
                                    // 检查结果是否已存在（避免context重叠导致的重复行）
                                    if (!matchResults.contains(resultLine)) {
                                        matchResults.add(resultLine);
                                    }
                                }
                                displayedMatchedLines++;
                            }
                        }
                    } catch (IOException e) {
                        // 跳过无法读取的文件（如二进制文件）
                    }
                }
            }

            if (matchResults.isEmpty()) {
                return "在 " + totalFilesSearched + " 个文件中未找到匹配 '" + pattern + "' 的内容"
                        + (filePattern != null && !filePattern.isEmpty() ? "（文件过滤: " + filePattern + "）" : "");
            }

            StringBuilder sb = new StringBuilder();
            sb.append("搜索模式: ").append(pattern);
            if (isRegex) sb.append(" (正则表达式)");
            sb.append("\n");
            sb.append("搜索目录: ").append(dirPath.toAbsolutePath()).append("\n");
            if (filePattern != null && !filePattern.isEmpty()) {
                sb.append("文件过滤: ").append(filePattern).append("\n");
            }
            sb.append("搜索文件: ").append(totalFilesSearched).append(" 个, 匹配文件: ").append(totalFilesMatched).append(" 个\n");
            sb.append("----------------------------------------\n");

            for (String resultLine : matchResults) {
                sb.append(resultLine).append("\n");
            }

            sb.append("----------------------------------------\n");
            sb.append("共找到 ").append(totalMatchedLines).append(" 个匹配行");
            if (context > 0) {
                sb.append("（上下文显示行不计入匹配行总数）");
            }
            if (truncated) {
                sb.append("（已截断，仅显示前 ").append(max).append(" 个匹配行，可缩小范围重试）");
            }
            return sb.toString();

        } catch (IOException e) {
            return "[ERROR] 搜索文件内容失败: " + e.getMessage();
        }
    }

    /**
     * 格式化文件大小为人类可读的字符串.
     */
    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
