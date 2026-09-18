package com.stupidbeauty.sisterfuture.utils;

import org.junit.Test;
import static org.junit.Assert.*;

public class ToolHopPolicyTest {
    @Test public void preservesCustomPromptAndToolEnhancements() {
        String original = "用户自定义人格\n【工具特别约束】保留认证规则\n/no_think\n";
        String result = ToolHopPolicy.appendToSystemPrompt(original);
        assertTrue(result.startsWith(original));
        assertTrue(result.contains("由客户端程序统计和限制"));
        assertTrue(result.contains("无论历史提示是否带来源标记"));
        assertTrue(result.contains("不要自行宣称"));
        assertTrue(result.contains("用户授权边界"));
    }

    @Test public void appliesEvenWithoutCustomPromptOrTools() {
        assertTrue(ToolHopPolicy.appendToSystemPrompt("").contains("【客户端工具调用计数规则】"));
    }

    @Test public void marksBothHopAndErrorLimitNoticesWithoutLosingReason() {
        for (String reason : new String[]{"连续调用工具 8 跳，达到安全上限", "工具已经连续 3 跳执行出错"}) {
            String result = ToolHopPolicy.markClientNotice(reason);
            assertTrue(result.startsWith("【客户端工具调用限制】"));
            assertTrue(result.contains(reason));
            assertTrue(result.contains("仅针对当时轮次"));
            assertTrue(result.contains("历史提示不代表后续轮次"));
        }
    }
}
