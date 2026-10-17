package com.stupidbeauty.sisterfuture.manager;

import com.stupidbeauty.sisterfuture.network.ModelAccessPoint;
import com.stupidbeauty.sisterfuture.manager.RequestParamCompatibilityManager.ReasoningEffortDecision;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 单元测试：覆盖 RequestParamCompatibilityManager 中 reasoning_effort 决策矩阵（#920293879609 方案 A）。
 *
 * 设计说明：
 *   - 本地 JVM JUnit，不依赖 Android framework。
 *   - 只测 decideReasoningEffort() 的纯函数决策逻辑。
 *   - 不测 applyReasoningEffortDecision()，因为它依赖 org.json.JSONObject，
 *     在 app/src/test/（普通 JVM 单测）下会抛 RuntimeException（android.jar stub）。
 *     决策逻辑已在 decideReasoningEffort 单测里覆盖；apply 的 JSONObject 操作
 *     是机械透传，CI 端到端测试会覆盖。
 *
 * 与 PR #627 同等的测试风格。
 */
public class RequestParamCompatibilityManagerTest
{
    private static final String OPENAI_SEALOS = "https://new-api-vxvfafio.cloud.sealos.io";
    private static final String OPENAI_OFFICIAL = "https://api.openai.com";
    private static final String MINIMAX = "https://api.minimaxi.com/v1";
    private static final String DASHSCOPE = "https://dashscope.aliyuncs.com/compatible-mode/v1";
    private static final String CHAT_COMPLETIONS = "/v1/chat/completions";
    private static final String RESPONSES = "/v1/responses";

    private final RequestParamCompatibilityManager manager = new RequestParamCompatibilityManager();

    /**
     * 构造 ModelAccessPoint 测试桩。ModelAccessPoint 实际构造函数：
     *   (String name, String baseUrl, String chatEndpoint, String modelName, String apiKey)
     * 本测试只关心 getName / getBaseUrl / getModelName 三个访问器。
     */
    private static ModelAccessPoint ap(String name, String baseUrl, String model)
    {
        return new ModelAccessPoint(name, baseUrl, CHAT_COMPLETIONS, model, "sk-placeholder");
    }

    // ========== decideReasoningEffort 决策矩阵 ==========

    @Test
    public void decide_gpt6_astra_sealos_withTools_returnsInjectLow()
    {
        ModelAccessPoint point = ap("gpt-6-astra", OPENAI_SEALOS, "gpt-6-astra");
        ReasoningEffortDecision d = manager.decideReasoningEffort(
            point, OPENAI_SEALOS + CHAT_COMPLETIONS, true);
        assertEquals(ReasoningEffortDecision.INJECT_LOW, d);
    }

    @Test
    public void decide_gpt56_sol_sealos_withTools_returnsInjectLow()
    {
        ModelAccessPoint point = ap("ppnew-ai", OPENAI_SEALOS, "gpt-5.6-sol");
        ReasoningEffortDecision d = manager.decideReasoningEffort(
            point, OPENAI_SEALOS + CHAT_COMPLETIONS, true);
        assertEquals(ReasoningEffortDecision.INJECT_LOW, d);
    }

    @Test
    public void decide_gpt6_astra_official_withTools_returnsInjectLow()
    {
        ModelAccessPoint point = ap("official", OPENAI_OFFICIAL, "gpt-6-astra");
        ReasoningEffortDecision d = manager.decideReasoningEffort(
            point, OPENAI_OFFICIAL + CHAT_COMPLETIONS, true);
        assertEquals(ReasoningEffortDecision.INJECT_LOW, d);
    }

    @Test
    public void decide_minimax_withTools_returnsKeepAsIs()
    {
        // Minimax 私有协议不在 OpenAI reasoning_effort 决策范围内
        ModelAccessPoint point = ap("MiniMax-M3", MINIMAX, "MiniMax-M3");
        ReasoningEffortDecision d = manager.decideReasoningEffort(
            point, MINIMAX + CHAT_COMPLETIONS, true);
        assertEquals(ReasoningEffortDecision.KEEP_AS_IS, d);
    }

    @Test
    public void decide_qwen_withTools_returnsKeepAsIs()
    {
        // 阿里百炼的 qwen 模型走 dashscope，不在 OpenAI 兼容判定里
        ModelAccessPoint point = ap("qwen", DASHSCOPE, "qwen3.7-plus");
        ReasoningEffortDecision d = manager.decideReasoningEffort(
            point, DASHSCOPE + CHAT_COMPLETIONS, true);
        assertEquals(ReasoningEffortDecision.KEEP_AS_IS, d);
    }

    @Test
    public void decide_gpt6_noTools_returnsKeepAsIs()
    {
        // 没有 tools 时不注入，避免对纯聊天请求做多余修改
        ModelAccessPoint point = ap("gpt-6-astra", OPENAI_SEALOS, "gpt-6-astra");
        ReasoningEffortDecision d = manager.decideReasoningEffort(
            point, OPENAI_SEALOS + CHAT_COMPLETIONS, false);
        assertEquals(ReasoningEffortDecision.KEEP_AS_IS, d);
    }

    @Test
    public void decide_gpt6_responsesEndpoint_returnsKeepAsIs()
    {
        // /v1/responses 端点不走 chat/completions 规则
        ModelAccessPoint point = ap("gpt-6-astra", OPENAI_SEALOS, "gpt-6-astra");
        ReasoningEffortDecision d = manager.decideReasoningEffort(
            point, OPENAI_SEALOS + RESPONSES, true);
        assertEquals(ReasoningEffortDecision.KEEP_AS_IS, d);
    }

    @Test
    public void decide_gpt4o_withTools_returnsKeepAsIs()
    {
        // gpt-4o 不在判定范围（OpenAI 协议 + gpt-4 系列无 reasoning_effort 强制要求）
        ModelAccessPoint point = ap("gpt-4o", OPENAI_SEALOS, "gpt-4o");
        ReasoningEffortDecision d = manager.decideReasoningEffort(
            point, OPENAI_SEALOS + CHAT_COMPLETIONS, true);
        assertEquals(ReasoningEffortDecision.KEEP_AS_IS, d);
    }

    @Test
    public void decide_nullAccessPoint_returnsKeepAsIs()
    {
        ReasoningEffortDecision d = manager.decideReasoningEffort(
            null, OPENAI_SEALOS + CHAT_COMPLETIONS, true);
        assertEquals(ReasoningEffortDecision.KEEP_AS_IS, d);
    }

    @Test
    public void decide_nullEndpoint_returnsKeepAsIs()
    {
        ModelAccessPoint point = ap("gpt-6-astra", OPENAI_SEALOS, "gpt-6-astra");
        ReasoningEffortDecision d = manager.decideReasoningEffort(point, null, true);
        assertEquals(ReasoningEffortDecision.KEEP_AS_IS, d);
    }

    // ========== 已有 Minimax 路径的回归测试（确保未被破坏） ==========

    @Test
    public void existing_minimax_thinkingParams_stillInjected()
    {
        ModelAccessPoint point = ap("MiniMax-M3", MINIMAX, "MiniMax-M3");
        String endpointKey = MINIMAX + CHAT_COMPLETIONS;
        assertTrue(manager.shouldInjectMinimaxThinkingParams(point, endpointKey));
    }

    @Test
    public void existing_qwen_thinkingParams_notInjected()
    {
        ModelAccessPoint point = ap("qwen", DASHSCOPE, "qwen3.7-plus");
        String endpointKey = DASHSCOPE + CHAT_COMPLETIONS;
        assertFalse(manager.shouldInjectMinimaxThinkingParams(point, endpointKey));
    }

    @Test
    public void existing_markUnsupported_takesEffect()
    {
        ModelAccessPoint point = ap("MiniMax-M3", MINIMAX, "MiniMax-M3");
        String endpointKey = MINIMAX + CHAT_COMPLETIONS;
        assertTrue(manager.shouldInjectMinimaxThinkingParams(point, endpointKey));
        manager.markThinkingParamsUnsupported(endpointKey);
        assertFalse(manager.shouldInjectMinimaxThinkingParams(point, endpointKey));
        assertTrue(manager.isKnownUnsupported(endpointKey));
    }
}
