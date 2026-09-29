# 超大历史消息读取修复

症状：启动 loadHistory 时出现 SQLiteBlobTooBigException / Row too big to fit into CursorWindow。
写入 SQLite 成功并不代表整行 JSON 能装进 Android CursorWindow。

修复仅改变读取方式：游标只取 position 和 SQL length，正文通过带绑定参数的
substr 每次读取 16384 个 Unicode 码点，再完整拼回 JSON。启动历史与列表摘要共用这一逻辑。
SQLite 的长度/偏移按码点计算，不能使用 Java String.length 的 UTF-16 长度推进偏移。
校验每块长度，缺失或不完整时抛错，禁止用空历史代替或静默跳过。

不升级数据库版本，不改变 schema 或写入格式，不删除、截断或重写已有行来恢复读取。
本修复不放大 CursorWindow，也不限制消息长度。仍需足够 Java 堆内存容纳完整 JSON 和解析结果，
这不是无限大小支持；数据库损坏、磁盘满及内存耗尽属于其他问题。

验证：本地 Java 编译检查、9 项现有 JVM 存储测试通过；桌面 SQLite 对 >7MB UTF-8 JSON
及 Unicode/转义边界进行 5 组重组与 SHA-256 一致性检查通过。
新增 Android 测试模拟旧版 schema 中第 81 行巨大消息，验证重开、摘要、原始数据库内容未改、
大消息再次保存、跨会话隔离和 emoji/换行/NUL 转义。测试源码已编译，尚未在 Android 设备执行。
桌面 SQLite 检查不能替代 Android CursorWindow 真机验收。

验收应覆盖安装升级而非卸载：保留原应用数据，直接打开原有巨大消息会话；核对消息前后顺序、
正文/附件数据与会话摘要。不要通过清除数据或卸载来绕过启动错误。
