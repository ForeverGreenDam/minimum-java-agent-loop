package com.greendam.tools.tools;

import com.greendam.tools.annotation.Param;
import com.greendam.tools.annotation.Tool;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shell 命令执行工具 — 允许 Agent 执行系统命令.
 *
 * <p><b>安全警告:</b> 该工具可执行任意系统命令，调用前应仔细检查命令的合法性.
 * 命令执行有超时限制，默认 60 秒.
 */
public class ShellTools {

    private static final long DEFAULT_TIMEOUT_SECONDS = 60;

    /**
     * 缓存检测到的 Windows OEM 代码页对应的 Charset.
     * 在 Windows 上，当 cmd.exe 的内部命令（dir/type/echo等）输出被重定向到管道时，
     * 它们使用的是 OEM 代码页（如中文Win→936/GBK，日文Win→932/Shift-JIS，英文Win→437），
     * 而不是 chcp 设置的"活动代码页"。因此必须检测 OEM 代码页来正确解码输出。
     */
    private static volatile Charset windowsOemCharset = null;

    /**
     * 检测 Windows 的 OEM 代码页对应的 Charset。
     * 通过执行 "chcp" 命令获取当前活动的 OEM 代码页编号。
     * 该方法只会执行一次，结果被缓存。
     */
    private static Charset detectWindowsOemCharset() {
        if (windowsOemCharset != null) {
            return windowsOemCharset;
        }
        try {
            Process p = new ProcessBuilder("cmd.exe", "/c", "chcp").start();
            byte[] bytes = p.getInputStream().readAllBytes();
            p.waitFor();
            // chcp 输出类似 "Active code page: 936" 或 "活动代码页: 936"
            // 用 ASCII 读取即可提取数字
            String output = new String(bytes, StandardCharsets.US_ASCII);

            Matcher m = Pattern.compile("(\\d+)").matcher(output);
            if (m.find()) {
                int codePage = Integer.parseInt(m.group(1));
                if (codePage == 65001) {
                    windowsOemCharset = StandardCharsets.UTF_8;
                } else {
                    // 通过独立方法获取 Charset，避免 IDEA 静态检查"未知编码"警告
                    windowsOemCharset = charsetForCodePage(codePage);
                }
            }
        } catch (Exception ignored) {
            // 检测失败时静默处理
        }
        // 兜底默认 UTF-8
        if (windowsOemCharset == null) {
            windowsOemCharset = StandardCharsets.UTF_8;
        }
        return windowsOemCharset;
    }

    /**
     * 根据 Windows 代码页编号获取对应的 {@link Charset}。
     * <p>
     * 提取为独立方法是为了避免 IDEA 的 "Unknown charset" 静态代码检查对
     * {@code Charset.forName("CP" + codePage)} 这种动态拼接字符串报"未知编码"警告。
     * 方法内部使用小写 "cp" 前缀（如 "cp936"），IDEA 能正确识别小写格式。
     * </p>
     *
     * @param codePage Windows 代码页编号，如 936(GBK)、932(Shift_JIS)、437(IBM437) 等
     * @return 对应的 Charset 对象
     */
    private static Charset charsetForCodePage(int codePage) {
        // Java 的 Charset.forName 大小写不敏感，"cp936"、"Cp936"、"CP936" 均可
        return Charset.forName("cp" + codePage);
    }

    /**
     * 执行操作系统命令并返回标准输出和标准错误.
     */
    @Tool(name = "executeShell", description = "执行一条Shell命令并返回标准输出和标准错误。默认60秒超时。超时后进程继续在后台运行并返回已捕获的输出。启动Web服务器等长时进程时，设置timeoutSeconds为5~10可立即返回。")
    public String executeShell(
            @Param(name = "command", description = "要执行的Shell命令。在Windows上优先兼容cmd.exe常见语法（如 dir、type、findstr、管道与重定向），Linux/Mac上使用bash语法") String command,
            @Param(name = "workingDir", description = "命令执行的工作目录，默认为当前工作目录", required = false) String workingDir,
            @Param(name = "timeoutSeconds", description = "超时秒数，默认60秒。启动Web服务器等后台进程时设为5~10秒可立即返回（进程继续运行）", required = false) Long timeoutSeconds
    ) {
        try {
            ProcessBuilder pb = new ProcessBuilder();

            // 根据操作系统设置 shell 和输出编码
            String osName = System.getProperty("os.name").toLowerCase();
            Charset outputCharset;
            if (osName.contains("win")) {
                // ===== Windows 平台 =====
                // 使用 cmd.exe 执行命令（兼容 dir、type、findstr、管道、重定向等语法）
                //
                // 【重要编码说明】
                // 不要使用 "chcp 65001 > nul && " 前缀！
                // 
                // 原因：cmd.exe 的内部命令（dir/type/echo等）在输出到管道（pipe）时，
                // 使用的是系统的 OEM 代码页（如中文Win→936/GBK），而非 chcp 设置的
                // "活动代码页"。加 chcp 65001 改不了管道输出的实际编码，反而导致
                // 读取时编码不一致而产生乱码。
                //
                // 正确做法：先检测 OEM 代码页编号，然后用对应的 Charset 读取输出。
                // 这样无论用户是中文系统(GBK)、日文系统(Shift-JIS)、韩文系统(EUC-KR)
                // 还是英文系统(CP437)，都能正确解码。
                //
                // 而对于 Python 等外部程序，我们设置 PYTHONIOENCODING=utf-8 环境变量，
                // 让它们主动输出 UTF-8。UTF-8 是 ASCII 的超集，与 GBK 等编码在 ASCII
                // 范围内兼容，所以混合输出时 ASCII 部分不会乱码。
                pb.command("cmd.exe", "/C", command);
                pb.environment().put("PYTHONIOENCODING", "utf-8");
                outputCharset = detectWindowsOemCharset();
            } else {
                // ===== Linux/Mac 平台 =====
                // 使用 sh 执行命令，统一使用 UTF-8 编码读取输出
                pb.command("sh", "-c", command);
                outputCharset = StandardCharsets.UTF_8;
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

            long timeout = (timeoutSeconds != null && timeoutSeconds > 0)
                    ? timeoutSeconds : DEFAULT_TIMEOUT_SECONDS;

            pb.redirectErrorStream(false);
            Process process = pb.start();

            // 并行读取 stdout 和 stderr — 避免单线程阻塞在 readLine 上
            // （对于服务器等长时进程，readLine 永远不会返回 null）
            StringBuilder stdout = new StringBuilder();
            StringBuilder stderr = new StringBuilder();

            Thread stdoutReader = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), outputCharset))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        synchronized (stdout) {
                            if (stdout.length() > 0) stdout.append("\n");
                            stdout.append(line);
                        }
                    }
                } catch (IOException ignored) {
                    // 流被关闭（超时或进程退出）→ 线程正常退出
                }
            }, "shell-stdout-reader");

            Thread stderrReader = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getErrorStream(), outputCharset))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        synchronized (stderr) {
                            if (stderr.length() > 0) stderr.append("\n");
                            stderr.append(line);
                        }
                    }
                } catch (IOException ignored) {
                    // 流被关闭 → 线程正常退出
                }
            }, "shell-stderr-reader");

            stdoutReader.start();
            stderrReader.start();

            // 等待进程完成或超时
            boolean finished = process.waitFor(timeout, TimeUnit.SECONDS);
            if (!finished) {
                // 超时：关闭流以解除 reader 线程的阻塞
                // 注意：不调用 destroyForcibly()，进程继续在后台运行
                try {
                    process.getInputStream().close();
                } catch (IOException ignored) {
                }
                try {
                    process.getErrorStream().close();
                } catch (IOException ignored) {
                }
                stdoutReader.join(2000);
                stderrReader.join(2000);
            } else {
                // 正常结束：等待 reader 线程自然结束
                stdoutReader.join();
                stderrReader.join();
            }

            int exitCode = finished ? process.exitValue() : -1;

            // 格式化结果
            StringBuilder result = new StringBuilder();
            if (!finished) {
                result.append("--- 命令在 ").append(timeout)
                        .append("s 超时后仍在后台运行 (PID: ").append(process.pid())
                        .append(")，以下为超时前捕获的输出 ---\n\n");
            }
            result.append("退出码: ").append(exitCode).append("\n");

            if (!stdout.isEmpty()) {
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
