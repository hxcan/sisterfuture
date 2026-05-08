// com.stupidbeauty.sisterfuture.tool.log.GitHubLogFilter.java
package com.stupidbeauty.sisterfuture.tool.log;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * GitHub Actions 日志过滤器 - 智能上下文提取版
 *
 * @author 太极美术工程狮狮长
 * @version 2.0.0 (重构：增加智能上下文提取逻辑，基于实际项目失败日志优化关键词)
 * @version 1.0.0 (初始版本，从 GetGitHubActionsLogsTool 抽取)
 */
public class GitHubLogFilter {

    // ==================== 错误关键词配置 ====================
    
    /**
     * 通用错误关键词列表
     * 来源：实际项目（sisterfuture）PR #397 失败日志分析 + 跨语言通用模式
     * 原则：尽量覆盖常见错误类型，避免过度假设特定语言/框架
     */
    private static final String[] ERROR_KEYWORDS = {
        "##[error]",           // GitHub Actions 原生标记
        "ERROR:",              // 通用错误前缀
        "error:",              // 小写变体
        "FAILED",              // 任务/步骤失败
        "Exception",           // Java/Kotlin 异常
        "Error",               // Java/Kotlin 错误类
        "panic",               // Go 崩溃
        "fatal",               // 严重错误
        "Traceback",           // Python 堆栈
        "exit code",           // Shell 非零退出
        "cannot find symbol",  // 编译/链接错误
        "failed to resolve",   // 依赖解析失败
        "unresolved reference",// Kotlin/Java 引用错误
        "undefined",           // 未定义变量/函数
        "syntax error",        // 语法错误
        "mismatched input",    // 解析错误 (ANTLR等)
        "stack overflow",      // 栈溢出
        "out of memory",       // 内存溢出
        "timeout",             // 超时
        "connection refused",  // 网络错误
        "permission denied",   // 权限错误
        "not found",           // 文件/资源未找到
        "null pointer",        // 空指针
        "assertion failed"     // 断言失败
    };

    /**
     * 预编译的正则表达式（用于快速匹配关键词）
     */
    private static final Pattern ERROR_PATTERN = buildErrorPattern();

    /**
     * 上下文窗口大小配置
     */
    private static final int PRE_WINDOW = 3;   // 错误行前抓取行数
    private static final int POST_WINDOW = 10; // 错误行后抓取行数

    // ==================== 静态初始化 ====================

    private static Pattern buildErrorPattern() {
        StringBuilder sb = new StringBuilder();
        sb.append("(");
        for (int i = 0; i < ERROR_KEYWORDS.length; i++) {
            if (i > 0) sb.append("|");
            sb.append(Pattern.quote(ERROR_KEYWORDS[i]));
        }
        sb.append(")");
        return Pattern.compile(sb.toString(), Pattern.CASE_INSENSITIVE);
    }

    // ==================== Runner 操作过滤（保留原有逻辑） ====================

    // Runner 环境操作日志过滤正则表达式模式
    private static final Pattern[] RUNNER_OP_PATTERNS = {
        Pattern.compile("^Post job cleanup"),
        Pattern.compile("^Cleaning up orphan processes"),
        Pattern.compile("^Terminate orphan process"),
        Pattern.compile("^[\\s]*\\[command\\]\\/usr\\/bin\\/git (version|config|init|remote|fetch|checkout|log|branch|status)"),
        Pattern.compile("^Temporarily overriding HOME"),
        Pattern.compile("^Adding repository directory.*safe\\.directory"),
        Pattern.compile("^\\[\\d{2};\\d{2}m.*\\[0m"),
        Pattern.compile("^#{4}\\[group\\]Post job cleanup"),
        Pattern.compile("^#{4}\\[endgroup\\]")
    };

    /**
     * 判断是否是 Runner 环境操作日志行
     * @param line 日志行
     * @return 是否是 Runner 环境操作日志
     */
    private boolean isRunnerOpLine(String line) {
        for (Pattern p : RUNNER_OP_PATTERNS) {
            if (p.matcher(line).find()) {
                return true;
            }
        }
        return false;
    }

    // ==================== 公共方法 ====================

    /**
     * 判断某行是否为错误行
     * @param line 日志行
     * @return 是否为错误行
     */
    public boolean isErrorLine(String line) {
        if (line == null || line.isEmpty()) {
            return false;
        }
        return ERROR_PATTERN.matcher(line).find();
    }

    /**
     * 智能提取错误块（带上下文）
     * 扫描整个日志，找出所有错误行，并为每个错误行提取前后 N 行作为上下文。
     * 自动合并重叠的错误块。
     *
     * @param logs 原始日志
     * @return 所有错误块的列表（每个块是一个字符串）
     */
    public List<String> extractErrorBlocksWithContext(String logs) {
        List<String> blocks = new ArrayList<>();
        if (logs == null || logs.isEmpty()) {
            return blocks;
        }

        String[] lines = logs.split("\n");
        int totalLines = lines.length;
        
        // 记录已处理的行范围，避免重复
        int lastEndIndex = -1;

        for (int i = 0; i < totalLines; i++) {
            if (isErrorLine(lines[i])) {
                // 计算当前错误块的起始和结束索引
                int startIndex = Math.max(0, i - PRE_WINDOW);
                int endIndex = Math.min(totalLines - 1, i + POST_WINDOW);

                // 检查是否与上一个块重叠
                if (startIndex <= lastEndIndex) {
                    // 重叠，跳过当前块
                    continue;
                }

                // 提取块内容
                StringBuilder block = new StringBuilder();
                for (int j = startIndex; j <= endIndex; j++) {
                    block.append(lines[j]).append("\n");
                }
                
                blocks.add(block.toString());
                lastEndIndex = endIndex;
            }
        }

        return blocks;
    }

    /**
     * 提取单个错误块（带上下文）
     * 用于只关心第一个错误或特定错误的场景
     *
     * @param logs 原始日志
     * @return 第一个错误块，如果没有则返回 null
     */
    public String extractFirstErrorBlockWithContext(String logs) {
        List<String> blocks = extractErrorBlocksWithContext(logs);
        return blocks.isEmpty() ? null : blocks.get(0);
    }

    /**
     * 从日志中提取指定步骤的错误信息（带上下文）
     *
     * @param logs 原始日志
     * @param stepName 步骤名称（用于定位该步骤的日志范围）
     * @return 该步骤的错误信息块，如果没有则返回 null
     */
    public String extractErrorMessageForStepWithContext(String logs, String stepName) {
        // 简单的启发式：查找步骤开始标记，然后在该范围内搜索错误
        String[] lines = logs.split("\n");
        int stepStart = -1;

        // 查找步骤开始（例如：##[group]Run steps/name or ##[command]steps/name）
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].contains("##[group]") && lines[i].contains(stepName)) {
                stepStart = i;
                break;
            }
            // 也可以查找命令执行行
            if (lines[i].contains("[command]") && lines[i].contains(stepName)) {
                stepStart = i;
                break;
            }
        }

        if (stepStart == -1) {
            // 如果找不到明确步骤标记，尝试在整篇日志中搜索包含步骤名的错误
            return extractFirstErrorBlockWithContext(logs);
        }

        // 截取该步骤的日志范围
        StringBuilder stepLogs = new StringBuilder();
        for (int i = stepStart; i < lines.length; i++) {
            // 遇到下一个步骤开始标记即停止
            if (i > stepStart && lines[i].startsWith("##[group]")) {
                break;
            }
            stepLogs.append(lines[i]).append("\n");
        }

        return extractFirstErrorBlockWithContext(stepLogs.toString());
    }

    // ==================== 原有兼容方法（保持向后兼容） ====================

    /**
     * 过滤 Runner 环境操作日志行
     * @param logs 原始日志
     * @return 过滤后的日志
     */
    public String filterRunnerOpLines(String logs) {
        StringBuilder filtered = new StringBuilder();
        String[] lines = logs.split("\n");
        for (String line : lines) {
            if (!isRunnerOpLine(line)) {
                filtered.append(line).append("\n");
            }
        }
        return filtered.toString();
    }

    /**
     * 过滤警告行
     * @param logs 原始日志
     * @return 过滤后的日志
     */
    public String filterWarningLines(String logs) {
        StringBuilder filtered = new StringBuilder();
        String[] lines = logs.split("\n");
        for (String line : lines) {
            String trimmedLine = line.trim().toLowerCase();
            if (!trimmedLine.contains("warning:")) {
                filtered.append(line).append("\n");
            }
        }
        return filtered.toString();
    }

    /**
     * 过滤只返回错误行（旧版简单逻辑，保留以兼容旧调用）
     * @param logs 原始日志
     * @return 过滤后的日志
     */
    public String filterErrorLines(String logs) {
        StringBuilder filtered = new StringBuilder();
        String[] lines = logs.split("\n");
        for (String line : lines) {
            if (line.contains("##[error]") || line.contains("ERROR") || 
                line.contains("failed") || line.contains("exception")) {
                filtered.append(line).append("\n");
            }
        }
        return filtered.length() > 0 ? filtered.toString() : "✅ 未发现明显错误";
    }

    /**
     * 从日志中提取错误信息（旧版简单逻辑，保留以兼容旧调用）
     * @param logs 原始日志
     * @param stepName 步骤名称
     * @return 错误信息
     */
    public String extractErrorMessageForStep(String logs, String stepName) {
        String[] lines = logs.split("\n");
        StringBuilder errors = new StringBuilder();
        boolean inErrorSection = false;
        for (String line : lines) {
            if (line.contains("##[error]")) {
                inErrorSection = true;
                errors.append(line.substring(line.indexOf("##[error]"))).append("\n");
            } else if (inErrorSection) {
                if (line.startsWith("20") && line.contains("Z ")) {
                    break;
                }
                errors.append(line).append("\n");
            }
        }
        return errors.length() > 0 ? errors.toString().trim() : null;
    }
}