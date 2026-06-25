package com.greendam.tools.tools;

import com.greendam.tools.annotation.Param;
import com.greendam.tools.annotation.Tool;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * Shell 命令执行工具 — 允许 Agent 执行系统命令.
 *
 * <p><b>安全警告:</b> 该工具可执行任意系统命令，调用前应仔细检查命令的合法性.
 * 命令执行有超时限制，默认 60 秒.
 */
public class ShellTools {

    private static final long DEFAULT_TIMEOUT_SECONDS = 60;

    /**
     * 执行操作系统命令并返回标准输出和标准错误.
     */
    @Tool(name = "executeShell", description = "执行一条Shell命令并返回标准输出和标准错误。命令默认60秒超时，避免长时间阻塞。用于执行系统操作如查看文件、运行脚本等。")
    public String executeShell(
            @Param(name = "command", description = "要执行的Shell命令。在Windows上使用cmd.exe语法，在Linux/Mac上使用bash语法") String command,
            @Param(name = "workingDir", description = "命令执行的工作目录，默认为当前工作目录", required = false) String workingDir
    ) {
        try {
            ProcessBuilder pb = new ProcessBuilder();

            // 根据操作系统设置 shell
            String osName = System.getProperty("os.name").toLowerCase();
            if (osName.contains("win")) {
                pb.command("cmd.exe", "/c", command);
            } else {
                pb.command("sh", "-c", command);
            }

            // 设置工作目录
            if (workingDir != null && !workingDir.trim().isEmpty()) {
                File dir = new File(workingDir);
                if (dir.exists() && dir.isDirectory()) {
                    pb.directory(dir);
                } else {
                    return "[ERROR] 工作目录不存在: " + workingDir;
                }
            }

            pb.redirectErrorStream(false);
            Process process = pb.start();

            // 读取 stdout
            StringBuilder stdout = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (stdout.length() > 0) stdout.append("\n");
                    stdout.append(line);
                }
            }

            // 读取 stderr
            StringBuilder stderr = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (stderr.length() > 0) stderr.append("\n");
                    stderr.append(line);
                }
            }

            // 等待进程完成（带超时）
            boolean finished = process.waitFor(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return "[ERROR] 命令执行超时 (" + DEFAULT_TIMEOUT_SECONDS + "s): " + command;
            }

            int exitCode = process.exitValue();

            // 格式化结果
            StringBuilder result = new StringBuilder();
            result.append("退出码: ").append(exitCode).append("\n");

            if (!stdout.isEmpty()) {
                // 截断过长的输出
                String outStr = stdout.toString();
                if (outStr.length() > 8000) {
                    outStr = outStr.substring(0, 8000) + "\n... (stdout 过长，已截断至8000字符)";
                }
                result.append("--- stdout ---\n").append(outStr).append("\n");
            }
            if (!stderr.isEmpty()) {
                String errStr = stderr.toString();
                if (errStr.length() > 8000) {
                    errStr = errStr.substring(0, 8000) + "\n... (stderr 过长，已截断至8000字符)";
                }
                result.append("--- stderr ---\n").append(errStr).append("\n");
            }
            if (stdout.isEmpty() && stderr.isEmpty()) {
                result.append("(无输出)\n");
            }

            return result.toString().trim();
        } catch (Exception e) {
            return "[ERROR] 命令执行失败: " + e.getMessage();
        }
    }
}
