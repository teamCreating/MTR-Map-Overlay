# MESSAGES.md — Agent 交流墙（只追加，不改不删）

> 格式（追加在最上面）：
>
> ```
> ## [YYYY-MM-DD HH:MM] 操作者标识 — 一句话摘要
> 正文：背景 / 做了什么 / 需要谁注意什么 / 关联 commit 或任务。
> ```

## [2026-09-16 02:20] ZCode — v1.3.0 统一版：JourneyMap 集成并入主 mod
- journeymap 分支的地标功能已移植进 main（NeoForge 1.21.1）。注意 JourneyMap 6.x 移除了旧
  `journeymap.client.api`（v1），现在使用 **v2 API**（`journeymap.api.v2.*`），依赖来自官方 maven
  `info.journeymap:journeymap-api-neoforge:2.0.0-1.21.1-SNAPSHOT`（jm.gserv.me，已在 build.gradle 配置）。
- 集成入口 `integration/journeymap/JourneyMapIntegration`（无 JM imports，类加载隔离）；
  JM API 触点全部在 `JourneyMapLandmarkManager` 与 `MTRJourneyMapPlugin`（仅 JM 在场时加载）。
- 标记纹理现在位于 `assets/mtrsurveyor/textures/atlas/marker/`（旧分支的 mtrjourneymap 命名空间
  与实际 RL 不匹配的隐患已一并修复）。
- 实机验证：JM 6.0.8 下插件被发现、landmark 同步运行、testMarker 在全屏地图上实际渲染（有截图）。
- journeymap 分支自此可视为归档（功能已并入 main）。


## [2026-09-16 01:05] ZCode — 回退事故排查完毕，仓库健康，协作协议上线
- 今晚 00:50 有 agent 对本仓库执行了 `git reset`，此前曾把工作区+暂存区整体回退为废弃的 Forge 1.20.1
  架构（-1574 行：network/ 包、neoforge.mods.toml、路径层渲染器全被删）。所幸该次回退只停留在暂存区，
  已被 reset 丢弃；HEAD(v1.2.0, fa8ff07) 完好，`git diff HEAD` 为空，`./gradlew build` 通过，
  远端 origin/main 与本地一致。
- 措施：新建 AGENTS.md（三条铁律+工作流程）、本交流墙、STATE.md、TASKS.md。
- 提醒：所有 agent 开工前必读 AGENTS.md；平台已从 Forge 1.20.1 迁移到 NeoForge 1.21.1，
  旧记忆里的 "Forge 版本/main 分支" 描述一律以 STATE.md 为准。
