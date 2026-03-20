package com.stupidbeauty.sisterfuture.utils;

import android.util.Log;

/**
 * 上下文长度检测工具类
 * 
 * 用于检测 API 返回的错误信息是否表示上下文超长。
 * 支持多种错误消息格式的匹配。
 * 
 * @author SisterFuture Team
 * @since 2026-03-17
 */
public class ContextLengthUtils {
    
    private static final String TAG = "ContextLengthUtils";

    /**
     * 检测错误信息是否表示上下文超长
     * 
     * @param errorMessage 错误信息字符串
     * @return 如果是上下文超长错误返回 true，否则返回 false
     */
    public static boolean isContextLengthError(String errorMessage) {
        if (errorMessage == null || errorMessage.isEmpty()) {
            return false;
        }
        
        // 支持多种错误消息格式
        boolean isContextTooLong = errorMessage.contains("Range of input length should be") ||
                                   errorMessage.contains("context length") ||
                                   errorMessage.contains("exceeds the available context size") ||
                                   errorMessage.contains("exceeds maximum context length") ||
                                   errorMessage.contains("context window exceeds limit") ||
                                   errorMessage.contains("Exceeded limit on max bytes to request body"); // ✅ #4884 阿里云请求体超长错误
        
        if (isContextTooLong) {
            Log.d(TAG, "✅ 检测到上下文超长错误：" + errorMessage.substring(0, Math.min(100, errorMessage.length())));
        }
        
        return isContextTooLong;
    }

    /**
     * 私有构造函数，防止实例化
     */
    private ContextLengthUtils() {
        // 工具类不应被实例化
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
}