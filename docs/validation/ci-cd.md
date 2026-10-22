# GitHub Actions CI/CD 验收记录

日期：2026-10-22，操作者：Codex-CI。Java 根包 `io.github.teamcreating.mtrmap`，Maven group `io.github.teamcreating`，模组版本保持 `1.5.2`。本任务未修改平台源码、已发布正式版标签或附件。

## 已完成的检查

- Actions 专用语法工具 actionlint v1.7.12、Python 语法检查、git diff 空白检查通过；各 Action 固定完整提交 SHA。
- 两个分支均通过 18 项 CI 工具回归测试，覆盖六种 JAR 合约、旧命名空间、版本、字节码、入口、Mixin、依赖隔离、许可证、测试缺失/失败/异常跳过、危险 ZIP 路径、候选来源/过期/Artifact 锁定、审批后摘要变化、标签冲突、Snapshot PR/外部来源/旧提交拒绝及上传失败保留旧链接。
- 从现有本地六包完整封装候选并重新校验，226 个文件纳入完整性清单；篡改发布说明后被拒绝。许可证比较只容忍 LF/CRLF 差异，不改变原始许可内容。
- GitHub Runner 已重新构建六个目标，主线两包共享 86 项通过测试，四个兼容目标各 89 项通过；生成测试世界的用例按设计显式跳过。已下载两个远端 CI bundle，独立复核所有 JAR、测试报告和文件哈希。
- 成功生成六包 Candidate 和 Nightly 证据，已下载并完整重新校验。相同源码组合第二次 Nightly 命中成功记录、跳过六平台构建；失败的完整验证不会创建成功记录。
- Snapshot 已发布为可更新 Prerelease，同一页面提供两分支六包，分别记录 SHA、版本、更新时间和 CI 来源。页面清单、六个公开附件的远端 SHA-256 均已核对；正式版 v1.5.2 继续保留 Latest。自动 workflow_run 和手动核对入口均已触发。
- Publish 使用真实候选完成 dry-run，实际发布 job 按设计跳过；未创建正式标签、未发布正式版本。现有同版本标签不匹配候选源码时，dry-run 报告阻止原因，实际发布必须中止。

## 可复查的首次验收来源

| 项目 | 运行 / Artifact |
|---|---|
| main CI，源码 `9ea00e93fda7c4f4f78a60210595957af7bdb775` | [39795208609](https://github.com/teamCreating/MTR-Map-Overlay/actions/runs/39795208609) |
| compat CI，源码 `7f633673e137ff3abc40f6e283fe102e1c05e6ce` | [39795216879](https://github.com/teamCreating/MTR-Map-Overlay/actions/runs/39795216879) |
| 六包候选 | [39795528897](https://github.com/teamCreating/MTR-Map-Overlay/actions/runs/39795528897)，Artifact `12432214611` |
| Nightly 完整验证 | [39795535590](https://github.com/teamCreating/MTR-Map-Overlay/actions/runs/39795535590) |
| Nightly 相同 SHA 跳过 | [39796589094](https://github.com/teamCreating/MTR-Map-Overlay/actions/runs/39796589094) |
| Publish 只读演练 | [39797034921](https://github.com/teamCreating/MTR-Map-Overlay/actions/runs/39797034921) |
| Snapshot | [固定下载页](https://github.com/teamCreating/MTR-Map-Overlay/releases/tag/snapshot)；[首次自动更新](https://github.com/teamCreating/MTR-Map-Overlay/actions/runs/39796997682)，[主线自动更新](https://github.com/teamCreating/MTR-Map-Overlay/actions/runs/39797626681)，[手动核对](https://github.com/teamCreating/MTR-Map-Overlay/actions/runs/39797046220) |

候选 Manifest SHA-256：`3099ed0dcecdac3c47a458ffda2512fc09b311b71c5f966998ff434076bd5764`。

Nightly Manifest SHA-256：`c001440b3cbc0d542178537f05b92651f67e63f667c19744182fafe1a1c570df`。

后续文档提交会更新分支 HEAD 并正常触发新 CI/Snapshot；上表记录首次验收的固定来源，不随 HEAD 改写。候选未做新的游戏内人工验收，不应直接批准为正式发布。

## 远端设置与后续人工操作

- `github-release` Environment 已建立：仅允许 main，审批人为 `A-BenLi06`，允许自发自审，禁止管理员绕过环境审批。
- 维护分支规则在验收记录推送后启用：必须 PR，`CI Gate`（GitHub Actions app `15368`）必过且与目标分支同步，禁止强推和删除，不增加第二位代码审查人。
- 用户已选择稍后配置 `RELEASE_TOKEN`。该凭证仅限此仓库，需要 Contents/Workflows write、Actions read，保存到 `github-release` 环境；未复制本地 GitHub 登录凭证。其缺失不影响上述流程及 dry-run，真实发布会明确失败。
- 正式发布时间及版本继续由用户决定；本次没有触发审批或正式发布，没有运行新的 Minecraft 实机验收。使用方式见 [CI/CD 文档](../CI-CD.md)。
