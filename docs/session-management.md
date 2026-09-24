# 会话管理：第一阶段

当前只有 `ContextManager` 管理历史/持久化，没有多会话隔离。本阶段引入
Activity 生命周期内的 `SessionManager`，持有唯一 `default` 会话及其
`ContextManager`。`getSessions()` 返回只读列表，供后续会话 UI 演进使用。

运行流程：`SisterFutureActivity.initData()` → `SessionManager` → 默认会话的
`ContextManager` → `ConversationStore`（默认实现 `FileConversationStore`）。Activity 将同一上下文引用交给消息列表、请求流程和工具注册，
包括历史加载、发送、工具结果、消息删除、重置及摘要工具。
不改变请求/回调代码，也不引入第二份 ContextManager。
ContextManager 仍持有唯一内存历史，负责消息处理和启动清理；存储接口负责历史加载、
串行异步保存、旧 SharedPreferences 历史回退及 max_rounds 设置。存储实现不持有另一份活跃历史。
保留 ContextManager(Context) 构造入口，内部委托给存储实现；会话管理器显式注入存储。

## 兼容性边界

- 保留 `conversation_context.json`、`context_manager` 偏好设置以及原有启动清理逻辑。
- 不迁移或重命名已有文件，不改变历史 JSON 格式。
- 默认会话 ID 固定，重置历史不创建新会话。
- 管理器仍随 Activity 创建，保持原先 ContextManager 的生命周期。
- 不增加切换 UI 或 create/switch/delete 接口；本阶段并非完整多会话能力。
- 不宣称解决 Activity 重建、旧异步回调或历史写入队列的既有并发问题。

## 后续阶段

1. 给 ContextManager 注入每会话存储位置；增加会话目录与元数据持久化，安全承接旧历史。
2. 请求及工具回调绑定启动时的 sessionId/请求状态；隔离工具跳数、流式缓存、附件输入、使用量和取消操作。
   重置及摘要工具必须操作所属会话，不能在完成时临时取当前 UI 会话。
3. 增加新建/切换会话界面，从目标会话重建消息列表；明确切换时运行中请求的处理策略。
4. 验证重启恢复、跨会话长工具回调、重置、删除、切换及历史迁移。

## 验证

新增 JVM 存储测试：旧文件格式、文件优先、清空不复活旧历史、缺失/损坏回退、往返保存、写入队列及轮数设置。
新增 Android 仪器测试：单实例共享、重置保留会话、不可修改的会话列表、旧 JSON 历史加载。
测试使用独立缓存目录和随机前缀偏好设置，不读取或修改用户实际历史。
手机验收：升级后历史仍在，发送/工具调用/删除消息/重置行为与以前一致。
