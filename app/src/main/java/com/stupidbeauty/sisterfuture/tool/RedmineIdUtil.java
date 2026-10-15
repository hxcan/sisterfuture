// com.stupidbeauty.sisterfuture.tool.RedmineIdUtil.java
package com.stupidbeauty.sisterfuture.tool;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Redmine ID 解析工具。
 *
 * 背景：OpenAI Function Calling 协议（以及多数 OpenAI 兼容接入点）不接受 JSON Schema 的
 * "type":"long"，会直接返回 HTTP 400 "Invalid schema ... 'long' is not valid"。
 * 为此我们把工具 schema 中所有原本是 long 的 ID 字段统一改为 string（"750160066086"）。
 *
 * 本工具类负责把模型传回的字符串 ID 安全地转回 long，并兼容以下输入形态：
 *   1. JSON 字符串："750160066086"   ← OpenAI 协议下最常见
 *   2. JSON 数字：750160066086      ← 私有协议下仍可能出现
 *   3. 数组元素：["1","2"] 或 [1,2] ← blockedByIds / blockingIds
 *
 * 关联：sisterfuture 项目 #920250883721
 * */
public final class RedmineIdUtil
{
    private RedmineIdUtil() {}

    /**
     * 解析单个 ID 字段为 long。
     *
     * @param arguments  工具参数对象
     * @param key        字段名
     * @param required   是否必填：true 时缺字段或解析失败抛 IllegalArgumentException
     * @return 解析后的 long；若 required=false 且字段缺失则返回 0L
     * */
    public static long parseLong(JSONObject arguments, String key, boolean required) throws JSONException
    {
        if (arguments == null || !arguments.has(key) || arguments.isNull(key))
        {
            if (required)
                throw new IllegalArgumentException("Missing required parameter: " + key);
            return 0L;
        }
        Object raw = arguments.get(key);
        if (raw == null || raw == JSONObject.NULL)
        {
            if (required)
                throw new IllegalArgumentException("Parameter " + key + " is null");
            return 0L;
        }
        if (raw instanceof Number)
        {
            long v = ((Number) raw).longValue();
            if (required && v <= 0)
                throw new IllegalArgumentException(key + " 必须大于 0");
            return v;
        }
        if (raw instanceof String)
        {
            String s = ((String) raw).trim();
            if (s.isEmpty() || "null".equalsIgnoreCase(s))
            {
                if (required)
                    throw new IllegalArgumentException("Parameter " + key + " is empty");
                return 0L;
            }
            try
            {
                long v = Long.parseLong(s);
                if (required && v <= 0)
                    throw new IllegalArgumentException(key + " 必须大于 0");
                return v;
            }
            catch (NumberFormatException e)
            {
                throw new IllegalArgumentException(key + " 不是合法数字: " + s);
            }
        }
        throw new IllegalArgumentException(key + " 类型不受支持: " + raw.getClass().getSimpleName());
    }

    /**
     * 可选解析：缺字段时返回默认值。
     * */
    public static long optLong(JSONObject arguments, String key, long defaultValue) throws JSONException
    {
        if (arguments == null || !arguments.has(key) || arguments.isNull(key))
            return defaultValue;
        return parseLong(arguments, key, false);
    }

    /**
     * 解析 long 数组。
     * 支持元素为字符串或数字两种形态。
     * */
    public static JSONArray parseLongArray(JSONArray input) throws JSONException
    {
        JSONArray out = new JSONArray();
        if (input == null) return out;
        for (int i = 0; i < input.length(); i++)
        {
            Object item = input.isNull(i) ? null : input.get(i);
            if (item == null) continue;
            if (item instanceof Number)
            {
                out.put(((Number) item).longValue());
            }
            else if (item instanceof String)
            {
                String s = ((String) item).trim();
                if (s.isEmpty() || "null".equalsIgnoreCase(s)) continue;
                try
                {
                    out.put(Long.parseLong(s));
                }
                catch (NumberFormatException e)
                {
                    throw new IllegalArgumentException("数组中包含非法数字: " + s);
                }
            }
            else
            {
                throw new IllegalArgumentException("数组元素类型不受支持: " + item.getClass().getSimpleName());
            }
        }
        return out;
    }
}
