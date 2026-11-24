package com.stupidbeauty.sisterfuture;

import com.stupidbeauty.sisterfuture.manager.ModelAccessPointManager;
import com.stupidbeauty.sisterfuture.tool.CompressConversationContextTool;
import com.stupidbeauty.sisterfuture.tool.Tool;
import com.stupidbeauty.sisterfuture.utils.FileLogger;
import org.json.JSONObject;

/**
 * 统一的上下文压缩入口：启动时、上下文超长时、手动调用都走这里。
 *
 * 职责：
 * - 在后台线程执行压缩，不阻塞调用方（尤其是 UI 线程）
 * - 通过回调把「开始 / 完成 / 失败」通知给界面，让界面能给出反馈
 * - 界面反馈只显示在聊天区，不写入 ContextManager，因此不进入上下文
 */
public final class ContextCompressionController {

    private static final String TAG = "ContextCompression";

    /** 压缩过程的界面反馈回调。实现方需保证在主线程被调用。 */
    public interface Feedback {
        /** 压缩开始。reason 用于界面提示文案，例如「上下文超长」。 */
        void onCompressionStarted(String reason);

        /** 压缩完成。coveredMessages 是被摘要替代的消息数，status 是 success / partial。 */
        void onCompressionFinished(int coveredMessages, String status);

        /** 压缩失败。message 是失败原因。 */
        void onCompressionFailed(String message);
    }

    private final ContextManager context;
    private final ModelAccessPointManager models;

    public ContextCompressionController(ContextManager context, ModelAccessPointManager models) {
        this.context = context;
        this.models = models;
    }

    /**
     * 异步触发一次压缩。
     *
     * @param reason   触发原因，用于界面提示（例如「上下文超长」）
     * @param feedback 界面反馈回调，可为 null
     */
    public void compressAsync(final String reason, final Feedback feedback) {
        if (context == null || models == null) {
            return;
        }
        final CompressConversationContextTool tool = new CompressConversationContextTool(context, models);
        try {
            tool.executeAsync(new JSONObject(), new Tool.OnResultCallback() {
                @Override
                public void onResult(JSONObject result) {
                    if (result == null) {
                        notifyFailed(feedback, "压缩未返回结果");
                        return;
                    }
                    String status = result.optString("status", "unknown");
                    if ("error".equals(status)) {
                        notifyFailed(feedback, result.optString("message", "压缩失败"));
                        return;
                    }
                    int covered = result.optInt("coveredMessages", 0);
                    FileLogger.i(TAG, "✅ [CONTEXT_COMPRESSION_FINISHED] reason=" + reason
                        + " | status=" + status + " | coveredMessages=" + covered
                        + " | failedSegments=" + result.optJSONArray("failedSegments"));
                    if (feedback != null) {
                        feedback.onCompressionFinished(covered, status);
                    }
                }

                @Override
                public void onError(Exception e) {
                    notifyFailed(feedback, e == null ? "压缩失败" : String.valueOf(e.getMessage()));
                }
            });
        } catch (Exception e) {
            notifyFailed(feedback, e == null ? "压缩失败" : String.valueOf(e.getMessage()));
        }
    }

    private static void notifyFailed(Feedback feedback, String message) {
        FileLogger.w(TAG, "❌ [CONTEXT_COMPRESSION_FAILED] " + message);
        if (feedback != null) {
            feedback.onCompressionFailed(message);
        }
    }
}
