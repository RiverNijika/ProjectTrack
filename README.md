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

JVM 单元测试位于 `app/src/test/java/`，覆盖首阶段的文件类型识别、最新节点选择、节点删除后的资料保留、文件过滤、同名文件处理、流式复制、失败回滚、UTF-8 校验和孤立文件清理，以及第二阶段的备份/恢复完整往返、恢复到非空数据库、重复恢复、附件缺失/大小/SHA-256 校验、损坏 JSON/缺字段/无效关系/不支持版本、ZIP 路径穿越/绝对路径/重复条目、导出与恢复取消、读写与数据库失败回滚、跨项目关联拒绝、导出期间数据一致性、导出 JSON/条目预算边界和 Room schema 导出。提交竞争测试使用可控 `TransactionRunner` 模拟“DAO 已写入、事务尚未返回”的窗口，验证的是取消语义，不代表已在真实 Room 或真机上复现竞争。

截至 2026-11-23：

- `assembleDebug`：通过。
- `testDebugUnitTest`：74 个用例，0 失败、0 错误；已强制重新执行确认（包含提交竞争与导出预算测试）。
- `lint`：通过，存在非阻断警告。
- APK 权限检查不含 INTERNET、相机、麦克风或外部存储权限。
- Room schema 已导出到 `app/schemas/`，数据库版本保持 1。
- **尚未进行设备验证**：没有安装 APK，没有实际通过 SAF 导出/恢复备份，没有验证“覆盖旧备份后文件尾部无残留”、没有实际播放 MP3/MP4，也没有通过真实文件走查导入和外部打开。上述场景是发布前必须补做的验收项。

## 后续优先事项与范围边界

下一阶段优先事项：

- 真机或模拟器完整验收，包括 SAF 备份导出/恢复、重复恢复和取消操作。
- 为未来的 Room schema 变更补充迁移和迁移测试（当前版本 1）。
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

JVM unit tests under `app/src/test/java/` cover the phase-one behaviors (file classification, latest-node selection, content retention after node deletion, file filtering, duplicate names, streaming copies, rollback, UTF-8 validation, and orphan cleanup) plus phase-two backup/restore round trips, restoring into a non-empty database, repeated restores, missing/size/SHA-256 attachment validation, corrupt JSON, missing fields, invalid relationships, unsupported versions, ZIP path traversal/absolute paths/duplicate entries, export and restore cancellation, read/write/database failure rollback, cross-project relationship rejection, export consistency, export JSON/ZIP-entry budget boundaries, and Room schema export. Commit-race tests use a controllable `TransactionRunner` to simulate “DAO written, transaction not yet returned”; they verify cancellation semantics and do not claim that a race was reproduced on real Room or a physical device.

As of November 23, 2026:

- `assembleDebug`: passed.
- `testDebugUnitTest`: 74 tests, 0 failures, and 0 errors; forcibly rerun with the new commit-race and export-budget tests.
- `lint`: passed with non-blocking warnings.
- APK permission inspection found no Internet, camera, microphone, or external-storage permissions.
- The Room schema is exported to `app/schemas/` and the database version remains 1.
- **No device verification has been performed**: the APK has not been installed, SAF backup export/restore has not been exercised on a device, overwriting a larger backup and checking for stale tail bytes has not been tested, MP3/MP4 playback has not been tested, and real-file imports and external opening have not been verified. These are required before release.

## Next Priorities and Scope Boundaries

Next-phase priorities:

- Complete emulator or physical-device acceptance testing, including SAF backup export/restore, repeated restore, and cancellation.
- Add migrations and migration tests for future Room schema changes (the current version is 1).
- Usability improvements to backup/restore while preserving non-destructive, fully local behavior.

The current product direction excludes in-app image, PDF, and Office previews, as well as cloud synchronization, accounts, collaboration, audio recording, video capture, code editing, predefined workflows, Gantt charts, and completion percentages. They must not be added without an explicit direction change from the human project owner.

## Contributing and Conduct

Read the [Contributing Guide](CONTRIBUTING.md), [Code of Conduct](CODE_OF_CONDUCT.md), and [Security Policy](SECURITY.md) before contributing. New or updated public documentation should provide both Chinese and English content.

## License

ProjectTrack is licensed under the [MIT License](LICENSE). The license itself retains the standard English MIT text to avoid altering its terms; project documentation is provided in Chinese and English.
