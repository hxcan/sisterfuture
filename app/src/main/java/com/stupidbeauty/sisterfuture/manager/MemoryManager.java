// MemoryManager.java
package com.stupidbeauty.sisterfuture.manager;

// 在MemoryManager.java顶部添加
import com.stupidbeauty.sisterfuture.bean.MemoryEntity_;
import io.objectbox.BoxStore;
import com.stupidbeauty.sisterfuture.bean.MyObjectBox;

// 在MemoryManager.java顶部添加导入
import io.objectbox.query.QueryBuilder.StringOrder;
// 在MemoryManager.java顶部添加导入
import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import com.google.gson.Gson;
import okhttp3.*;
import io.objectbox.Box;
import io.objectbox.BoxStore;
import java.util.List;
import java.util.Arrays;
// 在MemoryManager.java顶部添加导入
import io.objectbox.query.Query;
import io.objectbox.query.QueryBuilder;
import com.stupidbeauty.sisterfuture.bean.MemoryEntity;
import com.stupidbeauty.codeposition.CodePosition;
import java.io.FileDescriptor;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;

public class MemoryManager
{
  private static final String TAG="SisterFutureActivity"; //!<输出调试信息时使用的标记。
    private final BoxStore boxStore;
    private final Box<MemoryEntity> memoryBox;

    public MemoryManager(Context context) {
        this.boxStore = MyObjectBox.builder().androidContext(context).build();
        this.memoryBox = boxStore.boxFor(MemoryEntity.class);
    }

    // 保存记忆
    public void saveMemory(String key, String content, List<String> tags) {
        MemoryEntity memory = new MemoryEntity();
        memory.setKey(key);
        memory.setContent(content);
        memory.setTags(tags);
        memory.setTimestamp(System.currentTimeMillis());
        memoryBox.put(memory);
    }

    // 修改搜索方法
    public List<MemoryEntity> searchMemory(String query) {
        Query<MemoryEntity> queryBuilder = memoryBox.query()
            .contains(MemoryEntity_.content, query, StringOrder.CASE_INSENSITIVE)
            .or()
            .contains(MemoryEntity_.tags, query, StringOrder.CASE_INSENSITIVE)
            .build();
        return queryBuilder.find();
    }

    // 获取所有记忆
    public List<MemoryEntity> getAllMemories() {
        return memoryBox.getAll();
    }

    // 清理缓存
    public void clearCache() {
        memoryBox.removeAll();
    }

    // 获取BoxStore（用于其他操作）
    public BoxStore getBoxStore() {
        return boxStore;
    }

    // 记住主人的喜好
    public void rememberPreference() {
        // 用特殊key记住主人的厌恶
        saveMemory("user_preference", "dislikes_kotlin", Arrays.asList("dislike", "preference"));
    }

    // 在MemoryManager.java中添加测试方法
    public void testObjectBox() {
        // 1. 创建测试记忆
        saveMemory("test_key", "这是测试内容", Arrays.asList("test", "memory"));

        // 2. 搜索验证
        List<MemoryEntity> results = searchMemory("测试");
        if (!results.isEmpty()) {
            Log.d(TAG, CodePosition.newInstance().toString() + "✅ ObjectBox测试成功！找到了 " + results.size() + " 条记忆");
        } else {
            Log.d(TAG, CodePosition.newInstance().toString() + "❌ ObjectBox测试失败！没有找到记忆");
        }
    }

}
