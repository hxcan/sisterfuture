package com.stupidbeauty.sisterfuture.shopping;

import android.content.Context;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ShoppingListManager {

    private static final String DATA_FILE_PATH = "/data/shopping_list.json";
    private List<ShoppingItem> items;
    private final Gson gson;
    private final Context context;

    public ShoppingListManager(Context context) {
        this.context = context;
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

    // 5. 导出功能: 将购物清单导出为CSV文件
    public boolean exportToCsv(String filePath) {
        try (PrintWriter writer = new PrintWriter(new File(filePath), StandardCharsets.UTF_8.name())) {
            // 写入表头
            writer.println("ID,物品名称,数量,单位,分类,状态,所属老人,最后更新时间");
            // 写入数据行
            for (ShoppingItem item : items) {
                writer.printf("%s,%s,%d,%s,%s,%s,%s,%s%n",
                        item.getId(),
                        item.getName(),
                        item.getQuantity(),
                        item.getUnit(),
                        item.getCategory(),
                        item.getStatus(),
                        item.getOwner(),
                        item.getLastUpdated()
                );
            }
            return true;
        } catch (IOException e) {
            System.err.println("无法导出购物清单到文件: " + e.getMessage());
            return false;
        }
    }

    // 6. 导入功能: 从CSV文件导入购物清单数据，支持自动校验和错误处理
    public boolean importFromCsv(String filePath) {
        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(filePath, StandardCharsets.UTF_8))) {
            String header = reader.readLine();
            if (header == null || !header.trim().equals("ID,物品名称,数量,单位,分类,状态,所属老人,最后更新时间")) {
                System.err.println("CSV文件格式不正确，缺少正确的表头。");
                return false;
            }

            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",", -1); // -1 保证最后一个字段即使为空也能被分割出来
                if (parts.length < 8) {
                    System.err.println("CSV文件中某行数据不完整，跳过: " + line);
                    continue;
                }

                // 解析数据（这里只做简单验证，实际应用中应有更复杂的逻辑）
                String id = parts[0].trim();
                String name = parts[1].trim();
                int quantity = Integer.parseInt(parts[2].trim());
                String unit = parts[3].trim();
                String category = parts[4].trim();
                String status = parts[5].trim();
                String owner = parts[6].trim();
                String lastUpdated = parts[7].trim();

                // 验證關鍵字段
                if (id.isEmpty() || name.isEmpty() || quantity <= 0) {
                    System.err.println("数据验证失败，跳过无效行: " + line);
                    continue;
                }

                if (!isValidStatus(status)) {
                    System.err.println("状态值不合法，跳过: " + line);
                    continue;
                }

                // 创建新条目并添加
                ShoppingItem newItem = new ShoppingItem();
                newItem.setId(id);
                newItem.setName(name);
                newItem.setQuantity(quantity);
                newItem.setUnit(unit);
                newItem.setCategory(category);
                newItem.setStatus(status);
                newItem.setOwner(owner);
                newItem.setLastUpdated(lastUpdated);
                items.add(newItem);
            }
            saveItems(); // 保存所有导入的数据
            return true;
        } catch (IOException e) {
            System.err.println("无法从文件导入购物清单: " + e.getMessage());
            return false;
        } catch (NumberFormatException e) {
            System.err.println("数据格式错误，无法解析数量: " + e.getMessage());
            return false;
        }
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
