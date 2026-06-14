package com.stupidbeauty.sisterfuture.shopping;

import android.content.Context;
import android.content.ContextWrapper;
import android.util.Log;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.stupidbeauty.sisterfuture.utils.FileLogger;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ShoppingListManager {

    private static final String TAG = "ShoppingListManager";
    private static final String DATA_FILE_NAME = "shopping_list.json";
    private List<ShoppingItem> items;
    private final Gson gson;
    private final Context context;

    public ShoppingListManager(Context context) {
        this.context = context;
        this.items = new ArrayList<>();
        this.gson = new Gson();
        FileLogger.d(TAG, "CONSTRUCTOR: about to call loadItems()");
        loadItems();
        FileLogger.d(TAG, "CONSTRUCTOR: loadItems() done, items.size=" + (items == null ? "null" : items.size()));
    }

    // 1. 创建条目 (Create)
    public boolean addItem(ShoppingItem item) {
        if (item == null || item.getName() == null || item.getQuantity() <=0) {
            return false;
        }
        item.setId(generateId());
        item.setStatus("待购买");
        item.setLastUpdated(String.valueOf(System.currentTimeMillis()));
        FileLogger.d(TAG, "addItem: new item id=[" + item.getId() + "] (class=" + item.getId().getClass().getSimpleName() + ")");
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
                item.setLastUpdated(String.valueOf(System.currentTimeMillis()));
                saveItems();
                return true;
            }
        }
        return false;
    }

    // 4. 删除条目 (Delete)
    public boolean deleteItem(String id) {
        FileLogger.d(TAG, "=== deleteItem START ===");
        FileLogger.d(TAG, "deleteItem: input id=[" + id + "] (class=" + (id == null ? "null" : id.getClass().getSimpleName()) + ")");
        FileLogger.d(TAG, "deleteItem: items.size=" + items.size());

        if (items != null && !items.isEmpty()) {
            String allIds = items.stream()
                .map(item -> "[" + (item.getId() == null ? "null" : item.getId()) + "/" + (item.getId() == null ? "null" : item.getId().getClass().getSimpleName()) + "]")
                .collect(Collectors.joining(", "));
            FileLogger.d(TAG, "deleteItem: all item IDs in memory: " + allIds);
        }

        ShoppingItem itemToDelete = null;
        for (ShoppingItem item : items) {
            String itemIdInList = item.getId();
            boolean equalsResult = (id != null && itemIdInList != null && id.equals(itemIdInList));
            FileLogger.d(TAG, "deleteItem: comparing input=[" + id + "] vs item=[" + itemIdInList + "] (itemId class=" + (itemIdInList == null ? "null" : itemIdInList.getClass().getSimpleName()) + ") | equals=" + equalsResult);
            if (equalsResult) {
                itemToDelete = item;
                break;
            }
        }

        FileLogger.d(TAG, "deleteItem: found? " + (itemToDelete != null));

        if (itemToDelete != null) {
            items.remove(itemToDelete);
            FileLogger.d(TAG, "deleteItem: removed from in-memory list, new size=" + items.size());
            saveItems();
            FileLogger.d(TAG, "deleteItem: saveItems() done");
            FileLogger.d(TAG, "=== deleteItem END (success) ===");
            return true;
        }
        FileLogger.d(TAG, "=== deleteItem END (not found) ===");
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
        java.io.BufferedReader reader = null;
        try {
            FileReader fileReader = new FileReader(filePath);
            reader = new java.io.BufferedReader(fileReader);
            String header = reader.readLine();
            if (header == null || !header.trim().equals("ID,物品名称,数量,单位,分类,状态,所属老人,最后更新时间")) {
                System.err.println("CSV文件格式不正确，缺少正确的表头。\n");
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
                    System.err.println("状态值不合法，跳过: " + status);
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
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    System.err.println("关闭读取器时发生错误: " + e.getMessage());
                }
            }
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
        FileLogger.d(TAG, "=== loadItems START ===");
        try {
            File file = getDataFile();
            FileLogger.d(TAG, "loadItems: file path=" + file.getAbsolutePath() + ", exists=" + file.exists() + ", size=" + (file.exists() ? file.length() : -1));

            if (file.exists()) {
                try (FileReader reader = new FileReader(file)) {
                    items = gson.fromJson(reader, new TypeToken<List<ShoppingItem>>(){}.getType());
                    FileLogger.d(TAG, "loadItems: after Gson parse, items is null? " + (items == null));
                    if (items != null) {
                        FileLogger.d(TAG, "loadItems: after Gson parse, items.size=" + items.size());
                        if (!items.isEmpty()) {
                            String allIds = items.stream()
                                .map(item -> "[" + (item.getId() == null ? "null" : item.getId()) + "/" + (item.getId() == null ? "null" : item.getId().getClass().getSimpleName()) + "]")
                                .collect(Collectors.joining(", "));
                            FileLogger.d(TAG, "loadItems: loaded item IDs: " + allIds);
                        }
                    }
                }
            } else {
                FileLogger.d(TAG, "loadItems: file does NOT exist, items remains empty");
            }
        } catch (IOException e) {
            FileLogger.d(TAG, "loadItems: IOException occurred: " + e.getMessage());
            System.err.println("无法加载购物清单: " + e.getMessage());
        } catch (Exception e) {
            FileLogger.d(TAG, "loadItems: Exception occurred: " + e.getClass().getName() + ": " + e.getMessage());
        }
        FileLogger.d(TAG, "=== loadItems END ===");
    }

    // 保存数据文件 (使用Gson)
    private void saveItems() {
        try {
            File file = getDataFile();
            file.getParentFile().mkdirs(); // 确保目录存在
            try (FileWriter writer = new FileWriter(file)) {
                gson.toJson(items, writer);
            }
        } catch (IOException e) {
            System.err.println("无法保存购物清单: " + e.getMessage());
        }
    }

    // 获取数据文件对象，使用应用私有目录
    private File getDataFile() {
        return new File(context.getFilesDir(), DATA_FILE_NAME);
    }

    // Getter
    public List<ShoppingItem> getItems() {
        return items;
    }
}
