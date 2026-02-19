package com.stupidbeauty.sisterfuture.shopping;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ShoppingListManager {

    private static final String DATA_FILE_PATH = "/data/shopping_list.json";
    private List<ShoppingItem> items;
    private final Gson gson;

    public ShoppingListManager() {
        this.items = new ArrayList<>();
        this.gson = new Gson();
        loadItems();
    }

    // 1. 创建条目 (Create)
    public boolean addItem(ShoppingItem item) {
        if (item == null || item.getName() == null || item.getQuantity() <= 0) {
            return false;
        }
        item.setId(generateId());
        item.setStatus("待购买");
        item.setLastUpdated(LocalDateTime.now().toString());
        items.add(item);
        saveItems();
        return true;
    }

    // 2. 查询条目 (Read)
    public List<ShoppingItem> searchItems(String query, String status) {
        List<ShoppingItem> results = new ArrayList<>();
        for (ShoppingItem item : items) {
            if ((query == null || item.getName().toLowerCase().contains(query.toLowerCase())) &&
                (status == null || item.getStatus().equals(status))) {
                results.add(item);
            }
        }
        return results;
    }

    // 3. 更新条目 (Update)
    public boolean updateItem(String id, Map<String, Object> updates) {
        for (ShoppingItem item : items) {
            if (item.getId().equals(id)) {
                if (updates.containsKey("quantity")) {
                    int quantity = (int) updates.get("quantity");
                    if (quantity > 0) item.setQuantity(quantity);
                }
                if (updates.containsKey("status")) {
                    String status = (String) updates.get("status");
                    if (isValidStatus(status)) {
                        item.setStatus(status);
                    }
                }
                item.setLastUpdated(LocalDateTime.now().toString());
                saveItems();
                return true;
            }
        }
        return false;
    }

    // 4. 删除条目 (Delete)
    public boolean deleteItem(String id) {
        ShoppingItem itemToDelete = null;
        for (ShoppingItem item : items) {
            if (item.getId().equals(id)) {
                itemToDelete = item;
                break;
            }
        }
        if (itemToDelete != null) {
            items.remove(itemToDelete);
            saveItems();
            return true;
        }
        return false;
    }

    // 辅助方法：生成唯一ID
    private String generateId() {
        return "item_" + System.currentTimeMillis();
    }

    // 辅助方法：验证状态是否有效
    private boolean isValidStatus(String status) {
        return status != null && (status.equals("待购买") || status.equals("已购买") || status.equals("已失效"));
    }

    // 读取数据文件 (使用Gson)
    private void loadItems() {
        try {
            File file = new File(DATA_FILE_PATH);
            if (file.exists()) {
                try (FileReader reader = new FileReader(file)) {
                    items = gson.fromJson(reader, new TypeToken<List<ShoppingItem>>(){}.getType());
                }
            }
        } catch (IOException e) {
            System.err.println("无法加载购物清单: " + e.getMessage());
        }
    }

    // 保存数据文件 (使用Gson)
    private void saveItems() {
        try {
            File file = new File(DATA_FILE_PATH);
            file.getParentFile().mkdirs(); // 确保目录存在
            try (FileWriter writer = new FileWriter(file)) {
                gson.toJson(items, writer);
            }
        } catch (IOException e) {
            System.err.println("无法保存购物清单: " + e.getMessage());
        }
    }

    // Getter
    public List<ShoppingItem> getItems() {
        return items;
    }
}

// 购物清单条目类
class ShoppingItem {
    private String id;
    private String name;
    private int quantity;
    private String unit;
    private String category;
    private String status;
    private String owner;
    private String lastUpdated;

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getOwner() { return owner; }
    public void setOwner(String owner) { this.owner = owner; }

    public String getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(String lastUpdated) { this.lastUpdated = lastUpdated; }
}