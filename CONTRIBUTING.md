# 贡献指南

[English](#contributing-guide)

感谢您对 ProjectTrack 的关注。以下规范用于保证协作清晰、可追踪，并与当前产品方向保持一致。

## 开始之前

- 新功能或较大改动应先讨论目标和方案，避免重复工作。
- 报告问题时，先检查是否已有相同问题，并提供复现步骤、预期结果、实际结果和运行环境。
- 安全漏洞不要通过公开问题报告，请遵循 [安全政策](SECURITY.md)。
- 不要自行加入云同步、账号、服务器接口或非指定格式的应用内预览；产品方向变化由人类项目所有者决定。

## 改动原则

- 每次改动聚焦一个目标，避免混入无关重构或格式化。
- 遵循仓库的 `.editorconfig`、`.gitattributes` 及项目使用的语言和框架规范。
- 运行与改动相关的检查，并准确说明验证结果；不得把未执行或未通过的检查写成成功。
- 行为发生变化时，同步更新相关文档，不保留过时说明。
- 不提交密钥、本地环境配置、构建产物、缓存、签名文件或临时文件。
- 不擅自改变项目的完全本地化定位或 MIT 许可证。

## 文档与 AI 制作声明

- 公开说明文档的新增或修改应同时提供中文和英文内容，并保持两种语言表达的事实一致。
- README 中的 AI 制作声明及人类项目所有者角色不得删除或弱化。
- AI 生成或修改的代码、测试和文档应经过审查与适当验证，不得虚构人类贡献、测试结果或设备验收结果。

## 提交与拉取请求

- 提交应范围清晰，提交信息应准确说明改动内容和原因。
- 拉取请求应包含改动摘要、验证方式、相关问题和必要的升级说明。
- 审查中如需变更方向，应先在讨论中确认，再继续扩大改动范围。

## 文档

公开文档应与项目实际行为一致。项目功能、技术栈和维护流程发生变化时，应及时更新安装、开发、测试和使用说明。

---

# Contributing Guide

[中文](#贡献指南)

Thank you for your interest in ProjectTrack. These guidelines keep collaboration clear and traceable while respecting the current product direction.

## Before You Start

- Discuss the goals and approach for new features or substantial changes before implementation.
- Search for existing reports before filing an issue. Include reproduction steps, expected and actual behavior, and environment details.
- Do not disclose security vulnerabilities in public issues; follow the [Security Policy](SECURITY.md).
- Do not add cloud synchronization, accounts, server APIs, or additional in-app preview formats on your own. Product-direction changes are decided by the human project owner.

## Change Principles

- Keep each change focused on one goal and avoid unrelated refactoring or formatting.
- Follow `.editorconfig`, `.gitattributes`, and the project's language and framework conventions.
- Run relevant checks and report their results accurately. Never describe unexecuted or failed checks as successful.
- Update affected documentation when behavior changes, and remove obsolete descriptions.
- Do not commit secrets, local configuration, build artifacts, caches, signing material, or temporary files.
- Do not change the fully local product model or MIT License without the owner's explicit decision.

## Documentation and AI-Created Notice

- New or updated public documentation should include both Chinese and English content with consistent facts in both languages.
- The AI-created notice and human project owner's role in README must not be removed or downplayed.
- AI-generated code, tests, and documentation must receive appropriate review and verification. Do not invent human contributions, test results, or device-acceptance outcomes.

## Commits and Pull Requests

- Keep commits focused and use messages that accurately explain what changed and why.
- Pull requests should include a change summary, verification steps, related issues, and any necessary upgrade notes.
- Confirm changes in direction during review before expanding the scope further.

## Documentation

Public documentation must match actual project behavior. Update installation, development, testing, and usage instructions when features, technology, or maintenance workflows change.
