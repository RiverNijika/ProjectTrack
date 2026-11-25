# ProjectTrack 发布说明与发布流程

[English](#projecttrack-release-and-signing-guide)

> **状态：0.2.0 正式版本，正式签名、覆盖升级与设备验收均已完成。**
>
> 当前版本为 `versionCode = 2`、`versionName = 0.2.0`。正式签名 APK/AAB 已生成并通过校验，随 `v0.2.0` Tag 和 GitHub Release 发布。

## 1. 当前正式版本

| 项目 | 值 |
| --- | --- |
| versionCode | `2` |
| versionName | `0.2.0` |
| applicationId | `com.projecttrack.app` |
| minSdk / targetSdk | `26` / `37` |
| Room 数据库版本 | `1` |
| Room schema | `app/schemas/com.projecttrack.app.data.local.ProjectTrackDatabase/1.json` |
| 备份格式 | `ProjectTrackBackup` formatVersion `1`，databaseVersion `1` |
| 正式签名 | **已完成正式签名与验证**（APK v2/v3、AAB jarsigner） |
| 发布状态 | **v0.2.0 正式发布** |

0.1.0（versionCode 1）保留为覆盖升级的旧版本基线。不得用低 versionCode 覆盖高版本，也不得通过删除数据库或降级来绕过升级失败。

## 2. 构建与分发策略

- 面向 GitHub 直接分发：使用完整通用 APK，不使用按设备拆分的 APK，不自研差分更新。
- 可按需生成 AAB 供未来应用商店使用；AAB 不能直接安装，必须经商店签名与生成 APK。
- 正式发布包必须来自本正式版本的同一份源码与同一正式签名密钥。
- Debug APK、androidTest APK、unsigned Release APK、AAB 和测试日志默认不提交到公开仓库。

Release 构建命令：

```bat
gradlew.bat :app:assembleDebug
gradlew.bat :app:assembleDebugAndroidTest
gradlew.bat :app:testDebugUnitTest
gradlew.bat :app:lint
gradlew.bat :app:assembleRelease
gradlew.bat :app:bundleRelease
```

## 3. 版本与覆盖升级规则

1. `versionCode` 严格递增，`versionName` 不与已发布版本重复。
2. 升级必须保留项目、节点、文字记录和附件；不得在升级时清库、重建或删除附件。
3. Room 当前版本为 1；未来 schema 变更必须加入逐级 `Migration`，并在 `ProjectTrackMigrationTest` 中用 `MigrationTestHelper` 验证。
4. 禁止 `fallbackToDestructiveMigration`，禁止以删库重建掩盖迁移问题。
5. 备份 `formatVersion` 与 `databaseVersion` 职责分离：
   - `formatVersion` 决定 ZIP/JSON 容器 reader；
   - `databaseVersion` 决定旧数据到当前业务模型的映射；
   - 未知未来格式/数据库版本必须提示“需要升级应用”，不得部分恢复或修改现有数据。
6. v1 备份必须能恢复到当前版本；当前 fixtures 位于：
   - `app/src/test/resources/backup-v1-sample.ptbackup`
   - 同目录 `backup-v1-sample.txt` 说明其合成来源与格式。

## 4. Room 迁移链

- 当前 schema 版本为 1，迁移链 `ProjectTrackMigrations.ALL` 为空。
- `ProjectTrackDatabase.build()` 显式 `.addMigrations(*ProjectTrackMigrations.ALL)`，没有破坏性回退。
- 迁移测试位于 `app/src/androidTest/java/com/projecttrack/app/data/local/ProjectTrackMigrationTest.kt`，读取 `app/schemas` 作为 androidTest assets。
- 未来新增版本时：
  1. 在实体/DAO 变化后提升 `PROJECT_TRACK_DATABASE_VERSION`；
  2. 在 `ProjectTrackMigrations.ALL` 加入 `1→2`、`2→3` 等逐级迁移；
  3. 扩展测试，逐级执行并断言旧数据、外键和附件关系；
  4. 更新备份 reader 的 databaseVersion 映射与固定兼容样本。

## 5. 备份兼容与非破坏性恢复

- 当前只支持 v1 格式。v1 reader 位于 `BackupCompatibility.kt`；未来 Room 升级时在 v1 reader 中显式映射到当前业务模型。
- 不允许把旧备份中的 databaseVersion 直接改写为当前版本，不允许跳过字段、关系、大小或 SHA-256 校验。
- 恢复始终生成新项目副本和新 ID，不覆盖已有项目；同名文件不覆盖。
- 未知未来版本、损坏 ZIP、缺失附件、大小/SHA-256 不匹配、关系越界均须在任何正式数据库/附件写入前拒绝。
- 恢复/导出取消边界已有仪器测试覆盖；提交已返回后按成功处理，不删除已提交附件。

## 6. 正式签名

**本轮已完成候选正式签名；仓库仍不包含私钥、密码或签名产物。**

- 正式密钥库在项目工作区外的受控私有目录，PKCS#12，别名 `projecttrack`，RSA 4096 / SHA256withRSA，证书有效期至 2054-04-12。
- 证书 SHA-256 指纹：`48:A8:1F:EE:3B:7E:24:2B:B6:13:E9:F3:86:06:C7:1D:FE:35:09:7A:EA:9D:5C:58:A4:04:81:E1:D7:DB:E6:34`。
- Release APK 先 zipalign，再由 `apksigner` 签名；AAB 由 `jarsigner` 签名。正式包通过 GitHub Release 上传，原始私有发布记录和校验信息继续本地保留。
- 验证结果：
  - `zipalign -c -v -p 4`：通过；
  - `apksigner verify --verbose --print-certs`：v2/v3 均为 true，证书指纹匹配；
  - `jarsigner -verify -verbose -certs`：exit 0，`jar 已验证`。
- 旧版 0.1.0 与候选 0.2.0 使用同一正式签名，已在隔离 API 36 AVD 完成覆盖升级；正式签名 0.2.0 也完成了干净安装。
- 正式签名包的大小、SHA-256、证书指纹和验证输出已记录在本地私有发布记录中。

未来正式版本的签名规则：

1. 使用同一把正式密钥；密钥、密码、别名不得进入仓库、Gradle 明文配置、文档、Issue、CI 日志或聊天。
2. 密钥必须备份到独立离线介质/可信密码管理方案；当前用户已决定暂缓离线备份，因此**仍存在密钥丢失风险**，不得写成已备份。
3. 通过环境变量或交互式 stdin/受控进程 API 注入密码，不把明文密码放入命令行。
4. 用 `apksigner` 签名 APK、`jarsigner` 签名 AAB，并完整校验签名方案和证书指纹。
5. 用同一签名安装覆盖验证，不得通过卸载清数据来“解决”签名不一致。

## 7. 发布前检查清单

- [x] HEAD 包含全部预期改动，工作区无未审查文件；无 commit/push/Tag/Release 由本流程自动完成。
- [x] `versionCode` 严格递增、`versionName` 正确且未重复。
- [x] `minSdk 26` / `targetSdk 37` 未无理由变更；如变更已记录兼容影响。
- [x] Room schema 版本与迁移链一致，无 `fallbackToDestructiveMigration`。
- [x] v1 固定备份样本、未来版本拒绝、迁移测试通过。
- [x] 真实旧版（0.1.0 / versionCode 1）覆盖升级到 0.2.0 正式版本，项目/节点/记录/附件保留。
- [x] 候选版恢复旧版导出的 v1 备份成功，未覆盖现有数据。
- [x] JVM 77、Android 仪器测试 15/15（API 26/29/33/36 + 红米 API 37）、Lint 通过并记录实际数量。
- [x] API 26/29/33/36 模拟器矩阵通过；红米 API 37 完整 15/15 与重点用例 3/3 通过。
- [x] Release 不包含 `TestHostActivity` 或 debug-only 组件。
- [x] Release 权限检查无 Internet、相机、麦克风、外部存储宽泛权限。
- [x] 正式签名完成，`apksigner verify` / `jarsigner -verify` 通过，证书指纹与元数据一致。
- [x] APK/AAB 路径、大小、SHA-256、签名证书指纹记录在私有发布记录。
- [x] 更新说明、已知限制、回滚说明和 SECURITY 私密报告链接保留。
- [x] 行为准则维护者联系方式和执行流程已补齐。
- [ ] 用户/主审核最终批准并创建 Tag、Release、上传 APK。
- [ ] 离线密钥备份（用户明确暂缓；不是本轮发布阻断项，但风险未消除）。

## 8. 回滚与失败处理

- **不通过降级 versionCode、删库、清除应用数据或卸载重装来处理升级失败。**
- 用户数据出现异常时，优先保留现场，导出诊断/备份（如果应用仍可启动），等待前向修复。
- 升级失败不得回退数据库文件；应恢复上一份完整本地备份中的项目副本，或安装更高 versionCode 的修复包。
- 正式发布后若需撤回，保留已发布产物和签名记录，发布前向修复版本；不使用低 versionCode 覆盖。
- 任何发布事故必须记录：版本、设备/API、异常、影响范围、数据是否完整、修复与回归结果。

## 9. 当前已知限制与阻塞

- **已解除：BLOCKED_PHONE**。红米 M511CD / API 37 已完成 0.2.0 Debug 的完整 15/15 和重点用例 3/3，已有应用数据保留；正式签名包的升级/干净安装验证在隔离 API 36 AVD 完成。不能把模拟器结果写成红米上安装了正式签名包。
- **已解除：BLOCKED_SIGNING**。已生成 PKCS#12 正式密钥并用 DPAPI 保护密码；APK v2/v3、AAB jarsigner 验证通过，证书指纹已记录。
- **已解除：BLOCKED_MAINTAINER_CONTACT**。行为准则已补充维护者邮箱、报告主题、保密、公平审查和禁止报复原则。
- **暂缓/不阻断本轮：离线密钥备份**。用户明确暂缓；当前没有独立离线备份，仍存在密钥丢失风险，不能写成已完成。
- **仍未验证**：API 31/32 和其它厂商 ROM；低存储、大备份人工取消、进程被杀恢复仅覆盖现有自动化边界。
- **本轮未复现**：HyperOS 一次性空白窗口；红米连续外部打开/返回 3 轮和项目/列表/备份导航均保持正常焦点，不能据此证明问题不存在。

---

# ProjectTrack Release and Signing Guide

> **Status: 0.2.0 stable release. Production signing, in-place upgrade verification, and device acceptance are complete.**
>
> Current version: `versionCode = 2`, `versionName = 0.2.0`. The production-signed APK/AAB has been generated and verified and is distributed through the `v0.2.0` tag and GitHub Release.

## 1. Current stable version

| Item | Value |
| --- | --- |
| versionCode | `2` |
| versionName | `0.2.0` |
| applicationId | `com.projecttrack.app` |
| minSdk / targetSdk | `26` / `37` |
| Room database version | `1` |
| Room schema | `app/schemas/com.projecttrack.app.data.local.ProjectTrackDatabase/1.json` |
| Backup format | `ProjectTrackBackup` formatVersion `1`, databaseVersion `1` |
| Production signing | **Completed and verified** (APK v2/v3, AAB jarsigner) |
| Release status | **v0.2.0 stable release** |

0.1.0 (versionCode 1) remains the old-version baseline for in-place upgrade testing. Never downgrade versionCode or delete the database to bypass an upgrade failure.

## 2. Build and distribution policy

- GitHub direct distribution uses the complete universal APK; no per-device split APKs and no custom delta updates.
- An AAB can be produced for future stores; an AAB is not directly installable and must be signed and converted by the store.
- Production artifacts must come from the same source and the same production signing key.
- Debug APKs, androidTest APKs, unsigned Release artifacts, AABs, and test logs are not committed.

Build commands:

```bat
gradlew.bat :app:assembleDebug
gradlew.bat :app:assembleDebugAndroidTest
gradlew.bat :app:testDebugUnitTest
gradlew.bat :app:lint
gradlew.bat :app:assembleRelease
gradlew.bat :app:bundleRelease
```

## 3. Version and in-place upgrade rules

1. `versionCode` strictly increases; `versionName` never duplicates a published version.
2. Upgrades must preserve projects, nodes, notes, and attachments. Never clear the database, rebuild it, or delete attachments during upgrade.
3. Room is currently version 1. Future schema changes require stepwise `Migration`s validated with `MigrationTestHelper` in `ProjectTrackMigrationTest`.
4. `fallbackToDestructiveMigration` is forbidden. Never mask migration defects by recreating the database.
5. Backup `formatVersion` and `databaseVersion` have separate responsibilities:
   - `formatVersion` selects the ZIP/JSON reader;
   - `databaseVersion` selects the mapping from an older schema to the current business model;
   - unknown future formats/database versions must tell the user to upgrade the app and must not partially restore or modify existing data.
6. v1 backups must remain restorable on the current version. Fixed fixtures:
   - `app/src/test/resources/backup-v1-sample.ptbackup`
   - `backup-v1-sample.txt` documents its synthetic origin and format.

## 4. Room migration chain

- Current schema version is 1; `ProjectTrackMigrations.ALL` is empty.
- `ProjectTrackDatabase.build()` explicitly calls `.addMigrations(*ProjectTrackMigrations.ALL)` with no destructive fallback.
- `ProjectTrackMigrationTest` reads `app/schemas` as androidTest assets.
- For a future version:
  1. bump `PROJECT_TRACK_DATABASE_VERSION` after entity/DAO changes;
  2. add stepwise `1→2`, `2→3`, etc. migrations to `ProjectTrackMigrations.ALL`;
  3. extend tests to run each step and assert old rows, foreign keys, and attachment relationships;
  4. update the backup reader's databaseVersion mapping and fixed fixtures.

## 5. Backup compatibility and non-destructive restore

- Only format v1 is supported now. The v1 reader lives in `BackupCompatibility.kt`; future Room upgrades must add explicit field mappings there.
- Never rewrite an old backup's databaseVersion to the current version, and never skip field, relationship, size, or SHA-256 validation.
- Restore always creates new project copies and new IDs; it never overwrites existing projects or same-named files.
- Unknown future versions, corrupt ZIPs, missing attachments, size/SHA-256 mismatches, and invalid relationships must be rejected before writing the real database or attachments.
- Export/restore cancellation boundaries are covered by instrumentation tests. Once a commit has returned, it is treated as success and committed attachments are never deleted.

## 6. Production signing

**Candidate production signing is complete for this release candidate; the repository still contains no private key, password, or signed artifact.**

- The formal keystore is outside the project workspace in a controlled private directory: PKCS#12, alias `projecttrack`, RSA 4096 / SHA256withRSA, valid until 2054-04-12.
- Certificate SHA-256 fingerprint: `48:A8:1F:EE:3B:7E:24:2B:B6:13:E9:F3:86:06:C7:1D:FE:35:09:7A:EA:9D:5C:58:A4:04:81:E1:D7:DB:E6:34`.
- The Release APK was zipaligned then signed with `apksigner`; the AAB was signed with `jarsigner`. Signed artifacts are uploaded through GitHub Releases while private verification records remain local.
- Verification:
  - `zipalign -c -v -p 4`: passed;
  - `apksigner verify --verbose --print-certs`: v2/v3 true; certificate fingerprint matched;
  - `jarsigner -verify -verbose -certs`: exit 0, "jar verified".
- Old 0.1.0 and candidate 0.2.0 use the same production key; an in-place upgrade was completed on an isolated API 36 AVD. Production-signed 0.2.0 also completed a clean install.
- Artifact sizes, SHA-256 values, certificate fingerprint, and verification output are recorded in the private release record.

Rules for future production versions:

1. Use the same production key. Never put keys, passwords, or aliases in the repository, plain Gradle configuration, docs, issues, CI logs, or chat.
2. Back up the key to independent offline media or a trusted password manager. The owner has explicitly deferred offline backup, so **key-loss risk remains** and backup must not be claimed as complete.
3. Inject passwords through environment variables, interactive stdin, or a controlled process API; never put a plaintext password on the command line.
4. Sign APKs with `apksigner`, sign AABs with `jarsigner`, and verify signature schemes and certificate fingerprints.
5. Verify in-place installation with the same key; never uninstall/clear data to work around a signature mismatch.

## 7. Pre-release checklist

- [x] HEAD contains all reviewed changes; no uncommitted unreviewed files.
- [x] versionCode increases, versionName is correct and not reused.
- [x] minSdk 26 / targetSdk 37 unchanged without documented compatibility review.
- [x] Room schema and migration chain agree; no destructive fallback.
- [x] Fixed v1 backup fixture, future-version rejection, and migration tests pass.
- [x] A real 0.1.0 (versionCode 1) in-place upgrade to the candidate preserves projects, nodes, notes, and attachments.
- [x] Candidate restores an old v1 backup as a new copy without overwriting existing data.
- [x] JVM 77, Android instrumentation 15/15 (API 26/29/33/36 plus Redmi API 37), and Lint passed with recorded counts.
- [x] API 26/29/33/36 emulator matrix passed; Redmi API 37 passed 15/15 plus focused 3/3.
- [x] Release contains no `TestHostActivity` or debug-only components.
- [x] Release permissions have no Internet, camera, microphone, or broad external-storage permission.
- [x] Production signing completed and verified; certificate fingerprint matches metadata.
- [x] APK/AAB paths, sizes, SHA-256, and certificate fingerprint are in the private release record.
- [x] Release notes, known limitations, rollback guidance, and SECURITY private-report link retained.
- [x] Code-of-Conduct maintainer contact and enforcement channel completed.
- [ ] Owner/lead final approval, tag, release, and APK upload.
- [ ] Offline key backup (owner deferred; not a blocking item for this round, but the risk remains).

## 8. Rollback and failure handling

- **Never downgrade versionCode, delete the database, clear app data, or uninstall/reinstall to work around a failed upgrade.**
- Preserve the failure state; export diagnostics/backup if the app still launches; wait for a forward fix.
- Do not revert database files. Restore project copies from the previous complete local backup, or install a higher-versionCode fix.
- After release, retain published artifacts and signing records; publish a forward fix instead of downgrading.
- Record every release incident: versions, device/API, exception, scope, data integrity, fix, and regression.

## 9. Known limitations and blockers

- **Resolved: BLOCKED_PHONE**. The Redmi M511CD / API 37 passed the full 0.2.0 Debug suite 15/15 and focused 3/3, preserving existing app data. Production-signed upgrade/clean-install verification was completed on isolated API 36 AVDs; do not claim the production-signed package was installed on the phone.
- **Resolved: BLOCKED_SIGNING**. A PKCS#12 production key was generated and its password protected with DPAPI; APK v2/v3 and AAB jarsigner verification passed, and the certificate fingerprint was recorded.
- **Resolved: BLOCKED_MAINTAINER_CONTACT**. The Code of Conduct now has a maintainer email, report subject, confidentiality, fair-review, and no-retaliation principles.
- **Deferred, not blocking this round: offline key backup**. Explicitly deferred by the owner; no independent offline backup exists and key-loss risk remains. Do not claim it is complete.
- **Unverified**: API 31/32 and other OEM ROMs; low-storage, large-backup manual cancellation, and process-kill recovery are covered only by existing automated boundaries.
- **Not reproduced this round**: the one-off HyperOS blank window. Three external-open/back cycles plus project/list/backup navigation kept a normal focused window; this does not prove the issue is absent.
