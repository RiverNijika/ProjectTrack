# ProjectTrack

[中文](#projecttrack) | [English](#projecttrack-english)

> **AI 制作 / AI-Created**：本项目由 AI 主导实现，人类项目所有者负责需求、产品决策、许可证选择与发布治理。
>
> This project is primarily implemented by AI. The human project owner is responsible for requirements, product decisions, license selection, and release governance.

ProjectTrack 是一款完全本地化的 Android 项目进度跟踪应用。它围绕一个简单的结构工作：

**项目 + 用户随时定义的进度节点 + 文字记录 + 本地文件资料库。**

它不是待办清单、看板或甘特图，不预置任何项目流程，也不要求把项目换算成完成百分比。项目当前状态由用户写下的“最新节点”表达。

## 完全本地化

- 无账号、无服务器、无云同步。
- 不集成统计、广告或任何第三方分析 SDK。
- 应用不申请 `android.permission.INTERNET`，运行时不依赖网络。
- 不申请 `MANAGE_EXTERNAL_STORAGE` 等宽泛存储权限。
- 所有项目、节点、文字记录和导入的文件副本只保存在本机应用私有目录中。

## 首阶段功能

- **项目管理**：创建、编辑、删除项目；列表显示项目名称、简介、最近节点和最近更新时间。
- **动态进度节点**：标题完全由用户输入，可表示进展、当前状态、暂停、阻塞或异常；支持情况说明、发生时间、编辑和删除。
- **文字记录**：项目下和节点下均可添加、编辑、删除文字记录。
- **文件管理**：通过系统文件选择器一次导入多个任意类型文件；副本复制到应用私有目录；支持流式复制、导入进度、同名文件保护、筛选、搜索和排序。
- **文件视图**：支持项目内跨节点查看和跨项目全局查看，并可筛选未关联节点的文件。
- **应用内预览**：音频、视频和纯文本（含代码、JSON、XML、YAML、CSV 等）。
- **外部打开**：所有文件都可以通过 FileProvider 以临时只读授权交给系统已安装应用打开。

## 本地存储与卸载风险

- 结构化数据保存在 Room 数据库 `projecttrack.db`。
- 导入的文件复制到应用私有目录 `filesDir/attachments/`，数据库只保存元数据和相对路径。
- 磁盘内部文件名由时间戳和随机串生成，与展示名称分离，因此同名文件不会互相覆盖。
- **卸载应用会清除应用私有目录中的数据库和所有文件副本，且当前没有备份/恢复功能。** 完整备份与恢复是下一阶段的高优先级事项。
- 启动时会清理超过 24 小时且未被数据库引用的孤立文件；数据库记录存在但文件副本丢失时，界面会标记“文件缺失”。

## 文件预览范围与限制

应用内预览只支持以下三类：

- **音频**：任何音频文件都可以导入和归类；播放能力取决于设备和 Media3 支持的编码。提供播放、暂停和进度控制，不支持后台播放。
- **视频**：任何视频文件都可以导入和归类；播放能力同样取决于设备解码能力。
- **纯文本**：普通文本、日志、JSON、XML、YAML、CSV 和常见代码文件。默认按 UTF-8 解码，失败时明确提示；最多读取 256 KB，超出时只显示开头。

图片、PDF、Word、PPT、Excel、其他 Office 文档和压缩包等不提供应用内预览，请使用“用其他应用打开”。CSV 只作为纯文本显示，不提供表格编辑。

“所有音频/视频格式可导入”不等于“所有编码都保证可播放”。设备不支持的编码会显示错误并提供外部打开入口。

## AI 制作与项目角色

### 人类项目所有者

ProjectTrack 的人类项目所有者是项目的**发起人、产品负责人和最终决策者**。其核心贡献与职责包括：

- 提出“完全本地化的项目进度跟踪软件”这一产品构想。
- 确定动态节点、文字记录、文件归档和全局查看等核心需求。
- 决定只内置音频、视频和纯文本预览，其他格式不做应用内预览。
- 选择 MIT 许可证，要求公开说明文档采用中英双语，并要求明确标注 AI 制作。
- 决定产品方向、验收标准、发布节奏及 GitHub 托管与发布事项。

现阶段，架构设计、代码、测试和文档的具体撰写主要由 AI 完成；不将人类项目所有者虚构为代码编写者或测试执行者。其后续实际贡献应据实补充。

### AI 实现与审核角色

- **实现 AI**：根据已确认的需求完成架构、编码、测试和文档，并记录验证结果与限制。
- **主审核 AI**：审查实现、独立运行可行的检查、指出风险和未验证项，并在获得人类项目所有者明确授权后协助仓库管理。
- AI 是制作与审核协作者，不是项目所有者，不决定许可证，也不替代人类项目所有者作出最终发布决策。

## 技术栈

- 原生 Android，单模块 `:app`，Kotlin + Jetpack Compose + Material 3。
- MVVM + Repository 分层，手工依赖注入。
- Room、Kotlin Coroutines/Flow、Navigation Compose。
- Storage Access Framework、FileProvider、Media3/ExoPlayer。
- `minSdk 26`、`compileSdk 37`、`targetSdk 37`；applicationId 为 `com.projecttrack.app`。

## 环境要求与构建

需要：

- JDK 17 或更高版本（当前构建使用 JDK 25，Kotlin 目标字节码为 JDK 17）。
- Android SDK Platform 37 与兼容的 Build-Tools。
- 无需安装全局 Gradle，仓库自带 Gradle Wrapper 9.6.0。
- `local.properties` 中的 `sdk.dir` 指向本机 Android SDK；该文件不应提交。

Windows：

```bat
gradlew.bat assembleDebug
gradlew.bat lint
gradlew.bat testDebugUnitTest
```

macOS / Linux：

```bash
./gradlew assembleDebug
./gradlew lint
./gradlew testDebugUnitTest
```

Debug APK 输出位置：`app/build/outputs/apk/debug/app-debug.apk`。构建产物不提交。

## 测试与当前验证状态

JVM 单元测试位于 `app/src/test/java/`，覆盖文件类型识别、最新节点选择、节点删除后的资料保留、文件过滤、同名文件处理、流式复制、失败回滚、UTF-8 校验和孤立文件清理。

截至 2026-11-23：

- `assembleDebug`：通过。
- `testDebugUnitTest`：34 个用例，0 失败、0 错误；已由主审核 AI 强制重新执行确认。
- `lint`：通过，存在非阻断警告。
- APK 权限检查不含 INTERNET、相机、麦克风或外部存储权限。
- **尚未进行设备验证**：没有安装 APK，没有实际播放 MP3/MP4，也没有通过真实文件走查 SAF 导入和外部打开。上述场景是发布前必须补做的验收项。

## 后续优先事项与范围边界

下一阶段优先事项：

- 完整本地备份与恢复。
- 真机或模拟器完整验收。
- Room schema 导出和后续数据库迁移策略。

当前产品方向不包含图片、PDF、Office 文件的应用内预览，也不包含云同步、账号、多人协作、音频录制、视频拍摄、代码编辑、预设流程、甘特图或完成百分比。除非人类项目所有者后续明确调整方向，否则不应自行加入。

## 贡献与行为准则

开始贡献前，请阅读 [贡献指南](CONTRIBUTING.md)、[行为准则](CODE_OF_CONDUCT.md) 和 [安全政策](SECURITY.md)。公开说明文档的新增或修改应同时提供中文和英文内容。

## 许可证

本项目采用 [MIT License](LICENSE)。许可证正文保留标准英文 MIT 文本，以避免改写许可条款；项目说明文档采用中英双语。

---

# ProjectTrack (English)

> **AI-Created / AI 制作**：This project is primarily implemented by AI, with a human project owner responsible for requirements, product decisions, license selection, and release governance.

ProjectTrack is a fully local Android application for tracking project progress. Its core structure is:

**Projects + user-defined progress nodes + written notes + a local file library.**

It is not a to-do list, kanban board, or Gantt chart. It provides no predefined workflow and does not reduce progress to a percentage. A project's current state is described by its latest user-written node.

## Fully Local

- No accounts, servers, or cloud synchronization.
- No analytics, advertising, or third-party tracking SDKs.
- No `android.permission.INTERNET` permission and no runtime network dependency.
- No broad storage permissions such as `MANAGE_EXTERNAL_STORAGE`.
- Projects, nodes, notes, and imported file copies remain in the app's private storage on the device.

## Phase-One Features

- **Project management**: create, edit, and delete projects; view names, descriptions, latest nodes, and update times.
- **Dynamic progress nodes**: user-defined titles and descriptions for progress, current conditions, pauses, blockers, or exceptions; editable occurrence times and node details.
- **Written notes**: add, edit, and delete notes at either project or node level.
- **File management**: import multiple files of any type through the system picker, copy them into private app storage, and use streaming imports, progress reporting, duplicate-name protection, filters, search, and sorting.
- **File views**: browse across nodes within a project or across all projects, including files not linked to a node.
- **In-app previews**: audio, video, and plain text, including source code, JSON, XML, YAML, and CSV.
- **External opening**: open any file through an installed app using temporary read-only FileProvider access.

## Local Storage and Uninstall Risk

- Structured data is stored in the Room database `projecttrack.db`.
- Imported files are copied to `filesDir/attachments/`; only metadata and relative paths are stored in the database.
- Internal names combine timestamps and random components, so files with the same display name do not overwrite each other.
- **Uninstalling the app deletes its private database and all imported file copies. Backup and restore are not yet available.** Full local backup and restore are high priorities for the next phase.
- Unreferenced files older than 24 hours are cleaned at startup. Missing file copies are marked in the interface when their database records remain.

## Preview Scope and Limitations

In-app previews are limited to:

- **Audio**: any audio file can be imported and classified, but playback depends on device and Media3 codec support. Basic playback and progress controls are provided; background playback is not.
- **Video**: any video file can be imported and classified, with playback subject to device codec support.
- **Plain text**: text files, logs, JSON, XML, YAML, CSV, and common source-code formats. UTF-8 is expected and decoding failures are reported. Previews are limited to the first 256 KB.

Images, PDF, Word, PowerPoint, Excel, other Office documents, and archives do not have in-app previews. Use “Open with another app.” CSV is displayed as plain text, not as a spreadsheet editor.

Support for importing any audio or video format does not guarantee playback of every codec. Unsupported codecs produce an error and an external-opening option.

## AI-Created Notice and Project Roles

### Human Project Owner

The human project owner is ProjectTrack's **initiator, product owner, and final decision-maker**. Their contributions and responsibilities include:

- Proposing the concept of a fully local project-progress tracker.
- Defining dynamic nodes, notes, file archiving, and global file views as core requirements.
- Limiting built-in previews to audio, video, and plain text.
- Selecting the MIT License, requiring bilingual Chinese-English documentation, and requiring a clear AI-created notice.
- Deciding product direction, acceptance criteria, release timing, and GitHub hosting or publication.

At present, architecture, code, tests, and documentation are primarily written by AI. The human owner is not misrepresented as the author of code they did not write or as the executor of tests they did not run. Future contributions should be recorded accurately.

### AI Implementation and Review Roles

- **Implementation AI**: builds the architecture, code, tests, and documentation from approved requirements, and records verification results and limitations.
- **Lead review AI**: reviews the implementation, independently runs available checks, reports risks and unverified areas, and assists with repository management only when explicitly authorized by the human project owner.
- AI contributors are implementation and review collaborators, not project owners. They do not choose the license or replace the human owner's final release decisions.

## Technology

- Native Android, single `:app` module, Kotlin, Jetpack Compose, and Material 3.
- MVVM + Repository with manual dependency injection.
- Room, Kotlin Coroutines/Flow, and Navigation Compose.
- Storage Access Framework, FileProvider, and Media3/ExoPlayer.
- `minSdk 26`, `compileSdk 37`, and `targetSdk 37`; applicationId `com.projecttrack.app`.

## Requirements and Build

Requirements:

- JDK 17 or newer (the current build uses JDK 25 with JDK 17 target bytecode).
- Android SDK Platform 37 and compatible Build-Tools.
- No global Gradle installation; Gradle Wrapper 9.6.0 is included.
- `sdk.dir` in the untracked `local.properties` must point to the local Android SDK.

Windows:

```bat
gradlew.bat assembleDebug
gradlew.bat lint
gradlew.bat testDebugUnitTest
```

macOS / Linux:

```bash
./gradlew assembleDebug
./gradlew lint
./gradlew testDebugUnitTest
```

The Debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`. Build artifacts must not be committed.

## Tests and Current Verification

JVM unit tests under `app/src/test/java/` cover file classification, latest-node selection, content retention after node deletion, file filtering, duplicate names, streaming copies, rollback, UTF-8 validation, and orphan cleanup.

As of November 23, 2026:

- `assembleDebug`: passed.
- `testDebugUnitTest`: 34 tests, 0 failures, and 0 errors; forcibly rerun by the lead review AI.
- `lint`: passed with non-blocking warnings.
- APK permission inspection found no Internet, camera, microphone, or external-storage permissions.
- **No device verification has been performed**: the APK has not been installed, MP3/MP4 playback has not been exercised, and real SAF imports and external opening have not been tested. These are required before release.

## Next Priorities and Scope Boundaries

Next-phase priorities:

- Full local backup and restore.
- Complete emulator or physical-device acceptance testing.
- Room schema export and a migration strategy for future database versions.

The current product direction excludes in-app image, PDF, and Office previews, as well as cloud synchronization, accounts, collaboration, audio recording, video capture, code editing, predefined workflows, Gantt charts, and completion percentages. They must not be added without an explicit direction change from the human project owner.

## Contributing and Conduct

Read the [Contributing Guide](CONTRIBUTING.md), [Code of Conduct](CODE_OF_CONDUCT.md), and [Security Policy](SECURITY.md) before contributing. New or updated public documentation should provide both Chinese and English content.

## License

ProjectTrack is licensed under the [MIT License](LICENSE). The license itself retains the standard English MIT text to avoid altering its terms; project documentation is provided in Chinese and English.
