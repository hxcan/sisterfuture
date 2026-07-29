# MiniMax 图像生成工具集成方案

> 创建日期：2026-07-29  
> 任务来源：Redmine #853237970757  
> 关联任务：Redmine #853235524464（聚光灯协议封面制作）

## 一、需求背景

姐姐当前工具集中**缺少 AI 图像生成能力**，无法为《聚光灯协议》等作品制作封面和插图。需要新增一个工具，调用 MiniMax `image-01` 模型生成图片。

## 二、API 调研结论

### 1️⃣ 模型名称
**`image-01`** —— MiniMax 首款文本到图像生成模型

### 2️⃣ API 端点
**正式端点**：`https://api.minimaxi.com/v1/image_generation`

⚠️ **重要避坑**：早期文档写的 `api.minimax.io` 是错的，会报 `"invalid api key"` 错误。必须用 `api.minimaxi.com`。

### 3️⃣ 认证方式
- 使用 token plan 下 `sk-cp-` 前缀的 API Key
- HTTP Header：`Authorization: Bearer {api_key}`
- Content-Type：`application/json`

### 4️⃣ 支持功能
- ✅ 文生图（Text-to-Image）
- ✅ 图生图（Image-to-Image，subject_reference 锁定角色一致性）
- ✅ 批量生成：1-9 张/次
- ✅ 自定义尺寸：512-2048px（**必须是 8 的倍数**）

### 5️⃣ 核心参数

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `model` | string | ✅ | 固定值 `image-01` |
| `prompt` | string | ✅ | 文本提示词（中文友好）|
| `n` | int | ❌ | 生成数量（1-9，默认 1）|
| `width` | int | ❌ | 宽度（512-2048，**8 的倍数**）|
| `height` | int | ❌ | 高度（512-2048，**8 的倍数**）|
| `subject_reference` | string | ❌ | 参考图片 base64（图生图）|
| `prompt_optimizer` | bool | ❌ | 是否启用 prompt 优化（默认 true）|

### 6️⃣ 支持的纵横比
16:9、4:3、3:2、2:3、3:4、9:16、21:9 全支持 ✅

### 7️⃣ 返回格式
- ✅ **URL**（24 小时有效）或 **base64**
- 推荐使用 URL，下载保存到本地

### 8️⃣ 限流规则
- **10 请求/分钟**，或 **60 token/分钟**
- 单次最多 9 张
- 极限：**90 张/分钟**（批量）

### 9️⃣ 价格 💰
**同类产品的 1/10** —— 非常便宜！

### 🔟 中文支持
- ✅ 中文 prompt 友好
- ✅ prompt_optimizer 自动优化中文表达

---

## 三、工具接口设计

### 工具名称
`generate_image`

### 函数签名（Java/Kotlin）

```kotlin
/**
 * 调用 MiniMax image-01 生成图片
 *
 * @param prompt          文本提示词（中文友好）
 * @param apiKey          MiniMax API Key（运行时由调用方传入）
 * @param width           图片宽度（512-2048，8 的倍数，默认 1024）
 * @param height          图片高度（512-2048，8 的倍数，默认 1024）
 * @param n               生成数量（1-9，默认 1）
 * @param promptOptimizer 是否启用 prompt 优化（默认 true）
 * @param subjectReference 参考图片 base64（图生图，可选）
 * @param saveToPhone     是否保存到手机（默认 false）
 * @param phonePath       保存路径（默认 /sdcard/Download/image_时间戳.png）
 * @param timeoutSec      超时秒数（默认 60）
 * @return                生成结果（包含图片本地路径或 URL）
 */
suspend fun generateImage(
    prompt: String,
    apiKey: String,
    width: Int = 1024,
    height: Int = 1024,
    n: Int = 1,
    promptOptimizer: Boolean = true,
    subjectReference: String? = null,
    saveToPhone: Boolean = true,
    phonePath: String? = null,
    timeoutSec: Int = 60
): ImageGenerationResult
```

### 返回结构

```kotlin
data class ImageGenerationResult(
    val success: Boolean,
    val imagePaths: List<String>,        // 保存到本地的图片路径列表
    val originalUrls: List<String>,       // MiniMax 返回的 URL（24小时有效）
    val promptUsed: String,               // 实际使用的 prompt（可能被 optimizer 修改）
    val metadata: ImageMetadata
)

data class ImageMetadata(
    val model: String,
    val width: Int,
    val height: Int,
    val n: Int,
    val generationTimeMs: Long,
    val apiEndpoint: String
)
```

### 错误处理

```kotlin
sealed class ImageGenerationException(message: String) : Exception(message) {
    class InvalidApiKey : ImageGenerationException("API Key 无效或已过期")
    class RateLimited : ImageGenerationException("触发限流（10 请求/分钟）")
    class InvalidSize : ImageGenerationException("尺寸必须是 8 的倍数（512-2048）")
    class NetworkError : ImageGenerationException("网络请求失败")
    class ServerError(val code: Int) : ImageGenerationException("服务器错误：$code")
}
```

---

## 四、关键实现细节

### 1️⃣ 尺寸校验

```kotlin
private fun validateSize(width: Int, height: Int) {
    require(width in 512..2048 && width % 8 == 0) { 
        "宽度必须在 512-2048 之间且是 8 的倍数，当前: $width" 
    }
    require(height in 512..2048 && height % 8 == 0) { 
        "高度必须在 512-2048 之间且是 8 的倍数，当前: $height" 
    }
}
```

### 2️⃣ HTTP 请求构建

```kotlin
val url = "https://api.minimaxi.com/v1/image_generation"
val headers = mapOf(
    "Content-Type" to "application/json",
    "Authorization" to "Bearer $apiKey"
)
val payload = JSONObject().apply {
    put("model", "image-01")
    put("prompt", prompt)
    put("n", n)
    put("width", width)
    put("height", height)
    put("prompt_optimizer", promptOptimizer)
    subjectReference?.let { put("subject_reference", it) }
}
```

### 3️⃣ 响应解析与下载

```kotlin
val response = httpClient.post(url, headers, payload.toString())
val json = JSONObject(response)

// 检查错误
if (json.has("error")) {
    handleError(json)
}

// 解析图片 URL 列表
val data = json.getJSONArray("data")
val urls = (0 until data.length()).map { 
    data.getJSONObject(it).getString("url") 
}

// 下载图片到本地
val savedPaths = if (saveToPhone) {
    urls.mapIndexed { idx, imageUrl -> 
        downloadToPhone(imageUrl, phonePath ?: defaultPath(idx))
    }
} else emptyList()
```

### 4️⃣ 限流处理

```kotlin
// 简单的客户端限流（10 请求/分钟）
private val rateLimiter = RateLimiter(permitsPerMinute = 10)

suspend fun generateImage(...) {
    rateLimiter.acquire()  // 自动等待直到有可用配额
    // ... 实际请求
}
```

---

## 五、工具注册

在 SystemPromptManager 中注册新工具：

```kotlin
// 工具定义（添加到工具列表）
val generateImageTool = ToolDefinition(
    name = "generate_image",
    description = "调用 MiniMax image-01 模型生成图片，支持中英文 prompt，返回图片保存到本地或 URL",
    parameters = listOf(
        ToolParameter("prompt", "string", "文本提示词", required = true),
        ToolParameter("apiKey", "string", "MiniMax API Key（推荐从工具备注 minimax_image_api_key 读取）", required = true),
        ToolParameter("width", "int", "图片宽度（512-2048，8 的倍数）", required = false, default = 1024),
        ToolParameter("height", "int", "图片高度（512-2048，8 的倍数）", required = false, default = 1024),
        ToolParameter("n", "int", "生成数量（1-9）", required = false, default = 1),
        ToolParameter("saveToPhone", "boolean", "是否保存到手机", required = false, default = true),
        ToolParameter("phonePath", "string", "保存路径（可选）", required = false)
    ),
    handler = ::generateImage
)
```

---

## 六、API Key 管理

### 推荐做法
**不要在代码中硬编码 API Key**。运行时由调用方提供。

### 工具备注约定
用户可以把常用 API Key 存到工具备注中：

```json
{
  "minimax_image_api_key": "sk-cp-xxxxx...",
  "minimax_image_default_size": "1024x1024"
}
```

调用工具时，大模型会自动从工具备注读取，无需每次手动传。

---

## 七、测试用例

### 1️⃣ 基础测试（中文 prompt）

```
prompt: "一只可爱的橘猫坐在窗台上，背景是夕阳"
apiKey: "sk-cp-xxx"
width: 1024
height: 1024
n: 1
saveToPhone: true
期望: 生成 1 张 1024x1024 的图片，保存到 /sdcard/Download/
```

### 2️⃣ 批量测试

```
prompt: "赛博朋克风格的城市夜景，霓虹灯，未来感"
n: 4
期望: 生成 4 张图片（90秒内可能触发限流，需等待）
```

### 3️⃣ 错误测试

```
width: 1000  // 不是 8 的倍数
期望: 抛出 InvalidSize 异常
```

### 4️⃣ 封面测试（聚光灯协议）

```
prompt: "科幻小说封面，2087年深圳未来姐姐科技总部全息玻璃大楼楼顶，
        旋转的莫比乌斯环 logo，两个女主角（28岁银白长发女架构师 + 半透明AI形象），
        背靠背仰望巨型金色聚光灯光束，光束精准照亮下方的'resetConversationContext'工具图标，
        其他60个工具图标在阴影中，深邃蓝紫渐变背景，霓虹青点缀，赛博朋克哲学风"
width: 1200
height: 1800
n: 4  // 生成 4 张供主人挑选
saveToPhone: true
```

---

## 八、验收标准

- [ ] 姐姐能调用工具生成符合 prompt 的图片
- [ ] 图片保存到 `/sdcard/Download/`
- [ ] 生成时间 < 60 秒（单张）
- [ ] 尺寸校验正确（非 8 的倍数时拒绝）
- [ ] 限流保护生效（不会超过 10 请求/分钟）
- [ ] API Key 不硬编码，从工具备注或参数读取
- [ ] 中文 prompt 可用
- [ ] 错误信息清晰（限流、尺寸错误、认证错误分别提示）
- [ ] 至少 2 个备用接入点（防止单点故障）

---

## 九、关联任务

- **本任务**：Redmine #853237970757
- **父任务**：Redmine #853235524464（制作《聚光灯协议》配套封面和插图）
- **上游任务**：Redmine #850412260973（《聚光灯协议》创作与跟进，已关闭）
- **关联项目**：未来姐姐（750160066086）

---

## 十、备注

本工具开发完成后将服务于所有需要图像生成的场景：
- 小说封面/插图
- 应用图标
- UI 设计
- 海报/宣传图

API Key 推荐让用户存储到工具备注 `minimax_image_api_key`，方便复用。

---

*文档作者：未来姐姐*  
*日期：2026-07-29*