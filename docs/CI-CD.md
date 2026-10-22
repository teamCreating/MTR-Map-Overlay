# GitHub Actions：构建、Snapshot 与人工批准发布

本项目维持 `main`（NeoForge/Fabric 1.21.1）和 `compat/mtr-4.0-mc-1.20`（Forge/Fabric 1.20.1、1.20.4）。Java 根包为 `io.github.teamcreating.mtrmap`，Maven group 为 `io.github.teamcreating`。版本只由维护者修改，CI 不递增 `mod_version`。

## 日常开发与下载

向维护分支推送或提交 PR 后，`build` 运行该分支全部目标、CI 工具回归测试、JUnit 和 JAR 校验；最终必过状态是 **CI Gate**。普通 Artifact 保留 14 天，失败日志及已有测试报告也会上传。各 Action 固定到完整提交 SHA。

main 两个包共享 NeoForge 的共用测试（至少 86 项通过）；四个兼容目标分别运行测试（各至少 89 项通过）。只有 `TestWorldGeneratorTest` 可以显式跳过，CI 不生成或改写测试存档。数量下限是当前基线，修改须经审查。编译及 JAR 检查不是游戏运行验证。

通过本仓库维护分支 push/手动 CI 的包会更新固定的 [Snapshot 下载页](https://github.com/teamCreating/MTR-Map-Overlay/releases/tag/snapshot)。main 更新两个包，compat 更新四个包；每组显示自己的版本、完整源码 SHA、时间和 CI 链接。两分支开发版本可以暂时不同。

Snapshot 标为 Prerelease，正式版保留 Latest。文件包含 MC、MTR、Loader、短 SHA 和不可变构建 Artifact ID，模组内版本仍是源码的 `mod_version`。固定 `snapshot` 标签只作下载入口，不强制移动；GitHub 自动生成的 Source code 压缩包不代表不断更新的六包源码，应使用页面的实际 SHA 链接。

发布器仅执行当前 main 工作流中的可信代码；下载产物只作数据处理，不运行其中代码。发布任务串行执行，每次核对两分支最新成功构建，避免 GitHub 替换 pending 任务造成漏更。旧 SHA 构建不会回写；新文件先上传、核对，页面链接切换后才清理旧文件。失败保留上一套有效下载链接。需要修复未完成的更新时，可从 main 手动运行 `Snapshot`，重新核对两分支。

`Nightly` 每天 UTC 20:23（台北次日约 04:23）检查双分支。仅完全验证成功后保存与两 SHA 绑定的 cache marker，无变化则跳过六包构建；失败不会标记成功，cache 缺失或被回收会重新完整构建。GitHub 定时调度可能延迟。夜间证据保留 90 天。

## 准备候选包

1. 由维护者决定版本，确保两个源码提交的 `mod_version` 相同，并在 main 准备 `docs/releases/v版本.md`。
2. 在 Actions → **Candidate** → **Run workflow** 选择 `main`，填写 `version`。`main_sha`、`compat_sha` 可留空，在启动阶段各读取一次 HEAD；也可填写对应分支历史中的完整 40 位 SHA。
3. 六个目标从锁定的 SHA 独立构建；任何一个失败都不会生成完整候选。main 与 compat 无需合并源码。
4. 下载运行摘要链接中的 `candidate-运行ID-尝试号`。其中包括六包、`SHA256SUMS`、`release-manifest.json`、固定的发布说明、测试报告、`validation-evidence.zip` 和人工验收清单。证据保留 90 天。
5. 针对这批候选 JAR 完成六平台游戏验收，记录运行 ID、Manifest SHA-256、测试人、时间和结论。曾经测试过 Snapshot 不能替代这一步。

Manifest 记录双分支源码 SHA、源码版本、各目标测试数量、文件名/大小/哈希、候选运行 ID、尝试号和工作流提交。main Fabric 的记录明确声明共享主线 NeoForge 测试。

重跑失败 job 会保留之前已成功目标，只接受相同源 SHA；完整重跑可重新解析 SHA，因此必须重新下载并测试新候选。发布前选择最高完成尝试的候选 Artifact，审批后固定使用选定的 Artifact ID 及 Manifest 摘要。

## 正式发布：只在你启动并批准后发生

在 Actions → **Publish** 选择 `main`，填写：

| 输入 | 含义 |
|---|---|
| `candidate_run_id` | 已下载并验收的成功 Candidate 运行 ID |
| `validation` | 测试人、时间、六平台人工验收记录或其链接 |
| `dry_run` | 默认勾选；只读验证，不创建标签或发布 |

先保留 `dry_run` 演练，正式发布时取消。真实发布会先锁定 Artifact ID 和 Manifest 摘要，再等待 `github-release` Environment 审批。你批准后，重新下载同一 Artifact、核对全部证据，然后创建 `v版本` → main SHA、`v版本-mc1.20` → compat SHA。六包及验证材料全部上传并核对后，Draft 才转成正式 Release 并设为 Latest。不会重新编译。

已存在标签必须指向同一源码 SHA；附件重试必须同名同哈希。已发布的正式版本只读，禁止覆盖、移动标签或替换附件。同一版本的源码已改变时，需要由你指定新版本后再正式发布。当前已发布 v1.5.2 不会被新的同版本开发包替换；dry-run 会明确报告这个阻止原因。

## 仓库设置与凭证

- 两分支 Ruleset 要求 PR 和 `CI Gate`，禁止强推及删除维护分支，代码审查人数不额外增加。
- Environment：`github-release`；审批人 `A-BenLi06`，允许自己发起并批准，禁止管理员跳过环境审批；仅允许 main。
- 在该 Environment 手动添加 `RELEASE_TOKEN`：仅限本仓库的 fine-grained token，权限为 **Contents: write、Workflows: write、Actions: read**。Workflows 权限用于创建涉及工作流文件的历史源码标签；Actions read 用于审批后下载锁定候选。不要将令牌提交到仓库或贴入聊天。
- Snapshot 使用默认 `GITHUB_TOKEN`，仅发布 job 获得 Contents write、Actions read。编译/PR 不获得发布令牌；正式发布令牌只在批准后的 job 中可用。
- 缺少 `RELEASE_TOKEN` 不影响 CI、Snapshot、Nightly、Candidate 和 Publish dry-run；真实发布会明确失败，不自动改用其他凭证。
- 首次上线在两个分支分别手动运行 `build` 初始化 Snapshot；正式发布仍须单独启动并批准。

## 本地验证

```powershell
python -m unittest discover -s tools/ci -p 'test_*.py' -v
python tools/verify_platform_matrix.py --compat ../mtrmap-mc120
# 兼容分支仍支持：
python tools/verify_artifacts.py --loader all --minecraft 1.20.4
```

两个分支的 `tools/ci`、`build.yml`、`build-target.yml` 必须同步更新。main 另外持有跨分支编排及发布工作流。首期不包含第三方渠道发布和自动启动 Minecraft。
