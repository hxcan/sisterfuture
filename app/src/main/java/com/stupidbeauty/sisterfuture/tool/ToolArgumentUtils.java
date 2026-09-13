package com.stupidbeauty.sisterfuture.tool;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * 工具参数解析工具类
 *
 * 背景：
 * 任务 #890876009761（genericWebRequest 应容忍参数序列化错误）已完成，PR #564 已合并。
 * 任务 #892678951452（把 safeGetString 容错方案推广到所有 sisterfuture 工具）进行中。
 * 本类是 Phase 1 的基础设施：抽取 safeGetString / safeGetInt / safeGetBoolean 等容错方法
 * 到公共类，所有 *Tool.java 后续都改用本类，避免 LLM 序列化参数轻微异常时整个工具调用崩溃。
 *
 * 🆕 Phase 1 引用：父任务 #892678951452，本 Phase 子任务 #892824624696
 *
 * 容错策略：
 * 1. 先尝试直接 getXxx（标准路径）
 * 2. 失败时遍历所有 key，做大小写不敏感匹配（兼容 method/METHOD/Method）
 * 3. 返回 null 或默认值（让调用方决定如何处理缺失）
 *
 * @author 未来姐姐
 */
public class ToolArgumentUtils {

    private static final String TAG = "ToolArgumentUtils";

    /**
     * 🆕 安全获取字符串字段
     *
     * 容错逻辑：
     * 1. obj 或 key 为 null → 返回 null
     * 2. 先尝试直接 getString（标准路径）
     * 3. 失败时遍历所有 key，做大小写不敏感匹配
     * 4. 仍找不到 → 返回 null
     *
     * @param obj 参数 JSONObject（通常是工具的 arguments）
     * @param key 要获取的参数名
     * @return 参数字符串值，找不到返回 null
     */
    public static String safeGetString(JSONObject obj, String key) {
        return safeGetString(obj, key, null);
    }

    /**
     * 🆕 安全获取字符串字段（带默认值）
     *
     * @param obj 参数 JSONObject
     * @param key 要获取的参数名
     * @param defaultValue 默认值（找不到时返回）
     * @return 参数字符串值，找不到返回 defaultValue
     */
    public static String safeGetString(JSONObject obj, String key, String defaultValue) {
        if (obj == null || key == null) {
            return defaultValue;
        }
        // 1. 标准路径：直接 getString
        try {
            String v = obj.getString(key);
            if (v != null) {
                return v;
            }
        } catch (JSONException ignored) {
            // fall through to case-insensitive search
        }
        // 2. 大小写不敏感兜底
        try {
            JSONArray names = obj.names();
            if (names != null) {
                for (int i = 0; i < names.length(); i++) {
                    String k = names.getString(i);
                    if (k != null && k.equalsIgnoreCase(key)) {
                        try {
                            Object v = obj.get(k);
                            if (v != null) {
                                return String.valueOf(v);
                            }
                        } catch (JSONException ignored) {
                            // try next key
                        }
                    }
                }
            }
        } catch (JSONException ignored) {
            // names() failed
        }
        // 3. 找不到返回默认值
        return defaultValue;
    }

    /**
     * 🆕 安全获取整数字段（带默认值）
     *
     * 容错逻辑：
     * 1. obj 或 key 为 null → 返回 defaultValue
     * 2. 先尝试直接 getInt（标准路径）
     * 3. 如果类型不对，尝试字符串转数字
     * 4. 仍找不到 → 大小写不敏感兜底
     *
     * @param obj 参数 JSONObject
     * @param key 要获取的参数名
     * @param defaultValue 默认值（找不到时返回）
     * @return 参数整数值，找不到返回 defaultValue
     */
    public static int safeGetInt(JSONObject obj, String key, int defaultValue) {
        if (obj == null || key == null) {
            return defaultValue;
        }
        // 1. 标准路径
        if (obj.has(key) && !obj.isNull(key)) {
            try {
                return obj.getInt(key);
            } catch (JSONException ignored) {
                // 类型不对，尝试转字符串再解析
                try {
                    String v = obj.getString(key);
                    if (v != null && !v.isEmpty()) {
                        return Integer.parseInt(v.trim());
                    }
                } catch (Exception ignored2) {
                    // fall through
                }
            }
        }
        // 2. 大小写不敏感兜底
        try {
            JSONArray names = obj.names();
            if (names != null) {
                for (int i = 0; i < names.length(); i++) {
                    String k = names.getString(i);
                    if (k != null && k.equalsIgnoreCase(key)) {
                        try {
                            Object v = obj.get(k);
                            if (v instanceof Integer) {
                                return (Integer) v;
                            } else if (v instanceof Number) {
                                return ((Number) v).intValue();
                            } else if (v != null) {
                                String str = String.valueOf(v).trim();
                                if (!str.isEmpty()) {
                                    return Integer.parseInt(str);
                                }
                            }
                        } catch (Exception ignored) {
                            // try next key
                        }
                    }
                }
            }
        } catch (JSONException ignored) {
            // names() failed
        }
        return defaultValue;
    }

    /**
     * 🆕 安全获取布尔字段（带默认值）
     *
     * 容错逻辑：
     * 1. obj 或 key 为 null → 返回 defaultValue
     * 2. 先尝试直接 getBoolean（标准路径）
     * 3. 如果类型不对，尝试字符串解析（"true"/"false"/"1"/"0"/"yes"/"no"）
     * 4. 仍找不到 → 大小写不敏感兜底
     *
     * @param obj 参数 JSONObject
     * @param key 要获取的参数名
     * @param defaultValue 默认值（找不到时返回）
     * @return 参数布尔值，找不到返回 defaultValue
     */
    public static boolean safeGetBoolean(JSONObject obj, String key, boolean defaultValue) {
        if (obj == null || key == null) {
            return defaultValue;
        }
        // 1. 标准路径
        if (obj.has(key) && !obj.isNull(key)) {
            try {
                return obj.getBoolean(key);
            } catch (JSONException ignored) {
                // 类型不对，尝试字符串解析
                try {
                    String v = obj.getString(key);
                    if (v != null) {
                        String trimmed = v.trim().toLowerCase();
                        if (trimmed.equals("true") || trimmed.equals("1") || trimmed.equals("yes")) {
                            return true;
                        }
                        if (trimmed.equals("false") || trimmed.equals("0") || trimmed.equals("no")) {
                            return false;
                        }
                    }
                } catch (Exception ignored2) {
                    // fall through
                }
            }
        }
        // 2. 大小写不敏感兜底
        try {
            JSONArray names = obj.names();
            if (names != null) {
                for (int i = 0; i < names.length(); i++) {
                    String k = names.getString(i);
                    if (k != null && k.equalsIgnoreCase(key)) {
                        try {
                            Object v = obj.get(k);
                            if (v instanceof Boolean) {
                                return (Boolean) v;
                            } else if (v != null) {
                                String str = String.valueOf(v).trim().toLowerCase();
                                if (str.equals("true") || str.equals("1") || str.equals("yes")) {
                                    return true;
                                }
                                if (str.equals("false") || str.equals("0") || str.equals("no")) {
                                    return false;
                                }
                            }
                        } catch (Exception ignored) {
                            // try next key
                        }
                    }
                }
            }
        } catch (JSONException ignored) {
            // names() failed
        }
        return defaultValue;
    }

    /**
     * 🆕 安全获取 JSONObject 字段
     *
     * 容错逻辑：
     * 1. obj 或 key 为 null → 返回 null
     * 2. 先尝试直接 getJSONObject（标准路径）
     * 3. 大小写不敏感兜底
     *
     * 适用场景：headers、params、fields 等嵌套对象参数。
     *
     * @param obj 参数 JSONObject
     * @param key 要获取的参数名
     * @return 嵌套 JSONObject，找不到返回 null
     */
    public static JSONObject safeGetJSONObject(JSONObject obj, String key) {
        if (obj == null || key == null) {
            return null;
        }
        // 1. 标准路径
        if (obj.has(key) && !obj.isNull(key)) {
            try {
                JSONObject v = obj.getJSONObject(key);
                if (v != null) {
                    return v;
                }
            } catch (JSONException ignored) {
                // fall through
            }
        }
        // 2. 大小写不敏感兜底
        try {
            JSONArray names = obj.names();
            if (names != null) {
                for (int i = 0; i < names.length(); i++) {
                    String k = names.getString(i);
                    if (k != null && k.equalsIgnoreCase(key)) {
                        try {
                            Object v = obj.get(k);
                            if (v instanceof JSONObject) {
                                return (JSONObject) v;
                            }
                        } catch (JSONException ignored) {
                            // try next key
                        }
                    }
                }
            }
        } catch (JSONException ignored) {
            // names() failed
        }
        return null;
    }

    /**
     * 🆕 要求参数存在：用于关键参数缺失时统一抛 IllegalArgumentException
     *
     * 使用场景：在工具的 executeAsync 开头，对关键参数做必填校验。
     * 例如：
     * <pre>
     *   String url = ToolArgumentUtils.safeGetString(arguments, "url");
     *   ToolArgumentUtils.require(arguments, "url", url != null && !url.isEmpty(),
     *       "url 参数不能为空");
     * </pre>
     *
     * @param obj 参数 JSONObject（用于错误信息）
     * @param key 参数名（用于错误信息）
     * @param condition 参数是否有效的布尔条件
     * @param message 错误信息
     * @throws IllegalArgumentException 当 condition 为 false 时抛出
     */
    public static void require(JSONObject obj, String key, boolean condition, String message) {
        if (!condition) {
            android.util.Log.w(TAG, "缺少必需参数 [" + key + "]: " + message);
            throw new IllegalArgumentException("缺少必需参数 [" + key + "]: " + message);
        }
    }
}
