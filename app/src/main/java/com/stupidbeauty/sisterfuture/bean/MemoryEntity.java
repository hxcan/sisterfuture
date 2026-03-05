

// MemoryEntity.java
package com.stupidbeauty.sisterfuture.bean;


// 在MemoryEntity.java顶部添加
import io.objectbox.annotation.Convert;
// 在MemoryEntity.java顶部添加
import io.objectbox.annotation.Entity;
import com.stupidbeauty.sisterfuture.bean.StringListConverter;
import io.objectbox.annotation.Id;
import java.util.List;
import java.util.Arrays;

// 在类名后添加
@Entity
public class MemoryEntity {
    @Id
    private long id;

    private String key;
    private String content;

    // @Convert(converter = StringListConverter.class, dbType = String.class)
    private List<String> tags; // ✅ 使用转换器将List转为JSON字符串存储

    private long timestamp;

    // Getters and Setters
    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
