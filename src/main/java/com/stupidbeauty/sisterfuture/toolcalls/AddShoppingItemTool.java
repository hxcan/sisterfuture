package com.stupidbeauty.sisterfuture.toolcalls;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.stupidbeauty.sisterfuture.shopping.ShoppingItem;
import com.stupidbeauty.sisterfuture.shopping.ShoppingListManager;

/**
 * 一个用于向购物清单中添加商品的工具类。
 * 它遵循与 'AddNoteTool' 类一致的模式，提供了一个简洁的接口来执行此操作。
 */
public class AddShoppingItemTool {

    private final ShoppingListManager manager;
    private final Gson gson;

    public AddShoppingItemTool() {
        this.manager = new ShoppingListManager();
        this.gson = new Gson();
    }

    /**
     * 向购物清单中添加一个商品。
     * 
     * @param name 物品名称。
     * @param quantity 物品数量。
     * @param unit 物品单位（如：个、瓶、斤）。
     * @param category 物品分类（如：食品、药品、日用品）。
     * @param owner 所属老人（如：父亲、母亲）。
     * @return 一个包含操作结果的JSON字符串，包含成功状态和消息。
     */
    public String execute(String name, Integer quantity, String unit, String category, String owner) {
        try {
            // 验证输入参数
            if (name == null || name.trim().isEmpty()) {
                return createResponse(false, "物品名称不能为空。");
            }
            if (quantity == null || quantity <= 0) {
                return createResponse(false, "物品数量必须大于0。");
            }
            if (unit == null || unit.trim().isEmpty()) {
                unit = "个"; // 默认单位
            }
            if (category == null || category.trim().isEmpty()) {
                category = "其他"; // 默认分类
            }
            if (owner == null || owner.trim().isEmpty()) {
                owner = "未知"; // 默认所属人
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
            boolean success = manager.addItem(item);
            return createResponse(success, success ? "已成功添加物品 '" + name + "' 到购物清单。" : "添加失败，请检查数据或系统错误。");

        } catch (Exception e) {
            return createResponse(false, "执行操作时发生异常: " + e.getMessage());
        }
    }

    /**
     * 创建一个标准的响应JSON字符串。
     */
    private String createResponse(boolean success, String message) {
        JsonObject response = new JsonObject();
        response.addProperty("success", success);
        response.addProperty("message", message);
        return gson.toJson(response);
    }
}