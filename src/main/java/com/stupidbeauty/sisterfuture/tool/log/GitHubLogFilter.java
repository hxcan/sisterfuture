// com.stupidbeauty.sisterfuture.tool.log.GitHubLogFilter.java
package com.stupidbeauty.sisterfuture.tool.log;

import java.util.regex.Pattern;

/**
 * GitHub Actions 日志过滤器
 * 
 * @author 太极美术工程狮狮长
 * @version 1.0.0 (初始版本，从 GetGitHubActionsLogsTool 抽取)
 */
public class GitHubLogFilter {

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
    public boolean isRunnerOpLine(String line) {
        for (Pattern p : RUNNER_OP_PATTERNS) {
            if (p.matcher(line).find()) {
                return true;
            }
        }
        return false;
    }

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
     * 过滤警告行（以 warning 开头的行，不区分大小写）
     * 修复：GitHub Actions 日志以时间戳开头，WARNING 可能在时间戳后面
     * 改为检查整行是否包含 "WARNING:" 或 "warning:" 模式
     */
    public String filterWarningLines(String logs) {
        StringBuilder filtered = new StringBuilder();
        String[] lines = logs.split("\n");

        for (String line : lines) {
            String trimmedLine = line.trim().toLowerCase();
            // 使用 contains 而不是 startsWith，GitHub Actions 日志前面有时间戳
            // 只检查是否包含 "warning:" 标记，忽略大小写
            if (!trimmedLine.contains("warning:")) {
                filtered.append(line).append("\n");
            }
        }

        return filtered.toString();
    }

    /**
     * 过滤只返回错误行
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
     * 从日志中提取错误信息
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
                // 继续收集错误相关的行，直到遇到新的 section
                if (line.startsWith("20") && line.contains("Z ")) {
                    // 可能是新的时间戳行，停止收集
                    break;
                }
                errors.append(line).append("\n");
            }
        }

        return errors.length() > 0 ? errors.toString().trim() : null;
    }
}