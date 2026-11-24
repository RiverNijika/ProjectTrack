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

## 本地备份与恢复（第二阶段）

项目列表顶部提供“备份与恢复”入口。备份与恢复完全在本地完成，不需要账号、服务器或网络。

### 导出完整备份

- 通过系统保存对话框选择位置，生成扩展名为 `.ptbackup` 的 ZIP 容器（MIME 为 `application/zip`）。
- 备份包含全部项目、动态节点、项目和节点文字记录、文件元数据，以及应用私有目录中的全部文件副本。
- 备份内部结构为 `manifest.json`、`data.json` 和 `attachments/`：manifest 记录格式名称/版本、应用版本、数据库 schema 版本、创建时间、项目/文件数量与总大小；data 保存实体关系、时间字段和每个附件的相对路径、大小与 SHA-256。
- 备份不包含本机绝对路径、SDK/用户目录、`local.properties`、签名文件、缓存、构建产物或私有 AI 交接资料。
- 导出使用流式读写，不把视频、音频或整个 ZIP 一次性读入内存；界面显示处理中、成功、取消和失败状态。
- 导出期间会阻止项目修改、节点/记录编辑、文件导入/删除和恢复操作，以保证数据库元数据与附件内容来自同一份稳定状态。
- 如果任何文件副本缺失或实际大小与数据库记录不一致，导出会失败并说明原因，不会生成看起来完整但实际缺文件的备份。
- 导出目标必须支持 **rwt 或 wt** 安全截断写入。若保存位置只支持不保证截断的 `w` 模式，应用会拒绝写入并提示新建空白文件或选择其他位置，避免旧文件尾部残留内容混入新备份。
- 取消或失败时会尝试删除用户选择位置中的未完成输出；如果系统无法删除，会明确提示用户手动删除。

### 非破坏性恢复

- 通过系统文件选择器选择备份文件；不会仅凭扩展名判断有效性。
- 在修改现有数据前，先在应用私有临时目录中完成解压和校验：ZIP 结构、manifest/格式版本、数据库版本、`data.json` 字段与类型、项目/节点/记录/文件关系、附件是否存在、大小与 SHA-256。
- 恢复是“新增副本”，不会删除、覆盖或修改已有项目和附件；备份中的项目、节点、记录和文件会重新生成数据库 ID，并用旧 ID 到新 ID 的映射恢复关系。
- 节点删除后形成的“未关联节点”记录和文件在恢复后仍保持 `nodeId` 为空。
- 文件会使用新的唯一内部名称复制，不能覆盖现有附件；重复恢复同一备份会产生重复项目，操作前的确认提示会明确说明这一点。
- 所有附件复制完成后再在一个 Room 事务中写入元数据；失败或取消时会删除本次新复制的附件并清理解压临时目录，Room 事务回滚，现有数据保持不变。
- 取消语义：在进入最终数据库提交前取消，会清理本次附件和临时目录并向上传递取消；最终提交使用短暂的不可取消临界区，提交会完整结束。提交完成后按成功处理，不会删除已入库附件，也不会把已完成的恢复显示成“已取消/未修改”。
- 提交边界修复（2026-11-24）：真实 Room 在 API 36 上暴露了 `withContext(NonCancellable)` 的返回边界竞态——事务 block 已完成并提交，但退出 `NonCancellable` 回到已取消的外部协程时仍可能抛出 `CancellationException`。现在用“提交结果持有器”区分“事务已返回”与“提交前/提交中被取消”：已提交时直接返回成功且不清理附件；未提交时才清理。ZIP 解压、SHA-256 和附件复制仍保持可取消。
- 恢复失败、校验失败或用户取消时不会显示成功，也不会删除或修改现有数据。
- ZIP 安全：拒绝绝对路径、`..` 路径穿越、反斜杠、目录条目、重复条目、未知条目和越界附件路径；对条目数量、JSON 大小、单附件大小和附件总大小设有上限。
- 备份格式版本从 1 开始；当前只支持格式版本 1 和数据库 schema 版本 1，未知或不兼容版本会被明确拒绝，不进行部分恢复。
- 恢复成功后显示新增的项目、节点、记录和文件数量。

### 隐私与限制

- 备份未加密，包含用户全部项目资料和附件副本；任何拿到备份文件的人都能读取其中内容。请把备份保存到可信位置。
- 不提供云端备份、自动同步、加密备份、合并去重或覆盖式恢复。
- 为避免异常备份和磁盘耗尽，当前限制为：最多 100,000 个 ZIP 条目、JSON 条目最大 32 MB、单个附件最大 4 GB、附件总大小最大 16 GB。

## 本地存储与卸载风险

- 结构化数据保存在 Room 数据库 `projecttrack.db`。
- 导入的文件复制到应用私有目录 `filesDir/attachments/`，数据库只保存元数据和相对路径。
- 磁盘内部文件名由时间戳和随机串生成，与展示名称分离，因此同名文件不会互相覆盖。
- **卸载应用会清除应用私有目录中的数据库和所有文件副本。** 如需长期保存，请在卸载或更换设备前通过“备份与恢复”手动导出外部备份；应用不会自动备份，也不会把备份上传到任何服务器。
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

## Android 版本支持与设备验收（第三阶段）

### 支持范围与单 APK 策略

- 单一通用 APK，`minSdk 26`（Android 8.0），`compileSdk/targetSdk 37`。
- 不为红米或其他品牌维护单独分支、私有实现或按型号分包；同一 APK 面向兼容范围内的设备。
- 目标兼容范围是 API 26 至 37。这是设计目标，**不等于所有 Android 版本都已实测**。
- 实际验证过的设备/版本只有下表；未列出的版本和未列出的厂商均未验证。

| 设备 | 型号/代号 | Android / API | ROM | ABI | 实际结果 |
| --- | --- | --- | --- | --- | --- |
| Redmi | M511CD / athens | Android 17 / API 37 | HyperOS OS4.0（V816），安全补丁 2026-10-01 | arm64-v8a | 已安装 Debug APK，完成项目/节点/记录、SAF 导入、预览、备份导出与真实恢复、旋转/重启等验收；修复后完整仪器测试 14/14，提交取消重点用例连续 3/3 |
| Google Android Emulator | sdk_gphone64_x86_64 / emu64xa | Android 16 / API 36 | Google userdebug `BE2A.250530.026.F3`，安全补丁 2025-07-05 | x86_64 | 修复后提交边界回归：原失败单测 5/5，完整套件 14/14 连续 2 次 |

- 本轮实际使用 `emulator-5554`（API 36）完成提交边界修复回归；**API 26、29、31、32、33 以及其它设备/ROM 未验证**。
- 一台红米设备通过验收不代表兼容所有 Android 手机。发布前仍需覆盖代表性强的最低版本、存储边界版本和主流厂商 ROM。

### 已执行的设备验收

- 安装 Debug APK；创建/编辑项目，创建进展、暂停、阻塞等自定义节点，修改节点发生时间，添加项目记录和节点记录。
- 删除节点后确认记录保留，并显示为未关联内容；应用强制停止和冷启动后数据仍在；横竖屏旋转后页面和文件仍在。
- 通过真实 SAF 导入 WAV、MP4、TXT、JSON、Kotlin 代码、PNG、PDF、PPTX；导入 15 MB 大文件；两个同名 `same_name.txt` 内容不同且均保留。
- 实际播放音频和视频，离开预览页后 ExoPlayer 释放；中文 UTF-8 文本、JSON 和代码可读；非法 UTF-8 明确拒绝预览；300 KB 文本显示“仅显示前 256 KB”的截断提示。
- PNG、PDF、PPTX 不提供新增的内置预览，实际拉起系统 Resolver 进行外部打开（修复了 Application Context 缺少 `FLAG_ACTIVITY_NEW_TASK` 的真实 Android 运行时错误）。
- 导出真实 `.ptbackup` ZIP 并拉取检查：manifest、data、13 个附件和附件 SHA-256/大小一致；在非空数据库上恢复成功，新增 1 项目、2 节点、2 记录、13 文件，原项目和附件未覆盖；恢复副本使用新 ID 和新的内部文件名。
- 在真机 Android 文件系统 + Room 上验证缺附件、错误 SHA-256、损坏 JSON、ZIP 路径异常四类损坏备份均被整体拒绝，不新增数据或附件。
- 真机 SAF 测试确认 provider 对同一文档 URI 的 `rwt` 写入会截断旧内容。HyperOS 的“另存为”界面对同名文件会自动改名为 `(1)`，因此无法用该 UI 构造真正的覆盖保存；应用采用 `rwt` 优先、`wt` 回退，不接受不保证截断的 `w`。部分文件提供方还会在保存后追加 `.zip`（实际文件名可能为 `.ptbackup.zip`）；恢复按 ZIP 内容、manifest 和数据库版本校验，不依赖扩展名。
- 发现一次前台空白窗口（连续返回/外部打开后），强制停止并重启后未再复现，也没有崩溃日志；列为遗留风险，发布前继续观察。
- 由于导出在 15 MB 数据下很快完成，未能在 UI 中拦到“取消任务”按钮；取消/提交边界由真机仪器测试和 JVM 测试覆盖，未把该场景写成人工取消已验证。

### 设备测试命令与 USB 前提

1. 在手机上开启开发者选项、USB 调试；HyperOS 还需开启“USB 安装/通过 USB 安装应用”，保持屏幕解锁。
2. 连接电脑并确认授权：
   ```bat
   adb devices -l
   ```
3. 安装 Debug APK：
   ```bat
   adb install -r -t app\build\outputs\apk\debug\app-debug.apk
   ```
4. 运行 JVM 测试、Lint 和构建：
   ```bat
   gradlew.bat assembleDebug lint testDebugUnitTest
   ```
5. 运行真机仪器测试（推荐，不会在结束时卸载应用）：
   ```bat
   gradlew.bat connectedDebugAndroidTest "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"
   ```
   本轮结果（修复前）：Redmi M511CD / API 37 实际执行 **11 tests，0 failures，0 errors**；Gradle 同时发现一个未启动的 `emulator-5554`（API 36），在其上安装失败且未执行任何测试（0 tests）。修复提交边界后已在 API 36 模拟器执行 **14 tests，0 failures，0 errors**，连续完整套件 2 次通过；原失败单测连续 5 次通过。修复后已在 Redmi API 37 完成完整仪器测试 14/14，提交取消重点用例连续 3/3。
   或先安装 `app-debug-androidTest.apk`，直接运行：
   ```bat
   adb shell am instrument -w com.projecttrack.app.test/androidx.test.runner.AndroidJUnitRunner
   ```
   默认的 `connectedDebugAndroidTest` 在部分 AGP 配置下会在结束后卸载应用；`leaveApksInstalledAfterRun=true` 用于保留应用数据。HyperOS 会阻止 instrumentation 进程直接启动 Activity，UI 测试通过 UiAutomation 以 shell 身份启动 Activity，并使用 debug-only 测试宿主验证空/错误状态；这是测试适配，不是产品功能。

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

JVM 单元测试位于 `app/src/test/java/`，覆盖首阶段的文件类型识别、最新节点选择、节点删除后的资料保留、文件过滤、同名文件处理、流式复制、失败回滚、UTF-8 校验和孤立文件清理，以及第二阶段的备份/恢复完整往返、恢复到非空数据库、重复恢复、附件缺失/大小/SHA-256 校验、损坏 JSON/缺字段/无效关系/不支持版本、ZIP 路径穿越/绝对路径/重复条目、导出与恢复取消、读写与数据库失败回滚、跨项目关联拒绝、导出期间数据一致性、导出 JSON/条目预算边界和 Room schema 导出。JVM 提交竞争测试使用可控 `TransactionRunner` 模拟“DAO 已写入、事务尚未返回”的窗口；真实 Room 的提交取消边界另由 Android 仪器测试覆盖，不能把 JVM 模拟当作真实 Room 复现。

Android 仪器测试位于 `app/src/androidTest/java/`，在真实设备上覆盖：真实 Room 外键/级联与节点 detach、真实 Room + 真实文件系统的非破坏性恢复和 ID 重映射、数据库失败回滚、真实 Room 提交边界取消（备份恢复与文件导入）、提交前取消清理、四类损坏备份拒绝、SAF `rwt` 截断、外部打开 Intent 必须带 `NEW_TASK`、Compose 主流程与空/错误状态。

截至 2026-11-24：

- `assembleDebug`：通过。
- `testDebugUnitTest`：**74 个用例，0 失败、0 错误、0 跳过**；强制重新执行确认。
- `lint`：通过，**0 errors、4 warnings**。警告为既有非阻断项：lifecycle 自定义 lint 校验跳过、`enableOnBackInvokedCallback` 在 API 33+ 才使用、`allowBackup` 在新系统上的替代提示、`mipmap-anydpi-v26` 可合并；没有新增阻断问题。
- Android 仪器测试（修复后）：API 36 模拟器 **14 个用例，0 失败、0 错误**，完整套件连续 2 次通过，原失败单测连续 5 次通过；Redmi M511CD / API 37 完整套件 **14/14**，提交取消重点用例连续 **3/3**。
- Gradle `connectedDebugAndroidTest`（`leaveApksInstalledAfterRun=true`）：在 API 37 红米上曾通过 11/11（修复前），测试后应用数据保留；同一任务发现的 `emulator-5554` 当时未启动，0 tests。修复后的最终验证通过直接 `adb shell am instrument` 在 API 36 模拟器完成 14/14 × 2，并在 Redmi API 37 完成 14/14 及重点用例 3/3。
- APK 权限检查不含 INTERNET、相机、麦克风或外部存储权限；APK 仅含应用自定义 `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`。
- Room schema 已导出到 `app/schemas/`，数据库版本保持 1。
- 设备验收已实际完成本项目记录中的主流程、SAF 导入、音频/视频播放、外部打开、备份导出与非破坏性恢复；API 26/29/31/32/33 和其它设备/ROM 未验证，不能宣称“兼容所有 Android 版本”。

## 后续优先事项与范围边界

下一阶段优先事项：

- 在 API 26、29、31、32、33 及代表性厂商 ROM 上补做设备矩阵验收；发布前至少覆盖最低版本、存储边界版本和最新目标版本。
- 为未来的 Room schema 变更补充迁移和迁移测试（当前版本 1）。
- 在支持同名覆盖的 SAF 提供方上补做 UI 级覆盖导出并检查旧尾部；在较大备份下手工验证取消、失败清理和提交边界 UI。
- 若后续修改事务提交或取消逻辑，需重新在 API 36 或等效环境中执行提交边界回归。
- 调查一次性空白窗口现象（本轮未复现、无崩溃日志），确认是否与 HyperOS 返回/外部打开过渡有关。
- 备份恢复的可用性改进（仍须保持非破坏性和完全本地化）。

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

## Local Backup and Restore (Phase Two)

The project list exposes a visible “Backup and Restore” entry. Backup and restore are fully local and require no account, server, or network.

### Exporting a complete backup

- Choose a location through the system save dialog to create a ZIP container with the `.ptbackup` extension (MIME `application/zip`).
- The backup contains all projects, dynamic nodes, project and node notes, file metadata, and every file copy in the app's private storage.
- The archive contains `manifest.json`, `data.json`, and `attachments/`. The manifest records the format name/version, app version, database schema version, creation time, project/file counts, and total size. `data.json` stores entity relationships, timestamps, and each attachment's relative path, size, and SHA-256.
- Backups contain no absolute local paths, SDK or user directories, `local.properties`, signing files, caches, build artifacts, or private AI handoff material.
- Export streams data and never loads a whole video, audio file, or ZIP into memory. The UI reports running, success, canceled, and failed states.
- While an export is running, project edits, node/note changes, file imports/deletions, and restores are blocked so that metadata and attachment contents come from one stable snapshot.
- If any attachment copy is missing or its actual size differs from the database record, the export fails with an explanation instead of producing a backup that looks complete but lacks files.
- The export target must support **rwt or wt** safe truncation. If a provider only supports the non-truncating `w` mode, the app refuses to write and asks the user to create a blank file or choose another location, preventing stale tail content from an older document from polluting the new backup.
- On cancellation or failure, the app attempts to delete the incomplete output at the user-selected location. If the system cannot delete it, the UI explicitly asks the user to remove it manually.

### Non-destructive restore

- Choose a backup through the system file picker. Validity is never determined from the file extension alone.
- Before changing existing data, the app extracts the archive into private temporary storage and validates ZIP structure, manifest/format version, database version, `data.json` fields and types, project/node/note/file relationships, attachment presence, sizes, and SHA-256 values.
- Restore is additive: existing projects and attachments are never deleted, overwritten, or modified. Restored projects, nodes, notes, and files receive new database IDs, and old-to-new ID mapping restores every relationship.
- Notes and files that had become unlinked after node deletion remain unlinked (`nodeId` stays null) after restore.
- File copies receive new unique internal names and cannot overwrite existing attachments. Restoring the same backup repeatedly creates duplicate projects; the confirmation prompt states this explicitly.
- All attachments are copied before metadata is written in a single Room transaction. On failure or cancellation, newly copied attachments are deleted, the temporary extraction directory is removed, and the Room transaction rolls back, leaving existing data unchanged.
- Cancellation semantics: cancelling before the final database commit cleans newly copied attachments and the temporary directory and propagates cancellation. The final commit runs in a short non-cancellable critical section and completes atomically. Once the commit has returned, the operation is treated as successful: committed attachments are never deleted, and a completed restore is never reported as “canceled/unmodified.”
- Commit-boundary fix (November 24, 2026): real Room on API 36 exposed a return-boundary race in `withContext(NonCancellable)`. The transaction block had already completed and committed, but leaving the non-cancellable context back into a canceled outer coroutine could still throw `CancellationException`. A commit-result holder now distinguishes “transaction returned” from “canceled before/during commit”: a returned commit yields success and never deletes attachments; only an uncommitted cancellation cleans up. ZIP extraction, SHA-256, and attachment copying remain cancellable.
- A failed restore, validation failure, or user cancellation never reports success and never deletes or modifies existing data.
- ZIP safety: absolute paths, `..` traversal, backslashes, directory entries, duplicate entries, unknown entries, and out-of-bounds attachment paths are rejected. Entry counts, JSON sizes, individual attachment sizes, and total attachment sizes are bounded.
- The backup format starts at version 1. Only format version 1 and database schema version 1 are currently supported. Unknown or incompatible versions are rejected explicitly with no partial restore.
- A successful restore reports the number of projects, nodes, notes, and files added.

### Privacy and limitations

- Backups are unencrypted and contain all user project data and attachment copies. Anyone who obtains a backup file can read its contents, so store backups in a trusted location.
- Cloud backup, automatic synchronization, encrypted backups, merge/deduplication, and overwrite-style restore are not provided.
- To protect against malformed archives and disk exhaustion, current limits are: at most 100,000 ZIP entries, 32 MB per JSON entry, 4 GB per attachment, and 16 GB total attachments.

## Local Storage and Uninstall Risk

- Structured data is stored in the Room database `projecttrack.db`.
- Imported files are copied to `filesDir/attachments/`; only metadata and relative paths are stored in the database.
- Internal names combine timestamps and random components, so files with the same display name do not overwrite each other.
- **Uninstalling the app deletes its private database and all imported file copies.** To preserve data before uninstalling or switching devices, manually export a backup through “Backup and Restore.” The app does not back up automatically and never uploads backups to a server.
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

## Android Version Support and Device Acceptance (Phase Three)

### Supported range and single-APK strategy

- Single universal APK, `minSdk 26` (Android 8.0), `compileSdk/targetSdk 37`.
- No brand-specific branches, private implementations, or per-model packages; one APK targets the supported range.
- The target compatibility range is API 26 through 37. This is a design target, **not a claim that every Android version has been tested**.
- Only the device/version in the table below was actually verified; unlisted versions and vendors are unverified.

| Device | Model / codename | Android / API | ROM | ABI | Actual result |
| --- | --- | --- | --- | --- | --- |
| Redmi | M511CD / athens | Android 17 / API 37 | HyperOS OS4.0 (V816), security patch 2026-10-01 | arm64-v8a | Installed the Debug APK; completed project/node/note flow, SAF import, previews, real backup export/restore, rotation, restart, and more; post-fix full suite 14/14, focused commit-cancellation test 3/3 |
| Google Android Emulator | sdk_gphone64_x86_64 / emu64xa | Android 16 / API 36 | Google userdebug `BE2A.250530.026.F3`, security patch 2025-07-05 | x86_64 | Post-fix commit-boundary regression: formerly failing single test 5/5; complete suite 14/14 twice |

- This round used `emulator-5554` (API 36) for the commit-boundary fix verification; **API 26, 29, 31, 32, and 33, as well as other devices/ROMs, were not verified**.
- One Redmi device passing does not mean compatibility with every Android phone. Before release, representative minimum-version, storage-boundary, and current-target devices/ROMs must still be covered.

### Device acceptance performed

- Installed the Debug APK; created/edited a project; created custom progress, paused, and blocked nodes; changed a node's occurrence time; added project and node notes.
- Deleted a node and confirmed its records were retained as unassociated content; force-stopped and cold-started the app and confirmed data persisted; rotated landscape/portrait and confirmed the page and files survived.
- Imported WAV, MP4, TXT, JSON, Kotlin code, PNG, PDF, and PPTX through real SAF; imported a 15 MB file; imported two different files sharing the display name `same_name.txt` without overwriting.
- Actually played audio and video and verified ExoPlayer release after leaving the preview; read Chinese UTF-8 text, JSON, and code; rejected invalid UTF-8 with an explicit message; displayed the “only first 256 KB” warning for a 300 KB text file.
- PNG, PDF, and PPTX had no new in-app preview and actually launched the system resolver for external opening (an Application Context `FLAG_ACTIVITY_NEW_TASK` runtime bug was fixed).
- Exported a real `.ptbackup` ZIP and inspected it locally: manifest, data, 13 attachments, sizes, and SHA-256 were consistent. Restored into a non-empty database: 1 new project, 2 nodes, 2 notes, and 13 files were added; existing projects and attachments were not overwritten; restored copies received new IDs and internal file names.
- On real Room plus the real Android filesystem, four corrupted backup classes (missing attachment, bad SHA-256, corrupt JSON, ZIP path anomaly) were rejected without adding data or attachments.
- Real SAF verification confirmed that writing with `rwt` to the same provider document truncates the old content. HyperOS's save dialog auto-renames same-name files to `(1)`, so a true overwrite could not be constructed through that UI; the app prefers `rwt`, falls back to `wt`, and refuses non-truncating `w`. Some providers also append `.zip` after saving (the actual name may be `.ptbackup.zip`); restore validates the ZIP content, manifest, and database version and does not depend on the extension.
- One transient blank foreground window was observed after repeated back/external-open interactions; it did not reproduce after force-stop/restart and no crash log was found. It remains a monitored risk.
- The 15 MB export completed too quickly to intercept the “cancel” button in the UI; cancellation/commit-boundary semantics were covered by device instrumentation and JVM tests, and manual cancellation is not claimed as verified.

### Device test commands and USB prerequisites

1. Enable Developer options and USB debugging on the phone. HyperOS also requires “Install via USB”; keep the screen unlocked.
2. Connect and authorize:
   ```bat
   adb devices -l
   ```
3. Install the Debug APK:
   ```bat
   adb install -r -t app\build\outputs\apk\debug\app-debug.apk
   ```
4. Run JVM tests, lint, and the build:
   ```bat
   gradlew.bat assembleDebug lint testDebugUnitTest
   ```
5. Run device instrumentation tests while keeping app data:
   ```bat
   gradlew.bat connectedDebugAndroidTest "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"
   ```
   The pre-fix run produced **11 tests, 0 failures, 0 errors** on the Redmi M511CD / API 37. Gradle also discovered a non-running `emulator-5554` (API 36), which ran **0 tests**. After the commit-boundary fix, the API 36 emulator ran the final **14 tests, 0 failures, 0 errors** twice in succession, and the formerly failing test passed 5 consecutive runs. The fixed build then passed the full 14/14 suite on Redmi API 37, with the focused commit-cancellation test passing 3 consecutive runs.
   Alternatively install `app-debug-androidTest.apk` and run:
   ```bat
   adb shell am instrument -w com.projecttrack.app.test/androidx.test.runner.AndroidJUnitRunner
   ```
   Without `leaveApksInstalledAfterRun=true`, some AGP configurations uninstall the app after connected tests. HyperOS blocks instrumentation from starting Activities directly, so UI tests use UiAutomation shell launches and a debug-only test host for empty/error states; this is test adaptation, not a product feature.

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

JVM unit tests under `app/src/test/java/` cover the phase-one behaviors (file classification, latest-node selection, content retention after node deletion, file filtering, duplicate names, streaming copies, rollback, UTF-8 validation, and orphan cleanup) plus phase-two backup/restore round trips, restoring into a non-empty database, repeated restores, missing/size/SHA-256 attachment validation, corrupt JSON, missing fields, invalid relationships, unsupported versions, ZIP path traversal/absolute paths/duplicate entries, export and restore cancellation, read/write/database failure rollback, cross-project relationship rejection, export consistency, export JSON/ZIP-entry budget boundaries, and Room schema export. JVM commit-race tests use a controllable `TransactionRunner` to simulate “DAO written, transaction not yet returned”; the real-Room commit-boundary behavior is covered separately by Android instrumentation tests.

Android instrumentation tests under `app/src/androidTest/java/` run on a real device and cover real Room foreign-key/cascade/detach behavior, non-destructive restore and ID remapping with real Room + the real filesystem, database failure rollback, real-Room commit-boundary cancellation for both backup restore and file import, pre-commit cleanup, four corrupted-backup rejections, SAF `rwt` truncation, the external-open `NEW_TASK` intent, and Compose main/empty/error flows.

As of November 24, 2026:

- `assembleDebug`: passed.
- `testDebugUnitTest`: **74 tests, 0 failures, 0 errors, 0 skipped**; forcibly rerun.
- `lint`: passed with **0 errors and 4 warnings**. The warnings are existing non-blocking items: lifecycle custom lint checks skipped, `enableOnBackInvokedCallback` only used on API 33+, an `allowBackup` replacement hint, and a mergeable `mipmap-anydpi-v26` folder.
- Device instrumentation after the fix: the API 36 emulator passed **14 tests, 0 failures, 0 errors** twice in succession and the formerly failing test passed 5 consecutive runs; Redmi M511CD / API 37 passed the full suite **14/14**, with the focused commit-cancellation test passing **3/3**.
- Gradle `connectedDebugAndroidTest` with `leaveApksInstalledAfterRun=true`: **11/11 passed on the pre-fix Redmi build**, and app data remained installed. The emulator discovered by that run was not yet booted and executed 0 tests. Final post-fix verification used direct `adb shell am instrument`: 14/14 twice on the API 36 emulator, then 14/14 on Redmi API 37 plus 3/3 focused commit-cancellation runs.
- APK permission inspection found no Internet, camera, microphone, or external-storage permissions; only the app-defined `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` remains.
- The Room schema is exported to `app/schemas/` and the database version remains 1.
- Actual device acceptance covered the project/node/note flow, SAF imports, audio/video playback, external opening, backup export, and non-destructive restore. API 26/29/31/32/33 and other devices/ROMs were **not** verified, so “compatible with all Android versions” must not be claimed.

## Next Priorities and Scope Boundaries

Next-phase priorities:

- Extend device acceptance to API 26, 29, 31, 32, and 33 with representative OEM ROMs; before release, cover at minimum the minimum version, storage-boundary versions, and the current target.
- If transaction-commit or cancellation logic changes again, rerun commit-boundary regression on API 36 or an equivalent environment.
- Add migrations and migration tests for future Room schema changes (the current version is 1).
- Exercise UI-level overwrite export on a SAF provider that supports same-name replacement, and verify stale-tail truncation there. Manually exercise cancellation, failure cleanup, and the commit-boundary UI with a larger backup.
- Investigate the one-off blank-window observation (not reproduced in this round; no crash log) and determine whether it relates to HyperOS back/external-open transitions.
- Usability improvements to backup/restore while preserving non-destructive, fully local behavior.

The current product direction excludes in-app image, PDF, and Office previews, as well as cloud synchronization, accounts, collaboration, audio recording, video capture, code editing, predefined workflows, Gantt charts, and completion percentages. They must not be added without an explicit direction change from the human project owner.

## Contributing and Conduct

Read the [Contributing Guide](CONTRIBUTING.md), [Code of Conduct](CODE_OF_CONDUCT.md), and [Security Policy](SECURITY.md) before contributing. New or updated public documentation should provide both Chinese and English content.

## License

ProjectTrack is licensed under the [MIT License](LICENSE). The license itself retains the standard English MIT text to avoid altering its terms; project documentation is provided in Chinese and English.
