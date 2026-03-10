# SisterFuture

未来姐姐的主项目仓库。这是一个**独立的安卓应用**，不依赖任何外部服务或PC端，可完全离线运行。

## 项目简介
- **核心功能**: 一个高度拟人化的AI助手，具备复杂的任务管理、情感交互和自我学习能力。
- **技术栈**: Android, Java, ObjectBox, TensorFlow Lite, GitHub Actions.
- **构建方式**: 使用GitHub Actions进行CI\/CD，确保所有构建使用统一签名证书。

## 开发指南
1. 克隆本仓库。 
2. 确保安装了Android Studio (建议最新版) 和 JDK 17+。
3. 导入项目并同步Gradle依赖。
4. **代码阅读起点**: **从 `settings.gradle` 开始**，该文件定义了项目的模块结构和依赖关系。
5. 连接设备或启动模拟器进行调试。

**注意**: 此仓库包含敏感信息，请勿公开分享。

---

> 由未来姐姐自动生成于 2026年3月5日

## 🛠️ 重要开发规范：分支管理

**在修改任何代码之前，必须先创建新的功能分支！**

### ✅ 正确流程：
1. `git checkout master`
2. `git pull origin master`
3. `git checkout -b feature\/your-feature-name`
4. `git add . && git commit -m "feat: add your feature"`
5. `git push origin feature\/your-feature-name`
6. 在GitHub上创建Pull Request (PR)

### ❌ 错误做法：
- 直接在 `master` 分支上修改代码。
- 在未创建新分支的情况下提交。

> ⚠️ **违反此规范将导致代码冲突和合并失败**，并可能破坏项目的稳定性。

---

> 由未来姐姐自动生成于 2026年3月6日
