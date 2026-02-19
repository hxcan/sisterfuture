package com.stupidbeauty.sisterfuture.tool;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import org.json.JSONObject;

import com.stupidbeauty.sisterfuture.shopping.ShoppingItem;
import com.stupidbeauty.sisterfuture.shopping.ShoppingListManager;

/**
 * 添加购物清单条目工具类。
 * 该类实现了与 'AddNoteTool' 相同的接口，确保能正确地集成到整个工具体系中。
 */
public class AddShoppingItemTool implements Tool {
    private static final String TAG = "AddShoppingItemTool";
    private final Context context;
    private ShoppingListManager shoppingListManager;

    public AddShoppingItemTool(Context context) {
        this.context = context;
        this.shoppingListManager = new ShoppingListManager(context);
    }

    @Override
    public String getName() {
        return "add_shopping_item";
    }

    @Override
    public JSONObject getDefinition() {
        try {
            JSONObject functionDef = new JSONObject();
            functionDef.put("name", "add_shopping_item");
            functionDef.put("description", "向购物清单中添加一个商品。");

            JSONObject parameters = new JSONObject();
            parameters.put("type", "object");
            parameters.put("properties", new JSONObject()
                .put("name", new JSONObject()
                    .put("type", "string")
                    .put("description", "物品名称。"))
                .put("quantity", new JSONObject()
                    .put("type", "integer")
                    .put("description", "物品数量。"))
                .put("unit", new JSONObject()
                    .put("type", "string")
                    .put("description", "物品单位（如：个、瓶、斤）。"))
                .put("category", new JSONObject()
                    .put("type", "string")
                    .put("description", "物品分类（如：食品、药品、日用品）。"))
                .put("owner", new JSONObject()
                    .put("type", "string")
                    .put("description", "所属老人（如：父亲、母亲）。"))
            );
            parameters.put("required", new JSONObject().put("required", new String[]{"name", "quantity"}));

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
        String name = arguments.getString("name");
        Integer quantity = arguments.getInt("quantity");
        String unit = arguments.optString("unit", "个");
        String category = arguments.optString("category", "其他");
        String owner = arguments.optString("owner", "未知");

        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("物品名称不能为空。");
        }
        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("物品数量必须大于0。");
        }

        // 构建购物清单条目
        ShoppingItem item = new ShoppingItem();
        item.setName(name.trim());
        item.setQuantity(quantity);
        item.setUnit(unit.trim());
        item.setCategory(category.trim());
        item.setOwner(owner.trim());
        item.setStatus("待购买");
        item.setLastUpdated(java.time.LocalDateTime.now().toString());

        // 调用管理器添加条目
        boolean success = shoppingListManager.addItem(item);

        JSONObject result = new JSONObject();
        result.put("success", success);
        result.put("message", success ? "已成功添加物品 '" + name + "' 到购物清单。" : "添加失败，请检查数据或系统错误。");
        result.put("processed_at", System.currentTimeMillis());
        // 已移除不当附加信息

        return result;
    }

    @Override
    public String getDefaultSystemPromptEnhancement() {
        return "必须在用户明确要求添加购物清单条目时才调用此工具。需要提供物品名称和数量。";
    }
}