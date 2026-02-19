package com.stupidbeauty.sisterfuture.tool;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import org.json.JSONObject;

import com.stupidbeauty.sisterfuture.shopping.ShoppingItem;
import com.stupidbeauty.sisterfuture.shopping.ShoppingListManager;

/**
 * 查询购物清单条目工具类。
 * 该类实现了与 'AddShoppingItemTool' 相同的接口，确保能正确地集成到整个工具体系中。
 */
public class ListShoppingItemsTool implements Tool {
    private static final String TAG = "ListShoppingItemsTool";
    private final Context context;
    private ShoppingListManager shoppingListManager;

    public ListShoppingItemsTool(Context context) {
        this.context = context;
        this.shoppingListManager = new ShoppingListManager(context);
    }

    @Override
    public String getName() {
        return "list_shopping_items";
    }

    @Override
    public JSONObject getDefinition() {
        try {
            JSONObject functionDef = new JSONObject();
            functionDef.put("name", "list_shopping_items");
            functionDef.put("description", "查询当前购物清单中的所有物品条目。返回完整的清单内容，包括名称、数量、状态等信息。");

            JSONObject parameters = new JSONObject();
            parameters.put("type", "object");
            parameters.put("properties", new JSONObject()); // 无参数

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
        // 获取所有购物项，使用正确的 getter 方法
        java.util.List<ShoppingItem> items = shoppingListManager.getItems();

        // 构建返回结果
        JSONObject result = new JSONObject();
        result.put("items", new org.json.JSONArray(items));
        result.put("count", items.size());
        result.put("message", "成功获取 " + items.size() + " 个购物项。");
        result.put("processed_at", System.currentTimeMillis());

        return result;
    }

    @Override
    public String getDefaultSystemPromptEnhancement() {
        return "必须在用户明确要求列出购物清单时才调用此工具。不接受任何参数，直接返回全部条目。";
    }
}