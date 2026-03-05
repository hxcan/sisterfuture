# SSH 远程命令执行工具使用说明

## 📦 核心类

### RemoteCommandExecutor
提供异步执行远程 SSH 命令的能力。

**方法签名：**
```java
Future<CommandResult> executeAsync(
    Context context,      // Android 上下文
    String hostname,      // 目标主机 IP/域名 (如 "192.168.1.100")
    int port,             // SSH 端口 (默认 22)
    String username,      // 登录用户名
    byte[] privateKeyBytes, // 已解密的私钥内容
    String command        // 要执行的 Shell 命令
)
```

### CommandResult
封装执行结果的标准化对象。

**字段：**
- `status`: "success" 或 "failed"
- `stdout`: 标准输出内容
- `stderr`: 错误输出内容
- `exitCode`: 进程退出码

---

## 🔧 使用步骤

### 1. 加载私钥
```java
// 从 Victoria Fresh VFS 获取加密私钥
byte[] encryptedKey = RemoteCommandExecutor.loadPrivateKeyFromVFS(context);

// TODO: 在此处添加解密逻辑（建议 AES-GCM）
byte[] decryptedKey = decryptPrivateKey(encryptedKey, userPassword);
```

### 2. 执行命令
```java
Future<CommandResult> future = RemoteCommandExecutor.executeAsync(
    context, 
    "gx10.example.com",   // 目标主机
    22,                    // SSH 端口
    "sisterfuture",        // 用户名
    decryptedKey,          // 已解密私钥
    "ls -l /opt/models/"   // 要执行的命令
);

// 等待结果（可设置超时）
try {
    CommandResult result = future.get(60, TimeUnit.SECONDS);
    if ("success".equals(result.status)) {
        Log.i("SSH", "命令执行成功:\n" + result.stdout);
    } else {
        Log.e("SSH", "命令失败: " + result.stderr);
    }
} catch (Exception e) {
    Log.e("SSH", "执行超时或异常: " + e.getMessage());
}
```

---

## 🌰 典型场景

### 场景 1: 模型文件传输
```java
executeAsync(context, "gx10", 22, "user", key, 
    "rsync -avz --progress /local/path/ ssh_user@gx10:/remote/path/")
```

### 场景 2: 环境初始化
```java
executeAsync(context, "gx10", 22, "user", key,
    "apt-get update && apt-get install -y docker.io")
```

### 场景 3: 服务管理
```java
executeAsync(context, "gx10", 22, "user", key,
    "systemctl restart qwen-model-service")
```

---

## ⚠️ 安全注意事项

1. **私钥处理**  
   - 严禁明文硬编码私钥
   - 必须在内存中解密后立即清除敏感数据
   - 建议使用 Android Keystore 保护密钥材料

2. **权限控制**  
   - 仅允许授权用户调用远程执行功能
   - 记录审计日志（命令、时间、结果摘要）

3. **超时控制**  
   - 所有操作必须设置最大等待时间（默认 60 秒）
   - 防止长时间阻塞主线程

4. **错误处理**  
   - 捕获并分类常见异常（网络断开、认证失败、命令不存在等）
   - 提供友好的错误提示给大模型解析

---

## 🔄 后续优化方向

- [ ] 完整实现私钥解密逻辑（AES-GCM + KeyStore）
- [ ] 支持密码认证模式（兼容部分旧设备）
- [ ] 增加并发连接池管理
- [ ] 实现命令沙箱化（限制危险命令执行）
- [ ] 添加可视化进度反馈

---

## 📝 贡献指南

提交 PR 前请确保：
1. 通过单元测试（正常执行、超时、认证失败场景）
2. 代码符合项目风格（Checkstyle 格式检查）
3. 更新相关文档说明

---

**最后更新时间：** 2026-03-05