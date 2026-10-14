package com.stupidbeauty.sisterfuture.manager;

import com.stupidbeauty.sisterfuture.network.ModelAccessPoint;
import com.stupidbeauty.sisterfuture.utils.FileLogger;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 请求参数兼容性管理器（#919750362575）
 *
 * 集中处理不同模型服务在请求体参数上的差异：
 *  - 哪些私有参数（目前为 Minimax 的 enable_thinking / thinking / reasoning_split）
 *    在当前接入点上是合法的；
 *  - 当接入点明确拒绝这些参数时，是否需要就地回退到无参数版请求；
 *  - 与 stream_options 的兼容回退策略完全对称，复用同一套"已知不支持的接入点"记忆机制。
 *
 * 设计目标：保持对 TongYiClient 的最小侵入，让网络层只问"我该塞这些参数吗？
 *           我需要做回退吗？"，不重复写判定逻辑。
 */
public class RequestParamCompatibilityManager
{
    private static final String TAG = "ReqParamCompat";

    /**
     * Minimax 私有思考控制参数在请求体中使用的字段名集合。
     * 用于：注入时统一管理；剔除时按集合一次性 remove；判定 4xx 错误体时匹配字段名。
     */
    private static final String PARAM_ENABLE_THINKING = "enable_thinking";
    private static final String PARAM_THINKING = "thinking";
    private static final String PARAM_REASONING_SPLIT = "reasoning_split";

    /**
     * 已知不支持 Minimax 私有思考控制参数的接入点（按 baseUrl+endpoint 作 key）。
     * 一旦服务端返回 4xx 并指明 enable_thinking / thinking / reasoning_split 是 unknown parameter，
     * 后续请求将跳过这些参数，避免反复重试失败。
     */
    private final Set<String> thinkingParamsUnsupportedEndpoints = ConcurrentHashMap.newKeySet();

    /**
     * 根据当前接入点判断是否需要向请求体注入 Minimax 私有思考控制参数。
     * 判定规则（保守优先，未来易扩展）：
     *  1. 若该接入点已被记入"已知不支持"列表 → false
     *  2. 若 modelName 或 baseUrl 命中 Minimax 域名（api.minimaxi.com / minimaxi.com） → true
     *  3. 其它一律 false
     *
     * @param accessPoint  当前接入点
     * @param endpointKey  用于"已知不支持"缓存的 key（baseUrl + chatEndpoint）
     */
    public boolean shouldInjectMinimaxThinkingParams(ModelAccessPoint accessPoint, String endpointKey)
    {
        if (accessPoint == null)
        {
            return false;
        }

        if (endpointKey != null && thinkingParamsUnsupportedEndpoints.contains(endpointKey))
        {
            return false;
        }

        String baseUrl = safe(accessPoint.getBaseUrl()).toLowerCase(Locale.ROOT);
        String modelName = safe(accessPoint.getModelName()).toLowerCase(Locale.ROOT);
        String name = safe(accessPoint.getName()).toLowerCase(Locale.ROOT);

        // 域名命中：Minimax / MiniMax
        if (baseUrl.contains("minimaxi.com") || baseUrl.contains("minimax.com"))
        {
            return true;
        }

        // 兜底：模型名或接入点名称显式标记 Minimax 系列
        if (modelName.contains("minimax") || name.contains("minimax"))
        {
            return true;
        }

        return false;
    }

    /**
     * 将 Minimax 私有思考控制参数写入请求体。
     * 仅当 shouldInjectMinimaxThinkingParams() 为 true 时调用方才会调到这里。
     */
    public void injectMinimaxThinkingParams(JSONObject requestBody) throws JSONException
    {
        requestBody.put(PARAM_ENABLE_THINKING, false);

        JSONObject thinkingParams = new JSONObject();
        thinkingParams.put("type", "disabled");
        thinkingParams.put("budget_tokens", 100);
        requestBody.put(PARAM_THINKING, thinkingParams);

        JSONObject reasoningParams = new JSONObject();
        reasoningParams.put("split", true);
        requestBody.put(PARAM_REASONING_SPLIT, reasoningParams.opt("split"));

        FileLogger.d(TAG, "🌐 [Minimax] 已添加 Minimax 思考控制参数：reasoning_split=true, thinking.budget_tokens=100");
    }

    /**
     * 剥离请求体中所有 Minimax 私有思考控制参数，原地修改并返回。
     * 用于兼容回退路径：服务端明确表示不认这些参数时，复用同一请求体其它字段重发一次。
     */
    public JSONObject stripMinimaxThinkingParams(JSONObject requestBody) throws JSONException
    {
        requestBody.remove(PARAM_ENABLE_THINKING);
        requestBody.remove(PARAM_THINKING);
        requestBody.remove(PARAM_REASONING_SPLIT);
        FileLogger.w(TAG, "🧹 [PARAM_STRIP] 剥离 Minimax 私有思考控制参数后重试");
        return requestBody;
    }

    /**
     * 判定错误响应是否属于"Minimax 私有思考控制参数不被支持"的情形。
     * 与 TongYiClient 中已有的 isUnsupportedStreamOptionsResponse() 完全对称的策略：
     * 仅在 4xx 状态码 + 错误体点名了 enable_thinking / thinking / reasoning_split 之一
     * 且包含"unknown / unsupported / not supported / 未知 / 不支持"等拒绝语义时返回 true。
     */
    public boolean isUnsupportedThinkingParamsResponse(int statusCode, String responseBody)
    {
        if (statusCode < 400 || statusCode >= 500 || responseBody == null || responseBody.isEmpty())
        {
            return false;
        }

        String normalized = responseBody.toLowerCase(Locale.ROOT);
        boolean namesTargetField = normalized.contains(PARAM_ENABLE_THINKING)
            || normalized.contains(PARAM_THINKING)
            || normalized.contains(PARAM_REASONING_SPLIT)
            || normalized.contains("reasoning_split")
            || normalized.contains("reasoning split");
        if (!namesTargetField)
        {
            return false;
        }

        return normalized.contains("unknown")
            || normalized.contains("unsupported")
            || normalized.contains("not supported")
            || normalized.contains("does not support")
            || normalized.contains("unrecognized")
            || normalized.contains("not recognized")
            || normalized.contains("unexpected")
            || normalized.contains("not allowed")
            || normalized.contains("not permitted")
            || normalized.contains("invalid")
            || normalized.contains("未知")
            || normalized.contains("不支持")
            || normalized.contains("无效");
    }

    /**
     * 将当前 endpoint 标记为"已知不支持 Minimax 私有思考控制参数"。
     * 调用方在确认触发兼容回退成功后调用，避免后续请求再次携带这些参数。
     */
    public void markThinkingParamsUnsupported(String endpointKey)
    {
        if (endpointKey != null && !endpointKey.isEmpty())
        {
            thinkingParamsUnsupportedEndpoints.add(endpointKey);
            FileLogger.d(TAG, "🧠 [COMPAT] 接入点已记入已知不支持列表：" + endpointKey);
        }
    }

    /**
     * 查询当前 endpoint 是否已被记入"已知不支持"列表。
     * 主要供网络层在请求构建时直接跳过 Minimax 私有参数注入。
     */
    public boolean isKnownUnsupported(String endpointKey)
    {
        return endpointKey != null && thinkingParamsUnsupportedEndpoints.contains(endpointKey);
    }

    private static String safe(String s)
    {
        return s == null ? "" : s;
    }
}
