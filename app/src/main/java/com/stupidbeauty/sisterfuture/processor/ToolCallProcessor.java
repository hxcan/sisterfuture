package com.stupidbeauty.sisterfuture.processor;

/**
 * 工具调用处理器骨架类
 * 定义类结构和回调接口，暂不实现具体逻辑
 */
public class ToolCallProcessor {

    /**
     * 回调接口：处理任务完成和错误情况
     */
    public interface OnCompletionListener {
        void onCompleted(Object result);
        void onError(Exception e);
    }

    // 字段声明（待实现）
    private OnCompletionListener listener;

    // 构造函数
    public ToolCallProcessor() {
        // 初始化逻辑待定
    }

    /**
     * 提交任务执行（接口方法，无实现）
     */
    public void submitTask(String taskType, Object params) {
        // TODO: 待实现具体逻辑
    }
}