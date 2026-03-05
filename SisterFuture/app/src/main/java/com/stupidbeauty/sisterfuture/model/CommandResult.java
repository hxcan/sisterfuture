package com.stupidbeauty.sisterfuture.model;

public class CommandResult {
    public final String status;      // "success" | "failed"
    public final String stdout;      // 输出内容
    public final String stderr;      // 错误信息
    public final int exitCode;       // 退出码

    private CommandResult(String status, String stdout, String stderr, int exitCode) {
        this.status = status;
        this.stdout = stdout;
        this.stderr = stderr;
        this.exitCode = exitCode;
    }

    public static CommandResult success(String stdout, String stderr, int exitCode) {
        return new CommandResult("success", stdout, stderr, exitCode);
    }

    public static CommandResult failed(String message, String stdout, String stderr, int exitCode) {
        return new CommandResult("failed", 
                                 stdout != null ? stdout : "", 
                                 stderr != null ? stderr : message, 
                                 exitCode);
    }
}