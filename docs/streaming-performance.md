# 流式输出性能诊断

搜索日志标记 `[PERF]`。每种指标最多约每 5 秒输出一条汇总，流式请求结束或失败时
另输出剩余统计。只包含计数、平均/最大毫秒数和耗时 ≥16ms 的样本数，不含正文或参数。
存储及 Markdown 指标跨请求累计，短时最后一批样本可能到下一次操作才输出。

- stream_callback_gap：客户端回调到达间隔，不等同于纯网络耗时，也包含客户端前置处理。
- stream_ui_queue：从回调到达到界面线程开始执行的排队时间。
- stream_ui_handling：界面线程响应处理时间，包含 JSON 解析、通知和界面更新调度，
  不含之后 RecyclerView 的实际布局/绘制。
- message_markdown_bind：消息绑定中 setMarkdown 的同步耗时，不是整帧绘制时间。
- history_snapshot：调用线程全量序列化历史的耗时。
- history_write_queue：历史写入等待后台队列的时间。
- history_sqlite_write：后台打开数据库、事务写入和关闭数据库的总耗时。

复现：安装诊断版，先正常询问，再要求输出较长带 Markdown 的回答；观察卡顿后导出日志，
注明大致时间、是否刚重置会话。回调间隔长但队列/渲染快，倾向上游供给不均匀；
排队或渲染明显增大，倾向界面负担；快照耗时高可能影响界面，后台写库慢本身不等于
界面被同步阻塞。≥16ms 只是诊断阈值，不直接等同掉帧，尤其不适用于判断网络间隔好坏。

此次仅添加诊断，不改变线程分配、刷新频率或存储策略。

---

## 流式 Markdown 渲染闪烁指标（markdown_render_flicker）

用于任务 #958696185511「修复流式输出时 Markdown 表格渲染闪烁问题」的
子任务 #958698459506（1/4 测量）。该指标**按单次流式独立归集**，
在流式结束时输出一行，与上面跨请求累计的指标互不干扰。

日志形态：

```
[PERF] markdown_render_flicker bindCount=… durationMs=… bindTotalMs=… bindMaxMs=… shapeSwitch=… heightChange=… tableSeen=…
```

| 字段 | 含义 | 判读 |
|---|---|---|
| bindCount | 本次流式期间 setMarkdown 的调用次数 | 重建频次；越大闪得越频繁 |
| durationMs | 本次流式持续时长 | 用于换算「每秒重建次数」 |
| bindTotalMs | setMarkdown 单次耗时之和 | 渲染总开销 |
| bindMaxMs | setMarkdown 单次最大耗时 | 是否有单次卡顿尖峰 |
| shapeSwitch | 「纯文本形态 ↔ 表格形态」切换次数 | **0 = 无形态抖动；>0 = 存在形态来回跳** |
| heightChange | 文本视图高度变化次数 | **0 = 无尺寸跳动；>0 = 存在尺寸抖动** |
| tableSeen | 本次流式是否出现过完整表格 | 0 说明本次没表格，数据不可用于判断 |

### 判读要点

- `shapeSwitch > 0` → 主要矛盾是**形态抖动**（未闭合表格被当作纯文本渲染，
  分隔行到达后突然变成表格），对应修复方向是「流式期间不启用 TablePlugin」。
- `shapeSwitch == 0` 且 `heightChange > 0` → 主要矛盾是**尺寸跳动**
  （每次重建都重新测量列宽与行高），对应修复方向是「表格渲染稳定化」。
- `shapeSwitch == 0` 且 `heightChange == 0` 但 `bindCount` 很大 →
  主要矛盾是**重建频次**（同一内容被反复重解析重绘），对应修复方向是「防抖刷新」。

### 复现步骤

1. 安装诊断版
2. 提问一个要求输出 Markdown 表格的请求（表头 + `|---|` 分隔行 + 若干数据行）
3. 等流式完全结束
4. 导出日志，取 `[PERF] markdown_render_flicker` 一行

同时用一句话描述肉眼观感（A 反复出现消失 / B 原地抖动 / C 两者都有），
主观观感与指标互为佐证。
