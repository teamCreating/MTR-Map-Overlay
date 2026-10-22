# TASKS.md — 任务看板

> 认领规则：把任务改成 `[进行中]` 并写上操作者+时间；完成后改成 `[完成]`（附 commit）。
> 中途放弃改回 `[待领]` 并注明原因。新任务加到对应分区末尾。
> 同一时间一个任务只能有一个操作者。改别人的任务前先在 MESSAGES.md 沟通。

## 进行中

### [功能] 非 TRAIN 轨道渲染开关
- `TrackSampler` 目前只采样 TRAIN 模式轨道。增加配置项按 TransportMode 过滤（BOAT/CABLE_CAR 可选）。
- 验收：配置切换后地图轨道层随之变化。

### [健壮性] Xaero mixin 未命中时显式提示
- `require = 0` 静默跳过导致用户不知道路径层失效。方案：MixinPlugin postApply 已有日志；
  补一个"应用了 mixin 但首帧日志 60 秒未出现"的启动后检查日志（或 README 已有说明，评估是否足够）。

### [文档] walkthrough.md 英文化（可选）
- 面向国际用户的英文版 walkthrough（当前为中文）。

## 完成

- [完成] GitHub v1.5.2正式发布 — Codex，2026-10-22 15:01。用户明确授权发布；六个附件沿用版本-MC-MTR-loader命名，附SHA256SUMS.txt及下载环境标签，中英文说明覆盖相对v1.5.1新增/优化/修复，末尾附v1.5.1...v1.5.2 diff。已设Latest并核对远端7附件大小/哈希/正文；main tag v1.5.2，兼容tag v1.5.2-mc1.20。版本继续固定1.5.2。[Release](https://github.com/teamCreating/MTR-Map-Overlay/releases/tag/v1.5.2)。

- [完成] 最新1.5.2移植及四兼容目标回归 — Codex，2026-10-22 10:00，见本条所在提交。纯2D/原生曲线/线路缩放/全图列车动态v1/三级车厢显示与视口索引完成移植；Java17、Core4.0只读车辆accessor、Forge47/49和Fabric通道适配。四目标各89测试、构建及生产产物结构检查通过；四独立服务端与Xaero/JourneyMap6/5实机回归通过。Forge1.20.4首轮原生GLFW崩溃，重试通过且保留未明原因记录。详见docs/validation/platform-matrix-1.5.2.md，版本固定1.5.2，距离/ETA暂停。

- [完成] 第二轮审查：轨道视口剔除、JourneyMap 图标批处理、分块重组校验及跨世界缓存清理 — Codex，2026-09-25 00:43，commit d63ea19；NeoForge/Fabric 构建和单测通过，待实机验证。
- [完成] 共用轨道采样缓存与寻路热路径优化 — Codex，2026-09-25 00:32，commit 78ae6fc；NeoForge/Fabric 构建与单测通过；仅改数据采集和寻路，不改协议格式。
- [完成] 双平台 v1.4.6 正式发布，发布说明按 v1.4.4 差异重写；误发的 v1.4.7 预发布及 tag 已删除 — Codex，2026-09-23 21:12，commit 8357133；NeoForge/Fabric 构建通过，MC 留给 Ben 手动验证。
- [完成] Fabric 1.21.1 并行构建与 NeoForge 双平台构建 — Codex，2026-09-23 15:36，commit f41e62e；源码保留，更正后以 v1.4.6 发布。
- [完成] Xaero 站点/站台图标与轨道共用地图变换，修复 Xaero/JourneyMap 站点纹理裁切 — Codex，2026-09-23 14:09，v1.4.6；`gradlew build` 通过，游戏由 Ben 手动验证。
- [完成] 地标改为地图内小图标，禁止写入普通路标系统 — Codex，2026-09-23 01:37，v1.4.5，commit 4b6146d；build/test 通过，MC 由 Ben 手动验证
- [完成] 全图站点/站台持久航点 + 共线轨道彩虹带 — Codex，2026-09-22，v1.4.4，commit 6a95614；build/test 通过，MC 由 Ben 手动验证
- [完成] ROUTE 直接复用 TRACK 的 MapTrack 数据、drawPolyline、线宽与透明度 — Codex，2026-09-20，v1.4.3，commit 46a87b9；build 通过，MC 由 Ben 手动验证
- [完成] 无 Depot 路径时严格贴轨回退（同 TrackSampler 采样；断路不画直线）— Codex，2026-09-20，v1.4.2，commit e436796；无头测试通过，MC 由 Ben 手动验证
- [完成] 版本哈希探测热更（协议 v3）+ 路线颜色改用 MTR 真实寻路结果（染色渲染） — ZCode，2026-09-20，v1.4.1
- [完成] 路线层沿轨道寻路吸附 + 共线车道偏移（协议 v2，实机验证） — ZCode，2026-09-20
- [完成] 统一版：JourneyMap 集成并入主 mod（v2 API，实机验证通过） — ZCode，2026-09-16
- [完成] NeoForge 1.21.1 移植 + 实机验证 — ZCode，2026-09-05，commit fa8ff07
- [完成] 全网同步（方案C）+ 轨道层 + 路径层重建 — ZCode，2026-09-05，commits 1e9ef5f..100bfab
- [完成] 回退事故排查与仓库健康验证 — ZCode，2026-09-16（HEAD 无损，详见 AGENTS.md §4）

## [完成] MTR 4.0.5 / MC 1.20.1 Forge + Fabric 兼容分支
- 操作者：Codex，2026-09-30 17:06。分支 compat/mtr-4.0-mc-1.20，独立 worktree；移植当前 v1.5.1 功能，保留 main 的 NeoForge/Fabric 1.21.1。
- 范围：构建、入口、网络、MTR 包名、地图渲染、依赖元数据、CI 与文档；无头构建/单测，游戏由 Ben 验证。

- 完成：Codex，2026-09-30 18:15。双构建及每平台 25 项测试、产物检查通过；实现 commit `4b3963e`。MC 1.20.4 尚未移植，实机验证待 Ben 完成。

## [完成] MC 1.20.4 Forge/Fabric 扩展
- 操作者：Codex，2026-09-30 18:58。代码 commit `5652056` 已推送兼容分支；保持 mod 版本 1.5.1，覆盖 MC 1.20.1 / 1.20.4、Forge/Fabric 四目标，各 25 项测试及产物检查通过。新增 Forge 49 / JourneyMap 5 适配，并修正 Fabric 两版本的 JourneyMap 插件入口；main 未改。实机检查由 Ben 手动完成。

## [完成] MC 1.20 实机与独立服务端检查、兼容 JAR 补发
- 操作者：Codex，2026-10-01 17:47。四目标独立服务端均完成开发环境启动；Xaero / JourneyMap 地图、四种图层状态、平移与缩放通过自动或用户手动验证。MTR 4.0.5 测试网格为 2 routes / 5 rails / 6 landmarks。
- MC 1.20.4 JourneyMap 5 工具栏 icon 参数修复初始化异常。四目标 build、每个 25 项测试与产物检查通过；mod、pack、metadata 均为 1.5.1。
- 代码/QA commit `4fce1a6` 已推送。本次打包的四个以 1.5.1 标注并包含 MC / MTR / loader 范围的 JAR 已加入 GitHub v1.5.1 release；保留原两个附件并更新中英文说明。production JAR 独立启动和跨机器联机未验证。
