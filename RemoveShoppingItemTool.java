package com.stupidbeauty.sisterfuture.tool;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import org.json.JSONObject;
import org.json.JSONArray;

import com.stupidbeauty.sisterfuture.shopping.ShoppingListManager;

/**
 * 从购物清单中删除条目工具类。
 * 该类实现了与 'AddShoppingItemTool' 相同的接口，确保能正确地集成到整个工具体系中。
 */
public class RemoveShoppingItemTool implements Tool {
    private static final String TAG = "RemoveShoppingItemTool";
    private final Context context;

    public RemoveShoppingItemTool(Context context) {
        this.context = context;
    }

    @Override
    public String getName() {
        return "remove_shopping_item";
    }

    @Override
    public JSONObject getDefinition() {
        try {
            JSONObject functionDef = new JSONObject();
            functionDef.put("name", "remove_shopping_item");
            functionDef.put("description", "从购物清单中删除一个商品条目，根据其唯一 ID。"
                + "必须在用户明确要求删除购物清单项时才调用此工具。需要提供物品的唯一 ID。");

            JSONObject parameters = new JSONObject();
            parameters.put("type", "object");
            parameters.put("properties", new JSONObject()
                .put("item_id", new JSONObject()
                    .put("type", "string")
                    .put("description", "物品的唯一标识符（ID）。"))
            );
            parameters.put("required", new JSONArray().put("item_id")); // ✅ 修复：使用 JSONArray

            functionDef.put("parameters", parameters);
            return new JSONObject().put("type", "function").put("function", functionDef);
        } catch (Exception e) {
            Log.e(TAG, "Failed to build definition", e);
            return new JSONObject();
        }
    }

    @Override
    public boolean shouldInclude() {
        return true;
    }

    @Override
    public boolean isAsync() {
        return false;
    }

    @Override
    public JSONObject execute(JSONObject arguments) throws Exception {
        // 解析参数
        String itemId = arguments.getString("item_id");
        if (itemId == null || itemId.trim().isEmpty()) {
            throw new IllegalArgumentException("物品的唯一标识符（ID）不能为空。");
        }

        // 每次执行时都创建新的 ShoppingListManager 实例，强制重新加载数据
        ShoppingListManager shoppingListManager = new ShoppingListManager(context);
        boolean success = shoppingListManager.deleteItem(itemId);

        // 构建返回结果
        JSONObject result = new JSONObject();
        result.put("success", success);
        result.put("message", success ? "已成功删除物品 '" + itemId + "' 从购物清单。" : "删除失败，可能该物品不存在或系统错误。");
        result.put("processed_at", System.currentTimeMillis());
        
        return result;
    }

    @Override
    public String getDefaultSystemPromptEnhancement() {
        return "必须在用户明确要求删除购物清单项时才调用此工具。需要提供物品的唯一 ID.";
    }
}