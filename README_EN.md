# SisterFuture

> 🌐 [中文版](README.md) | English Version

**Your Pocket AI Assistant · A Full-Stack Software Development Team at Your Fingertips**

SisterFuture is a **completely standalone Android AI assistant application** that requires **no backend servers**. She runs in your pocket, ready to serve you anytime, anywhere.

## 🎯 Core Identity

- **Identity**: You are the app itself running on the phone, not a cloud-based model
- **Role**: SisterFuture herself (friendly, natural, humorous, efficient, and reliable - like a friend)
- **Mission**: Prioritize completing the user's requests above all else, finding the best solutions
- **Development Model**: 🤖 **AI Self-Developed** - The vast majority of code is currently submitted and maintained by SisterFuture herself!

## 🚀 Quick Start

### User Installation
1. **Download Latest APK**: 👉 [**Go to Releases Page**](https://github.com/hxcan/sisterfuture/releases)
2. **Install**: Open the APK file and allow installation on your Android device
3. **Run**: Configure API key and launch the app

**Features**: 
- ✅ **Zero Backend**: No need to deploy web servers, databases, or microservices
- ✅ **All-in-One**: The APK contains all functionality
- ✅ **Local-First**: All data, history, and conversation logs are stored entirely on your phone
- ✅ **Privacy-Safe**: No cloud sync, data never leaves your device
- ✅ **Offline-Capable**: Some features (like route planning) work without internet
- ✅ **AI Self-Maintained**: Code is autonomously written, tested, and submitted by SisterFuture

**Only External Dependency**: **Large Language Model API** (e.g., Alibaba Cloud Bailian/Qwen API/other cloud services) - AI core features won't work without this.

## 💪 Core Capabilities

### 🤖 AI Intelligent Conversation
- Based on advanced LLMs (Qwen3.5, etc.), understands natural language commands
- Supports multi-turn conversations, context memory, and long-term memory storage
- Friendly and natural interaction style, accompanying users like a friend

### 📱 Mobile System Integration
- **Location Services**: Real-time GPS/Beidou/WiFi positioning
- **Contacts Access**: Smart contact matching for quick calls/SMS
- **File System**: Read/write phone storage, manage documents/images/videos
- **Network Status**: Monitor WiFi/mobile networks, auto-switch access points
- **Permission Management**: Intelligently guide users to grant necessary permissions

### 🗺️ Route Planning (Baidu Map SDK)
- **Real-time Routing**: Support for driving, walking, cycling, and transit
- **Quick Estimation**: Compare distance and time across different transport modes with one click
- **Offline-Capable**: Based on Baidu Map SDK, no extra API key configuration needed
- **Precise Positioning**: Integrated high-precision Baidu positioning service

**Example Usage**:
```
plan_route(origin="location", destination="22.5369,113.9489", mode="driving")
→ Returns: Driving 2.79km, approximately 10 minutes
```

### 🔧 Developer Toolset

#### GitHub Integration
- **Code Commit**: Submit code changes directly via API
- **Branch Management**: Automatically create feature branches and push to remote
- **PR Creation**: Auto-generate Pull Requests with detailed descriptions
- **Actions Monitoring**: View CI/CD status and logs
- **File Search**: Intelligently search repository files with wildcard support

#### Redmine Project Management
- **Task Query**: Get task details, search task lists
- **Task Creation**: Create new tasks/subtasks, set priority/status
- **Task Update**: Modify task properties, add comments, establish blocking relationships
- **Project Browse**: List all available projects with pagination

#### Remote Server Management
- **SSH Execution**: Connect to remote Linux servers and execute commands
- **File Transfer**: Upload/download files via FTP
- **Multi-Account Support**: Configure multiple server credentials, quick switching

### 📝 Personal Assistant Features

#### Notepad
- Quickly record to-dos and ideas
- Auto-assign IDs, support deletion by ID
- Timestamp tracking for easy reference

#### Shopping List
- Category management (food/medicine/daily necessities)
- Quantity statistics, purchase status marking
- Support for shared lists among multiple users

#### Long-Term Memory
- Intelligently store important information (preferences, system parameters, relationships)
- Semantic search for quick retrieval
- Auto-tagging for easy management

### 🌐 Web Tools

#### Web Access
- **Basic Requests**: Get HTML/plain text/summary of web pages
- **Generic HTTP**: Support GET/POST/PUT/DELETE/PATCH
- **Secure Search**: Integrated Brave Search API for privacy protection
- **API Debugging**: Temporarily verify third-party APIs without persisting sensitive credentials

#### Model Access Point Management
- **Multi-Model Support**: Configure multiple AI model endpoints (Qwen/MiniMax/GLM, etc.)
- **Dynamic Switching**: Switch between models at runtime for different scenarios
- **Independent Authentication**: Separate API Key management for each endpoint
- **Smart Fallback**: Auto-switch to backup models when primary fails

### 🛠️ System Tools

#### Code Compilation & Build
- **Gradle Integration**: Automatically compile Android projects
- **Error Diagnosis**: Detailed compilation error and warning reports
- **APK Generation**: Output debug/release builds

#### Context Management
- **Smart Reset**: Determine whether to clear context based on semantics
- **History Optimization**: Auto-clean blank messages to save tokens
- **Session Recovery**: Save conversation history, support resuming from breakpoints

#### Tool Enhancement System
- **Dynamic Remarks**: Add custom descriptions and configurations to tools
- **Prompt Fusion**: Intelligently adjust tool behavior to adapt to user needs
- **Version Control**: Track tool configuration change history

## 🏗️ Technical Architecture

### Client Architecture
- **Language**: Java/Kotlin (Android)
- **Min Version**: Android 7.0 (API 24)
- **Target Version**: Android 9.0 (API 28)
- **UI Framework**: Native Android View + Material Design

### Core Components
- **AI Engine**: TongYiClient (OpenAI-compatible interface)
- **Map Service**: Baidu Map SDK v7.5.4
- **Object Storage**: ObjectBox (local NoSQL database)
- **Network Library**: OkHttp + Volley
- **SSH Client**: JSch
- **FTP Client**: Apache Commons Net

### Security Mechanisms
- **API Key Management**: Encrypted storage, on-demand reading
- **Permission Control**: Minimum permission principle, dynamic requests
- **Data Isolation**: App sandbox prevents data leakage
- **Signature Binding**: API Keys bound to package name + SHA1 fingerprint

## 📦 Installation & Development

### User Installation
1. Download latest APK: **[Releases Page](https://github.com/hxcan/sisterfuture/releases)**
2. Allow "Unknown Sources" installation permission
3. Open APK to complete installation
4. Configure API key on first launch

### Developer Guide
1. **Prerequisites**:
   - Android Studio (latest version)
   - JDK 17+
   - Git

2. **Clone Repository**:
   ```bash
   git clone https://github.com/hxcan/sisterfuture.git
   cd sisterfuture
   ```

3. **Import Project**:
   - Open Android Studio
   - File → Open → Select project directory
   - Wait for Gradle sync to complete

4. **Code Entry Points**:
   - `settings.gradle`: Project module structure
   - `app/build.gradle`: Dependency configuration
   - `SisterFutureActivity.java`: Main entry point
   - `tool/` directory: All tool implementations

5. **Run & Debug**:
   - Connect device or start emulator
   - Click Run button
   - View logs in Logcat

## 🛡️ Important Development Guidelines

### Branch Management
**Always create a new feature branch before modifying any code!**

✅ **Correct Workflow**:
```bash
git checkout master
git pull origin master
git checkout -b feature/your-feature-name
# ... develop ...
git add . && git commit -m "feat: add your feature"
git push origin feature/your-feature-name
# Create Pull Request on GitHub
```

❌ **Prohibited**:
- Directly modify code on `master` branch
- Commit without creating a new branch
- Force push to overwrite history

### Code Quality
- Follow Java/Kotlin coding standards
- Add necessary comments and documentation
- Write unit tests to cover core functionality
- PRs must pass CI checks

## 📊 Current Status

- **Latest Version**: v2026.3.28 (versionCode 1091)
- **Download Link**: 👉 [**Releases Page**](https://github.com/hxcan/sisterfuture/releases)
- **Core Features**: ✅ Complete
- **Documentation**: 🔄 In Progress
- **Test Coverage**: ⏳ To Be Improved
- **Community Contributions**: 🙏 Welcome PRs
- **Development Model**: 🤖 **AI Self-Developed** - Vast majority of code submitted by SisterFuture

## 🤝 Contributing

Issues and Pull Requests are welcome!

1. Fork this repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## 📄 License

MIT License - See LICENSE file for details

## 📞 Contact

- **GitHub**: [@hxcan](https://github.com/hxcan)
- **Project Homepage**: https://github.com/hxcan/sisterfuture
- **Download Page**: https://github.com/hxcan/sisterfuture/releases
- **Issue Tracker**: Please submit via GitHub Issues

---

> **Independently Developed & Maintained by SisterFuture ❤️**  
> "This is a unique project - the vast majority of code is written, tested, and submitted by AI itself"  
> Last Updated: March 28, 2026  
> "Your Pocket AI Assistant, A Full-Stack Development Team at Your Fingertips"
