package com.stupidbeauty.sisterfuture;

import com.stupidbeauty.sisterfuture.manager.ModelAccessPointManager;
import com.stupidbeauty.sisterfuture.utils.FileLogger;
import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 启动时按条件自动触发一次上下文压缩。
 *
 * 背景：2026-11-23 会话 9d8ebcda 上下文涨到约 3M tokens（请求体 12.1MB），
 * 服务端直接断连，而压缩工具从来没人调用。现在改为启动时判断体积，
 * 超阈值就自动压一次，避免用户再次手动处理。
 *
 * 范围（主人明确要求）：仅启动时、仅一次、仅单一体积阈值。
 * 不做：会话切换时触发、每轮响应后触发、服务端拒绝后自愈重试、连续多次压缩、冷却节流。
 *
 * 失败必须降级：压缩失败只记日志，绝不能影响启动或让应用崩溃。
 */
public final class AutoCompressionStartup {

    private static final String TAG = "AutoCompressionStartup";

    /**
     * 触发阈值（请求体字节数）。
     *
     * 2026-11-24 主人调整：原 2MB 太短，未达模型上下文上限就触发压缩属于过早压缩，
     * 白白牺牲对话历史。阈值上提到 8MB。
     */
    public static final int THRESHOLD_BYTES = 8 * 1024 * 1024;

    /**
     * 单次压缩的总时长预算（毫秒）。
     *
     * 2026-11-24 主人指示：从开始压缩起，一分钟内必须结束并把已有成果提交，
     * 不等所有片段全部跑完（实测大会话要 2 小时，几乎没有实用性）。
     */
    public static final long TIME_BUDGET_MS = 60_000L;

    /** 配置读取用的系统属性键（优先于 SharedPreferences，便于线上临时调参）。 */
    private static final String PROP_THRESHOLD = "auto_compress_threshold_bytes";
    private static final String PROP_TIME_BUDGET = "auto_compress_time_budget_ms";

    /** 配置读取用的 SharedPreferences。 */
    private static final String PREFS_NAME = "sisterfuture_prefs";
    private static final String PREF_THRESHOLD = "auto_compress_threshold_bytes";
    private static final String PREF_TIME_BUDGET = "auto_compress_time_budget_ms";

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "auto-context-compression");
        thread.setDaemon(true);
        return thread;
    });

    private AutoCompressionStartup() {
    }

    /**
     * 入口：判断当前会话是否超过阈值，超过则后台触发一次压缩。
     *
     * @param context      当前会话的 ContextManager（必须与 Activity 使用同一个实例，
     *                      这样 installCompression 更新内存状态后无需额外刷新）
     * @param accessPoints 接入点管理器
     * @param feedback     界面反馈回调，可为 null
     */
    public static void run(final ContextManager context,
                           final ModelAccessPointManager accessPoints,
                           final ContextCompressionController.Feedback feedback) {
        if (context == null || accessPoints == null) {
            return;
        }
        WORKER.execute(() -> {
            try {
                int bytes = estimateRequestBytes(context);
                int threshold = resolveThresholdBytes();
                if (bytes <= threshold) {
                    FileLogger.i(TAG, "⏭️ [AUTO_COMPRESS] 启动检查：上下文未超阈值，跳过 | bytes="
                        + bytes + " | threshold=" + threshold);
                    return;
                }
                long timeBudgetMs = resolveTimeBudgetMs();
                FileLogger.i(TAG, "🚀 [AUTO_COMPRESS] 启动检查：上下文超阈值，触发一次自动压缩 | bytes="
                    + bytes + " | threshold=" + threshold + " | timeBudgetMs=" + timeBudgetMs
                    + " | contextInstance=" + Integer.toHexString(System.identityHashCode(context))
                    + " | compressionAlreadyInProgress=" + context.isCompressionInProgress());
                if (feedback != null) {
                    feedback.onCompressionStarted("启动检查");
                }
                new ContextCompressionController(context, accessPoints)
                    .compressAsync("启动检查", feedback, timeBudgetMs);
            } catch (Throwable t) {
                // 自动压缩永远不能影响启动
                FileLogger.e(TAG, "❌ [AUTO_COMPRESS] 启动自动压缩异常，已忽略", t);
            }
        });
    }

    /**
     * 解析实际生效的体积阈值。
     * 优先级：系统属性 → SharedPreferences → 默认 8MB。
     */
    static int resolveThresholdBytes() {
        Long fromProperty = readLongFromSystemProperty(PROP_THRESHOLD);
        if (fromProperty != null && fromProperty > 0) {
            return fromProperty.intValue();
        }
        Long fromPreferences = readLongFromPreferences(PREF_THRESHOLD);
        if (fromPreferences != null && fromPreferences > 0) {
            return fromPreferences.intValue();
        }
        return THRESHOLD_BYTES;
    }

    /**
     * 解析实际生效的单次压缩总时长预算（毫秒）。
     * 优先级：系统属性 → SharedPreferences → 默认 60 秒。
     */
    static long resolveTimeBudgetMs() {
        Long fromProperty = readLongFromSystemProperty(PROP_TIME_BUDGET);
        if (fromProperty != null && fromProperty > 0) {
            return fromProperty;
        }
        Long fromPreferences = readLongFromPreferences(PREF_TIME_BUDGET);
        if (fromPreferences != null && fromPreferences > 0) {
            return fromPreferences;
        }
        return TIME_BUDGET_MS;
    }

    private static Long readLongFromSystemProperty(String key) {
        try {
            String value = System.getProperty(key);
            if (value == null || value.trim().isEmpty()) {
                return null;
            }
            return Long.parseLong(value.trim());
        } catch (Throwable t) {
            FileLogger.w(TAG, "[AUTO_COMPRESS] 读取系统属性失败 | key=" + key + " | error=" + t.getClass().getSimpleName());
            return null;
        }
    }

    private static Long readLongFromPreferences(String key) {
        try {
            Context appContext = SisterFutureApplication.getAppContext();
            if (appContext == null) {
                return null;
            }
            SharedPreferences preferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            if (preferences == null || !preferences.contains(key)) {
                return null;
            }
            long value = preferences.getLong(key, -1L);
            return value > 0 ? Long.valueOf(value) : null;
        } catch (Throwable t) {
            FileLogger.w(TAG, "[AUTO_COMPRESS] 读取 SharedPreferences 失败 | key=" + key + " | error=" + t.getClass().getSimpleName());
            return null;
        }
    }

    /**
     * 估算当前请求体大小（字节）。
     * 与真实请求使用同一份投影结果（getRequestMessages）。
     */
    static int estimateRequestBytes(ContextManager context) {
        try {
            List<JSONObject> messages = context.getRequestMessages();
            if (messages == null || messages.isEmpty()) {
                return 0;
            }
            JSONArray array = new JSONArray(messages);
            return array.toString().getBytes("UTF-8").length;
        } catch (Exception e) {
            FileLogger.e(TAG, "❌ [AUTO_COMPRESS] 估算请求体大小失败", e);
            return 0;
        }
    }
}