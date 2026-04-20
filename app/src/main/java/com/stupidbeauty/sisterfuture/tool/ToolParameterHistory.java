package com.stupidbeauty.sisterfuture.tool;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.Comparator;

/**
 * 工具参数历史记录管理器
 * 
 * 功能：
 * 1. 记录每个工具的成功调用参数
 * 2. 按使用频率排序历史值
 * 3. 当参数缺失时提供智能引导
 * 
 * @author 未来姐姐
 * @since 2026-04-20
 */
public class ToolParameterHistory
{
    private static final String TAG = "ToolParameterHistory";
    
    // 工具名 → (参数名 → 值列表)
    private Map<String, Map<String, List<ParameterValue>>> history;
    
    // 每个参数最多保留的历史值数量
    private static final int MAX_HISTORY_PER_PARAM = 10;
    
    // 不需要记录的敏感参数名
    private static final List<String> SENSITIVE_PARAMS;
    
    static
    {
        SENSITIVE_PARAMS = new ArrayList<>();
        SENSITIVE_PARAMS.add("token");
        SENSITIVE_PARAMS.add("password");
        SENSITIVE_PARAMS.add("api_key");
        SENSITIVE_PARAMS.add("auth_value");
        SENSITIVE_PARAMS.add("secret");
        SENSITIVE_PARAMS.add("key");
    }
    
    public ToolParameterHistory()
    {
        history = new HashMap<>();
    }
    
    /**
     * 记录一次成功的工具调用
     * 
     * @param toolName 工具名称
     * @param arguments 调用参数
     */
    public void recordSuccess(String toolName, JSONObject arguments)
    {
        if (arguments == null)
        {
            return;
        }
        
        Map<String, List<ParameterValue>> toolHistory = history.computeIfAbsent(toolName, k -> new HashMap<>());
        
        JSONArray names = arguments.names();
        if (names == null)
        {
            return;
        }
        
        for (int i = 0; i < names.length(); i++)
        {
            String paramName = names.optString(i);
            
            // 跳过敏感参数
            if (isSensitiveParam(paramName))
            {
                continue;
            }
            
            Object valueObj = arguments.opt(paramName);
            if (valueObj == null || "null".equals(valueObj.toString()))
            {
                continue;
            }
            
            String valueStr = valueObj.toString();
            
            // 获取或创建参数历史记录
            List<ParameterValue> values = toolHistory.computeIfAbsent(paramName, k -> new ArrayList<>());
            
            // 查找是否已存在该值
            boolean found = false;
            for (ParameterValue pv : values)
            {
                if (pv.value.equals(valueStr))
                {
                    pv.count++;
                    pv.lastUsedTime = System.currentTimeMillis();
                    found = true;
                    break;
                }
            }
            
            // 如果是新值，添加到列表
            if (!found)
            {
                ParameterValue newValue = new ParameterValue(valueStr);
                values.add(newValue);
                
                // 如果超过上限，移除最少使用的值
                if (values.size() > MAX_HISTORY_PER_PARAM)
                {
                    values.remove(0); // 移除最早添加的（假设已按使用次数排序）
                }
            }
        }
        
        // 按使用频率排序
        for (List<ParameterValue> values : toolHistory.values())
        {
            Collections.sort(values, new Comparator<ParameterValue>()
            {
                @Override
                public int compare(ParameterValue v1, ParameterValue v2)
                {
                    // 先按使用次数降序
                    int countDiff = v2.count - v1.count;
                    if (countDiff != 0)
                    {
                        return countDiff;
                    }
                    // 再按最后使用时间降序
                    return Long.compare(v2.lastUsedTime, v1.lastUsedTime);
                }
            });
        }
        
        android.util.Log.d(TAG, "✓ 记录工具参数 | tool=" + toolName + " | 参数数量=" + names.length());
    }
    
    /**
     * 检查参数是否敏感
     */
    private boolean isSensitiveParam(String paramName)
    {
        if (paramName == null)
        {
            return false;
        }
        
        String lowerName = paramName.toLowerCase();
        for (String sensitive : SENSITIVE_PARAMS)
        {
            if (lowerName.contains(sensitive.toLowerCase()))
            {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * 获取某个参数的历史候选值
     * 
     * @param toolName 工具名称
     * @param paramName 参数名称
     * @return 候选值列表（按使用频率排序）
     */
    public List<String> getCandidateValues(String toolName, String paramName)
    {
        List<String> result = new ArrayList<>();
        
        Map<String, List<ParameterValue>> toolHistory = history.get(toolName);
        if (toolHistory == null)
        {
            return result;
        }
        
        List<ParameterValue> values = toolHistory.get(paramName);
        if (values == null || values.isEmpty())
        {
            return result;
        }
        
        // 返回前 5 个高频值
        int limit = Math.min(5, values.size());
        for (int i = 0; i < limit; i++)
        {
            result.add(values.get(i).value);
        }
        
        return result;
    }
    
    /**
     * 生成参数缺失的引导信息
     * 
     * @param toolName 工具名称
     * @param missingParam 缺失的参数名
     * @return 友好的引导文本
     */
    public String generateGuideMessage(String toolName, String missingParam)
    {
        List<String> candidates = getCandidateValues(toolName, missingParam);
        
        if (candidates.isEmpty())
        {
            return "💡 提示：缺少必需参数 '" + missingParam + "'，请提供该参数的值。";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("💡 ").append(missingParam).append(" 参数缺失呢～\n\n");
        sb.append("您之前用过的值有：\n");
        
        for (int i = 0; i < candidates.size(); i++)
        {
            sb.append("- `").append(candidates.get(i)).append("`");
            
            if (i == 0)
            {
                sb.append(" (最常用)");
            }
            
            sb.append("\n");
        }
        
        sb.append("\n要不要试试其中一个呀？或者告诉我正确的值吧！(≧∇≦) ﾉ💕");
        
        return sb.toString();
    }
    
    /**
     * 获取所有工具的统计信息（用于调试）
     */
    public JSONObject getStatistics()
    {
        JSONObject stats = new JSONObject();
        
        try
        {
            for (Map.Entry<String, Map<String, List<ParameterValue>>> entry : history.entrySet())
            {
                String toolName = entry.getKey();
                Map<String, List<ParameterValue>> toolHistory = entry.getValue();
                
                JSONObject toolStats = new JSONObject();
                for (Map.Entry<String, List<ParameterValue>> paramEntry : toolHistory.entrySet())
                {
                    String paramName = paramEntry.getKey();
                    List<ParameterValue> values = paramEntry.getValue();
                    
                    JSONObject paramStats = new JSONObject();
                    paramStats.put("unique_values", values.size());
                    
                    JSONArray valueArray = new JSONArray();
                    for (ParameterValue pv : values)
                    {
                        JSONObject valObj = new JSONObject();
                        valObj.put("value", pv.value);
                        valObj.put("count", pv.count);
                        valueArray.put(valObj);
                    }
                    
                    paramStats.put("values", valueArray);
                    toolStats.put(paramName, paramStats);
                }
                
                stats.put(toolName, toolStats);
            }
        }
        catch (Exception e)
        {
            android.util.Log.e(TAG, "生成统计信息失败", e);
        }
        
        return stats;
    }
    
    /**
     * 清空历史记录（用于测试或重置）
     */
    public void clear()
    {
        history.clear();
        android.util.Log.d(TAG, "✓ 已清空所有参数历史记录");
    }
    
    /**
     * 内部类：参数值及其使用统计
     */
    private static class ParameterValue
    {
        String value;
        int count;
        long lastUsedTime;
        
        ParameterValue(String value)
        {
            this.value = value;
            this.count = 1;
            this.lastUsedTime = System.currentTimeMillis();
        }
    }
}