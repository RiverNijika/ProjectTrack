# ProjectTrack 0.2.0 发布说明

[English](#projecttrack-020-release-notes)

> **状态：正式版本（Stable Release）。**
> `versionCode = 2`，`versionName = 0.2.0`；`v0.2.0` Tag 和 GitHub Release 已用于正式发布。

> 正式证书 SHA-256：`48:A8:1F:EE:3B:7E:24:2B:B6:13:E9:F3:86:06:C7:1D:FE:35:09:7A:EA:9D:5C:58:A4:04:81:E1:D7:DB:E6:34`

## 核心功能

- 完全本地化的 Android 项目进度跟踪：项目、动态进度节点、项目/节点文字记录、本地文件资料库。
- 音频、视频和纯文本应用内预览；文件可通过 FileProvider 交给外部应用只读打开。
- 通过系统文件选择器导入任意类型文件，项目内/跨项目筛选、搜索和排序。
- 无账号、无服务器、无云同步、无统计/广告 SDK；不申请 Internet 权限。

## 备份与非破坏性恢复

- 导出完整的 `ProjectTrackBackup` v1 ZIP（manifest、数据、附件），附件带大小和 SHA-256 校验。
- 恢复生成新项目副本和新 ID，不覆盖已有项目，不覆盖同名文件；未知未来格式/数据库版本会被拒绝且不修改现有数据。
- 本版本把备份格式版本与 Room 数据库版本职责分离，并新增 v1 兼容读取层和固定合成样本。
- 提交取消边界继续保证：已提交的恢复不会被取消清理误删附件；提交前取消会清理附件和临时文件。

## 设备兼容范围

0.2.0 正式版本实际执行了以下矩阵：

| 环境 | API / Android | 仪器测试 | 结果 |
| --- | --- | --- | --- |
| Google emulator | API 26 / Android 8.0 | 15 项 | 通过；SAF MediaStore 测试按 API29+ 条件跳过 |
| Google emulator | API 29 / Android 10 | 15 项 | 首次完整套件有 1 次 UI 等待超时；单独复现通过，完整重跑通过 |
| Google emulator | API 33 / Android 13 | 15 项 | 通过 |
| Google emulator | API 36 / Android 16 | 15 项 | 通过；正式签名 0.1.0→0.2.0 覆盖升级、旧版 v1 备份恢复、正式签名 0.2.0 干净安装/备份路径通过 |
| Redmi M511CD | API 37 / Android 17 | 15 项 | **通过**；重点提交取消用例 3/3；已有数据/27 个附件保留；空白窗口本轮未复现 |

JVM 单元测试 77 项通过。正式签名 APK 通过 zipalign、apksigner v2/v3；AAB 通过 jarsigner 验证。以上不代表兼容所有 Android 手机。

## 升级注意事项

- 从 0.1.0（versionCode 1）覆盖安装到 0.2.0（versionCode 2）会保留项目、节点、记录和附件；Debug 版已在红米 API 37 验证，正式签名版已在隔离 API 36 AVD 验证。
- 不要卸载或清除应用数据来完成升级；不要使用低 versionCode 覆盖高版本。
- Room 数据库仍为 version 1；没有执行破坏性迁移。未来 schema 变更必须逐级迁移并补迁移测试。
- 旧版导出的 v1 备份可在当前正式版本恢复为项目副本；真实旧版备份已在正式签名 API 36 AVD 验证。
- Release APK/AAB 已完成正式签名并通过校验，随 v0.2.0 GitHub Release 提供下载。
- 行为准则维护者报告渠道已补齐；私密漏洞报告仍使用 SECURITY.md 的 GitHub Security Advisories。

## 已知限制

- API 31/32 和其它厂商 ROM 未完整验证。
- 红米连续外部打开/返回 3 轮及导航本轮未出现空白窗口，但不能据此证明该历史问题不存在，仍需在后续版本观察。
- 离线密钥备份按用户决定暂缓；当前没有独立离线备份，密钥丢失风险仍未消除，不能写成已完成。
- 低存储/写入失败、较大备份人工取消、进程被杀后的恢复仍需更多设备级验证。

## 阻塞项状态

- **已解决：BLOCKED_PHONE**。红米 API 37 完成 0.2.0 Debug 完整 15/15 和重点用例 3/3；正式签名升级/干净安装在隔离 API 36 AVD 完成。
- **已解决：BLOCKED_SIGNING**。正式签名 APK/AAB 已生成并通过 zipalign、apksigner v2/v3 和 jarsigner 验证。
- **已解决：BLOCKED_MAINTAINER_CONTACT**。行为准则报告邮箱、主题、保密、公平审查和禁止报复原则已补齐。
- **按所有者决定暂缓，不列为本轮阻断项**：离线密钥备份尚未完成，密钥丢失风险仍未消除，不得声称已备份。
- **未验证**：API 31/32 和其它 OEM ROM；低存储、大备份人工取消、进程被杀恢复仍需更多设备级验证。

---

# ProjectTrack 0.2.0 Release Notes

> **Status: Stable Release.**
> `versionCode = 2`, `versionName = 0.2.0`; the `v0.2.0` tag and GitHub Release are used for the official distribution.

> Production certificate SHA-256: `48:A8:1F:EE:3B:7E:24:2B:B6:13:E9:F3:86:06:C7:1D:FE:35:09:7A:EA:9D:5C:58:A4:04:81:E1:D7:DB:E6:34`

## Core features

- Fully local Android project progress tracking: projects, user-defined progress nodes, project/node notes, and a local file library.
- In-app audio, video, and plain-text previews; files can be opened read-only by external apps through FileProvider.
- Import any file type through the system picker; filter, search, and sort within or across projects.
- No accounts, servers, cloud sync, analytics, or ads; no Internet permission.

## Backup and non-destructive restore

- Export a complete `ProjectTrackBackup` v1 ZIP (manifest, data, attachments) with size and SHA-256 validation per attachment.
- Restore creates new project copies and new IDs; it never overwrites existing projects or same-named files. Unknown future formats/database versions are rejected without modifying existing data.
- This version separates backup format version from Room database version and adds a v1 compatibility reader plus a fixed synthetic fixture.
- Commit-cancellation behavior remains: a committed restore never has its attachments deleted by cancellation cleanup; pre-commit cancellation cleans up new attachments and temporary files.

## Device coverage

Matrix actually run for this candidate:

| Environment | API / Android | Instrumentation | Result |
| --- | --- | --- | --- |
| Google emulator | API 26 / Android 8.0 | 15 tests | Passed; SAF MediaStore test skipped by its API29+ condition |
| Google emulator | API 29 / Android 10 | 15 tests | One UI-wait timeout in the first full suite; isolated retry passed and the full rerun passed |
| Google emulator | API 33 / Android 13 | 15 tests | Passed |
| Google emulator | API 36 / Android 16 | 15 tests | Passed; production-signed 0.1.0→0.2.0 upgrade, old v1 backup restore, and production-signed 0.2.0 clean install/backup path passed |
| Redmi M511CD | API 37 / Android 17 | 15 tests | **Passed**; focused commit-cancellation 3/3; existing data/27 attachments preserved; blank window not reproduced this round |

JVM unit tests: 77 passed. Production-signed APK passed zipalign and apksigner v2/v3; AAB passed jarsigner verification. This does not claim compatibility with every Android phone.

## Upgrade notes

- Installing 0.2.0 (versionCode 2) over 0.1.0 (versionCode 1) preserves projects, nodes, notes, and attachments; the Debug build was verified on the Redmi API 37 and the production-signed build on an isolated API 36 AVD.
- Do not uninstall or clear app data to upgrade, and never overwrite a higher version with a lower versionCode.
- Room remains version 1; no destructive migration was performed. Future schema changes require stepwise migrations and tests.
- A v1 backup exported by the old version restores as project copies on the current candidate; a real old-version backup was verified on the production-signed API 36 AVD.
- The Release APK/AAB are production-signed and verified; signed artifacts remain in a private local directory and have not been uploaded or released.
- The Code of Conduct maintainer reporting channel is now complete; private security reports still use GitHub Security Advisories as described in SECURITY.md.

## Blocker status

- **Resolved: BLOCKED_PHONE**. Redmi API 37 passed the full 0.2.0 Debug suite 15/15 and focused 3/3; production-signed upgrade/clean-install verification completed on isolated API 36 AVDs.
- **Resolved: BLOCKED_SIGNING**. Production-signed APK/AAB artifacts were generated and verified with zipalign, apksigner v2/v3, and jarsigner.
- **Resolved: BLOCKED_MAINTAINER_CONTACT**. The Code of Conduct report email, subject, confidentiality, fair-review, and no-retaliation principles are complete.
- **Deferred by the owner, not a blocker for this round**: offline key backup remains incomplete; key-loss risk remains and backup must not be claimed complete.
- **Unverified**: API 31/32 and other OEM ROMs; low-storage, large-backup manual cancellation, and process-killed recovery need more device-level verification.

## Known limitations

- API 31/32 and other OEM ROMs are not fully verified.
- The Redmi blank-window issue was not observed during three external-open/back cycles and navigation this round, but that does not prove it is absent; continue monitoring in later versions.
- Offline key backup is deferred by the owner; no independent offline backup exists, so key-loss risk remains and backup must not be claimed complete.
- Low-storage/write-failure, large-backup manual cancellation, and process-killed recovery need more device-level verification.
