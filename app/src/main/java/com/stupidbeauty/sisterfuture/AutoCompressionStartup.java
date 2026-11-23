package com.stupidbeauty.sisterfuture;

import com.stupidbeauty.sisterfuture.manager.ModelAccessPointManager;
import com.stupidbeauty.sisterfuture.tool.CompressConversationContextTool;
import com.stupidbeauty.sisterfuture.tool.Tool;
import com.stupidbeauty.sisterfuture.utils.FileLogger;
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
     * 依据本次事故：2.5MB 请求体正常，12.1MB 被服务端直接 reset，
     * 取中间保守值 2MB。
     */
    public static final int THRESHOLD_BYTES = 2 * 1024 * 1024;

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
     */
    public static void run(final ContextManager context, final ModelAccessPointManager accessPoints) {
        if (context == null || accessPoints == null) {
            return;
        }
        WORKER.execute(() -> {
            try {
                int bytes = estimateRequestBytes(context);
                if (bytes <= THRESHOLD_BYTES) {
                    FileLogger.i(TAG, "⏭️ [AUTO_COMPRESS] 启动检查：上下文未超阈值，跳过 | bytes="
                        + bytes + " | threshold=" + THRESHOLD_BYTES);
                    return;
                }
                FileLogger.i(TAG, "🚀 [AUTO_COMPRESS] 启动检查：上下文超阈值，触发一次自动压缩 | bytes="
                    + bytes + " | threshold=" + THRESHOLD_BYTES);
                compressOnce(context, accessPoints);
            } catch (Throwable t) {
                // 自动压缩永远不能影响启动
                FileLogger.e(TAG, "❌ [AUTO_COMPRESS] 启动自动压缩异常，已忽略", t);
            }
        });
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

    private static void compressOnce(final ContextManager context,
                                     final ModelAccessPointManager accessPoints) {
        final CompressConversationContextTool tool =
            new CompressConversationContextTool(context, accessPoints);
        try {
            tool.executeAsync(new JSONObject(), new Tool.OnResultCallback() {
                @Override
                public void onResult(JSONObject result) {
                    if (result == null) {
                        FileLogger.w(TAG, "⚠️ [AUTO_COMPRESS] 自动压缩返回空结果");
                        return;
                    }
                    FileLogger.i(TAG, "✅ [AUTO_COMPRESS] 自动压缩完成 | status="
                        + result.optString("status", "unknown")
                        + " | coveredMessages=" + result.optInt("coveredMessages", 0)
                        + " | failedSegments=" + result.optJSONArray("failedSegments"));
                }

                @Override
                public void onError(Exception e) {
                    FileLogger.e(TAG, "❌ [AUTO_COMPRESS] 自动压缩失败，原上下文未改变", e);
                }
            });
        } catch (Exception e) {
            FileLogger.e(TAG, "❌ [AUTO_COMPRESS] 启动自动压缩异常，原上下文未改变", e);
        }
    }
}