# 会话管理

最新阶段已加入会话选择界面，见 [会话切换](session-switching.md)；下文保留此前阶段说明。

## 当前阶段：重置创建新会话

- 重置按钮及 `resetConversationContext` 创建 UUID 标识的新空会话，旧 SQLite 历史不删除。
- `SessionManager` 从 SQLite 会话表读取目录；`session_navigation/current_session_id`
  偏好设置持久化当前选择。先创建数据库记录，再同步保存选择，最后发布新上下文。
- 请求捕获原始 ContextManager、ToolManager 和流式工具参数缓冲；已开始的工具批次
  全部回复仍写入原会话。切换后才收到的尚未执行工具调用记录为取消，不启动新副作用。
- `Tool.shouldContinueAfterResult(result)` 默认 true。重置成功返回 false，失败仍为 true；
  决策依据单次结果，不使用共享可变标志。同批任一工具要求停止，就在全部结果写入后停止续接。
- 批次完成门闩要求调用注册及 assistant 工具调用消息写入完成，且全部结果齐全，
  才能收尾一次；避免同步回调提前续接。
- 尚无切换旧会话的界面；旧记录保留，下一阶段增加会话选择。

验证：修改的 Java 源码及 Android 会话测试通过本地编译检查；
13 项 JVM 测试通过。Android 测试仅编译，尚未在设备执行；完整 APK 构建仍需 CI。
真机验收应覆盖按钮重置、工具重置、同批多个异步工具、重置后立即发送、
重启恢复新会话，以及失败时旧历史不被清空。

## 第一阶段记录（历史方案）

更新：存储实现已演进为 SQLite，见 [SQLite 存储与迁移](sqlite-conversation-storage.md)。
下文描述首次职责拆分时的文件存储方案；界面仍为默认单会话。

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
