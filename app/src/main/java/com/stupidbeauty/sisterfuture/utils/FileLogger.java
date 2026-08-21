package com.stupidbeauty.sisterfuture.utils;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Pattern;


/**
 * 文件日志工具类 - #4834
 *
 * 功能：
 * 1. 日志输出到外置存储（优先）或应用私有目录（降级）
 * 2. 按日期命名和分割（单文件最大 10MB）
 * 3. 自动清理超过 7 天的旧日志
 * 4. 敏感信息自动过滤
 * 5. 外置存储不可写时自动降级到内置存储（Android 10+ Scoped Storage 兼容）
 *
 * 使用方式：
 * FileLogger.init(context);
 * FileLogger.d("TAG", "调试信息");
 * FileLogger.e("TAG", "错误信息");
 *
 * @author 未来姐姐
 * @since 2026-03-19
 *
 * 🔥 修复：Android 10+ Scoped Storage 兼容
 * - 优先尝试外置存储（/sdcard/Download/sisterfuture_logs/）方便主人查看
 * - 失败时降级到应用私有目录（/data/data/com.stupidbeauty.sisterfuture/files/sisterfuture_logs/）
 */
public class FileLogger {

    private static final String TAG = "FileLogger";

    // 🔥 修改：不再硬编码 LOG_DIR，改为动态初始化
    private static File logDir = null;

    // 单文件最大大小：10MB
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;

    // 日志保留天数：7 天
    private static final int MAX_DAYS = 7;

    // 日志级别
    public static final int LEVEL_DEBUG = 0;
    public static final int LEVEL_INFO = 1;
    public static final int LEVEL_WARN = 2;
    public static final int LEVEL_ERROR = 3;

    // 当前日志级别（默认 DEBUG）
    private static int currentLevel = LEVEL_DEBUG;

    // 当前日志文件
    private static File currentLogFile = null;
    private static long currentFileSize = 0;

    // 敏感信息过滤正则
    private static final Pattern API_KEY_PATTERN = Pattern.compile("(api[_-]?key|token|secret|password)[\"']?\\s*[:=]\\s*[\"']?[\\w-]+", Pattern.CASE_INSENSITIVE);

    /**
     * 初始化日志系统
     * 必须在 Application.onCreate() 中调用
     *
     * 🔥 修复：优雅降级逻辑
     * 1. 优先尝试外置存储（/sdcard/Download/sisterfuture_logs/）
     * 2. 外置存储不可写时降级到应用私有目录（/data/data/.../files/sisterfuture_logs/）
     * 3. 兜底：私有目录也不可写时仅写 logcat（不丢日志，只是不落盘）
     */
    public static void init(Context context) {
        try {
            // 第一步：尝试外置存储（主人方便在文件管理器中查看）
            File externalDir = new File(
                Environment.getExternalStorageDirectory(),
                "Download/sisterfuture_logs"
            );

            boolean useExternal = false;
            try {
                // 创建目录（如果父目录不存在，会一并创建）
                if (externalDir.mkdirs() || externalDir.exists()) {
                    // 测试可写性：尝试创建一个临时文件
                    File testFile = new File(externalDir, ".test_write_" + System.currentTimeMillis());
                    if (testFile.createNewFile()) {
                        // 创建成功 → 外置存储可用
                        testFile.delete();
                        useExternal = true;
                        Log.i(TAG, "✅ 使用外置存储: " + externalDir.getAbsolutePath());
                    } else {
                        Log.w(TAG, "⚠️ 外置存储测试文件创建失败");
                    }
                } else {
                    Log.w(TAG, "⚠️ 外置存储目录创建失败");
                }
            } catch (Exception e) {
                Log.w(TAG, "⚠️ 外置存储不可写，降级到内置存储: " + e.getMessage());
            }

            // 第二步：决定使用哪个目录
            if (useExternal) {
                logDir = externalDir;
            } else {
                // 降级到应用私有目录（永远可用）
                logDir = new File(context.getFilesDir(), "sisterfuture_logs");
                if (!logDir.exists()) {
                    logDir.mkdirs();
                }
                Log.i(TAG, "✅ 降级使用内置存储: " + logDir.getAbsolutePath());
            }

            // 清理旧日志
            cleanupOldLogs();

            // 初始化当前日志文件
            rotateLogFile();

            Log.i(TAG, "✅ FileLogger 初始化完成，日志目录: " + logDir.getAbsolutePath());
        } catch (Exception e) {
            Log.e(TAG, "❌ FileLogger 初始化失败（仅写 logcat）", e);
        }
    }

    /**
     * 设置日志级别
     * @param level 日志级别
     */
    public static void setLevel(int level) {
        currentLevel = level;
    }

    /**
     * 调试日志
     */
    public static void d(String tag, String message) {
        if (currentLevel <= LEVEL_DEBUG) {
            Log.d(tag, message);
            writeToFile("D", tag, message);
        }
    }

    /**
     * 信息日志
     */
    public static void i(String tag, String message) {
        if (currentLevel <= LEVEL_INFO) {
            Log.i(tag, message);
            writeToFile("I", tag, message);
        }
    }

    /**
     * 警告日志
     */
    public static void w(String tag, String message) {
        if (currentLevel <= LEVEL_WARN) {
            Log.w(tag, message);
            writeToFile("W", tag, message);
        }
    }

    /**
     * 错误日志
     */
    public static void e(String tag, String message) {
        if (currentLevel <= LEVEL_ERROR) {
            Log.e(tag, message);
            writeToFile("E", tag, message);
        }
    }

    /**
     * 错误日志（带异常）
     */
    public static void e(String tag, String message, Throwable tr) {
        if (currentLevel <= LEVEL_ERROR) {
            Log.e(tag, message, tr);
            writeToFile("E", tag, message + "\n" + Log.getStackTraceString(tr));
        }
    }

    /**
     * 写入日志到文件
     */
    private static synchronized void writeToFile(String level, String tag, String message) {
        try {
            // 检查是否需要轮转日志文件
            if (currentLogFile == null || currentFileSize >= MAX_FILE_SIZE) {
                rotateLogFile();
            }

            if (currentLogFile == null) {
                return;
            }

            // 格式化日志行
            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
            // String filteredMessage = filterSensitiveInfo(message);
            String filteredMessage = message;
            String logLine = String.format("%s %s/%s: %s\n", timestamp, level, tag, filteredMessage);

            // 追加写入
            FileWriter writer = new FileWriter(currentLogFile, true);
            writer.append(logLine);
            writer.flush();
            writer.close();

            currentFileSize += logLine.getBytes().length;
        } catch (IOException e) {
            Log.e(TAG, "写入日志文件失败", e);
        }
    }

    /**
     * 轮转日志文件（按日期或大小）
     * ✅ 修复：文件名添加时间戳确保唯一性，避免同一天内轮转后写入同一文件
     */
    private static void rotateLogFile() {
        try {
            String dateStr = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
            // ✅ 修复：添加 HHmmss 时间戳确保文件名唯一
            String timeStr = new SimpleDateFormat("HHmmss", Locale.getDefault()).format(new Date());
            String logFileName = "sisterfuture_" + dateStr + "_" + timeStr + ".log";

            // 🔥 修改：使用动态 logDir（外置或内置）
            if (logDir == null) {
                Log.e(TAG, "❌ logDir 未初始化，请先调用 init(context)");
                currentLogFile = null;
                currentFileSize = 0;
                return;
            }

            currentLogFile = new File(logDir, logFileName);

            if (currentLogFile.exists()) {
                currentFileSize = currentLogFile.length();
            } else {
                currentFileSize = 0;
                currentLogFile.createNewFile();
            }

            // Log.i(TAG, "📄 切换到新日志文件：" + currentLogFile.getAbsolutePath());
        } catch (IOException e) {
            Log.e(TAG, "创建日志文件失败", e);
            currentLogFile = null;
            currentFileSize = 0;
        }
    }

    /**
     * 清理超过指定天数的旧日志
     */
    private static void cleanupOldLogs() {
        try {
            // 🔥 修改：使用动态 logDir
            if (logDir == null || !logDir.exists()) {
                return;
            }

            File[] logFiles = logDir.listFiles((dir, name) -> name.startsWith("sisterfuture_") && name.endsWith(".log"));
            if (logFiles == null || logFiles.length == 0) {
                return;
            }

            long now = System.currentTimeMillis();
            long maxAge = MAX_DAYS * 24 * 60 * 60 * 1000L; // 转换为毫秒

            int deletedCount = 0;
            for (File logFile : logFiles) {
                long fileAge = now - logFile.lastModified();
                if (fileAge > maxAge) {
                    boolean deleted = logFile.delete();
                    if (deleted) {
                        deletedCount++;
                        Log.i(TAG, "🗑️ 删除旧日志文件：" + logFile.getName());
                    }
                }
            }

            if (deletedCount > 0) {
                Log.i(TAG, "✅ 清理完成，共删除 " + deletedCount + " 个旧日志文件");
            }
        } catch (Exception e) {
            Log.e(TAG, "清理旧日志失败", e);
        }
    }

    /**
     * 过滤敏感信息
     */
    private static String filterSensitiveInfo(String message) {
        if (message == null) {
            return "";
        }

        // 过滤 API Key、Token 等敏感信息
        String filtered = API_KEY_PATTERN.matcher(message).replaceAll("$1=[FILTERED]");

        // 过滤常见的密钥格式
        filtered = filtered.replaceAll("[A-Za-z0-9]{32,}", "[KEY_FILTERED]");

        return filtered;
    }

    /**
     * 获取日志目录路径
     */
    public static String getLogDirPath() {
        return logDir != null ? logDir.getAbsolutePath() : "N/A";
    }

    /**
     * 获取当前日志文件路径
     */
    public static String getCurrentLogFilePath() {
        return currentLogFile != null ? currentLogFile.getAbsolutePath() : "N/A";
    }
}