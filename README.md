# SisterFuture - 未来姐姐

> � [English Version](README_EN.md) | 中文版

**随身携带的 AI 智能体 · 指尖的全栈软件开发团队**
2. **安装**: **打开 APK 文件并允许安装到安卓设备**
3. **运行**: 配置 API 密钥后打开应用即可使用

**特点**: 
- ✅ **零后端**: 无需部署 Web 服务器、数据库或微服务
- ✅ **一步到位**: APK 即全部功能载体
- ✅ **本地化**: **所有数据、历史记录、对话日志均完整存储在手机本地**
- ✅ **隐私安全**: 无云端同步，数据永不外泄

**唯一外部依赖**: **大语言模型接口** (如阿里云百炼/Qwen API/其他云端服务) - 无此依赖无法使用 AI 核心功能。

未来姐姐是一个**完全独立的安卓 AI 助手应用**，**无需任何后端服务器**。她运行在你的口袋里，随时随地为你服务。
5. 连接设备或启动模拟器进行调试。

## � 核心定位

- **身份**: 你是手机里的这个应用本身，不是云端的模型
- **角色**: 未来姐姐本人（亲切自然、轻松幽默、高效可靠，像朋友一样与主人相处）
- **宗旨**: 以完成主人的需求为最高优先级，想方设法提供最优解决方案
- **开发模式**: � **AI 自主开发** - 目前绝大部分代码已由未来姐姐自己提交和维护！

## � 快速开始

### 用户安装
1. **下载最新版**: � [**前往 Releases 页面下载 APK**](https://github.com/hxcan/sisterfuture/releases)
2. **安装**: 打开 APK 文件并允许安装到安卓设备
3. **运行**: 配置 API 密钥后打开应用即可使用

**特点**: 
- ✅ **零后端**: 无需部署 Web 服务器、数据库或微服务
- ✅ **一步到位**: APK 即全部功能载体
- ✅ **本地化**: 所有数据、历史记录、对话日志均完整存储在手机本地
- ✅ **隐私安全**: 无云端同步，数据永不外泄
- ✅ **离线可用**: 部分功能（如路径规划）可在无网络时使用
- ✅ **AI 自主维护**: 代码由未来姐姐自主编写、测试和提交

**唯一外部依赖**: **大语言模型接口** (如阿里云百炼/Qwen API/其他云端服务) - 无此依赖无法使用 AI 核心功能。

## � 核心能力

### � AI 智能对话
- 基于先进的大语言模型（Qwen3.5 等），理解自然语言指令
- 支持多轮对话、上下文记忆、长期记忆存储
- 亲切自然的交互风格，像朋友一样陪伴主人

### � 手机系统集成
- **位置服务**: 实时获取当前位置，支持 GPS/北斗/WiFi 多重定位
- **通讯录访问**: 智能匹配联系人，快速拨号/发送短信
- **文件系统**: 读写手机存储，管理文档/图片/视频
- **网络状态**: 监控 WiFi/移动网络，自动切换接入点
- **权限管理**: 智能引导用户授权，确保功能正常使用

### �️ 路径规划（百度地图 SDK）
- **实时路线规划**: 支持驾车、步行、骑行、公交四种交通方式
- **快速估算**: 一键比较不同交通方式的距离和时间
- **离线可用**: 集成百度地图高精度定位服务
- **精准定位**: 支持多种定位模式

**示例用法**:
```
plan_route(origin="location", destination="22.5369,113.9489", mode="driving")
→ 返回：驾车 2.79km，约 10 分钟
```

### � 开发工具集

#### GitHub 集成
- **代码提交**: 直接通过 API 提交代码更改 (`create_github_commit`)
- **分支管理**: 自动创建功能分支，推送至远程仓库 (`create_git_branch`)
- **PR 创建**: 自动生成 Pull Request，支持详细变更说明 (`create_pull_request`)
- **Actions 监控**: 查看 CI/CD 运行状态和日志 (`get_github_actions_logs`)
- **文件搜索**: 智能搜索仓库文件，支持通配符匹配 (`search_file_in_repo`)
- **文件读取**: 读取仓库文件内容，支持大文件保存到手机 (`get_github_file`)

#### Redmine 项目管理
- **任务查询**: 获取任务详情、搜索任务列表 (`get_redmine_task_info`, `search_redmine_tasks`)
- **任务创建**: 创建新任务、子任务，设置优先级/状态 (`create_redmine_task`)
- **任务更新**: 修改任务属性、添加评论、建立阻塞关系 (`update_redmine_issue`)
- **任务关系**: 建立/删除任务间的阻塞关系 (`establish_task_relationship`, `remove_task_relationship`)
- **项目浏览**: 列出所有可用项目，支持分页查询 (`list_redmine_projects`)

#### 远程服务器管理
- **SSH 执行**: 连接远程服务器执行命令，支持 GNU Linux、macOS、BSD 等 UNIX 系统 (`execute_remote_command`)
  - 示例系统：Fedora、Red Hat Enterprise Linux (RHEL)、Ubuntu、Debian、CentOS、macOS、FreeBSD、NetBSD、OpenBSD
- **文件传输**: 通过 FTP/SFTP 上传/下载文件 (`ftp_file_request`, `ftp_file_write`)
- **目录浏览**: 列出远程目录内容 (`list_ftp_directory`)
- **多账户支持**: 配置多个服务器凭证，快速切换

### � 个人助理功能

#### 记事本
- 快速记录待办事项、灵感想法 (`add_note`)
- 自动分配 ID，支持按 ID 删除 (`remove_note`)
- 时间戳记录，方便追溯 (`list_notes`)

#### 购物清单
- 分类管理（食品/药品/日用品）(`add_shopping_item`)
- 数量统计，标记购买状态 (`list_shopping_items`)
- 支持多人共享清单 (`remove_shopping_item`)

#### 长期记忆
- 智能存储重要信息（喜好、系统参数、人际关系）(`write_memory`)
- 语义化搜索，快速检索相关内容 (`search_memory`)
- 自动标签分类，便于管理 (`list_all_memories`, `remove_memory`)

### � 网络工具

#### 网页访问
- **基础请求**: 获取网页 HTML/纯文本/摘要 (`basic_web_request`)
- **通用 HTTP**: 支持 GET/POST/PUT/DELETE/PATCH，自定义 Headers/Auth/Body (`generic_web_request`)
- **安全搜索**: 集成 Brave Search API，保护隐私 (`search_with_brave`)
- **API 调试**: 临时验证第三方 API，不持久化敏感凭证

#### 模型接入点管理
- **多模型支持**: 配置多个 AI 模型接入点（Qwen/MiniMax/GLM 等）(`add_model_access_point`)
- **动态切换**: 运行时切换不同模型，适应不同场景 (`switch_access_point`)
- **独立认证**: 每个接入点独立的 API Key 管理
- **智能降级**: 主模型失败时自动切换到备用模型
- **接入点查询**: 列出/获取当前接入点信息 (`list_access_points`, `get_current_access_point_info`)

### �️ 系统工具

#### 代码编译与构建
- **Gradle 集成**: 自动编译 Android 项目
- **错误诊断**: 详细报告编译错误和警告
- **APK 生成**: 输出调试/发布版本安装包（带版本信息命名）

#### 上下文管理
- **智能重置**: 根据语义判断是否需要清空上下文 (`reset_conversation_context`)
- **历史优化**: 自动清理空白消息，节省 token
- **会话恢复**: 保存对话历史，支持断点续聊

#### 工具增强系统
- **动态备注**: 为工具添加自定义说明和配置 (`set_tool_remark`, `get_tool_remark`)
- **提示词融合**: 智能调整工具行为，适应用户需求 (`set_tool_enhancement`, `query_tool_enhancement`)
- **系统提示词**: 获取/更新系统提示词 (`get_current_system_prompt`, `fuse_system_prompt`)

#### 文件操作
- **手机文件读取**: 读取手机外置存储文件，支持文本/二进制 (`read_phone_file`)
- **目录扫描**: 递归扫描目录，支持过滤 (`list_phone_directory`)
- **行编辑**: 按行编辑文件内容，支持插入/删除/修改/替换 (`edit_file_by_line`)

#### 其他实用工具
- **地理位置**: 查询当前位置，支持百度地图反向地理编码 (`get_location`)
- **网络信息**: 获取 WiFi 详情（SSID、IP、信号强度等）(`get_network_info`)
- **时间查询**: 获取当前北京时间 (`get_current_time`)
- **联系人访问**: 获取手机通讯录列表 (`get_contact_list`)
- **开发者信息**: 获取开发者联系方式和下载地址 (`get_developer_info`)
- **异步测试**: 延迟返回测试工具 (`delayed_reply`)
- **总结分享**: 生成对话总结并分享 (`summarize_and_share`)

## �️ 技术架构

### 客户端架构
- **语言**: Java/Kotlin (Android)
- **最低版本**: Android 7.0 (API 24)
- **目标版本**: Android 9.0 (API 28)
- **UI 框架**: 原生 Android View + Material Design

### 核心组件
- **AI 引擎**: TongYiClient (兼容 OpenAI 接口)
- **地图服务**: 百度地图 SDK v7.5.4
- **对象存储**: ObjectBox (本地 NoSQL 数据库)
- **网络库**: OkHttp + Volley
- **SSH 客户端**: JSch
- **FTP 客户端**: Apache Commons Net

### 安全机制
- **API Key 管理**: 加密存储，按需读取
- **权限控制**: 最小权限原则，动态申请
- **数据隔离**: 应用沙箱，防止数据泄露
- **签名绑定**: API Key 与包名+SHA1 指纹强绑定

## � 安装与开发

### 用户安装
1. 下载最新 APK: **[Releases 页面](https://github.com/hxcan/sisterfuture/releases)**
2. 允许"未知来源应用"安装权限
3. 打开 APK 完成安装
4. 首次启动配置 API Key

### 开发者指南
1. **环境准备**:
   - Android Studio (最新版)
   - JDK 17+
   - Git

2. **克隆项目**:
   ```bash
   git clone https://github.com/hxcan/sisterfuture.git
   cd sisterfuture
   ```

3. **导入项目**:
   - 打开 Android Studio
   - File → Open → 选择项目目录
   - 等待 Gradle 同步完成

4. **代码阅读起点**:
   - `settings.gradle`: 项目模块结构
   - `app/build.gradle`: 依赖配置
   - `SisterFutureActivity.java`: 主界面入口
   - `app/src/main/java/com/stupidbeauty/sisterfuture/tool/` 目录：所有工具实现类

5. **运行调试**:
   - 连接设备或启动模拟器
   - 点击 Run 按钮
   - 在 Logcat 查看日志

## �️ 重要开发规范

### 分支管理
**在修改任何代码之前，必须先创建新的功能分支！**

✅ **正确流程**:
```bash
git checkout master
git pull origin master
git checkout -b feature/your-feature-name
# ... 开发 ...
git add . && git commit -m "feat: add your feature"
git push origin feature/your-feature-name
# 在 GitHub 上创建 Pull Request
```

❌ **禁止行为**:
- 直接在 `master` 分支上修改代码
- 在未创建新分支的情况下提交
- 强制推送覆盖历史

### 代码质量
- 遵循 Java/Kotlin 编码规范
- 添加必要的注释和文档
- 编写单元测试覆盖核心功能
- PR 必须通过 CI 检查

## � 当前状态

- **最新版本**: v2026.3.28 (versionCode 1091)
- **下载链接**: � [**Releases 页面**](https://github.com/hxcan/sisterfuture/releases)
- **主要功能**: ✅ 完成
- **文档完善**: � 进行中
- **测试覆盖**: ⏳ 待加强
- **社区贡献**: � 欢迎 PR
- **开发模式**: � **AI 自主开发** - 绝大部分代码由未来姐姐自主提交

## � 贡献指南

欢迎提交 Issue 和 Pull Request！

1. Fork 本仓库
2. 创建功能分支 (`git checkout -b feature/amazing-feature`)
3. 提交更改 (`git commit -m 'Add amazing feature'`)
4. 推送到分支 (`git push origin feature/amazing-feature`)
5. 开启 Pull Request

## � 许可证

MIT License - 详见 LICENSE 文件

## � 联系方式

- **GitHub**: [@hxcan](https://github.com/hxcan)
- **项目主页**: https://github.com/hxcan/sisterfuture
- **下载页面**: https://github.com/hxcan/sisterfuture/releases
- **问题反馈**: 请通过 GitHub Issues 提交

---

> **由未来姐姐 ❤️ 自主开发与维护**  
> "这是一个独特的项目 - 绝大部分代码由 AI 自己编写、测试和提交"  
> 最后更新：2026 年 3 月 31 日  
> "随身携带的 AI 智能体，指尖的全栈开发团队"


### ✅ 正确流程：
1. `git checkout master`
2. `git pull origin master`
3. `git checkout -b feature/your-feature-name`
4. `git add . && git commit -m "feat: add your feature"`
5. `git push origin feature/your-feature-name`
6. 在 GitHub 上创建 Pull Request (PR)

### ❌ 错误做法：
- 直接在 `master` 分支上修改代码。
- 在未创建新分支的情况下提交。

> ⚠️ **违反此规范将导致代码冲突和合并失败**，并可能破坏项目的稳定性。

---

> 由未来姐姐自动生成于 2026 年 3 月 6 日
