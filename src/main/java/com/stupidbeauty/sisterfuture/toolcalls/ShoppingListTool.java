package com.stupidbeauty.sisterfuture.toolcalls;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.stupidbeauty.sisterfuture.shopping.ShoppingItem;
import com.stupidbeauty.sisterfuture.shopping.ShoppingListManager;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 一个基于大模型的工具调用（Tool Calls）接口，用于通过自然语言与购物清单进行交互。
 * 它封装了 ShoppingListManager 的所有功能，并将用户指令转换为具体的操作。
 */
public class ShoppingListTool {

    private final ShoppingListManager manager;
    private final Gson gson;

    public ShoppingListTool() {
        this.manager = new ShoppingListManager();
        this.gson = new Gson();
    }

    /**
     * 根据用户的自然语言指令执行相应的购物清单操作。
     * 
     * @param instruction 用户的指令，例如 "添加一瓶牛奶到购物清单" 或 "列出所有待购买的物品"
     * @return 操作结果的JSON字符串，包含成功状态和详细信息。
     */
    public String execute(String instruction) {
        try {
            // 1. 解析指令 (这里简化为关键词匹配，实际应用中应使用更复杂的NLP)
            Map<String, Object> action = parseInstruction(instruction);
            if (action == null) {
                return createResponse(false, "无法理解您的指令，请使用标准格式如 '添加 [物品] 到清单' 或 '查询 [关键字]'。");
            }

            String operation = (String) action.get("operation");
            Map<String, String> params = (Map<String>) action.get("params");

            // 2. 根据操作类型执行相应功能
            switch (operation.toLowerCase()) {
                case "add":
                    return handleAdd(params);
                case "remove":
                case "delete":
                    return handleDelete(params);
                case "update":
                case "modify":
                    return handleUpdate(params);
                case "query":
                case "list":
                    return handleQuery(params);
                case "export":
                    return handleExport(params);
                case "import":
                    return handleImport(params);
                default:
                    return createResponse(false, "不支持的操作: " + operation + ". 支持的操作有: add, remove, update, query, export, import.");
            }
        } catch (Exception e) {
            return createResponse(false, "执行操作时发生错误: " + e.getMessage());
        }
    }

    /**
     * 解析用户指令，提取操作类型和参数。
     * 这是一个简化的示例，真实场景下需要集成强大的NLP引擎。
     */
    private Map<String, Object> parseInstruction(String instruction) {
        if (instruction == null || instruction.trim().isEmpty()) {
            return null;
        }

        String lower = instruction.toLowerCase().trim();

        // 添加操作 (添加 [物品] 到清单)
        if (lower.startsWith("add") || lower.startsWith("添加")) {
            String item = extractText(lower, "add", "添加");
            if (item != null && !item.isEmpty()) {
                Map<String, String> params = new HashMap<>();
                params.put("name", item);
                params.put("quantity", "1");
                return Map.of("operation", "add", "params", params);
            }
        }

        // 删除操作 (删除 [物品])
        if (lower.startsWith("remove") || lower.startsWith("delete") || lower.startsWith("删除")) {
            String item = extractText(lower, "remove", "delete", "删除");
            if (item != null && !item.isEmpty()) {
                Map<String, String> params = new HashMap<>();
                params.put("name", item);
                return Map.of("operation", "remove", "params", params);
            }
        }

        // 更新操作 (更新 [物品] 数量为 [数量])
        if (lower.startsWith("update") || lower.startsWith("modify") || lower.startsWith("修改")) {
            String item = extractText(lower, "update", "modify", "修改");
            if (item != null && !item.isEmpty()) {
                // 简单地从指令中查找数字作为数量（非常粗略的解析）
                int quantity = 1; // 默认值
                // 这里应使用正则表达式或NLP来准确提取数量，为简化省略。
                Map<String, String> params = new HashMap<>();
                params.put("name", item);
                params.put("quantity", String.valueOf(quantity));
                return Map.of("operation", "update", "params", params);
            }
        }

        // 查询操作 (查询 [关键字] 或 列出所有待购买的物品)
        if (lower.startsWith("query") || lower.startsWith("list") || lower.startsWith("查看") || lower.startsWith("列出")) {
            String query = extractText(lower, "query", "list", "查看", "列出");
            if (query != null && !query.isEmpty()) {
                Map<String, String> params = new HashMap<>();
                params.put("query", query);
                return Map.of("operation", "query", "params", params);
            } else {
                Map<String, String> params = new HashMap<>();
                params.put("status", "待购买");
                return Map.of("operation", "query", "params", params);
            }
        }

        // 导出操作 (导出购物清单)
        if (lower.startsWith("export") || lower.startsWith("导出")) {
            Map<String, String> params = new HashMap<>();
            params.put("file", "/data/shopping_list_export.csv");
            return Map.of("operation", "export", "params", params);
        }

        // 导入操作 (导入购物清单)
        if (lower.startsWith("import") || lower.startsWith("导入")) {
            Map<String, String> params = new HashMap<>();
            params.put("file", "/data/shopping_list_import.csv");
            return Map.of("operation", "import", "params", params);
        }

        return null;
    }

    private String extractText(String text, String... keywords) {
        for (String keyword : keywords) {
            int index = text.indexOf(keyword);
            if (index != -1) {
                int start = index + keyword.length();
                int end = text.length();
                // 简单地提取到句尾，实际应用中需要更精确的分词逻辑。
                return text.substring(start, end).trim();
            }
        }
        return null;
    }

    private String handleAdd(Map<String, String> params) {
        String name = params.get("name");
        int quantity = Integer.parseInt(params.getOrDefault("quantity", "1"));
        ShoppingItem item = new ShoppingItem();
        item.setName(name);
        item.setQuantity(quantity);
        item.setUnit("个");
        item.setCategory("食品");
        item.setOwner("父亲");
        boolean success = manager.addItem(item);
        return createResponse(success, success ? "已成功添加 " + name + " 到购物清单。" : "添加失败，可能物品名称为空或数量无效。");
    }

    private String handleDelete(Map<String, String> params) {
        String name = params.get("name");
        List<ShoppingItem> items = manager.searchItems(name, null);
        if (items.isEmpty()) {
            return createResponse(false, "未找到名为 '" + name + "' 的物品，无法删除。");
        }
        // 简化：只删除第一个匹配项
        boolean success = manager.deleteItem(items.get(0).getId());
        return createResponse(success, success ? "已成功删除物品 '" + name + "'。" : "删除失败。");
    }

    private String handleUpdate(Map<String, String> params) {
        String name = params.get("name");
        int quantity = Integer.parseInt(params.getOrDefault("quantity", "1"));
        List<ShoppingItem> items = manager.searchItems(name, null);
        if (items.isEmpty()) {
            return createResponse(false, "未找到名为 '" + name + "' 的物品，无法更新。");
        }
        Map<String, Object> updates = new HashMap<>();
        updates.put("quantity", quantity);
        boolean success = manager.updateItem(items.get(0).getId(), updates);
        return createResponse(success, success ? "已成功将物品 '" + name + "' 的数量更新为 " + quantity + "。" : "更新失败。");
    }

    private String handleQuery(Map<String, String> params) {
        String query = params.get("query");
        String status = params.get("status");
        List<ShoppingItem> items = manager.searchItems(query, status);
        if (items.isEmpty()) {
            return createResponse(true, "未找到匹配的物品。");
        }
        return createResponse(true, "找到 " + items.size() + " 个匹配的物品: " + gson.toJson(items));
    }

    private String handleExport(Map<String, String> params) {
        String filePath = params.get("file");
        boolean success = manager.exportToCsv(filePath);
        return createResponse(success, success ? "购物清单已成功导出到 " + filePath + "。" : "导出失败。");
    }

    private String handleImport(Map<String, String> params) {
        String filePath = params.get("file");
        boolean success = manager.importFromCsv(filePath);
        return createResponse(success, success ? "购物清单已成功从 " + filePath + " 导入。" : "导入失败。");
    }

    private String createResponse(boolean success, String message) {
        JsonObject response = new JsonObject();
        response.addProperty("success", success);
        response.addProperty("message", message);
        return gson.toJson(response);
    }
}