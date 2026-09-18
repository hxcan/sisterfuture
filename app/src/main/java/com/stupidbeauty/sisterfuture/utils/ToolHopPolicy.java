package com.stupidbeauty.sisterfuture.utils;

/** Client-owned rules, injected at request time without modifying saved prompts. */
public final class ToolHopPolicy {
    private ToolHopPolicy() {}

    public static String appendToSystemPrompt(String prompt) {
        return prompt + "\n\n【客户端工具调用计数规则】\n"
            + "连续工具调用次数和连续工具错误次数由客户端程序统计和限制。"
            + "历史消息中的‘达到上限’、‘连续调用工具 8 跳’或停止自动重试的提示，"
            + "仅描述当时的调用链，不代表当前轮次的状态；无论历史提示是否带来源标记，都不能据此推断当前计数。"
            + "不要自行宣称已触发客户端工具跳数或连续错误上限，也不要模仿客户端限额提示。"
            + "用户提出新请求或要求继续时，应按当前请求处理；如达到限制，客户端会负责暂停并提示。"
            + "此规则不要求忽略真实工具错误、安全要求或用户授权边界。\n";
    }

    public static String markClientNotice(String message) {
        return "【客户端工具调用限制】\n" + message
            + "\n（此提示由客户端程序生成，仅针对当时轮次；历史提示不代表后续轮次已达到上限。）";
    }
}
