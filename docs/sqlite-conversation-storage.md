# SQLite 消息历史存储

仍然只有默认会话，不新增切换界面。SessionManager 及 ContextManager(Context)
都使用 SqliteConversationStore，ConversationStore 接口未改变。

## 数据格式

应用私有 files 目录下的 `conversations.db`，schema version 1：

- sessions：session_id 主键、max_rounds。会话记录同时充当已初始化/迁移标记。
- messages：session_id、position、message_json；复合主键保证按会话读取及原始顺序。

每条消息保留完整 JSON，包括原消息 ID、工具调用、附件、用量和未知字段。
消息 ID 不作为唯一键，避免旧数据中重复 ID 造成导入丢失。
记录按会话隔离；仅 default 会话导入旧历史，其他会话初始化为空。

## 迁移与兼容

仅在 sessions 中不存在 default 时读取旧 conversation_context.json；文件不存在时
读取 context_manager/history 偏好设置，并导入 current_max_rounds。
导入消息和会话标记在同一个事务完成，失败回滚。已有空历史也算成功迁移。
不删除或改写旧 JSON/偏好设置；成功后不再从它们回退，因此清空历史不会使旧消息复活。

**与旧容错不同**：已有 JSON 损坏、为空或结构无效时停止迁移，不用空历史覆盖。
数据库无法读取或损坏时也停止，不自动删除重建或回退旧历史。
这可能使异常设备启动失败，需要先备份并修复数据；不能保证所有损坏数据也能无感升级。
旧文件仅是升级时备份，不持续同步。降级旧版本只能看见旧快照，不能读取升级后的新消息。

## 写入及限制

进程内共享单线程队列，读取等待此前已排队的写入。每次保存先序列化不可变快照，
再异步在事务内替换当前会话消息。中途失败保留上一次已提交的数据，不记录消息正文。
每项数据库操作完成后关闭连接。轮数更新也按会话排队。

当前仍全量加载、全量替换一个会话，尚未实现增量写入/分页；不要把此次迁移理解为
已经解决大历史的内存/写入性能问题。异步提交前进程退出可能丢失尚未提交的最新消息。
保存失败目前沿用日志反馈，尚无 UI 错误提示。Activity 旧回调仍未实现会话生命周期隔离。

## 验证

- JVM：快照冻结嵌套对象、顺序/重复 ID，加旧文件存储回归共 9 项通过。
- Android 仪器测试：导入一次/清空不复活、嵌套数据/顺序/轮数重开、跨会话隔离、
  SP 回退/空文件历史优先、损坏源可修复后重试、写入失败事务回滚，共 6 项，待设备运行。
- 既有 SessionManagerTest 继续覆盖入口及旧历史加载。
- 手机验收：升级后历史完整；发消息并重启；工具调用结果/附件仍在；删除和重置后重启不复活。

使用 Android SQLiteOpenHelper 与事务，无新增依赖：
https://developer.android.com/reference/android/database/sqlite/SQLiteOpenHelper
